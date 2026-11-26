package com.example.posture;

import android.content.Context;
import android.content.SharedPreferences;

/** 全部可调参数、功能开关与向导确认标志的本地存储封装。 */
public final class Prefs {

    private static final String FILE = "posture_prefs";

    private static final String K_ENABLED = "enabled";
    private static final String K_ENTER_DEG = "enter_deg";
    private static final String K_EXIT_DEG = "exit_deg";
    private static final String K_ENTER_HOLD = "enter_hold_ms";
    private static final String K_EXIT_HOLD = "exit_hold_ms";
    private static final String K_STILL_WINDOW = "still_window_ms";
    private static final String K_STILL_OBSERVE = "still_observe_ms";
    private static final String K_MIN_OBSERVE = "min_observe_ms";
    private static final String K_STILL_DELTA = "still_delta";
    private static final String K_FLAT_DEG = "flat_deg";
    private static final String K_FLAT_SUPPRESS = "flat_still_suppression";
    private static final String K_LP_ALPHA = "lp_alpha";
    private static final String K_REPEAT = "repeat_enabled";
    private static final String K_REPEAT_INTERVAL = "repeat_interval_ms";
    private static final String K_VIBRATE = "vibrate_enabled";
    private static final String K_VIBRATE_MS = "vibrate_ms";
    private static final String K_PAUSE_MIN = "pause_minutes";
    private static final String K_BOOT = "boot_start_enabled";
    private static final String K_PAUSED_UNTIL = "paused_until_wall_ms";

    // v2 新增：功能开关
    private static final String K_OVERLAY = "overlay_enabled";
    private static final String K_BANNER = "banner_enabled";
    private static final String K_SOUND = "sound_enabled";
    private static final String K_MONITOR_LOCKED = "monitor_when_locked";
    private static final String K_PAUSE_BTN = "pause_button_enabled";
    private static final String K_ONGOING_PAUSE = "ongoing_pause_action";
    private static final String K_ONGOING_GUIDE = "ongoing_guide_action";
    private static final String K_ONGOING_DETAIL = "ongoing_text_detail";
    private static final String K_GUIDE_ACK = "guide_ack_version";

    // v3 新增：悬浮条外观参数，默认值全部与 v2 硬编码行为一致
    private static final String K_OVERLAY_TEXT = "overlay_text";
    private static final String K_OVERLAY_WIDTH = "overlay_width_pct";
    private static final String K_OVERLAY_Y = "overlay_y_dp";
    private static final String K_OVERLAY_BG = "overlay_bg_color";
    private static final String K_OVERLAY_ALPHA = "overlay_bg_alpha";
    private static final String K_OVERLAY_RADIUS = "overlay_radius_dp";
    private static final String K_OVERLAY_TEXT_SP = "overlay_text_size_sp";

    /** 与 app/build.gradle 的 versionCode 同步；升级版本时 +1，首屏提示条会自动重新出现。 */
    public static final int ACK_VERSION = 3;

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() { return sp.getBoolean(K_ENABLED, true); }
    public void setEnabled(boolean v) { sp.edit().putBoolean(K_ENABLED, v).apply(); }

    public double getEnterDeg() { return getDouble(K_ENTER_DEG, 40.0); }
    public double getExitDeg() { return getDouble(K_EXIT_DEG, 30.0); }
    public long getEnterHoldMs() { return sp.getLong(K_ENTER_HOLD, 3000); }
    public long getExitHoldMs() { return sp.getLong(K_EXIT_HOLD, 1000); }
    public long getStillWindowMs() { return sp.getLong(K_STILL_WINDOW, 8000); }
    public long getStillObserveMs() { return sp.getLong(K_STILL_OBSERVE, 1500); }
    public long getMinObserveMs() { return sp.getLong(K_MIN_OBSERVE, 800); }
    public double getStillDelta() { return getDouble(K_STILL_DELTA, 0.06); }
    public double getFlatDeg() { return getDouble(K_FLAT_DEG, 78.0); }
    public boolean isFlatStillSuppression() { return sp.getBoolean(K_FLAT_SUPPRESS, true); }
    public double getLpAlpha() { return getDouble(K_LP_ALPHA, 0.85); }
    public boolean isRepeatEnabled() { return sp.getBoolean(K_REPEAT, true); }
    public long getRepeatIntervalMs() { return sp.getLong(K_REPEAT_INTERVAL, 30000); }
    public boolean isVibrateEnabled() { return sp.getBoolean(K_VIBRATE, true); }
    public long getVibrateMs() { return sp.getLong(K_VIBRATE_MS, 120); }
    public int getPauseMinutes() { return sp.getInt(K_PAUSE_MIN, 15); }
    public boolean isBootStartEnabled() { return sp.getBoolean(K_BOOT, true); }

    // v2 新增开关：默认值全部与 v1 行为一致
    public boolean isOverlayEnabled() { return sp.getBoolean(K_OVERLAY, true); }
    public boolean isBannerEnabled() { return sp.getBoolean(K_BANNER, true); }
    public boolean isSoundEnabled() { return sp.getBoolean(K_SOUND, true); }
    public boolean isMonitorWhenLocked() { return sp.getBoolean(K_MONITOR_LOCKED, false); }
    public boolean isPauseButtonEnabled() { return sp.getBoolean(K_PAUSE_BTN, true); }
    public boolean isOngoingPauseAction() { return sp.getBoolean(K_ONGOING_PAUSE, true); }
    public boolean isOngoingGuideAction() { return sp.getBoolean(K_ONGOING_GUIDE, true); }
    public boolean isOngoingTextDetail() { return sp.getBoolean(K_ONGOING_DETAIL, true); }
    public int getGuideAckVersion() { return sp.getInt(K_GUIDE_ACK, 0); }

    // v3：悬浮条外观 getter
    public String getOverlayText() { return sp.getString(K_OVERLAY_TEXT, "请抬起手机 · 别低头"); }
    public int getOverlayWidthPct() { return sp.getInt(K_OVERLAY_WIDTH, 100); }
    public int getOverlayYDp() { return sp.getInt(K_OVERLAY_Y, 28); }
    public int getOverlayBgColor() { return sp.getInt(K_OVERLAY_BG, 0xFF2B3240); }
    public int getOverlayBgAlpha() { return sp.getInt(K_OVERLAY_ALPHA, 230); }
    public int getOverlayRadiusDp() { return sp.getInt(K_OVERLAY_RADIUS, 20); }
    public int getOverlayTextSizeSp() { return sp.getInt(K_OVERLAY_TEXT_SP, 15); }

    public long getPausedUntilWallMs() { return sp.getLong(K_PAUSED_UNTIL, 0); }
    public void setPausedUntilWallMs(long v) { sp.edit().putLong(K_PAUSED_UNTIL, v).apply(); }
    public boolean isPaused() { return System.currentTimeMillis() < getPausedUntilWallMs(); }

    public void setEnterDeg(double v) { putDouble(K_ENTER_DEG, v); }
    public void setExitDeg(double v) { putDouble(K_EXIT_DEG, v); }
    public void setEnterHoldMs(long v) { sp.edit().putLong(K_ENTER_HOLD, v).apply(); }
    public void setExitHoldMs(long v) { sp.edit().putLong(K_EXIT_HOLD, v).apply(); }
    public void setFlatDeg(double v) { putDouble(K_FLAT_DEG, v); }
    public void setStillDelta(double v) { putDouble(K_STILL_DELTA, v); }
    public void setFlatStillSuppression(boolean v) { sp.edit().putBoolean(K_FLAT_SUPPRESS, v).apply(); }
    public void setRepeatEnabled(boolean v) { sp.edit().putBoolean(K_REPEAT, v).apply(); }
    public void setVibrateEnabled(boolean v) { sp.edit().putBoolean(K_VIBRATE, v).apply(); }
    public void setBootStartEnabled(boolean v) { sp.edit().putBoolean(K_BOOT, v).apply(); }

    // v2 补齐：v1 只有 getter、实际改不了的 7 个参数
    public void setStillWindowMs(long v) { sp.edit().putLong(K_STILL_WINDOW, v).apply(); }
    public void setStillObserveMs(long v) { sp.edit().putLong(K_STILL_OBSERVE, v).apply(); }
    public void setMinObserveMs(long v) { sp.edit().putLong(K_MIN_OBSERVE, v).apply(); }
    public void setLpAlpha(double v) { putDouble(K_LP_ALPHA, v); }
    public void setRepeatIntervalMs(long v) { sp.edit().putLong(K_REPEAT_INTERVAL, v).apply(); }
    public void setVibrateMs(long v) { sp.edit().putLong(K_VIBRATE_MS, v).apply(); }
    public void setPauseMinutes(int v) { sp.edit().putInt(K_PAUSE_MIN, v).apply(); }

    // v2 新增开关 setter
    public void setOverlayEnabled(boolean v) { sp.edit().putBoolean(K_OVERLAY, v).apply(); }
    public void setBannerEnabled(boolean v) { sp.edit().putBoolean(K_BANNER, v).apply(); }
    public void setSoundEnabled(boolean v) { sp.edit().putBoolean(K_SOUND, v).apply(); }
    public void setMonitorWhenLocked(boolean v) { sp.edit().putBoolean(K_MONITOR_LOCKED, v).apply(); }
    public void setPauseButtonEnabled(boolean v) { sp.edit().putBoolean(K_PAUSE_BTN, v).apply(); }
    public void setOngoingPauseAction(boolean v) { sp.edit().putBoolean(K_ONGOING_PAUSE, v).apply(); }
    public void setOngoingGuideAction(boolean v) { sp.edit().putBoolean(K_ONGOING_GUIDE, v).apply(); }
    public void setOngoingTextDetail(boolean v) { sp.edit().putBoolean(K_ONGOING_DETAIL, v).apply(); }
    public void setGuideAckVersion(int v) { sp.edit().putInt(K_GUIDE_ACK, v).apply(); }

    // v3：悬浮条外观 setter（拖动松手 / 文案变化时即时写入）
    public void setOverlayText(String v) { sp.edit().putString(K_OVERLAY_TEXT, v).apply(); }
    public void setOverlayWidthPct(int v) { sp.edit().putInt(K_OVERLAY_WIDTH, v).apply(); }
    public void setOverlayYDp(int v) { sp.edit().putInt(K_OVERLAY_Y, v).apply(); }
    public void setOverlayBgColor(int v) { sp.edit().putInt(K_OVERLAY_BG, v).apply(); }
    public void setOverlayBgAlpha(int v) { sp.edit().putInt(K_OVERLAY_ALPHA, v).apply(); }
    public void setOverlayRadiusDp(int v) { sp.edit().putInt(K_OVERLAY_RADIUS, v).apply(); }
    public void setOverlayTextSizeSp(int v) { sp.edit().putInt(K_OVERLAY_TEXT_SP, v).apply(); }

    /** 生成供 PostureDetector 使用的配置对象。v2 未改动：新开关不属于算法参数。 */
    public PostureDetector.Config buildDetectorConfig() {
        PostureDetector.Config c = new PostureDetector.Config();
        double enter = getEnterDeg();
        double exit = getExitDeg();
        if (exit >= enter) exit = enter - 10.0;
        if (exit < 0) exit = 0;
        c.enterDeg = enter;
        c.exitDeg = exit;
        c.enterHoldMs = getEnterHoldMs();
        c.exitHoldMs = getExitHoldMs();
        c.stillWindowMs = getStillWindowMs();
        c.stillObserveMs = getStillObserveMs();
        c.minObserveMs = getMinObserveMs();
        c.stillDelta = getStillDelta();
        c.flatDeg = getFlatDeg();
        c.flatStillSuppression = isFlatStillSuppression();
        c.lpAlpha = getLpAlpha();
        return c;
    }

    private double getDouble(String key, double def) {
        return Double.longBitsToDouble(sp.getLong(key, Double.doubleToLongBits(def)));
    }

    private void putDouble(String key, double v) {
        sp.edit().putLong(key, Double.doubleToLongBits(v)).apply();
    }
}
