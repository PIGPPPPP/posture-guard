package com.example.posture;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

/** 前台服务：传感器采集、姿态判定、提醒调度、屏幕状态联动。 */
public final class PostureService extends Service
        implements SensorEventListener, PostureDetector.Callback {

    private static final String TAG = "PostureService";

    public static final String ACTION_PAUSE = "com.example.posture.PAUSE";
    public static final String ACTION_RESUME = "com.example.posture.RESUME";
    public static final String ACTION_REFRESH_CONFIG = "com.example.posture.REFRESH_CONFIG";

    public interface UiListener {
        void onUpdate(double tiltDeg, String status, boolean monitoring);
    }

    public final class LocalBinder extends Binder {
        public PostureService getService() { return PostureService.this; }
    }

    private final IBinder binder = new LocalBinder();
    private UiListener uiListener;

    private Prefs prefs;
    private NotifyHelper notifyHelper;
    private OverlayReminder overlay;
    private PostureDetector detector;
    private SensorManager sensorManager;
    private Sensor sensor;
    private boolean isGravitySensor;
    private PowerManager powerManager;
    private KeyguardManager keyguardManager;
    private Handler handler;

    private boolean listening;
    private boolean lowHead;
    private long lastRemindMs;
    private long lastUiPushMs;

    // 物理朝向（绕设备 Z 轴的 0/90/180/270）：完全由重力分量推算，
    // 不读系统 UI 方向，因此与「自动旋转」开关无关，后台也可识别。
    private static final long ROT_STABLE_MS = 400;        // 方向最短稳定时长，须 < enterHoldMs(3000)
    private static final double ROT_HYSTERESIS_DEG = 10.0; // 45°±10° → 切换带 [35°,55°)
    private static final double FLAT_PLANE_RATIO = 0.20;  // 平放退化（≈cos78°）
    private int stableRotation = 0;    // 已确认方向，默认竖屏
    private int pendingRotation = 0;   // 候选方向
    private long pendingSinceMs = 0;   // 候选首次出现时刻（elapsedRealtime）

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(a)
                    || Intent.ACTION_SCREEN_OFF.equals(a)
                    || Intent.ACTION_USER_PRESENT.equals(a)) {
                evalMonitoring();
            }
        }
    };

    private final Runnable resumeRunnable = new Runnable() {
        @Override
        public void run() {
            prefs.setPausedUntilWallMs(0);
            evalMonitoring();
        }
    };

    /** 「试显示一次」用的自动收起回调，onDestroy 会统一清掉。 */
    private final Runnable hideOverlayOnce = () -> overlay.hide();

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        notifyHelper = new NotifyHelper(this, prefs);
        overlay = new OverlayReminder(this);
        detector = new PostureDetector();
        detector.setCallback(this);
        detector.configure(prefs.buildDetectorConfig());
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        handler = new Handler(Looper.getMainLooper());

        startAsForeground();

        // 这三个广播带 FLAG_RECEIVER_REGISTERED_ONLY，只能动态注册
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, f);
        }

        evalMonitoring();
    }

    private void startAsForeground() {
        Notification n = notifyHelper.buildOngoing("正在启动监测…", false);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ 必须传入与 manifest 一致的类型
                startForeground(NotifyHelper.ID_ONGOING, n,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NotifyHelper.ID_ONGOING, n);
            }
        } catch (Exception e) {
            Log.e(TAG, "startForeground 失败", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PAUSE.equals(action)) {
            pauseForAWhile();
        } else if (ACTION_RESUME.equals(action)) {
            prefs.setPausedUntilWallMs(0);
            handler.removeCallbacks(resumeRunnable);
            evalMonitoring();
        } else if (ACTION_REFRESH_CONFIG.equals(action)) {
            detector.configure(prefs.buildDetectorConfig());
            detector.reset();
            clearLowHead();
            evalMonitoring();
        } else {
            evalMonitoring();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    public void setUiListener(UiListener l) {
        uiListener = l;
        pushUi(true);
    }

    public boolean isMonitoring() { return listening; }
    public boolean isLowHead() { return lowHead; }
    public double getTiltDeg() { return detector.getTiltDeg(); }

    public String getStatusText() {
        if (!prefs.isEnabled()) return "已关闭";
        if (prefs.isPaused()) return "已暂停";
        if (!listening) return "未监测（熄屏 / 锁屏）";
        if (detector.isFlatStill()) return "静置 · 平放";
        if (lowHead) return "低头中";
        if (detector.getState() == PostureDetector.State.ENTERING) return "疑似低头确认中";
        return "正常";
    }

    /** 仅在 亮屏 + 总开关开 + 未暂停 时采样；锁屏时是否监测由开关决定，熄屏始终不采样。 */
    private void evalMonitoring() {
        boolean interactive = powerManager != null && powerManager.isInteractive();
        boolean locked = keyguardManager != null && keyguardManager.isKeyguardLocked();
        boolean active = prefs.isEnabled() && !prefs.isPaused() && interactive
                && (prefs.isMonitorWhenLocked() || !locked);

        if (active && !listening) {
            startMonitoring();
        } else if (!active && listening) {
            stopMonitoring();
        }
        notifyHelper.updateOngoing(ongoingText(active), prefs.isPaused());
        pushUi(true);
    }

    private String ongoingText(boolean active) {
        if (!prefs.isEnabled()) return "监测已关闭";
        if (prefs.isPaused()) return "已暂停，点「立即恢复」继续";
        if (!active) return "待机中（亮屏解锁后自动监测）";
        if (lowHead) return "检测到低头，请抬起手机";
        return "监测中 · 请把手机举到视线高度";
    }

    private void startMonitoring() {
        detector.configure(prefs.buildDetectorConfig());
        detector.reset();
        if (sensorManager == null) return;
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        isGravitySensor = true;
        if (sensor == null) {
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            isGravitySensor = false;
        }
        if (sensor != null) {
            listening = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            listening = false;
        }
        lastRemindMs = 0;
    }

    private void stopMonitoring() {
        if (listening && sensorManager != null) {
            try { sensorManager.unregisterListener(this); } catch (Exception ignored) { }
        }
        listening = false;
        pendingRotation = stableRotation;
        pendingSinceMs = 0;
        clearLowHead();          // 先撤提醒，reset 不会再触发 onExit
        detector.reset();
    }

    private void pauseForAWhile() {
        long until = System.currentTimeMillis() + prefs.getPauseMinutes() * 60_000L;
        prefs.setPausedUntilWallMs(until);
        handler.removeCallbacks(resumeRunnable);
        handler.postDelayed(resumeRunnable, prefs.getPauseMinutes() * 60_000L);
        evalMonitoring();
    }

    /** 无条件清理：关闭某通道开关时，已弹出的旧悬浮条/横幅也必须撤掉。 */
    private void clearLowHead() {
        if (lowHead) {
            lowHead = false;
            overlay.hide();
            notifyHelper.cancelLowHead();
        } else {
            overlay.hide();
            notifyHelper.cancelLowHead();
        }
        lastRemindMs = 0;
    }

    /**
     * 单帧几何判据：由重力在屏幕平面(XY)内的方位推算设备物理朝向。
     * 0/90/180/270 仅靠 atan2(|gy|,|gx|) 的象限给出；-1 = 平放/不可辨（保持上次方向）。
     * 竖屏低头是绕 X 轴俯仰（gx≈0 → phi≈90°），横屏竖直是绕 Z 轴 90°（phi≈0°），
     * 因此俯仰不会改变方位归属，只有横竖翻转才会。纯数值、无状态、无 Android API。
     */
    private static int rotationQuadrant(double gx, double gy, double gz, boolean curLandscape) {
        double n = Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (n < 1e-6) return -1;
        double plane = Math.hypot(gx, gy);
        if (plane < FLAT_PLANE_RATIO * n) return -1;      // 平放：方向不可辨
        double phi = Math.toDegrees(Math.atan2(Math.abs(gy), Math.abs(gx)));
        double threshold = 45.0 + (curLandscape ? ROT_HYSTERESIS_DEG : -ROT_HYSTERESIS_DEG);
        boolean land = (phi < threshold);                 // 竖→横需 phi<35；横→竖需 phi>55
        return land ? (gx >= 0 ? 90 : 270) : (gy >= 0 ? 0 : 180);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long ts = SystemClock.elapsedRealtime();
        float gx = event.values[0], gy = event.values[1], gz = event.values[2];

        boolean rotated = false;
        boolean curLand = (stableRotation == 90 || stableRotation == 270);
        int q = rotationQuadrant(gx, gy, gz, curLand);
        if (q >= 0) {
            boolean qLand = (q == 90 || q == 270);
            if (qLand != curLand) {
                // 候选计时必须按「横/竖类别」比较：横屏时 gx 符号翻转会让 q 在
                // 90↔270 间跳，若比对完整 q 值会反复重置 400ms 计时、切换被卡住。
                boolean pendingLand = (pendingRotation == 90 || pendingRotation == 270);
                if (qLand != pendingLand) {
                    pendingRotation = q;
                    pendingSinceMs = ts;
                } else if (ts - pendingSinceMs >= ROT_STABLE_MS) {
                    applyRotation(q);
                    rotated = true;
                }
            } else {
                pendingRotation = stableRotation;
                if (q != stableRotation) {      // 同类别 0↔180 / 90↔270：只更新，不 reset
                    stableRotation = q;
                    detector.setRotation(q);    // 返回 false，无副作用
                }
            }
        } else {
            pendingRotation = stableRotation;  // 平放：方向不可辨，保持
        }

        // 方向处理必须先于采样：reset 后本帧即用新轴计算，win 不混入旧轴样本
        detector.onSample(gx, gy, gz, isGravitySensor, ts);
        // 必须放在本帧采样之后，否则 UI 会短暂显示 reset 归零的 0.0°
        if (rotated) pushUi(true);
    }

    /** 一次方向变化 = 一组原子操作；沿用 startMonitoring / stopMonitoring / REFRESH_CONFIG 的模式。 */
    private void applyRotation(int q) {
        stableRotation = q;
        pendingRotation = q;
        pendingSinceMs = 0;
        detector.setRotation(q);
        detector.reset();          // 清 win/state/t0/wasLowHead/lpInit，切断跨旋转样本
        clearLowHead();            // 补 reset 吞掉的 onExit：撤悬浮条/横幅/通知（幂等）
        notifyHelper.updateOngoing(ongoingText(listening), prefs.isPaused());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    public void onEnterLowHead() {
        lowHead = true;
        lastRemindMs = SystemClock.elapsedRealtime();
        fireRemindChannels(true);
        notifyHelper.updateOngoing(ongoingText(listening), prefs.isPaused());
        pushUi(true);
    }

    @Override
    public void onExitLowHead() {
        clearLowHead();
        notifyHelper.updateOngoing(ongoingText(listening), prefs.isPaused());
        pushUi(true);
    }

    /** 按当前开关触发提醒通道；withOverlay=false 用于重复提醒（v1 语义：不重发悬浮条）。 */
    private void fireRemindChannels(boolean withOverlay) {
        if (withOverlay && prefs.isOverlayEnabled()) overlay.show(prefs.getOverlayText());
        if (prefs.isBannerEnabled()) notifyHelper.notifyLowHead();
        if (prefs.isVibrateEnabled()) notifyHelper.vibrateOnce();
    }

    // ---------- 悬浮条外观预览（由已绑定的 MainActivity 直接调用，不走广播） ----------

    /** 外观参数变化：只刷新样式，不重置判定、不撤掉正在显示的提醒。 */
    public void refreshOverlay() {
        overlay.refresh();
    }

    /** 在真实悬浮窗里试显示 2.5 秒，随后自动淡出。 */
    public void showOverlayOnce() {
        overlay.show(prefs.getOverlayText());
        handler.removeCallbacks(hideOverlayOnce);
        handler.postDelayed(hideOverlayOnce, 2500);
    }

    @Override
    public void onUpdate(double tiltDeg, PostureDetector.State state,
                         boolean stationary, boolean flatStill) {
        long now = SystemClock.elapsedRealtime();
        if (lowHead && prefs.isRepeatEnabled() && lastRemindMs > 0
                && now - lastRemindMs >= prefs.getRepeatIntervalMs()) {
            lastRemindMs = now;
            fireRemindChannels(false);   // 同一 ID 覆盖，不堆叠；不重发悬浮条
        }
        if (now - lastUiPushMs >= 200) pushUi(false);
    }

    private void pushUi(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastUiPushMs < 200) return;
        lastUiPushMs = now;
        if (uiListener != null) {
            try {
                uiListener.onUpdate(detector.getTiltDeg(), getStatusText(), listening);
            } catch (Exception ignored) { }
        }
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) { }
        overlay.destroy();                      // 先 cancel 动画 + 同步 removeView，防窗口泄漏
        handler.removeCallbacksAndMessages(null);
        stopMonitoring();                       // 其 clearLowHead() 的 hide() 已幂等
        notifyHelper.cancelLowHead();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
