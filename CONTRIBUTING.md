# 参与贡献 / Contributing

欢迎任何形式的参与 —— 提 issue、改文案、画图标、改代码都行。

## 最简单的方式

- 发现 bug 或想法 → 开一个 Issue，写清楚机型 / Android 版本 / 复现步骤。
- 改了东西 → 开 Pull Request，说明改了什么、为什么。

## 代码约定

- **保持零第三方依赖**：不引入 AndroidX / Compose / Kotlin / 任何库。这是这个项目的性格。
- `PostureDetector.java` 是纯 JVM 状态机，**不要在里面引入任何 `android.*` import**，否则 17 条自测就跑不起来了。
- 新增可调参数时：`Prefs` 加键 → `MainActivity` 的平行数组 + `valueOf/progressOf/formatValue` 三个 switch 同步 → 白话描述，不写术语。
- 改完先跑自测：`跑测试.bat`（需 JDK 17），应为 `PASS=17 FAIL=0`。

## 设计约定

- 颜色 / 间距 / 字阶 / 动效时长都走 `res/values/colors.xml`、`dimens.xml`，不要在代码里硬编码色值。
- 新文案同时更新 `strings.xml`（中文）与 README 中英文说明。
- 动效只动 `alpha` / `translation`，并尊重系统 `ANIMATOR_DURATION_SCALE`。

## 提交前检查

- [ ] `跑测试.bat` 17/17 通过
- [ ] Android Studio 能构建出 APK
- [ ] 没有把 keystore / local.properties / 工具链提交进来
- [ ] README 里的功能描述与实际行为一致

---

English contributors are very welcome — issues and PRs in either language are fine.
