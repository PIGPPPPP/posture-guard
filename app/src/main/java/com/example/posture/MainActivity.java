package com.example.posture;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 主界面 v4：三页结构（守护 / 提醒 / 调校）+ 底部导航。
 * 第一页是深色仪式舞台：巨型主开关 + 每日一句，把「开启守护」变成每天的一次郑重动作。
 * 保留 v3 的全部能力：可折叠分组、拖动条白话描述、松手即生效、外观实时预览。
 */
public final class MainActivity extends Activity implements PostureService.UiListener {

    private static final int REQ_NOTIFICATION = 101;
    private static final int N = 18;          // 13 个算法参数 + 5 个悬浮条外观参数

    // 索引沿用 v2 顺序，减少改动面；13~17 为 v3 新增外观项
    private static final int I_ENTER_DEG = 0, I_EXIT_DEG = 1, I_ENTER_HOLD = 2,
            I_FLAT_DEG = 3, I_STILL_DELTA = 4, I_EXIT_HOLD = 5,
            I_STILL_WINDOW = 6, I_STILL_OBSERVE = 7, I_MIN_OBSERVE = 8,
            I_LP_ALPHA = 9, I_REPEAT_INTERVAL = 10, I_VIBRATE_MS = 11,
            I_PAUSE_MIN = 12;
    private static final int I_OV_WIDTH = 13, I_OV_Y = 14, I_OV_ALPHA = 15,
            I_OV_RADIUS = 16, I_OV_TEXT_SP = 17;

    private static final int GRP_COMMON = 0, GRP_EXPERT = 1, GRP_APPEARANCE = 2;

    /** 白话标题：不出现「去抖 / 低通滤波系数 / 静置观察窗」等术语。 */
    private static final String[] SEEK_TITLES = {
            "多大角度算低头", "抬到哪算改好了", "低头多久才提醒", "平放桌面算不算",
            "多小的晃动算没动", "抬起来多久算数", "记多久的动静", "要安静观察多久",
            "至少看多久", "传感器不稳时多平滑", "一直低头多久再提醒", "震一下有多长",
            "暂停歇多久", "横幅宽度", "离屏幕顶端多远", "底色深浅", "圆角大小", "字有多大"
    };
    private static final String[] SEEK_DESCS = {
            "手机压得越低越容易提醒；往左更灵敏，往右更迟钝",
            "抬回这个角度以下，提醒就消失；要比「多大角度算低头」小",
            "不小心低一下不算，要一直低着才提醒",
            "手机平放桌上、几乎不动时不提醒",
            "数字越小越「较真」，一点点晃也当你在动；一般不用改",
            "抬起来要稳住一会儿，提醒才真的撤掉",
            "判断「手机没动」时，往回看多长时间",
            "手机完全不动要持续多久，才算真的放着没用",
            "再短也得盯这么久才下结论",
            "只有手机没有重力传感器时才用得上；一般不用改",
            "一直没抬起来，隔一阵子再轻轻提醒一次",
            "提醒时手机轻轻震动多久",
            "点一次「暂停」，歇这么久再继续看着",
            "横幅占屏幕多宽，拖到最右是铺满",
            "数字越大越靠下；只改上下，不开放左右",
            "数字越大越不透明，字会自动换成看得清的颜色",
            "数字越大，四个角越圆",
            "数字越大，字越大"
    };
    /** 每项默认值，用于「长按标题恢复默认」与说明里的「（默认 X）」。 */
    private static final double[] SEEK_DEFAULTS = {
            40, 30, 3000, 78, 0.06, 1000, 8000, 1500, 800, 0.85, 30000, 120, 15,
            100, 28, 90, 20, 15
    };
    /** 每根拖动条的整数进度上限；valueOf 把进度映射回真实值（底层仍存 v2 的量纲与键名）。 */
    private static final int[] SEEK_MAX_P = {
            75, 75, 195, 30, 999, 98, 56, 97, 49, 49, 119, 98, 119,
            10, 30, 40, 14, 10
    };
    private static final int[] SEEK_GROUP = {
            GRP_COMMON, GRP_EXPERT, GRP_COMMON, GRP_COMMON, GRP_EXPERT, GRP_EXPERT,
            GRP_EXPERT, GRP_EXPERT, GRP_EXPERT, GRP_EXPERT, GRP_COMMON, GRP_COMMON,
            GRP_COMMON, GRP_APPEARANCE, GRP_APPEARANCE, GRP_APPEARANCE,
            GRP_APPEARANCE, GRP_APPEARANCE
    };

    private static final int[] SWATCH_COLORS = {
            R.color.swatch_1, R.color.swatch_2, R.color.swatch_3, R.color.swatch_4,
            R.color.swatch_5, R.color.swatch_6, R.color.swatch_7, R.color.swatch_8
    };
    private static final int[] SWATCH_NAMES = {
            R.string.swatch_1, R.string.swatch_2, R.string.swatch_3, R.string.swatch_4,
            R.string.swatch_5, R.string.swatch_6, R.string.swatch_7, R.string.swatch_8
    };

    private Prefs prefs;
    private PostureService service;
    private boolean bound;

    private TextView tvAngle, tvStatus, tvMonitor;
    private View tipBar, tipBody, tipHead;
    private boolean tipOpen;
    private LinearLayout settingsGuard, settingsRemind, settingsTune;
    private Button btnPause;

    // v4 仪式舞台与三页导航
    private View stage, heroSwitch, heroHalo, heroCore, heroMark;
    private TextView tvRitualState, tvMantra, tvRitualHint;
    private final View[] pageViews = new View[3];
    private final View[] tabViews = new View[3];
    private final ImageView[] tabIcons = new ImageView[3];
    private final TextView[] tabLabels = new TextView[3];
    private int currentPage = 0;
    private Animator breathAnim;

    private final SeekBar[] seek = new SeekBar[N];
    private final TextView[] seekValue = new TextView[N];
    private final double[] cur = new double[N];
    private boolean adjusting;

    private LinearLayout swatchRow, previewHost;
    private EditText overlayTextEdit;
    private View previewBar;
    private boolean previewPending;

    private final List<View> entryViews = new ArrayList<>();
    private String lastStatus = "";
    private ObjectAnimator statusAnim;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((PostureService.LocalBinder) binder).getService();
            service.setUiListener(MainActivity.this);
            refreshUi();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);

        tvAngle = findViewById(R.id.tv_angle);
        tvStatus = findViewById(R.id.tv_status);
        tvMonitor = findViewById(R.id.tv_monitor);

        stage = findViewById(R.id.stage);
        heroSwitch = findViewById(R.id.hero_switch);
        heroHalo = findViewById(R.id.hero_halo);
        heroCore = findViewById(R.id.hero_core);
        heroMark = findViewById(R.id.hero_mark);
        tvRitualState = findViewById(R.id.tv_ritual_state);
        tvMantra = findViewById(R.id.tv_mantra);
        tvRitualHint = findViewById(R.id.tv_ritual_hint);

        setupTipBar();
        setupTabs();
        setupHeroSwitch();
        loadAll();                 // 先填 cur[]，addSeekRow 才能用真实值初始化进度
        buildSettings();
        applyEntryVisibility();
        refreshTipBar();
        refreshRitual();

        findViewById(R.id.btn_guide).setOnClickListener(
                v -> startActivity(new Intent(this, GuideActivity.class)));

        requestNotificationPermissionIfNeeded();
        runEntryAnimation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startServiceSafely();
        bindServiceSafely();
        loadAll();                 // 幂等：只回填进度，不触发回调
        refreshTipBar();
        applyEntryVisibility();
        refreshRitual();           // 跨天回到前台会换一句；从向导返回后状态也同步
        refreshUi();
    }

    @Override
    protected void onPause() {
        cancelEntryAnimation();
        if (breathAnim != null) {
            breathAnim.cancel();
            breathAnim = null;
        }
        if (service != null) service.setUiListener(null);
        if (bound) {
            try { unbindService(conn); } catch (Exception ignored) { }
            bound = false;
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (statusAnim != null) statusAnim.cancel();
        if (breathAnim != null) breathAnim.cancel();
        super.onDestroy();
    }

    // ---------- 首屏权限提示（只做视觉降级，功能与 v2 一致） ----------

    private void setupTipBar() {
        tipBar = findViewById(R.id.tip_bar);
        tipBody = findViewById(R.id.tip_body);
        tipHead = findViewById(R.id.tip_head);
        View.OnClickListener go = v -> startActivity(new Intent(this, GuideActivity.class));
        tipBar.setOnClickListener(go);
        findViewById(R.id.tip_text).setOnClickListener(go);
        findViewById(R.id.tip_go).setOnClickListener(go);
        findViewById(R.id.tip_dismiss).setOnClickListener(v -> {
            prefs.setGuideAckVersion(Prefs.ACK_VERSION);
            tipBar.setVisibility(View.GONE);
        });
        tipHead.setOnClickListener(v -> toggleTip());
    }

    private void toggleTip() {
        tipOpen = !tipOpen;
        if (reducedMotion()) {
            tipBody.setVisibility(tipOpen ? View.VISIBLE : View.GONE);
            return;
        }
        tipBody.animate().cancel();
        if (tipOpen) {
            tipBody.setVisibility(View.VISIBLE);
            tipBody.setAlpha(0f);
            tipBody.setTranslationY(-dp(6));
            tipBody.animate().alpha(1f).translationY(0f).setDuration(320)
                    .setInterpolator(ease()).start();
        } else {
            tipBody.animate().alpha(0f).setDuration(180)
                    .withEndAction(() -> tipBody.setVisibility(View.GONE)).start();
        }
    }

    private void refreshTipBar() {
        if (tipBar == null) return;
        tipBar.setVisibility(prefs.getGuideAckVersion() >= Prefs.ACK_VERSION
                ? View.GONE : View.VISIBLE);
    }

    // ---------- v4 三页导航 ----------

    private void setupTabs() {
        pageViews[0] = findViewById(R.id.page_guard);
        pageViews[1] = findViewById(R.id.page_remind);
        pageViews[2] = findViewById(R.id.page_tune);
        tabViews[0] = findViewById(R.id.tab_guard);
        tabViews[1] = findViewById(R.id.tab_remind);
        tabViews[2] = findViewById(R.id.tab_tune);
        tabIcons[0] = findViewById(R.id.tab_icon_guard);
        tabIcons[1] = findViewById(R.id.tab_icon_remind);
        tabIcons[2] = findViewById(R.id.tab_icon_tune);
        tabLabels[0] = findViewById(R.id.tab_label_guard);
        tabLabels[1] = findViewById(R.id.tab_label_remind);
        tabLabels[2] = findViewById(R.id.tab_label_tune);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            tabViews[i].setOnClickListener(v -> switchPage(idx));
        }
        switchPage(0);
    }

    private void switchPage(int idx) {
        if (idx == currentPage && pageViews[idx].getVisibility() == View.VISIBLE) return;
        currentPage = idx;
        for (int i = 0; i < 3; i++) {
            pageViews[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
            tabViews[i].setSelected(i == idx);
            tabLabels[i].setSelected(i == idx);
            // vector 图标是白色模板，选中染赭橘，未选中染灰
            tabIcons[i].setColorFilter(getColor(i == idx ? R.color.brand : R.color.nav_inactive));
        }
        // 外观预览在隐藏页里量不到宽度，切到该页时补一次刷新
        if (idx == 1) previewHost.post(this::refreshPreview);
        // 页面入场：整页轻微上浮淡入，只动 transform 与 alpha
        if (!reducedMotion()) {
            View v = pageViews[idx];
            v.setAlpha(0f);
            v.setTranslationY(dp(10));
            v.animate().alpha(1f).translationY(0f)
                    .setDuration(getResources().getInteger(R.integer.dur_base))
                    .setInterpolator(ease()).start();
        }
    }

    // ---------- v4 仪式主开关 ----------

    private void setupHeroSwitch() {
        heroSwitch.setOnClickListener(v -> {
            boolean next = !prefs.isEnabled();
            prefs.setEnabled(next);
            onSettingChanged(true);
            refreshRitual();
            if (!reducedMotion()) {
                // 一次干脆的「按下—回弹」，像盖下一枚章
                heroCore.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90)
                        .withEndAction(() -> heroCore.animate().scaleX(1f).scaleY(1f)
                                .setDuration(getResources().getInteger(R.integer.dur_base))
                                .setInterpolator(ease()).start()).start();
            }
            Toast.makeText(this, next ? "守护已开始" : "守护已收起",
                    Toast.LENGTH_SHORT).show();
        });
    }

    /** 刷新圆环配色、状态文案与每日一句；开启动态时启动/停止呼吸。 */
    private void refreshRitual() {
        if (heroCore == null) return;
        boolean on = prefs.isEnabled();
        heroCore.setBackgroundResource(on ? R.drawable.ritual_core_on
                : R.drawable.ritual_core_off);
        tvRitualState.setText(on ? R.string.ritual_state_on : R.string.ritual_state_off);
        tvRitualState.setTextColor(getColor(on ? R.color.stage_text : R.color.stage_sub));
        tvRitualHint.setText(on ? R.string.ritual_hint_on : R.string.ritual_hint_off);
        tvMantra.setText(on ? dailyMantra() : getString(R.string.mantra_rest));
        heroMark.setAlpha(on ? 1f : 0.45f);
        updateBreath(on);
    }

    /** 按一年中的第几天取模，每天换一句；跨天回到前台会重算。 */
    private String dailyMantra() {
        String[] arr = getResources().getStringArray(R.array.mantras);
        int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        return arr[(day - 1) % arr.length];
    }

    private void updateBreath(boolean on) {
        if (breathAnim != null) {
            breathAnim.cancel();
            breathAnim = null;
        }
        heroHalo.setScaleX(1f);
        heroHalo.setScaleY(1f);
        heroHalo.setAlpha(1f);
        if (!on || reducedMotion()) return;
        // 呼吸只动 scale 与 alpha，循环往复；不碰布局属性
        int dur = getResources().getInteger(R.integer.dur_breath);
        ObjectAnimator sx = ObjectAnimator.ofFloat(heroHalo, "scaleX", 1f, 1.09f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(heroHalo, "scaleY", 1f, 1.09f);
        ObjectAnimator al = ObjectAnimator.ofFloat(heroHalo, "alpha", 0.85f, 0.25f);
        // 循环属性属于 ValueAnimator 一族，AnimatorSet 没有；给每个子动画各自设
        for (ObjectAnimator a : new ObjectAnimator[]{sx, sy, al}) {
            a.setDuration(dur);
            a.setInterpolator(new LinearInterpolator());
            a.setRepeatCount(ObjectAnimator.INFINITE);
            a.setRepeatMode(ObjectAnimator.REVERSE);
        }
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy, al);
        breathAnim = set;
        set.start();
    }

    // ---------- 分组设置界面 ----------

    private void buildSettings() {
        settingsGuard = findViewById(R.id.settings_guard);
        settingsRemind = findViewById(R.id.settings_remind);
        settingsTune = findViewById(R.id.settings_tune);
        settingsGuard.removeAllViews();
        settingsRemind.removeAllViews();
        settingsTune.removeAllViews();

        // —— 守护页：状态总开关 + 提醒方式 + 监测范围 ——
        // 主开关已移到仪式舞台，这里只保留一条常驻通知说明，避免两个总开关打架
        LinearLayout g1 = addGroup(settingsGuard, getString(R.string.group_status), false);
        addNoteRow(g1, getString(R.string.ongoing_not_closable));

        LinearLayout g2 = addGroup(settingsGuard, getString(R.string.group_remind), false);
        addSwitchRow(g2, "顶部悬浮条提醒",
                "低头时在屏幕顶部显示提示条，需「显示悬浮窗」权限；未授权会自动降级。",
                prefs.isOverlayEnabled(), (b, c) -> {
                    prefs.setOverlayEnabled(c);
                    onSettingChanged(false);
                });
        addSwitchRow(g2, "通知横幅提醒", "低头时发一条高优先级通知横幅。",
                prefs.isBannerEnabled(), (b, c) -> {
                    prefs.setBannerEnabled(c);
                    onSettingChanged(false);
                });
        addSwitchRow(g2, "提醒提示音", "在「有声 / 静音」两条通知渠道间切换，使用系统默认提示音。",
                prefs.isSoundEnabled(), (b, c) -> {
                    prefs.setSoundEnabled(c);
                    onSettingChanged(false);
                });
        addSwitchRow(g2, "提醒时震动", "低头与重复提醒时短震一次，时长见「常用调节」。",
                prefs.isVibrateEnabled(), (b, c) -> {
                    prefs.setVibrateEnabled(c);
                    onSettingChanged(false);
                });
        addSwitchRow(g2, "持续低头重复提醒", "一直低头时按间隔重复提醒，间隔见「常用调节」。",
                prefs.isRepeatEnabled(), (b, c) -> {
                    prefs.setRepeatEnabled(c);
                    onSettingChanged(false);
                });
        addNoteRow(g2, "若把所有提醒通道都关掉，低头时将不会有任何提示。");

        LinearLayout g5 = addGroup(settingsGuard, getString(R.string.group_scope), true);
        addSwitchRow(g5, "锁屏时仍继续监测",
                "屏幕亮着但未解锁时也采样；熄屏始终不监测，不加唤醒锁、不额外耗电。",
                prefs.isMonitorWhenLocked(), (b, c) -> {
                    prefs.setMonitorWhenLocked(c);
                    onSettingChanged(true);
                });
        addSwitchRow(g5, "手机平放桌面时静默", "近水平且几乎不动时不误报低头。",
                prefs.isFlatStillSuppression(), (b, c) -> {
                    prefs.setFlatStillSuppression(c);
                    onSettingChanged(true);
                });
        addSwitchRow(g5, "开机自动启动守护", "手机重启后自动拉起前台服务，仍受小米自启动开关制约。",
                prefs.isBootStartEnabled(), (b, c) -> {
                    prefs.setBootStartEnabled(c);
                    onSettingChanged(false);
                });
        addSwitchRow(g5, "主界面显示「暂停」按钮", "关掉后仍可用常驻通知上的暂停按钮。",
                prefs.isPauseButtonEnabled(), (b, c) -> {
                    prefs.setPauseButtonEnabled(c);
                    applyEntryVisibility();
                });
        addSwitchRow(g5, "常驻通知显示「暂停/恢复」按钮", "只控制通知上那一个按钮，不影响通知本身。",
                prefs.isOngoingPauseAction(), (b, c) -> {
                    prefs.setOngoingPauseAction(c);
                    onSettingChanged(true);
                });
        addSwitchRow(g5, "常驻通知显示「设置向导」按钮", "不影响点击通知本身进入向导。",
                prefs.isOngoingGuideAction(), (b, c) -> {
                    prefs.setOngoingGuideAction(c);
                    onSettingChanged(true);
                });
        addSwitchRow(g5, "常驻通知显示详细状态文案", "关掉后常驻通知只显示精简标题。",
                prefs.isOngoingTextDetail(), (b, c) -> {
                    prefs.setOngoingTextDetail(c);
                    onSettingChanged(true);
                });
        btnPause = new Button(this);
        btnPause.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        btnPause.setBackgroundResource(resolveSelectable());
        btnPause.setOnClickListener(v -> {
            sendServiceAction(PostureService.ACTION_PAUSE);
            Toast.makeText(this, "已暂停 " + prefs.getPauseMinutes() + " 分钟",
                    Toast.LENGTH_SHORT).show();
            refreshUi();
        });
        g5.addView(btnPause);

        // —— 提醒页：悬浮条外观 + 5 项外观滑杆 + 实时预览 ——
        LinearLayout g3 = addGroup(settingsRemind, getString(R.string.group_appearance), false);
        addNoteRow(g3, getString(R.string.group_appearance_hint));
        buildAppearanceRows(g3);
        for (int i = 0; i < N; i++) {
            if (SEEK_GROUP[i] == GRP_APPEARANCE) addSeekRow(g3, i);
        }
        View preview = getLayoutInflater().inflate(R.layout.item_overlay_preview, g3, false);
        previewHost = preview.findViewById(R.id.preview_host);
        g3.addView(preview);

        // —— 调校页：常用 + 专家滑杆 ——
        LinearLayout g4 = addGroup(settingsTune, getString(R.string.group_common), false);
        addNoteRow(g4, getString(R.string.group_common_hint));
        for (int i = 0; i < N; i++) {
            if (SEEK_GROUP[i] == GRP_COMMON) addSeekRow(g4, i);
        }

        LinearLayout g6 = addGroup(settingsTune, getString(R.string.group_expert), true);
        addNoteRow(g6, getString(R.string.group_advanced_hint));
        for (int i = 0; i < N; i++) {
            if (SEEK_GROUP[i] == GRP_EXPERT) addSeekRow(g6, i);
        }

        rebuildSwatches();
        // 预览在提醒页（初始隐藏），宽度量为 0；延迟到切到该页时再刷新
        previewHost.post(this::refreshPreview);
    }

    /** 悬浮条外观分组：文案、3 个预设、8 色色卡、真实屏幕试显。 */
    private void buildAppearanceRows(LinearLayout group) {
        View row = getLayoutInflater().inflate(R.layout.item_overlay_appearance, group, false);
        overlayTextEdit = row.findViewById(R.id.overlay_text_edit);
        overlayTextEdit.setText(prefs.getOverlayText());
        overlayTextEdit.setSelection(overlayTextEdit.getText().length());
        overlayTextEdit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                String t = s.toString().trim();
                if (t.isEmpty()) t = getString(R.string.overlay_text_default);
                prefs.setOverlayText(t);
                refreshPreview();
                if (service != null) service.refreshOverlay();
            }
        });

        bindPreset(row, R.id.preset_default, R.string.overlay_text_default);
        bindPreset(row, R.id.preset_funny, R.string.overlay_text_funny);
        bindPreset(row, R.id.preset_casual, R.string.overlay_text_casual);

        swatchRow = row.findViewById(R.id.swatch_row);
        for (int k = 0; k < SWATCH_COLORS.length; k++) {
            View dot = new View(this);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    (int) getResources().getDimension(R.dimen.swatch_size),
                    (int) getResources().getDimension(R.dimen.swatch_size));
            p.setMargins(dp(4), 0, dp(4), 0);
            dot.setLayoutParams(p);
            int color = getColor(SWATCH_COLORS[k]);
            GradientDrawable d = (GradientDrawable) getDrawable(R.drawable.swatch_oval).mutate();
            d.setColor(color);
            dot.setBackground(d);
            dot.setTag(color);
            dot.setContentDescription("颜色：" + getString(SWATCH_NAMES[k]));
            final int chosen = color;
            dot.setOnClickListener(v -> {
                prefs.setOverlayBgColor(chosen);
                rebuildSwatches();
                refreshPreview();
                if (service != null) service.refreshOverlay();
            });
            swatchRow.addView(dot);
        }

        row.findViewById(R.id.btn_try_show).setOnClickListener(v -> tryShowOverlay());
        group.addView(row);
    }

    private void bindPreset(View row, int id, int textRes) {
        row.findViewById(id).setOnClickListener(v -> {
            String t = getString(textRes);
            overlayTextEdit.setText(t);
            overlayTextEdit.setSelection(t.length());
            prefs.setOverlayText(t);
            refreshPreview();
            if (service != null) service.refreshOverlay();
        });
    }

    private void rebuildSwatches() {
        if (swatchRow == null) return;
        int current = prefs.getOverlayBgColor();
        for (int k = 0; k < swatchRow.getChildCount(); k++) {
            View dot = swatchRow.getChildAt(k);
            Object tag = dot.getTag();
            boolean selected = tag instanceof Integer && (Integer) tag == current;
            GradientDrawable d = (GradientDrawable) dot.getBackground();
            if (d != null) d.setStroke(selected ? dp(2) : 0, getColor(R.color.brand));
        }
    }

    private void tryShowOverlay() {
        if (OverlayReminder.canDraw(this)) {
            if (service != null) {
                service.showOverlayOnce();
            } else {
                Toast.makeText(this, "服务还没连上，稍后再试", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.toast_no_overlay, Toast.LENGTH_SHORT).show();
            if (previewBar != null && !reducedMotion()) {
                previewBar.setAlpha(0f);
                previewBar.animate().alpha(1f).setDuration(260)
                        .setInterpolator(ease()).start();
            }
        }
    }

    /** 页内预览：与真实悬浮窗共用 OverlayReminder.buildOverlayView()，杜绝视觉漂移。 */
    private void refreshPreview() {
        if (previewHost == null) return;
        int w = previewHost.getWidth();
        if (w <= 0) {
            // 首次布局还没量出宽度：只排队刷新一次，避免无限 post
            if (!previewPending) {
                previewPending = true;
                previewHost.post(() -> {
                    previewPending = false;
                    refreshPreview();
                });
            }
            return;
        }
        previewHost.removeAllViews();
        View bar = OverlayReminder.buildOverlayView(this, prefs);
        int pct = prefs.getOverlayWidthPct();
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                pct >= 100 ? LinearLayout.LayoutParams.MATCH_PARENT : (int) (w * pct / 100f),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = (int) (dp(prefs.getOverlayYDp()) * 0.35f);  // 预览里缩放模拟上下位置
        bar.setLayoutParams(p);
        previewHost.addView(bar);
        previewBar = bar;
    }

    /** 分组头 + 内容平级 addView（沿用 v2 结构），折叠改用舒缓过渡。 */
    private LinearLayout addGroup(LinearLayout host, String title, boolean collapsed) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(18), 0, dp(8));
        head.setBackgroundResource(resolveSelectable());
        head.setClickable(true);

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(getColor(R.color.text_main));
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.text_body));
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        head.addView(label);

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_chevron);
        arrow.setRotation(collapsed ? 0f : 90f);
        head.addView(arrow);
        host.addView(head);

        final LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        body.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        host.addView(body);

        final boolean[] open = { !collapsed };
        head.setOnClickListener(v -> {
            open[0] = !open[0];
            animateGroup(host, open[0], body, arrow);
        });
        return body;
    }

    private void animateGroup(LinearLayout host, boolean open, LinearLayout body, ImageView arrow) {
        if (reducedMotion()) {
            body.setVisibility(open ? View.VISIBLE : View.GONE);
            arrow.setRotation(open ? 90f : 0f);
            return;
        }
        TransitionManager.beginDelayedTransition(host,
                new TransitionSet()
                        .addTransition(new Fade().setDuration(160))
                        .addTransition(new ChangeBounds().setDuration(320)
                                .setInterpolator(ease())));
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        arrow.animate().rotation(open ? 90f : 0f).setDuration(160).start();
    }

    private void addSwitchRow(LinearLayout group, String title, String desc,
                              boolean checked,
                              CompoundButton.OnCheckedChangeListener listener) {
        View row = getLayoutInflater().inflate(R.layout.item_switch_row, group, false);
        ((TextView) row.findViewById(R.id.item_title)).setText(title);
        ((TextView) row.findViewById(R.id.item_desc)).setText(desc);
        Switch sw = row.findViewById(R.id.row_switch);
        sw.setOnCheckedChangeListener(null);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(listener);
        group.addView(row);
    }

    /** 一行「白话标题 + 当前值 + 拖动条」，算法参数与外观参数共用。 */
    private void addSeekRow(LinearLayout group, int i) {
        View row = getLayoutInflater().inflate(R.layout.item_number_row, group, false);
        TextView title = row.findViewById(R.id.item_title);
        TextView desc = row.findViewById(R.id.item_desc);
        title.setText(SEEK_TITLES[i]);
        desc.setText(SEEK_DESCS[i] + "（默认 "
                + formatValue(i, SEEK_DEFAULTS[i]) + "）");

        SeekBar sb = row.findViewById(R.id.row_seek);
        sb.setMax(SEEK_MAX_P[i]);
        sb.setContentDescription("拖动调节：" + SEEK_TITLES[i]);
        TextView value = row.findViewById(R.id.row_value_text);
        value.setText(formatValue(i, cur[i]));
        seek[i] = sb;
        seekValue[i] = value;

        // 长按标题恢复这一项的默认值，不额外占一行按钮
        title.setOnLongClickListener(v -> {
            cur[i] = SEEK_DEFAULTS[i];
            adjusting = true;
            sb.setProgress(progressOf(i, cur[i]));
            adjusting = false;
            value.setText(formatValue(i, cur[i]));
            clampChain();
            if (i >= 13) {
                applyAppearanceToPrefs();
                refreshPreview();
                if (service != null) service.refreshOverlay();
            } else {
                commitAlgorithm();
            }
            return true;
        });

        adjusting = true;
        sb.setProgress(progressOf(i, cur[i]));
        adjusting = false;

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (adjusting) return;   // 联动回弹时防止递归
                cur[i] = valueOf(i, progress);
                value.setText(formatValue(i, cur[i]));
                clampChain();
                if (i >= 13) {
                    // 外观参数：即时写 + 刷新预览，不发 ACTION_REFRESH_CONFIG（不重置判定）
                    applyAppearanceToPrefs();
                    refreshPreview();
                    if (service != null) service.refreshOverlay();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) { }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (i >= 13) {
                    applyAppearanceToPrefs();
                    refreshPreview();
                    if (service != null) service.refreshOverlay();
                } else {
                    commitAlgorithm();
                }
            }
        });
        group.addView(row);
    }

    private void addNoteRow(LinearLayout group, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(getColor(R.color.text_sub));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.text_caption));
        tv.setLineSpacing(dp(2), 1f);
        tv.setPadding(dp(2), dp(2), dp(2), dp(10));
        group.addView(tv);
    }

    private void applyEntryVisibility() {
        if (btnPause == null) return;
        btnPause.setVisibility(prefs.isPauseButtonEnabled() ? View.VISIBLE : View.GONE);
        btnPause.setText("暂停 " + prefs.getPauseMinutes() + " 分钟");
    }

    /** needRefresh=true 时才重置判定窗口；通道类开关靠调用点读 Prefs 即时生效。 */
    private void onSettingChanged(boolean needRefresh) {
        if (needRefresh) sendServiceAction(PostureService.ACTION_REFRESH_CONFIG);
        refreshUi();
    }

    // ---------- 拖动条数值 ----------

    private void loadAll() {
        cur[I_ENTER_DEG] = prefs.getEnterDeg();
        cur[I_EXIT_DEG] = prefs.getExitDeg();
        cur[I_ENTER_HOLD] = prefs.getEnterHoldMs();
        cur[I_FLAT_DEG] = prefs.getFlatDeg();
        cur[I_STILL_DELTA] = prefs.getStillDelta();
        cur[I_EXIT_HOLD] = prefs.getExitHoldMs();
        cur[I_STILL_WINDOW] = prefs.getStillWindowMs();
        cur[I_STILL_OBSERVE] = prefs.getStillObserveMs();
        cur[I_MIN_OBSERVE] = prefs.getMinObserveMs();
        cur[I_LP_ALPHA] = prefs.getLpAlpha();
        cur[I_REPEAT_INTERVAL] = prefs.getRepeatIntervalMs();
        cur[I_VIBRATE_MS] = prefs.getVibrateMs();
        cur[I_PAUSE_MIN] = prefs.getPauseMinutes();
        cur[I_OV_WIDTH] = prefs.getOverlayWidthPct();
        cur[I_OV_Y] = prefs.getOverlayYDp();
        cur[I_OV_ALPHA] = Math.round(prefs.getOverlayBgAlpha() * 100f / 255f);
        cur[I_OV_RADIUS] = prefs.getOverlayRadiusDp();
        cur[I_OV_TEXT_SP] = prefs.getOverlayTextSizeSp();

        adjusting = true;
        for (int i = 0; i < N; i++) {
            if (seek[i] != null) seek[i].setProgress(progressOf(i, cur[i]));
            if (seekValue[i] != null) seekValue[i].setText(formatValue(i, cur[i]));
        }
        adjusting = false;
        clampChain();   // 旧版本可能存过不满足联动关系的值，加载时统一收敛
    }

    /** 4 条联动约束（沿用 v2 数学）：按依赖拓扑一次传播，被影响项瞬时回弹，不弹 Toast。 */
    private void clampChain() {
        double[] before = cur.clone();
        if (cur[I_EXIT_DEG] >= cur[I_ENTER_DEG] - 10) {
            cur[I_EXIT_DEG] = Math.max(0, cur[I_ENTER_DEG] - 10);
        }
        if (cur[I_EXIT_HOLD] > cur[I_ENTER_HOLD]) cur[I_EXIT_HOLD] = cur[I_ENTER_HOLD];
        double cap = Math.max(300, cur[I_ENTER_HOLD] - 200);
        if (cur[I_STILL_OBSERVE] > cap) cur[I_STILL_OBSERVE] = cap;
        if (cur[I_MIN_OBSERVE] > cur[I_STILL_OBSERVE]) {
            cur[I_MIN_OBSERVE] = cur[I_STILL_OBSERVE];
        }
        adjusting = true;
        for (int i = 0; i < N; i++) {
            if (cur[i] != before[i]) {
                if (seek[i] != null) seek[i].setProgress(progressOf(i, cur[i]));
                if (seekValue[i] != null) seekValue[i].setText(formatValue(i, cur[i]));
            }
        }
        adjusting = false;
    }

    private static double valueOf(int i, int p) {
        switch (i) {
            case 0: return 10 + p;
            case 1: return p;
            case 2: return 500 + p * 100L;
            case 3: return 60 + p;
            case 4: return 0.001 + p * 0.001;
            case 5: return 200 + p * 100L;
            case 6: return 2000 + p * 500L;
            case 7: return 300 + p * 100L;
            case 8: return 100 + p * 100L;
            case 9: return 0.50 + p * 0.01;
            case 10: return 5000 + p * 5000L;
            case 11: return 20 + p * 10L;
            case 12: return 1 + p;
            case 13: return 50 + p * 5;
            case 14: return p * 4;
            case 15: return 60 + p;
            case 16: return p * 2;
            case 17: return 12 + p;
            default: return 0;
        }
    }

    private static int progressOf(int i, double v) {
        int p;
        switch (i) {
            case 0: p = (int) Math.round(v - 10); break;
            case 1: p = (int) Math.round(v); break;
            case 2: p = (int) Math.round((v - 500) / 100.0); break;
            case 3: p = (int) Math.round(v - 60); break;
            case 4: p = (int) Math.round((v - 0.001) / 0.001); break;
            case 5: p = (int) Math.round((v - 200) / 100.0); break;
            case 6: p = (int) Math.round((v - 2000) / 500.0); break;
            case 7: p = (int) Math.round((v - 300) / 100.0); break;
            case 8: p = (int) Math.round((v - 100) / 100.0); break;
            case 9: p = (int) Math.round((v - 0.50) / 0.01); break;
            case 10: p = (int) Math.round((v - 5000) / 5000.0); break;
            case 11: p = (int) Math.round((v - 20) / 10.0); break;
            case 12: p = (int) Math.round(v - 1); break;
            case 13: p = (int) Math.round((v - 50) / 5.0); break;
            case 14: p = (int) Math.round(v / 4.0); break;
            case 15: p = (int) Math.round(v - 60); break;
            case 16: p = (int) Math.round(v / 2.0); break;
            case 17: p = (int) Math.round(v - 12); break;
            default: p = 0;
        }
        return Math.max(0, Math.min(SEEK_MAX_P[i], p));
    }

    /** 显示层把毫秒换成秒/分钟，界面不出现「毫秒」堆砌（极小值才保留）。 */
    private static String formatValue(int i, double v) {
        switch (i) {
            case 0: case 1: case 3:
                return (int) Math.round(v) + "°";
            case 2: case 5: case 6: case 7: case 8:
                return String.format(Locale.CHINA, "%.1f 秒", v / 1000.0);
            case 4:
                return String.format(Locale.CHINA, "%.3f", v);
            case 9:
                return String.format(Locale.CHINA, "%.2f", v);
            case 10:
                return v < 60000 ? (int) Math.round(v / 1000.0) + " 秒"
                        : String.format(Locale.CHINA, "%.0f 分钟", v / 60000.0);
            case 11:
                return String.format(Locale.CHINA, "%.2f 秒", v / 1000.0);
            case 12:
                return (int) Math.round(v) + " 分钟";
            case 13: case 15:
                return (int) Math.round(v) + "%";
            default:
                return String.valueOf((int) Math.round(v));
        }
    }

    /** 松手后一次写入 13 项并重置判定窗口；拖动中不写，避免每帧 detector.reset()。 */
    private void commitAlgorithm() {
        prefs.setEnterDeg(cur[I_ENTER_DEG]);
        prefs.setExitDeg(cur[I_EXIT_DEG]);
        prefs.setEnterHoldMs((long) cur[I_ENTER_HOLD]);
        prefs.setFlatDeg(cur[I_FLAT_DEG]);
        prefs.setStillDelta(cur[I_STILL_DELTA]);
        prefs.setExitHoldMs((long) cur[I_EXIT_HOLD]);
        prefs.setStillWindowMs((long) cur[I_STILL_WINDOW]);
        prefs.setStillObserveMs((long) cur[I_STILL_OBSERVE]);
        prefs.setMinObserveMs((long) cur[I_MIN_OBSERVE]);
        prefs.setLpAlpha(cur[I_LP_ALPHA]);
        prefs.setRepeatIntervalMs((long) cur[I_REPEAT_INTERVAL]);
        prefs.setVibrateMs((long) cur[I_VIBRATE_MS]);
        prefs.setPauseMinutes((int) cur[I_PAUSE_MIN]);
        sendServiceAction(PostureService.ACTION_REFRESH_CONFIG);
        applyEntryVisibility();
    }

    private void applyAppearanceToPrefs() {
        prefs.setOverlayWidthPct((int) cur[I_OV_WIDTH]);
        prefs.setOverlayYDp((int) cur[I_OV_Y]);
        prefs.setOverlayBgAlpha((int) Math.round(cur[I_OV_ALPHA] * 255 / 100.0));
        prefs.setOverlayRadiusDp((int) cur[I_OV_RADIUS]);
        prefs.setOverlayTextSizeSp((int) cur[I_OV_TEXT_SP]);
    }

    // ---------- 动效 ----------

    private void runEntryAnimation() {
        entryViews.clear();
        // 仪式页按「先文字、后开关、再数字」的顺序点亮，6 组错峰
        addEntry(R.id.tv_brand);
        addEntry(R.id.hero_switch);
        addEntry(R.id.tv_ritual_state);
        addEntry(R.id.tv_mantra);
        addEntry(R.id.tv_ritual_hint);
        addEntry(R.id.tv_angle);
        addEntry(R.id.tip_bar);
        addEntry(R.id.settings_guard);
        if (reducedMotion()) {
            cancelEntryAnimation();
            return;
        }
        int[] orders = {0, 1, 2, 3, 3, 4, 5, 5};
        for (int k = 0; k < entryViews.size(); k++) {
            View v = entryViews.get(k);
            int order = k < orders.length ? orders[k] : 5;
            v.setAlpha(0f);
            v.setTranslationY(dp(12));
            v.animate().alpha(1f).translationY(0f)
                    .setDuration(getResources().getInteger(R.integer.dur_base))
                    .setStartDelay((long) getResources().getInteger(R.integer.entry_stagger) * order)
                    .setInterpolator(ease()).start();
        }
    }

    private void addEntry(int id) {
        View v = findViewById(id);
        if (v != null) entryViews.add(v);
    }

    private void cancelEntryAnimation() {
        for (View v : entryViews) {
            v.animate().cancel();
            v.setAlpha(1f);
            v.setTranslationY(0f);
        }
        entryViews.clear();
    }

    private static PathInterpolator ease() {
        return new PathInterpolator(0.16f, 1f, 0.3f, 1f);
    }

    private boolean reducedMotion() {
        return Settings.Global.getFloat(getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0.01f;
    }

    private int resolveSelectable() {
        TypedValue out = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true);
        return out.resourceId;
    }

    // ---------- 服务与状态 ----------

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICATION);
        }
    }

    private void startServiceSafely() {
        try {
            Intent i = new Intent(this, PostureService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) {
        }
    }

    private void bindServiceSafely() {
        if (bound) return;
        try {
            bound = bindService(new Intent(this, PostureService.class), conn,
                    Context.BIND_AUTO_CREATE);
        } catch (Exception ignored) {
        }
    }

    private void sendServiceAction(String action) {
        try {
            Intent i = new Intent(this, PostureService.class);
            i.setAction(action);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onUpdate(double tiltDeg, String status, boolean monitoring) {
        runOnUiThread(() -> {
            tvAngle.setText(String.format(Locale.CHINA, "%.1f°", tiltDeg));
            tvMonitor.setText(monitoring ? "● 正在监测" : "○ 未监测");
            tvStatus.setText(status);
            if (!status.equals(lastStatus)) {
                int from = tvStatus.getCurrentTextColor();
                int to = getColor(status.contains("低头") ? R.color.brand : R.color.text_main);
                if (statusAnim != null) statusAnim.cancel();
                if (reducedMotion()) {
                    tvStatus.setTextColor(to);
                } else {
                    statusAnim = ObjectAnimator.ofArgb(tvStatus, "textColor", from, to);
                    statusAnim.setDuration(320);
                    statusAnim.start();
                }
                lastStatus = status;
            }
        });
    }

    private void refreshUi() {
        if (service == null) {
            tvAngle.setText("--°");
            tvStatus.setText("服务未连接");
            tvMonitor.setText("○ 未监测");
            return;
        }
        onUpdate(service.getTiltDeg(), service.getStatusText(), service.isMonitoring());
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }
}
