package com.example.posture;

/** 纯 JVM 自测：无需 Android / Gradle / JUnit，直接用 javac + java 运行。 */
public final class PostureDetectorSelfTest {

    private static int pass = 0;
    private static int fail = 0;

    private static void check(boolean ok, String name, String detail) {
        if (ok) {
            pass++;
            System.out.println("PASS  " + name);
        } else {
            fail++;
            System.out.println("FAIL  " + name + "   " + detail);
        }
    }

    private static final class Counter implements PostureDetector.Callback {
        int enter, exit;
        double lastTilt;
        boolean lastFlatStill, lastStationary;
        PostureDetector.State lastState;

        @Override public void onEnterLowHead() { enter++; }
        @Override public void onExitLowHead() { exit++; }
        @Override public void onUpdate(double tiltDeg, PostureDetector.State state,
                                       boolean stationary, boolean flatStill) {
            lastTilt = tiltDeg;
            lastState = state;
            lastStationary = stationary;
            lastFlatStill = flatStill;
        }
    }

    private static final class Rig {
        final PostureDetector d = new PostureDetector();
        final Counter c = new Counter();
        long ts = 0;

        Rig(PostureDetector.Config cfg) {
            if (cfg != null) d.configure(cfg);
            d.setCallback(c);
        }

        void feed(double gx, double gy, double gz, long ms) {
            long end = ts + ms;
            while (ts < end) {
                ts += 50;
                d.onSample((float) gx, (float) gy, (float) gz, true, ts);
            }
        }

        /** 以 baseTiltDeg 为中心、ampDeg 为振幅的正弦抖动，noise 叠加在 X 轴。 */
        void feedJitter(double baseTiltDeg, double ampDeg, double noise, long ms, long seed) {
            java.util.Random r = new java.util.Random(seed);
            long end = ts + ms;
            while (ts < end) {
                ts += 50;
                double t = Math.toRadians(baseTiltDeg + ampDeg * Math.sin(ts / 300.0));
                double gy = 9.8 * Math.cos(t);
                double gz = 9.8 * Math.sin(t);
                double gx = noise * (r.nextDouble() - 0.5);
                d.onSample((float) gx, (float) gy, (float) gz, true, ts);
            }
        }

        /** 模拟服务侧方向切换：setRotation + reset（撤提醒属 Android 侧，纯 JVM 不覆盖）。 */
        void rotation(int deg) {
            d.setRotation(deg);
            d.reset();
        }
    }

    private static double[] tiltVector(double tiltDeg, double split) {
        double t = Math.toRadians(tiltDeg);
        double gy = 9.8 * Math.cos(t);
        double rest = 9.8 * Math.sin(t);
        double gx = rest * Math.cos(split);
        double gz = rest * Math.sin(split);
        return new double[]{gx, gy, gz};
    }

    /** 横屏低头：绕设备 Y 轴俯仰，重力在 X/Z 平面分配，gy≈0。 */
    private static double[] landPitchVector(double tiltDeg) {
        double t = Math.toRadians(tiltDeg);
        return new double[]{9.8 * Math.cos(t), 0.0, 9.8 * Math.sin(t)};
    }

    public static void main(String[] args) {
        System.out.println("=== PostureDetector 纯 JVM 自测 ===");

        // ① 竖直 10s 恒 NORMAL
        {
            Rig r = new Rig(null);
            r.feed(0, 9.8, 0, 10000);
            check(r.d.getState() == PostureDetector.State.NORMAL && r.d.getTiltDeg() < 1.0
                            && r.c.enter == 0,
                    "① 竖直 10s 恒正常",
                    "state=" + r.d.getState() + " tilt=" + r.d.getTiltDeg() + " enter=" + r.c.enter);
        }

        // ② 倾 55° 持续 5s -> LOW_HEAD，onEnter 恰好 1 次
        {
            Rig r = new Rig(null);
            double[] g = tiltVector(55, 0);
            r.feed(g[0], g[1], g[2], 5000);
            check(r.d.getState() == PostureDetector.State.LOW_HEAD && r.c.enter == 1,
                    "② 倾斜 55° 5s 判定低头",
                    "state=" + r.d.getState() + " tilt=" + r.d.getTiltDeg() + " enter=" + r.c.enter);
        }

        // ③ 恢复竖直 -> onExit 1 次
        {
            Rig r = new Rig(null);
            double[] g = tiltVector(55, 0);
            r.feed(g[0], g[1], g[2], 5000);
            r.feed(0, 9.8, 0, 2000);
            check(r.d.getState() == PostureDetector.State.NORMAL && r.c.exit == 1,
                    "③ 恢复竖直后自动解除",
                    "state=" + r.d.getState() + " exit=" + r.c.exit);
        }

        // ④ 临界抖动 40°±3° 10s 不进入；再稳在 45° 3.5s 才进入
        {
            Rig r = new Rig(null);
            r.feedJitter(40, 3, 0, 10000, 42);
            boolean notEntered = r.c.enter == 0;
            check(notEntered, "④a 临界抖动 10s 不误报",
                    "enter=" + r.c.enter + " state=" + r.d.getState());
            double[] g = tiltVector(45, 0);
            r.feed(g[0], g[1], g[2], 3500);
            check(r.c.enter == 1 && r.d.getState() == PostureDetector.State.LOW_HEAD,
                    "④b 稳定 45° 3.5s 正常进入",
                    "enter=" + r.c.enter + " state=" + r.d.getState());
        }

        // ⑤ 平放桌面（水平 + 极小噪声）10s 全程静默
        {
            Rig r = new Rig(null);
            java.util.Random rnd = new java.util.Random(9);
            long end = r.ts + 10000;
            while (r.ts < end) {
                r.ts += 50;
                float gx = (float) (0.002 * (rnd.nextDouble() - 0.5));
                r.d.onSample(gx, 0f, 9.8f, true, r.ts);
            }
            check(r.c.enter == 0 && r.d.getState() == PostureDetector.State.NORMAL
                            && r.c.lastFlatStill,
                    "⑤ 平放桌面静默不误报",
                    "enter=" + r.c.enter + " flatStill=" + r.c.lastFlatStill
                            + " stationary=" + r.c.lastStationary
                            + " tilt=" + r.d.getTiltDeg());
        }

        // ⑥ 手持稳定低头（55° + 微抖）仍应进入，不被静置误屏蔽
        {
            Rig r = new Rig(null);
            r.feedJitter(55, 0, 0.15, 5000, 7);
            check(r.c.enter == 1 && r.d.getState() == PostureDetector.State.LOW_HEAD,
                    "⑥ 手持微抖低头不被屏蔽",
                    "enter=" + r.c.enter + " state=" + r.d.getState()
                            + " flatStill=" + r.c.lastFlatStill);
        }

        // ⑦ 竖屏下：|gy|/|g| 相同、X/Z 轴分配不同 -> 倾角一致
        {
            boolean ok = true;
            double first = -1;
            double[] splits = {0, Math.PI / 6, Math.PI / 3, Math.PI / 2, Math.PI * 0.8};
            String detail = "";
            for (double s : splits) {
                Rig r = new Rig(null);
                double[] g = tiltVector(55, s);
                r.feed(g[0], g[1], g[2], 50);
                double t = r.d.getTiltDeg();
                if (first < 0) first = t;
                if (Math.abs(t - first) > 0.01) ok = false;
                detail += String.format("%.2f ", t);
            }
            check(ok && Math.abs(first - 55.0) < 0.01,
                    "⑦ 竖屏下重力在 X/Z 轴分配不影响倾角",
                    "tilts=[" + detail + "] expect=55.00");
        }

        // ⑧ 平放静默开关关闭后，水平静止应正常判定为低头
        {
            PostureDetector.Config cfg = new PostureDetector.Config();
            cfg.flatStillSuppression = false;
            Rig r = new Rig(cfg);
            r.feed(0, 0, 9.8, 5000);
            check(r.c.enter == 1 && r.d.getState() == PostureDetector.State.LOW_HEAD,
                    "⑧ 关闭静置抑制后水平可判低头",
                    "enter=" + r.c.enter + " state=" + r.d.getState());
        }

        // ---------- v3.1 新增：横屏换轴后的识别与方向切换 ----------

        // ⑨ 横屏竖直（重力落在短边 X）应恒正常，且不能被误当成「平放桌面」静默
        {
            Rig r = new Rig(null);
            r.rotation(90);
            r.feed(9.8, 0, 0, 10000);
            check(r.d.getState() == PostureDetector.State.NORMAL && r.d.getTiltDeg() < 1.0
                            && r.c.enter == 0 && !r.c.lastFlatStill,
                    "⑨ 横屏竖直 10s 恒正常（不被当成平放静默）",
                    "state=" + r.d.getState() + " tilt=" + r.d.getTiltDeg()
                            + " enter=" + r.c.enter + " flatStill=" + r.c.lastFlatStill);
        }

        // ⑩ 横屏低头 55°（绕 Y 轴俯仰）应正常判定；旧公式此处恒 90° 且对俯仰零灵敏度
        {
            Rig r = new Rig(null);
            r.rotation(90);
            double[] g = landPitchVector(55);
            r.feed(g[0], g[1], g[2], 5000);
            check(r.d.getState() == PostureDetector.State.LOW_HEAD && r.c.enter == 1
                            && !r.c.lastFlatStill,
                    "⑩ 横屏低头 55° 5s 判定低头",
                    "state=" + r.d.getState() + " tilt=" + r.d.getTiltDeg()
                            + " enter=" + r.c.enter + " flatStill=" + r.c.lastFlatStill);
        }

        // ⑪ 横屏恢复竖直后自动解除
        {
            Rig r = new Rig(null);
            r.rotation(90);
            double[] g = landPitchVector(55);
            r.feed(g[0], g[1], g[2], 5000);
            r.feed(9.8, 0, 0, 2000);
            check(r.d.getState() == PostureDetector.State.NORMAL && r.c.exit == 1,
                    "⑪ 横屏恢复竖直后自动解除",
                    "state=" + r.d.getState() + " exit=" + r.c.exit);
        }

        // ⑫ 横屏平放桌面仍应静默（换轴后 |ax|/n≈0 -> 90° >= flatDeg）
        {
            Rig r = new Rig(null);
            r.rotation(90);
            java.util.Random rnd = new java.util.Random(9);
            long end = r.ts + 10000;
            while (r.ts < end) {
                r.ts += 50;
                float gx = (float) (0.002 * (rnd.nextDouble() - 0.5));
                r.d.onSample(gx, 0f, 9.8f, true, r.ts);
            }
            check(r.c.enter == 0 && r.d.getState() == PostureDetector.State.NORMAL
                            && r.c.lastFlatStill,
                    "⑫ 横屏平放桌面仍静默",
                    "enter=" + r.c.enter + " flatStill=" + r.c.lastFlatStill
                            + " tilt=" + r.d.getTiltDeg());
        }

        // ⑬ 横竖切换：不重复提醒、不误报、链路仍完整
        {
            Rig r = new Rig(null);
            double[] p = tiltVector(55, Math.PI / 2);   // 竖屏低头：gx=0，重力在 Y/Z 平面
            r.feed(p[0], p[1], p[2], 5000);
            int before = r.c.enter;                     // 应为 1
            r.rotation(90);
            check(r.d.getState() == PostureDetector.State.NORMAL
                            && r.c.enter == before && r.c.exit == 0,
                    "⑬a 竖→横切换归零，不重复提醒",
                    "state=" + r.d.getState() + " enter=" + r.c.enter + " exit=" + r.c.exit);

            r.feed(9.8, 0, 0, 10000);
            check(r.c.enter == before && r.d.getState() == PostureDetector.State.NORMAL
                            && !r.c.lastFlatStill,
                    "⑬b 切换后横屏竖直不误报",
                    "enter=" + r.c.enter + " state=" + r.d.getState()
                            + " flatStill=" + r.c.lastFlatStill);

            r.rotation(0);
            double[] p2 = tiltVector(55, Math.PI / 2);
            r.feed(p2[0], p2[1], p2[2], 5000);
            boolean lowAgain = r.c.enter == before + 1
                    && r.d.getState() == PostureDetector.State.LOW_HEAD;
            r.feed(0, 9.8, 0, 2000);
            check(lowAgain && r.c.exit == 1 && r.d.getState() == PostureDetector.State.NORMAL,
                    "⑬c 横→竖后判定与解除链路完整",
                    "enter=" + r.c.enter + " exit=" + r.c.exit
                            + " state=" + r.d.getState());
        }

        // ⑭ 横屏 + ACCELEROMETER 回退低通分支（原有用例全部 isGravity=true，从未覆盖）
        {
            Rig r = new Rig(null);
            r.rotation(90);
            long end = r.ts + 5000;
            while (r.ts < end) {
                r.ts += 50;
                r.d.onSample(5.621f, 0f, 8.028f, false, r.ts);
            }
            check(r.c.enter == 1 && r.d.getState() == PostureDetector.State.LOW_HEAD,
                    "⑭ 横屏 + 低通回退仍正确判定",
                    "enter=" + r.c.enter + " state=" + r.d.getState()
                            + " tilt=" + r.d.getTiltDeg());
        }

        System.out.println("=== 结果: PASS=" + pass + " FAIL=" + fail + " ===");
        if (fail > 0) System.exit(1);
    }
}
