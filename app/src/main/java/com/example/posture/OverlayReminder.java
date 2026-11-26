package com.example.posture;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

/**
 * 屏幕顶部的悬浮提醒条。
 * v3：文案、宽度、上下位置、底色、深浅、圆角、字号全部可编辑；
 * 预览与真实窗口共用 buildOverlayView()，避免两处视觉漂移。
 * 未授予「显示在其他应用上层」权限时静默降级为仅通知。
 */
public final class OverlayReminder {

    private final Context ctx;
    private final Prefs prefs;
    private final WindowManager wm;
    private final int screenWidthPx;
    private final float motionScale;

    private View view;
    private TextView textView;
    private WindowManager.LayoutParams lp;
    private boolean hiding;

    public OverlayReminder(Context ctx) {
        this.ctx = ctx;
        this.prefs = new Prefs(ctx);
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        this.screenWidthPx = ctx.getResources().getDisplayMetrics().widthPixels;
        this.motionScale = Settings.Global.getFloat(ctx.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
    }

    public static boolean canDraw(Context ctx) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx);
    }

    /** 共用绘制：设置页预览与真实悬浮窗都调用这一份，保证所见即所得。 */
    public static View buildOverlayView(Context ctx, Prefs prefs) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.overlay_bar, null);
        int bg = prefs.getOverlayBgColor();
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setAlpha(prefs.getOverlayBgAlpha());
        d.setCornerRadius(dp(ctx, prefs.getOverlayRadiusDp()));
        v.setBackground(d);
        TextView tv = v.findViewById(R.id.overlay_text);
        if (tv != null) {
            tv.setText(prefs.getOverlayText());
            tv.setTextColor(textColorFor(bg));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getOverlayTextSizeSp());
        }
        return v;
    }

    /** 亮底配近黑墨字，其余配白字；避免用户选到米白后看不清。 */
    private static int textColorFor(int argb) {
        int lum = (Color.red(argb) * 299 + Color.green(argb) * 587
                + Color.blue(argb) * 114) / 1000;
        return lum > 170 ? 0xFF1A1A17 : 0xFFFFFFFF;
    }

    public void show(String text) {
        if (!canDraw(ctx)) return;   // 自动降级：由 NotifyHelper 的横幅兜底
        hiding = false;
        if (view != null) {
            // 淡出途中又被触发：取消退出动画，立刻恢复可见并套用最新样式
            view.animate().cancel();
            view.setAlpha(1f);
            view.setTranslationY(0f);
            applyStyle(text);
            return;
        }
        try {
            view = buildOverlayView(ctx, prefs);
            textView = view.findViewById(R.id.overlay_text);
            if (text != null && textView != null) textView.setText(text);
            lp = buildLayoutParams();
            wm.addView(view, lp);
            fadeIn();
        } catch (Exception e) {
            removeNow(view);
        }
    }

    private WindowManager.LayoutParams buildLayoutParams() {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        applySizeTo(p);
        return p;
    }

    private void applySizeTo(WindowManager.LayoutParams p) {
        int pct = prefs.getOverlayWidthPct();
        p.width = pct >= 100 ? WindowManager.LayoutParams.MATCH_PARENT
                : (int) (screenWidthPx * pct / 100f);
        p.y = dp(ctx, prefs.getOverlayYDp());
        // 故意不设 p.alpha / p.x / p.windowAnimations：
        // Android 12+ 对 FLAG_NOT_TOUCHABLE 的非可信窗口按 lp.alpha 判定触摸穿透（上限 0.8）；
        // windowAnimations 只接受系统资源，自定义动画只能画在根 View 上。
    }

    /** 重新套用样式并让宽度/位置即时生效；不重置判定、不撤提醒。 */
    private void applyStyle(String text) {
        if (view == null || lp == null) return;
        int bg = prefs.getOverlayBgColor();
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setAlpha(prefs.getOverlayBgAlpha());
        d.setCornerRadius(dp(ctx, prefs.getOverlayRadiusDp()));
        view.setBackground(d);
        if (textView != null) {
            textView.setText(text != null ? text : prefs.getOverlayText());
            textView.setTextColor(textColorFor(bg));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getOverlayTextSizeSp());
        }
        applySizeTo(lp);
        try {
            wm.updateViewLayout(view, lp);   // 主线程；服务本就在主线程
        } catch (Exception ignored) {
        }
    }

    /** 外观参数变化时由已绑定的服务调用，仅刷新样式。 */
    public void refresh() {
        if (view != null && !hiding) applyStyle(null);
    }

    public void hide() {
        if (view == null || hiding) return;   // 幂等，避免重复淡出
        hiding = true;
        final View v = view;
        if (motionScale <= 0.01f) {
            removeNow(v);
            return;
        }
        v.animate().cancel();
        v.animate().alpha(0f).translationY(-dp(ctx, 6))
                .setDuration(180)
                .setInterpolator(new PathInterpolator(0.16f, 1f, 0.3f, 1f))
                .withEndAction(() -> {
                    if (hiding) removeNow(v);   // show() 会置 hiding=false，取消这次移除
                })
                .start();
    }

    /** onDestroy 兜底：先 cancel 再同步 removeView，不依赖异步回调。 */
    public void destroy() {
        if (view == null) return;
        view.animate().cancel();
        hiding = true;
        removeNow(view);
    }

    private void fadeIn() {
        if (view == null) return;
        if (motionScale <= 0.01f) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationY(-dp(ctx, 8));
        view.animate().alpha(1f).translationY(0f)
                .setDuration(260)
                .setInterpolator(new PathInterpolator(0.16f, 1f, 0.3f, 1f))
                .start();
    }

    private void removeNow(View v) {
        if (v == null) return;
        try {
            wm.removeView(v);
        } catch (Exception ignored) {
        }
        if (v == view) {
            view = null;
            textView = null;
            lp = null;
        }
    }

    private static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
