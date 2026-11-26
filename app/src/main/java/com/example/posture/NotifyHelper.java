package com.example.posture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** 通知渠道、常驻前台通知、低头横幅与震动。 */
public final class NotifyHelper {

    public static final String CH_ONGOING = "posture_ongoing";
    /** v1 遗留渠道：系统已创建后 App 无法修改其声音/震动，v2 起不再向它发通知。 */
    public static final String CH_ALERT = "posture_alert";
    /** v2：提示音开 → 系统默认提示音；提示音关 → 静音。用「换渠道」而非「改渠道」。 */
    public static final String CH_ALERT_SOUND = "posture_alert_sound";
    public static final String CH_ALERT_SILENT = "posture_alert_silent";
    public static final int ID_ONGOING = 1001;
    public static final int ID_ALERT = 1002;

    private final Context ctx;
    private final NotificationManager nm;
    private final Prefs prefs;

    public NotifyHelper(Context ctx, Prefs prefs) {
        this.ctx = ctx;
        this.prefs = prefs;
        this.nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannels();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ongoing = new NotificationChannel(
                    CH_ONGOING, ctx.getString(R.string.channel_ongoing_name),
                    NotificationManager.IMPORTANCE_LOW);
            ongoing.setDescription("常驻的监测状态通知，Android 要求前台服务必须显示");
            nm.createNotificationChannel(ongoing);

            NotificationChannel sound = new NotificationChannel(
                    CH_ALERT_SOUND, ctx.getString(R.string.channel_sound_name),
                    NotificationManager.IMPORTANCE_HIGH);
            sound.setDescription("检测到低头时弹出屏幕横幅（系统默认提示音）");
            sound.enableVibration(false);   // 震动统一由 vibrateOnce() 按开关控制
            nm.createNotificationChannel(sound);

            NotificationChannel silent = new NotificationChannel(
                    CH_ALERT_SILENT, ctx.getString(R.string.channel_silent_name),
                    NotificationManager.IMPORTANCE_HIGH);
            silent.setDescription("检测到低头时弹出屏幕横幅（不响铃）");
            silent.enableVibration(false);
            silent.setSound(null, null);    // 新建渠道时设定，之后不再修改
            nm.createNotificationChannel(silent);
        }
    }

    private int flags() {
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) f |= PendingIntent.FLAG_IMMUTABLE;
        return f;
    }

    private PendingIntent pauseIntent(boolean paused) {
        Intent i = new Intent(ctx, PostureService.class);
        i.setAction(paused ? PostureService.ACTION_RESUME : PostureService.ACTION_PAUSE);
        return PendingIntent.getService(ctx, 1, i, flags());
    }

    private PendingIntent guideIntent() {
        Intent i = new Intent(ctx, GuideActivity.class);
        return PendingIntent.getActivity(ctx, 2, i, flags());
    }

    /** 前台服务的常驻通知（Android 强制显示，只能关闭其按钮与文案，不能关闭通知本身）。 */
    public Notification buildOngoing(String statusText, boolean paused) {
        Notification.Builder b = channelBuilder(CH_ONGOING)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("抬举")
                .setContentText(prefs.isOngoingTextDetail() ? statusText : "抬举运行中")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(guideIntent());
        if (prefs.isOngoingPauseAction()) {
            b.addAction(new Notification.Action(R.drawable.ic_stat,
                    paused ? "立即恢复" : "暂停 " + prefs.getPauseMinutes() + " 分钟",
                    pauseIntent(paused)));
        }
        if (prefs.isOngoingGuideAction()) {
            b.addAction(new Notification.Action(R.drawable.ic_stat, "设置向导", guideIntent()));
        }
        return b.build();
    }

    public void updateOngoing(String statusText, boolean paused) {
        try {
            nm.notify(ID_ONGOING, buildOngoing(statusText, paused));
        } catch (SecurityException ignored) {
            // Android 13+ 未授予 POST_NOTIFICATIONS 时系统忽略，不崩溃
        }
    }

    /** 低头提醒横幅，固定 ID 覆盖，不堆叠；按提示音开关选择渠道。 */
    public void notifyLowHead() {
        String channel = prefs.isSoundEnabled() ? CH_ALERT_SOUND : CH_ALERT_SILENT;
        Notification.Builder b = channelBuilder(channel)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("请抬起手机")
                .setContentText("检测到低头姿势，把手机举到视线高度，别压着颈椎")
                .setAutoCancel(false)
                .setOnlyAlertOnce(false)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(guideIntent());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            b.setPriority(Notification.PRIORITY_HIGH);
        }
        try {
            nm.notify(ID_ALERT, b.build());
        } catch (SecurityException ignored) {
        }
    }

    public void cancelLowHead() {
        try {
            nm.cancel(ID_ALERT);
        } catch (SecurityException ignored) {
        }
    }

    /** 唯一的震动来源：受开关控制、时长可调。两条通知渠道均已关闭渠道级震动。 */
    public void vibrateOnce() {
        if (!prefs.isVibrateEnabled()) return;
        long ms = prefs.getVibrateMs();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (vm != null) {
                    vm.getDefaultVibrator().vibrate(
                            VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } else {
                Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
                if (v == null) return;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(ms);
                }
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    private Notification.Builder channelBuilder(String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(ctx, channelId);
        }
        return new Notification.Builder(ctx);
    }
}
