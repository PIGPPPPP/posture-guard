package com.example.posture;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 小米 HyperOS 权限自检与设置向导（替代独立说明文档）。 */
public final class GuideActivity extends Activity {

    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);
        prefs = new Prefs(this);

        findViewById(R.id.btn_ack).setOnClickListener(v -> {
            prefs.setGuideAckVersion(Prefs.ACK_VERSION);
            Toast.makeText(this, R.string.guide_ack_toast, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btn_recheck).setOnClickListener(v -> {
            prefs.setGuideAckVersion(0);
            Toast.makeText(this, R.string.guide_recheck_toast, Toast.LENGTH_SHORT).show();
        });

        rebuildList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuildList();   // 从系统设置返回后刷新状态标记
    }

    private void rebuildList() {
        LinearLayout list = findViewById(R.id.guide_list);
        if (list == null) return;
        list.removeAllViews();
        String pkg = getPackageName();

        addItem(list, "1. 通知权限", notifOk(), notifIntent(),
                "设置 → 通知与状态栏 → 本应用 → 允许通知。Android 13+ 首次打开会弹窗授权；前台服务的常驻通知也依赖它。不设置：Android 13+ 下所有通知（含常驻通知与横幅）都不会显示，等于收不到任何提醒。");
        addItem(list, "2. 显示悬浮窗", overlayOk(), overlayIntent(pkg),
                "设置 → 应用设置 → 应用管理 → 抬举 → 权限 → 显示悬浮窗 → 允许。不设置：顶部悬浮条不显示，自动降级为只用通知横幅提醒。");
        addItem(list, "3. 忽略电池优化", batteryOk(), batteryIntent(pkg),
                "应用管理 → 抬举 → 省电策略 / 电池优化 → 无限制（不优化）。不设置：系统可能在后台限制服务运行，导致提醒不稳定。");
        addItem(list, "4. 悬浮通知横幅", notifOk(), notifIntent(),
                "设置 → 通知与状态栏 → 悬浮通知 → 找到本应用并允许；使用期间请关闭勿扰模式。不设置：低头时只有通知栏图标，不会弹屏幕横幅，容易漏看。");
        addItem(list, "5. 自启动（小米默认关闭）", null, appDetailsIntent(pkg),
                "设置 → 应用设置 → 授权管理 → 自启动管理 → 找到「抬举」并允许。这是小米私有开关，App 无法检测也无法代开。不设置：手机重启后不会自动拉起守护服务，除非你手动打开一次 App，期间的低头将完全不被提醒。");
        addItem(list, "6. 省电策略「无限制」", null, appDetailsIntent(pkg),
                "应用管理 → 抬举 → 省电策略 → 无限制（允许后台运行）。这是前台服务不被冻结的核心开关。不设置：系统省电或长时间后台后可能冻结服务，提醒会延迟甚至停止，且状态栏常驻通知可能消失。");
        addItem(list, "7. 后台任务锁定", null, null,
                "最近任务界面 → 长按「抬举」卡片 → 点锁定图标。此项无系统跳转入口，请手动操作。不设置：一键清理/内存加速会把守护进程连同服务一起杀掉，直到下次打开 App 才恢复。");
        addItem(list, "8. 后台弹出界面（小米特有）", null, appDetailsIntent(pkg),
                "应用管理 → 抬举 → 权限 → 后台弹出界面 → 允许。不设置：顶部悬浮条在 App 处于后台时可能无法显示，此时会自动降级为只用通知横幅提醒；若连通知横幅也被关，就可能完全看不到提醒。");
    }

    private void addItem(LinearLayout parent, String title, Boolean ok, Intent intent, String path) {
        android.view.View row = getLayoutInflater().inflate(R.layout.item_guide, parent, false);
        ((TextView) row.findViewById(R.id.item_title)).setText(title);
        TextView state = row.findViewById(R.id.item_state);
        if (ok == null) {
            state.setText("需手动确认");
            state.setTextColor(0xFF8A6D00);
        } else if (ok) {
            state.setText("✓ 已开启");
            state.setTextColor(0xFF16803C);
        } else {
            state.setText("✗ 待设置");
            state.setTextColor(0xFFC62828);
        }
        ((TextView) row.findViewById(R.id.item_path)).setText(path);
        Button btn = row.findViewById(R.id.item_btn);
        boolean jumpable = intent != null && intent.resolveActivity(getPackageManager()) != null;
        if (jumpable) {
            btn.setEnabled(true);
            btn.setText("去设置");
            btn.setOnClickListener(v -> {
                try { startActivity(intent); }
                catch (Exception e) { btn.setText("请手动设置"); }
            });
        } else {
            btn.setEnabled(false);
            btn.setText("手动设置");
        }
        parent.addView(row);
    }

    private boolean notifOk() {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        boolean enabled = nm == null || nm.areNotificationsEnabled();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return enabled && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return enabled;
    }

    private boolean overlayOk() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private boolean batteryOk() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private Intent notifIntent() {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        return i;
    }

    private Intent overlayIntent(String pkg) {
        return new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + pkg));
    }

    private Intent batteryIntent(String pkg) {
        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + pkg));
        if (i.resolveActivity(getPackageManager()) == null) return appDetailsIntent(pkg);
        return i;
    }

    private Intent appDetailsIntent(String pkg) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + pkg));
    }
}
