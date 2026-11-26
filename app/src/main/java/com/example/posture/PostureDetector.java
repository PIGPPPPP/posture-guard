package com.example.posture;

import java.util.ArrayDeque;

/**
 * 纯 JVM 姿态判定状态机。刻意不引用任何 android.* API，可在 PC 上直接单测。
 *
 * 判定依据：归一化重力向量与「设备竖直参考轴」的夹角。
 *   竖屏：参考轴 = 长边 Y；横屏：参考轴 = 短边 X。
 *   0°  ≈ 设备竖直（正常），90° ≈ 设备水平（低头）。
 * 参考轴随物理朝向切换，由 setRotation(int) 注入（默认 0 = 竖屏）。
 * 取绝对值后该量对重力符号与 180° 反向不变；但横竖屏切换会改变参考轴，
 * 调用方须在切换时配合 reset() 清空判定窗口与状态机。
 * 时间由调用方以单调毫秒传入（服务用 SystemClock.elapsedRealtime()）。
 */
public final class PostureDetector {

    public enum State { NORMAL, ENTERING, LOW_HEAD, RECOVERING }

    public interface Callback {
        void onEnterLowHead();
        void onExitLowHead();
        void onUpdate(double tiltDeg, State state, boolean stationary, boolean flatStill);
    }

    /** 全部可调参数，默认值即产品默认值。 */
    public static final class Config {
        public double enterDeg = 40.0;          // 进入低头阈值
        public double exitDeg = 30.0;           // 退出阈值（滞回下界）
        public long enterHoldMs = 3000;         // 进入去抖
        public long exitHoldMs = 1000;          // 恢复去抖
        public long stillWindowMs = 8000;       // 样本保留上限
        public long stillObserveMs = 1500;      // 静置判定实际观察窗（须 < enterHoldMs）
        public long minObserveMs = 800;         // 最短稳定观察时长
        public double stillDelta = 0.06;        // 观察窗内归一化重力累计变化量阈值
        public double flatDeg = 78.0;           // 近水平辅助角
        public boolean flatStillSuppression = true; // 平放静默开关
        public double lpAlpha = 0.85;           // ACCELEROMETER 回退低通系数
    }

    private static final class Sample {
        final long ts;
        final double x, y, z;
        Sample(long ts, double x, double y, double z) {
            this.ts = ts; this.x = x; this.y = y; this.z = z;
        }
    }

    private Config cfg = new Config();
    private final ArrayDeque<Sample> win = new ArrayDeque<>();
    private Callback cb;

    private State state = State.NORMAL;
    private long t0;
    private boolean wasLowHead;
    private boolean lpInit;
    private double lx, ly, lz;

    private double tiltDeg;
    private double stillScore;
    private boolean stationary;
    private boolean flatStill;

    // 设备物理朝向（绕 Z 轴的 0/90/180/270），由调用方以原始数值注入；默认竖屏
    private int rotationDeg = 0;
    private boolean landscape = false;

    public void setCallback(Callback cb) { this.cb = cb; }

    public void configure(Config c) { if (c != null) this.cfg = c; }

    public Config getConfig() { return cfg; }
    public State getState() { return state; }
    public double getTiltDeg() { return tiltDeg; }
    public boolean isStationary() { return stationary; }
    public boolean isFlatStill() { return flatStill; }
    public double getStillScore() { return stillScore; }

    /**
     * 设置设备物理朝向（0/90/180/270，单位度）。由调用方以重力分量推算后以原始数值注入，
     * 本类零 android.* 依赖，纯 JVM 可编译。
     * @return 仅「横(90/270) ↔ 竖(0/180)」切换返回 true；同类别内 0↔180 / 90↔270 返回 false
     *         （Math.abs 已吞掉 180° 符号，无需重置判定）。调用方在返回 true 时须配合 reset()。
     */
    public boolean setRotation(int rotationDeg) {
        if (rotationDeg != 0 && rotationDeg != 90
                && rotationDeg != 180 && rotationDeg != 270) return false;
        boolean newLand = (rotationDeg == 90 || rotationDeg == 270);
        boolean changed = (newLand != this.landscape);
        this.rotationDeg = rotationDeg;
        this.landscape = newLand;
        return changed;
    }

    public int getRotation() { return rotationDeg; }
    public boolean isLandscape() { return landscape; }

    /**
     * 喂入一次采样。
     * @param isGravity true 表示已是重力向量（TYPE_GRAVITY）；false 表示原始加速度，需低通滤波
     * @param ts 单调毫秒时间戳
     */
    public State onSample(float gx, float gy, float gz, boolean isGravity, long ts) {
        double ax = gx, ay = gy, az = gz;
        if (!isGravity) {
            if (!lpInit) {
                lx = ax; ly = ay; lz = az; lpInit = true;
            } else {
                double a = cfg.lpAlpha;
                lx = a * lx + (1 - a) * ax;
                ly = a * ly + (1 - a) * ay;
                lz = a * lz + (1 - a) * az;
            }
            ax = lx; ay = ly; az = lz;
        }

        double n = Math.sqrt(ax * ax + ay * ay + az * az);
        if (n < 1e-6) return state;

        // 竖直参考轴随物理朝向切换：竖屏用长边 Y、横屏用短边 X。
        // Math.abs 吞掉重力符号与 180° 反向（反向竖屏 / 反向横屏归本类）。
        double ref = landscape ? Math.abs(ax) : Math.abs(ay);
        double ratio = ref / n;
        if (ratio > 1.0) ratio = 1.0;
        tiltDeg = Math.toDegrees(Math.acos(ratio));

        double nx = ax / n, ny = ay / n, nz = az / n;
        win.addLast(new Sample(ts, nx, ny, nz));
        while (!win.isEmpty() && ts - win.peekFirst().ts > cfg.stillWindowMs) {
            win.removeFirst();
        }

        // 仅统计最近 stillObserveMs 内的变化量：历史跳变不会长期污染静置判定
        long observeStart = ts - cfg.stillObserveMs;
        double score = 0;
        Sample prev = null;
        long firstTs = Long.MAX_VALUE;
        int count = 0;
        for (Sample s : win) {
            if (s.ts < observeStart) { prev = null; continue; }
            if (count == 0) firstTs = s.ts;
            if (prev != null) score += distance(s, prev);
            prev = s;
            count++;
        }
        stillScore = score;
        stationary = count >= 2
                && firstTs != Long.MAX_VALUE
                && (ts - firstTs) >= cfg.minObserveMs
                && score < cfg.stillDelta;
        // 近水平 + 几乎不动 = 平放桌面；手稳低头（倾角多在 40~70°）不会被屏蔽
        flatStill = cfg.flatStillSuppression && stationary && tiltDeg >= cfg.flatDeg;

        switch (state) {
            case NORMAL:
                if (flatStill) break;
                if (tiltDeg >= cfg.enterDeg) {
                    state = State.ENTERING;
                    t0 = ts;
                }
                break;
            case ENTERING:
                // 一旦低于进入阈值就取消并重新计时，临界抖动因此不会累积满去抖时长
                if (flatStill || tiltDeg < cfg.enterDeg) {
                    state = State.NORMAL;
                } else if (ts - t0 >= cfg.enterHoldMs) {
                    state = State.LOW_HEAD;
                    wasLowHead = true;
                    if (cb != null) cb.onEnterLowHead();
                }
                break;
            case LOW_HEAD:
                if (flatStill) {
                    state = State.NORMAL;
                    fireExit();
                } else if (tiltDeg <= cfg.exitDeg) {
                    state = State.RECOVERING;
                    t0 = ts;
                }
                break;
            case RECOVERING:
                if (flatStill) {
                    state = State.NORMAL;
                    fireExit();
                } else if (tiltDeg >= cfg.enterDeg) {
                    state = State.LOW_HEAD;
                } else if (ts - t0 >= cfg.exitHoldMs) {
                    state = State.NORMAL;
                    fireExit();
                }
                break;
            default:
                break;
        }

        if (cb != null) cb.onUpdate(tiltDeg, state, stationary, flatStill);
        return state;
    }

    private void fireExit() {
        if (wasLowHead) {
            wasLowHead = false;
            if (cb != null) cb.onExitLowHead();
        }
    }

    private static double distance(Sample a, Sample b) {
        double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 熄屏、重新解锁、暂停或参数变更后调用，清空窗口与状态。
     * 方向字段（rotationDeg / landscape）不在清空之列：它是设备物理属性，
     * 方向变更须由调用方单独调用 setRotation() 并配合本方法。
     */
    public void reset() {
        win.clear();
        state = State.NORMAL;
        t0 = 0;
        wasLowHead = false;
        lpInit = false;
        lx = ly = lz = 0;
        tiltDeg = 0;
        stillScore = 0;
        stationary = false;
        flatStill = false;
    }
}
