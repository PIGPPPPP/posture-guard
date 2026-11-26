package com.example.posture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 开机后拉起前台服务；服务内部仍会等亮屏解锁才开始采样。 */
public final class BootReceiver extends BroadcastReceiver {

    private static final String QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !QUICKBOOT_POWERON.equals(action)) {
            return;
        }
        Prefs prefs = new Prefs(ctx);
        if (!prefs.isEnabled() || !prefs.isBootStartEnabled()) return;
        Intent s = new Intent(ctx, PostureService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(s);
            } else {
                ctx.startService(s);
            }
        } catch (Exception ignored) {
            // 个别 ROM 在开机广播里限制后台启动，用户下次打开 App 会自动拉起
        }
    }
}
