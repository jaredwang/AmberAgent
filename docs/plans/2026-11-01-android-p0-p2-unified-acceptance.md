# Android 选定差异补全 · P0–P2 统一 JVM / 构建 / 真机验收（2026-11-01）

> 本文件是 handoff §9 专用交付记录，不修改 `2026-10-31-android-selected-parity-handoff.md`。
> 五阶段**代码**均已独立验收通过。本文件只记录统一测试/构建/真机结果。
> **不能**把「五阶段代码通过」写成全部完成。下文每项区分 JVM 与真机，未闭合项单列。

## 0. 结论（先看这个）

| 层 | 状态 |
| --- | --- |
| 五阶段代码 | 独立验收通过（见 §1 提交） |
| JVM / 编译 / APK | 已跑。app 全量 3567 tests 中 2 个既有 `AntigravityOAuthSessionTest` 并发基线失败，未降断言、未无关修 |
| 非 debuggable / R8 等价 | `fullProfileableDebug`（graphite 同包同签、`isDebuggable=false`）已装已跑；`fullBenchmarkRelease`（R8 minify）已组装、**未**装到生产 `app.amber.agent` |
| 真机安装/启动 | graphite `-r -t` 保留用户数据成功；`RouteActivity` Status ok；无 FATAL |
| P0 非 debuggable 真实 OCR | **真机通过**（profileable，owned ML Kit，WebView+Canvas 像素，`source=BITMAP_OCR`） |
| P1 Room 四态 / 水位 / 冷启 | **真机通过**（隔离 Room，不碰用户库） |
| P1 Compose 四态面板 | **未完成**：PIN 锁屏 + `createAndroidComposeRule` `waitForIdleSync` 挂起 |
| P2 Jev 设置迁移 | **真机通过**（ART decode） |
| P2 Jev 原生快照 / 真机自控 | **未完成**：Amber 无障碍未开；opt-in `realJev` 无参数 |
| P2 视频 contain-fit + 静音 pause | **真机通过**（无 Activity，解码器 + 数学） |
| P2 视频 Dialog 控制 / 时间线滚动 | **未完成**：同一 PIN 锁屏 Compose 阻塞 |
| P2 per-App 规划/隔离/接管/清除 | **真机通过**（设备 ART + 隔离 cache 文件，fixture 快照） |
| P2 per-App 对真实三方 App | **未完成**：无 Amber 无障碍、未对用户应用操作 |

停等主代理最终验收。未 push / 未开 PR。

---

## 1. 基线与五阶段代码提交

工作分支：`codex/android-p0-ocr`。本记录提交前 HEAD：`80ed4a964`。

| 阶段 | 首轮 | 返工 / 记录 | 代码验收 |
| --- | --- | --- | --- |
| P0 OCR | `fb65d85a0` | `b9682dd55`；记录 `66ad927d8` | 通过，真机当时待验 |
| P1 活动中心 | `d07a05f98` | `04c6a3116`；记录 `40a42abc4` | 通过，真机当时待验 |
| P2 Jev | `1a499d424` | `b42ea9c2a`；记录 `34807ca56` | 通过，真机当时待验 |
| P2 竖屏视频 | `cb52fa0ba` | `33deefa7d`；记录 `92a865912` | 通过，真机当时待验 |
| P2 per-App | `a90718ae6` | `80ed4a964`；记录见 per-App 执行文件 | 主代理独立复跑 92 tests `/tmp/amber-perapp-rereview.log` 通过 |

三个既有未跟踪用户文档保持不提交：

- `docs/plans/2026-10-26-chat-history-performance-phase2.md`
- `docs/plans/2026-10-31-android-selected-parity-handoff.md`
- `docs/plans/2026-10-31-ios-android-feature-diff.md`

未 `reset` / `clean` / `rebase`，未 push / 未开 PR。未读其他产品仓库。APK / 日志 / 截图只在 `app/build` 与 `/tmp`。

---

## 2. 环境（本轮开始时记录）

- 工作区：`/Users/hugo/Desktop/git/AmberAgent`
- Java：JBR 21.0.11（`/Users/hugo/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home`）
- SDK：`/Users/hugo/Library/Android/sdk`；Gradle `--offline`
- adb：`/opt/homebrew/bin/adb`

真机（报告可掩码）：

| 项 | 值 |
| --- | --- |
| 型号 | Xiaomi `2608BPX34C` |
| product / device | `lhasa` |
| 制造商 | Xiaomi |
| Android / API | 17 / 37 |
| ABI | `arm64-v8a` |
| 安全补丁 | 2026-09-01 |
| 无线 TLS ADB serial | `adb-0A07846009983540-Rmxr9u._adb-tls-connect._tcp` |
| 硬件 serial | `0A07…3540` |
| 封面屏 | 1168×1712（port 1） |
| 内屏 | 1672×2364（port 0） |
| 折叠状态 | 物理 `CLOSED`（0）。本轮曾留下 `OPENED`/`TENT` override，结束时 `cmd device_state state reset`，override 已空 |
| 锁屏 | `CredentialType: PIN`，`deviceLocked=1`，`mDreamingLockscreen=true`，FLAG_SECURE。未清 PIN、未卸载、未清库 |
| 无障碍 | 仅 `io.github.jackwener.dsh/...DshAccessibilityService`。Amber 无障碍未开，本轮未改 |

安装包：`app.amber.agent.graphite` versionName `2.6.8` / versionCode `396`，签名 `[97b4b87b]`。

- `firstInstallTime=2026-10-28 19:02:18`（全程保留）
- 结束时 `lastUpdateTime=2026-11-01 04:46:06`，`pkgFlags` 含 `DEBUGGABLE`（已从 profileable 还原 fullDebug）

---

## 3. 每阶段关键文件 / 依赖 / 迁移

### 3.1 P0 OCR

生产：`ScreenOcrService.kt`（`DefaultScreenOcrEngine`、`MlKitScreenTextRecognizer`）、`ScreenOcrModels.kt`、`ScreenPrimitiveTools`、`ScreenToolReceiptPresentation`、`JevScreenGoalRunner` 透传 `ocr_source`。

依赖：`com.google.mlkit:text-recognition-chinese:16.0.1`（bundled）。`gradle/libs.versions.toml` `mlkitTextRecognition = "16.0.1"`。

APK 实测（profileable 与 benchmarkRelease 相同）：

- `lib/arm64-v8a/libmlkit_google_ocr_pipeline.so` **11,064,544** bytes（未压缩）
- OCR 相关 zip 条目 34 个、**14,436,256** bytes，含 `Hani_ctc` / `Latn_ctc` / taser / `taser_tflite_gocrchinese_and_latin_*`
- R8 `fullBenchmarkRelease` 中上述 so 与模型仍在，未被 shrink 掉

支持 script：简中 / 繁中 / Latin。不宣称日/韩/俄。

无 Room 迁移。

### 3.2 P1 活动中心

生产：`ConversationActivityCenter`、`RoomRunTerminalStore`、`ChatActivityPanelContent`、`AppDatabase` version **22**。

`MIGRATION_21_22`：建 `conversation_activity_seen`（PK `run_id`）与 `conversation_activity_baseline`（单行 `id=1`）。

无新 Gradle 依赖。

### 3.3 P2 Jev

生产：`JevPurpose` 13 项（无 `SCREEN_AUTOMATION` 枚举值）、`PHONE_VERIFICATION` / `PHONE_ACTIONS`、`JevScreenGoalRunner`、`AgentPrefs` JSON 迁移（显式 PHONE_* 优先，legacy `SCREEN_AUTOMATION` 填缺省）。

设置资源六语种已有。无 Room 迁移。

### 3.4 P2 竖屏视频

生产：`VideoPlaybackDialog.kt`、`VideoDisplaySize.kt`（`containFitVideo` / `videoControlHeightPx`）、`ContainVideoView`。无 media3 / ExoPlayer 新依赖。

### 3.5 P2 per-App

生产：`PerAppSuccessPath.kt`、`PerAppSuccessPathStore.kt`、`JevScreenGoalRunner` 读写、`SettingAgentExecutionPage` 清除。落盘 `filesDir/jev/per_app_success_paths.json`。无 Room 迁移、无新依赖。

---

## 4. 命令与结果（可复核）

统一 JVM（`--offline`）：

```bash
./gradlew :core:jev:testDebugUnitTest \
  :core:settings:testDebugUnitTest \
  :app:testFullDebugUnitTest --offline
# 日志：/tmp/amber-unified-jvm.log
# core:jev 71 / 0 fail
# core:settings 122 / 0 fail
# app 3567 tests, 2 failed, 15 skipped → BUILD FAILED
```

既有失败（不降断言、本轮不修）：

1. `AntigravityOAuthSessionTest.late invalid grant cannot clear a session created after logout` — `AssertionError` at `AntigravityOAuthSessionTest.kt:58` / invokeSuspend `:77`
2. `AntigravityOAuthSessionTest.late successful refresh cannot replace a session created after logout` — `AssertionError` at `:27` / invokeSuspend `:46`

XML：`app/build/test-results/testFullDebugUnitTest/TEST-app.amber.ai.provider.providers.google.AntigravityOAuthSessionTest.xml`。

编译 / 组装：

```bash
./gradlew :app:assembleFullDebug --offline
# /tmp/amber-unified-assemble-debug.log  BUILD SUCCESSFUL in 21s
# 含 :app:compileFullDebugKotlin；APK /tmp/amber-s9-full-arm64-debug.apk 116M
# aapt: package app.amber.agent.graphite；含 application-debuggable

./gradlew :app:assembleFullBenchmarkRelease --offline
# /tmp/amber-unified-r8-benchmark.log  BUILD SUCCESSFUL in 2m 14s
# APK /tmp/amber-s9-full-arm64-benchmarkRelease.apk 71M
# package app.amber.agent；无 application-debuggable；minify true
# mapping：app/build/outputs/mapping/fullBenchmarkRelease/
# 仓库无 google-services.json / 无发行密钥；未造假 Firebase 配置
# 该 variant 签名为 debug，applicationId 为生产包名，故未安装以免覆盖用户数据

./gradlew :app:assembleFullDebug -PprofileableDebug=true --offline
# /tmp/amber-unified-profileable.log  BUILD SUCCESSFUL in 1m 13s
# APK /tmp/amber-s9-full-arm64-profileable.apk 94M
# 同包 graphite、同 debug 签名；aapt 无 application-debuggable

./gradlew :app:assembleFullDebugAndroidTest --offline
# /tmp/amber-s9-full-debug-androidTest.apk 2.8M
```

另组 `fullBaseline`（`app.amber.agent.debug`，非 debuggable，minify false）`/tmp/amber-s9-full-arm64-baseline.apk` 94M，未安装（不同 applicationId，与本次 graphite 用户数据无关）。

本地化脚本既有 FAIL（ja/ko/ru 缺部分 key、zh grok oauth waiting、`IntelligentUiStudyView` 内联中文）不是本轮引入，未顺手修。

---

## 5. 安装 / 启动（保留用户数据）

```bash
adb -s "$SERIAL" install -r -t /tmp/amber-s9-full-arm64-debug.apk     # Success
adb -s "$SERIAL" install -r -t /tmp/amber-s9-full-debug-androidTest.apk
adb -s "$SERIAL" shell am start -W -n app.amber.agent.graphite/app.amber.agent.RouteActivity
# Status: ok；Activity: ...RouteActivity；WaitTime ~3018ms
```

profileable 覆盖安装后再还原 debug：

```bash
adb -s "$SERIAL" install -r -t /tmp/amber-s9-full-arm64-profileable.apk  # Success；pkgFlags 无 DEBUGGABLE
# RouteActivity Status ok pid 9668
# 随后 OCR+video instrument OK（见 §6）
adb -s "$SERIAL" install -r -t /tmp/amber-s9-full-arm64-debug.apk        # Success
# firstInstallTime 仍为 2026-10-28 19:02:18
# RouteActivity Status ok pid 18443；pkgFlags 恢复 DEBUGGABLE
```

logcat 无 `FATAL EXCEPTION` / `AndroidRuntime`。锁屏下 `visibleRequested=false` 是 keyguard 遮挡，不是进程崩溃。FLAG_SECURE 使 `screencap` 得不到应用画面，不能当 UI 通过证据。

未卸载、未 `pm clear`、未改 locksettings。

---

## 6. 真机证据矩阵

runner：`app.amber.agent.graphite.test/app.amber.agent.AmberAgentAndroidTestRunner`（plain `Application`，无 Koin）。因此 **不能** 启动生产 Activity：`ReminderRescheduleReceiver` 会 `KoinApplication has not been started`。OCR / 视频 contain / Room / per-App 均改为无 Activity。Compose 规则仍会启动空 `ComponentActivity`，在 PIN 后会 `waitForIdleSync` 挂起。

证据目录：`/tmp/amber-s9-evidence/` 与 `/tmp/amber-s9-evidence/profileable-ocr/amber-s9/`。

### 6.1 P0 OCR — 真机（含非 debuggable）

命令（profileable，FLAG_DEBUGGABLE 关闭）：

```bash
adb shell am instrument -w -e class \
  'app.amber.agent.ScreenOcrDeviceTest,app.amber.agent.VideoContainFitDeviceTest' \
  app.amber.agent.graphite.test/app.amber.agent.AmberAgentAndroidTestRunner
# /tmp/amber-s9-connected-profileable-ocr.log
# Time: 3.228  OK (6 tests)
```

夹具：离屏 `WebView` / 自绘 `Canvas` `view.draw` → 1080×1600 bitmap；`IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`；`bitmapRecognizer=null` + 生产 `MlKitScreenTextRecognizer`；`accessibilityRegionsProvider={emptyList()}`。无 fake recognizer。

| 项 | 结果 | 证据 |
| --- | --- | --- |
| Canvas 中文 | `琥珀验证完成` bounds 59,255,578,344 | `canvas-ocr.txt` / `canvas-source.png` |
| Canvas Latin | `OCR-LATIN-MARK` | 同上 |
| Canvas 可点区域 | `立即查看详情` 非空包围盒 | 同上 |
| WebView | `琥珀验证`/`完成`/`OCR-`/`LATIN-`/`MARK`/`立即查看详`；断言含 琥珀+验证、LATIN、立即/查看 | `webview-ocr.txt` / `webview-source.png` |
| source | `BITMAP_OCR` / `RECOGNIZED` / `screenshotSource=offscreen_view_draw` / `recognizer=mlkit_text_recognition_chinese` | 两份 ocr.txt |
| AX 未供词 | `axHasChinese=false` `axHasLatin=false`；dump 为锁屏「无线调试/微信」 | `canvas-ax.txt` `webview-ax.txt` |
| 充分树零截图 | `triggered=false reason=tree_coverage_sufficient shots=0` | `sufficient-tree.txt` |
| 缺截图 | `diagnostics=[screenshot_unavailable]` `UNAVAILABLE` | `screenshot-unavailable.txt` |

同一 4 条 OCR 先前也在 debuggable graphite 上 2.513s 全过（`/tmp/amber-s9-connected-ocr.log`）。**非 debuggable 以 profileable 6 tests 为准。**

### 6.2 P1 活动中心

真机 Room（隔离库名 `amber_s9_activity_*`，不碰用户 DB）：

`ConversationActivityStoreDeviceTest.excludePriorityOpenAsReadColdStartAndUpgradeBaseline` — `/tmp/amber-s9-connected-core.log` OK。

覆盖：等待 / 失败 / 完成 / 运行 四态投影与优先级、当前对话排除、打开即已读、关库重开冷启仍在、升级基线前历史完成不轰炸。

`AppDatabaseMigrationTest` 全部 OK，含 `migration_21_22_creates_activity_watermark_tables`。

Compose `ConversationActivityDeviceTest` 四态面板：**未跑完**。原因：PIN keyguard，`createAndroidComposeRule` 在 `waitForIdleSync` 挂起。未清 PIN。夹具保留供解锁后跑。

### 6.3 P2 Jev

真机 ART：

- `legacyScreenAutomationSplitsToPhoneVerificationAndActions`
- `explicitPhoneKeysWinAndUnknownKeysDoNotDropTheSetting`

均 OK（`/tmp/amber-s9-connected-core.log`）。

`JevScreenDeviceTest.nativeSnapshotsRejectStaleAndVerifyLocalActions`：**未跑**。Amber 无障碍未启用；启用会改用户无障碍列表，本轮不做。

opt-in `realJev` / `bilibiliJev` / `SettingsTransferDeviceTest`：**未跑**（无 instrumentation args / 无保存的 Jev key，且需要 Amber 无障碍）。

### 6.4 P2 视频

真机无 Activity（profileable 同跑）：

- `portraitLandscapeSquareDecodeAndContainFitWithinDisplaySlot`
- `mutePlayerPausesAndDoesNotAutoResumeUntilStart`

封面屏 1168×1712、density 2.75、控制条 reserved 132、availH 1580（`video-contain.txt`）：

| 片段 | coded | decoded | fitted |
| --- | --- | --- | --- |
| portrait | 360×640 | 360×640 1.5s | 888×1580（高限制） |
| landscape | 640×360 | 640×360 1.5s | 1168×657（宽限制） |
| square | 360×360 | 360×360 1.5s | 1168×1168 ≤ availH |

`MediaPlayer` 预置音量 0，`pause` 后不自动 `start`（`video-player-pause.txt`）。

`VideoPlaybackDialog` 横竖方控制可点、ON_STOP 暂停、关弹层无声、时间线滚到 row-12 不抢滚动：**未在 Dialog UI 上完成**（PIN + Compose rule）。JVM `VideoDisplaySizeTest` 覆盖 contain 数学，不计入真机 Dialog。

### 6.5 P2 per-App

真机 ART + `FilePerAppSuccessPathStore` 于 `cacheDir`（不写用户 `filesDir/jev`）：

| 测试 | 结果 |
| --- | --- |
| `samePackageFreshSnapshotUniquelyAssistsAndCrossPackageIsIsolated` | OK |
| `ambiguousAndPageDriftFallBackWithoutInventingClicks` | OK（首轮失败后改为与 JVM 相同：fresh store + settings seed） |
| `plannerUniqueKindRefOnlyReordersLiveCandidates` | OK |
| `takeoverDoesNotRepeatAndClearBlocksInflightWrite` | OK |

日志：`/tmp/amber-s9-connected-core.log`。用 fixture 快照与 scripted transport，**不是**对微信等真实包发点击。未支付、未发消息、未删用户数据。

---

## 7. 本轮夹具（可提交）

| 文件 | 作用 |
| --- | --- |
| `app/src/androidTest/AndroidManifest.xml` | 登记 `ScreenOcrFixtureActivity`（测试 APK） |
| `ScreenOcrFixtureActivity.kt` | WebView/Canvas 工厂，AX hide descendants |
| `ScreenOcrDeviceTest.kt` | 离屏真实 ML Kit |
| `ConversationActivityDeviceTest.kt` | Store 隔离 Room + Compose 面板（后者待解锁） |
| `JevSettingsDeviceTest.kt` | 用途拆分 decode |
| `PerAppSuccessPathDeviceTest.kt` | 设备 ART 记忆 |
| `VideoPlaybackDeviceTest.kt` | Dialog UI（待解锁） |
| `VideoContainFitDeviceTest.kt` | 无 Activity contain + 静音 pause |
| `AmberS9VideoAssets.kt` | 本地 ffmpeg 色块 mp4 的 base64 |

生产代码无调试绕过。

---

## 8. 未闭合风险

1. PIN 锁屏阻塞所有 `createAndroidComposeRule` 真机 UI（活动中心面板、视频 Dialog/时间线）。安全替代：解锁后重跑上述两个 class；禁止 `locksettings clear`。
2. Amber 无障碍未开：原生 `JevScreenDeviceTest`、对真实前台包的 per-App 辅助未验。安全替代：用户手动打开 Amber 无障碍后定点跑，再恢复原 Dsh-only 列表。
3. 无 Jev API key / 账号：opt-in 真机自控与依赖云端的业务路径未验。
4. `fullBenchmarkRelease` 未装：生产 `applicationId`，debug 签名，装上去会覆盖正式包。OCR 的非 debuggable 证据用同签 graphite profileable。
5. 仓库无 `google-services.json`、无发行 keystore；未留假配置。正式 Play 签名 release 未组。
6. 测试 runner 无 Koin：不能把生产 `RouteActivity` 当 instrument 宿主。启动验证用 `am start`，不走 test runner。
7. WebView OCR 把 Latin 拆成 `OCR-` / `LATIN-` / `MARK` 多块；断言按子串通过，点击框来自「立即查看详」。
8. 既有 `AntigravityOAuthSessionTest` 2 失败、本地化脚本既有缺口：基线，本轮未动。
9. 折叠 override 已 `state reset`；结束时物理 CLOSED。亮度恢复 118，timeout 60000，stay_on 0，overlay 已删。电池 `dumpsys battery reset` 后仍显示 AC + 100%（锁屏也报正在充电），未强制 `unplug`。
10. 视频 Dialog 的「返回时间线不抢滚动」与「控制可操作」仍缺解锁后的 Compose 证据。

---

## 9. git status（本记录提交前）

```
 M app/src/androidTest/AndroidManifest.xml
?? app/src/androidTest/java/app/amber/agent/AmberS9VideoAssets.kt
?? app/src/androidTest/java/app/amber/agent/ConversationActivityDeviceTest.kt
?? app/src/androidTest/java/app/amber/agent/JevSettingsDeviceTest.kt
?? app/src/androidTest/java/app/amber/agent/PerAppSuccessPathDeviceTest.kt
?? app/src/androidTest/java/app/amber/agent/ScreenOcrDeviceTest.kt
?? app/src/androidTest/java/app/amber/agent/ScreenOcrFixtureActivity.kt
?? app/src/androidTest/java/app/amber/agent/VideoContainFitDeviceTest.kt
?? app/src/androidTest/java/app/amber/agent/VideoPlaybackDeviceTest.kt
?? docs/plans/2026-10-26-chat-history-performance-phase2.md   # 保持未跟踪
?? docs/plans/2026-10-31-android-selected-parity-handoff.md   # 保持未跟踪
?? docs/plans/2026-10-31-ios-android-feature-diff.md          # 保持未跟踪
?? docs/plans/2026-11-01-android-p0-p2-unified-acceptance.md  # 本文件，拟提交
```

拟提交：本文件 + 上表 androidTest。三份用户文档不加入。`/tmp` APK 与日志不进 git。

---

## 10. 停等最终验收

handoff §9 的 JVM / 组装 / 非 debuggable OCR / 安装启动 / 可在锁屏下完成的仪器路径已经落地，并留下可复核日志。Compose 面板、视频 Dialog、原生 Jev 快照、真实三方 App 经验仍打开。本项**不能**标完全完成。
