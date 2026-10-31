# Android 选定差异补全 · P0–P2 统一 JVM / 构建 / 真机验收（2026-11-01）

> 本文件是 handoff §9 专用交付记录，不修改 `2026-10-31-android-selected-parity-handoff.md`。
> 五阶段**代码**均已独立验收通过。本文件只记录统一测试/构建/真机结果。
> **不能**把「五阶段代码通过」写成全部完成。下文每项区分 JVM 与真机，未闭合项单列。

## 0. 结论（先看这个）

`752feca80` 最终验收**未通过**：设备证据未闭合。五阶段**代码**仍独立通过，本轮不否定。下表已更正，不再把离屏注入、不完整 suite 日志、contain-fit 数学、ART 假 snapshot 写成生产全链路。

| 层 | 状态 |
| --- | --- |
| 五阶段代码 | 独立验收通过（见 §1 提交） |
| JVM / 编译 / APK | 已跑。app 全量 3567 tests 中 2 个既有 `AntigravityOAuthSessionTest` 并发基线失败，未降断言、未无关修 |
| 非 debuggable / R8 等价 | `fullProfileableDebug` 已组装并曾覆盖安装；`fullBenchmarkRelease` 已组装、**未**装到生产 `app.amber.agent` |
| 真机安装/启动 | graphite `-r -t` 保留用户数据成功；`RouteActivity` Status ok |
| P0 离屏 ML Kit 像素 OCR | **部分证据已闭合（锁屏）**：owned ML Kit 识别中文/Latin 像素。`screenshotSource=offscreen_view_draw`，**不经过** `captureFreshBitmap` / Accessibility screenshot。完整 log：`/tmp/amber-s9-class-ocr-offscreen.log` OK (4 tests) CODE -1 exit 0。不能写成「非 debuggable 真实 OCR 全链路通过」 |
| P0 前台生产截图 OCR | **未跑**（待解锁）：fixture Activity 前台 + `DefaultScreenOcrEngine(context, ScreenCaptureManager(context))` + 实际 `AmberAccessibilityService` 截图 + AX 不给词 + 可点区域映射当前树唯一目标。随后非 debuggable 同包 profileable 再验 |
| P1 Room 四态 / 水位 / 冷启 | **已闭合（锁屏）**：per-class 完整日志 `INSTRUMENTATION_CODE: -1` + exit 0（§11.2）。不以 `/tmp/amber-s9-connected-core.log` 中途 STATUS 0 当 suite 通过 |
| P1 Compose 四态面板 | **未跑**（待解锁）。夹具：`createEmptyComposeRule` + 显式启动 + `ChatDeviceFixtureRunner`。锁屏隔离已证明 TIME_SET 崩测试可在 androidTest 消化，**不单是 PIN** |
| P2 Jev 设置 decode | **已闭合（锁屏）**：`JevSettingsDeviceTest` OK (2 tests) CODE -1 exit 0 |
| P2 Jev 原生快照 / scripted controller | **未跑**（待解锁）。可临时开启 Amber 无障碍并**精确恢复**原 `enabled_accessibility_services`（保留 DSH）。缺外部 Jev key **只**阻塞联网 `realJev`，不阻塞本地原生/scripted |
| P2 视频 contain-fit 数学 + 静音 MediaPlayer | **补充证据已闭合（锁屏）**：OK (2 tests) CODE -1 exit 0。**不是** `VideoPlaybackDialog` 实操 |
| P2 视频 Dialog 控制 / 时间线 | **未跑**（待解锁）。需真实界面截图 + pause/resume/close 点击 + 列表 row-12 |
| P2 per-App ART 假 snapshot | **已闭合（锁屏，假 snapshot）**：`PerAppSuccessPathDeviceTest` OK (4 tests) CODE -1 exit 0。`FakeAccessibilityController`，与真 controller **分开标** |
| P2 per-App 真 controller + 隔离 fixture | **未跑**（待解锁）。`JevScreenFixtureActivity` + 真实 AccessibilityController + scripted transport；定位/接管去重/清除。不对微信等真实业务包操作 |

前台项等用户解锁并保持亮屏后再跑。未 push / 未开 PR。不得把本表标成全部完成。

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

两个显式 runner（`am instrument` 组件选择，`newApplication` 固定 Application，不读私有 instrumentation 参数）：

- `AmberAgentAndroidTestRunner`：plain `Application`，锁屏 Room / JSON / ART / 离屏 OCR。`newApplication` 先装空 store / 无 AlarmManager 的 no-op `ReminderScheduler` Koin，再 disable `ReminderRescheduleReceiver`，`finish()` 恢复原 `enabledSetting`。plain Application 无生产 Koin 时 TIME_SET 会崩测试，这是测试隔离问题，不是已证实的生产 boot 缺陷。
- `ChatDeviceFixtureRunner`：`ChatDeviceFixtureApplication`（隔离 files/DB/prefs + Koin，不跑 `AmberAgentApp` cron/bubble/recoverInterrupted）。前台 Compose / Dialog 用此 runner。同样 no-op Koin + disable/restore，不碰其他组件。

Compose 使用 `createEmptyComposeRule`，规则启动时不拉 Activity。

证据目录：`/tmp/amber-s9-evidence/` 与 `/tmp/amber-s9-evidence/profileable-ocr/amber-s9/`。

### 6.1 P0 OCR — 离屏 ML Kit 像素（部分证据，不是生产截图链路）

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

同一 4 条 OCR 先前也在 debuggable graphite 上 2.513s 全过（`/tmp/amber-s9-connected-ocr.log`）。profileable 6 tests = 4 条离屏 OCR + 2 条 contain-fit 数学，**不是**前台 WebView/Canvas 生产截图通过。

生产路径（待解锁）见 §11：前台 `ScreenOcrFixtureActivity` + `DefaultScreenOcrEngine(context, ScreenCaptureManager(context))` + `screenshotSource=accessibility_screenshot` + AX 不给词 + 可点区域映射当前树唯一目标。离屏注入不得再冒充该链路。

### 6.2 P1 活动中心

真机 Room（隔离库名 `amber_s9_activity_*`，不碰用户 DB）：

`ConversationActivityStoreDeviceTest.excludePriorityOpenAsReadColdStartAndUpgradeBaseline` — `/tmp/amber-s9-connected-core.log` OK。

覆盖：等待 / 失败 / 完成 / 运行 四态投影与优先级、当前对话排除、打开即已读、关库重开冷启仍在、升级基线前历史完成不轰炸。

`AppDatabaseMigrationTest` 曾在未完成的 `core.log` 中出现过 STATUS 0；本轮以 §11 per-class 完整日志 + `INSTRUMENTATION_CODE` + 退出码为准。

Compose `ConversationActivityDeviceTest` 四态面板：**未跑**。先前挂起不能只写成 PIN：plain Application 无生产 Koin 时 TIME_SET 崩测试、`createAndroidComposeRule` 在规则启动时等 RESUMED、keyguard 下 Activity 到不了 RESUMED。夹具已改为 empty compose rule + `ChatDeviceFixtureRunner`；TIME_SET 隔离见 §11.5。解锁后跑前台面板。

### 6.3 P2 Jev

真机 ART：

- `legacyScreenAutomationSplitsToPhoneVerificationAndActions`
- `explicitPhoneKeysWinAndUnknownKeysDoNotDropTheSetting`

均 OK（`/tmp/amber-s9-connected-core.log`）。

`JevScreenDeviceTest.nativeSnapshotsRejectStaleAndVerifyLocalActions`：**未跑**（待解锁）。本轮允许临时开启 Amber 无障碍，保存并逐项恢复原 `enabled_accessibility_services`，禁止覆盖丢 DSH。

opt-in `realJev` / `bilibiliJev`：**未跑**。无外部 Jev key 只阻塞联网 realJev，不阻塞上述本地原生 + scripted transport。

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

`MediaPlayer` 预置音量 0，`pause` 后不自动 `start`（`video-player-pause.txt`）。这是解码器补充证据，**不能**代替 Dialog 实操。

`VideoPlaybackDialog` 横竖方控制可点、ON_STOP 暂停、关弹层无声、时间线滚到 row-12：**未在 Dialog UI 上完成**。解锁后跑改写后的 `VideoPlaybackDeviceTest`，记录截图与 pause/resume/close 点击，不用 contain-fit 数学冒充。

### 6.5 P2 per-App

真机 ART + `FilePerAppSuccessPathStore` 于 `cacheDir`（不写用户 `filesDir/jev`）：

| 测试 | 结果 |
| --- | --- |
| `samePackageFreshSnapshotUniquelyAssistsAndCrossPackageIsIsolated` | OK |
| `ambiguousAndPageDriftFallBackWithoutInventingClicks` | OK（首轮失败后改为与 JVM 相同：fresh store + settings seed） |
| `plannerUniqueKindRefOnlyReordersLiveCandidates` | OK |
| `takeoverDoesNotRepeatAndClearBlocksInflightWrite` | OK |

日志：当时写在未完成的 `/tmp/amber-s9-connected-core.log`（无 `INSTRUMENTATION_CODE`）。本轮 ART 假 snapshot 以 §11 per-class 完整日志为准。用 `FakeAccessibilityController` + scripted transport，**不是**真实 AccessibilityController，也**不是**对微信等真实包发点击。真 controller 见 `PerAppRealControllerDeviceTest`（待解锁）。未支付、未发消息、未删用户数据。

---

## 7. 本轮夹具（可提交）

| 文件 | 作用 |
| --- | --- |
| `app/src/androidTest/AndroidManifest.xml` | 登记 `ScreenOcrFixtureActivity` / `JevScreenFixtureActivity`（测试 APK） |
| `ScreenOcrFixtureActivity.kt` | WebView/Canvas 工厂，AX hide descendants |
| `ScreenOcrDeviceTest.kt` | 离屏 ML Kit 像素（部分证据） |
| `ScreenOcrProductionCaptureDeviceTest.kt` | 前台生产截图 OCR（待解锁） |
| `ConversationActivityDeviceTest.kt` | Store 隔离 Room + empty-compose 面板（后者待解锁） |
| `JevSettingsDeviceTest.kt` | 用途拆分 decode |
| `PerAppSuccessPathDeviceTest.kt` | ART + FakeAccessibilityController |
| `PerAppRealControllerDeviceTest.kt` | 真实 controller + 隔离 fixture（待解锁） |
| `VideoPlaybackDeviceTest.kt` | Dialog UI 实操（待解锁） |
| `VideoContainFitDeviceTest.kt` | 补充 contain + 静音 pause，不是 Dialog |
| `AmberS9VideoAssets.kt` | 本地 ffmpeg 色块 mp4 的 base64 |
| `AmberAgentAndroidTestRunner.kt` | 默认 plain `Application`；no-op ReminderScheduler Koin + disable/restore |
| `ChatDeviceFixtureRunner` | 第二 instrumentation；固定 `ChatDeviceFixtureApplication` |
| `TestReminderReceiverIsolation.kt` | 只动 reminder 接收器；persist 原 enabledSetting；no-op Koin 吞 TIME_SET |
| `AmberS9DeviceSupport.kt` | 解锁 assume、a11y 保存/恢复 DSH、证据、unique tree target |

生产代码无调试绕过。不启动真实用户作业。

---

## 8. 未闭合风险

1. 设备仍锁屏/休眠：前台 OCR / Compose 面板 / 视频 Dialog / 真 controller 均未跑。禁止 `locksettings clear`、不轮询 PIN。
2. P0 生产截图链路未闭合。离屏 `view.draw` 不得再写成全链路。解锁后跑 `ScreenOcrProductionCaptureDeviceTest`，再在 profileable 上复验。
3. Compose/Activity 挂起根因包括 plain Application 下 TIME_SET 崩测试、`createAndroidComposeRule` idle、测试包 Activity 注册。隔离在 androidTest runner（no-op Koin + disable/restore），不改生产 reminder。仍需解锁后实跑前台项。
4. 无外部 Jev key：只阻塞联网 `realJev`。本地原生快照与 scripted controller 不因此跳过。
5. `fullBenchmarkRelease` 未装：生产 `applicationId`，debug 签名。非 debuggable 用同签 graphite profileable。
6. 仓库无 `google-services.json`、无发行 keystore；未留假配置。
7. WebView OCR 可能把 Latin 拆成多块；断言按子串。
8. 既有 `AntigravityOAuthSessionTest` 2 失败、本地化脚本既有缺口：基线，本轮未动。
9. 视频 Dialog 仍缺解锁后的真实点击 + 截图。contain-fit 数学不是 Dialog 证据。
10. per-App ART 假 snapshot 与真 controller 必须分开标；不对用户真实账号应用操作。

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

## 10. 停等最终验收（历史，752feca80）

当时把离屏 OCR 写成「非 debuggable 真实 OCR 真机通过」，并把不完整 `core.log` 当 suite 通过。主代理最终验收未通过。更正见 §0 与 §11。本项**不能**标完全完成。

---

## 11. 752feca80 返工（本轮）

### 11.1 夹具 / runner

Compose/Activity 挂起**不能**只写成 PIN。锁屏下复现：

1. 默认 runner 用 plain `Application`，目标进程收到 `TIME_SET` 时 `ReminderRescheduleReceiver` 抛 `KoinApplication has not been started`，instrument 在测试执行前崩溃。这只证明 **plain test Application 不启动 Koin** 时会崩，**不能**写成已证实的生产 boot/时间变化竞态。
2. 曾用 `-e amberFixture s9` + 反射扫 `ActivityThread` 私有 Bundle；Android 版本不稳，已弃用。
3. `createAndroidComposeRule` 在规则启动时等 `RESUMED`；keyguard 下到不了 RESUMED。
4. 测试 APK fixture Activity 与目标包不是同一进程。

androidTest 隔离（不改生产 reminder 行为）：

- `AmberAgentAndroidTestRunner`：`newApplication` 固定 plain `Application`。
- `ChatDeviceFixtureRunner`：同一测试 APK 的第二 instrumentation，`newApplication` 固定 `ChatDeviceFixtureApplication`。选择方式：`am instrument -w -r -e class … app.amber.agent.graphite.test/app.amber.agent.ChatDeviceFixtureRunner`。
- `TestReminderReceiverIsolation`：先装空 store / 无 AlarmManager 的 no-op `ReminderScheduler` Koin（吞掉已在途的 TIME_SET，且不碰用户闹钟），再 disable 这一个 receiver；`finish()` 恢复 persist 的原 `enabledSetting`。不碰无障碍、不碰 WorkManager `RescheduleReceiver`。shell `pm disable`/`pm default-state` 在本机抛 `Shell cannot change component state`，不能当隔离手段。
- Compose：`createEmptyComposeRule` + `assumeUnlocked` / `assumeS9FixtureApplication`。
- 生产 OCR / 真 controller 夹具仍待解锁跑。

`d2ad26d88` 两行生产 Koin 守卫已由 `ecab66e9b` 撤销。那两行只覆盖 plain test Application 不启动 Koin 的路径，**不能**写成已证实的生产 boot/时间变化缺陷修复。

### 11.2 锁屏可跑 class（完整 raw 日志）

`/tmp/amber-s9-connected-core.log` 止于 `ScreenOcrDeviceTest` START，无 `INSTRUMENTATION_CODE`，**不算** suite 通过。本轮逐 class `am instrument -w -r`，要求文件含 `INSTRUMENTATION_CODE: -1`、`OK (N tests)`、adb exit 0。Gradle `--console plain`。不把中途 `STATUS_CODE: 0` 当整 suite 通过。

当时 APK 仍含后来撤销的生产守卫与反射 runner。结果只证明那些 class 能跑完，**不再**当作生产守卫的证据：

| class | log | OK | INSTRUMENTATION_CODE | exit |
| --- | --- | --- | --- | --- |
| `AppDatabaseMigrationTest` | `/tmp/amber-s9-class-migration.log` | 7 tests / 0.75s | -1 | 0 |
| `JevSettingsDeviceTest` | `/tmp/amber-s9-class-jevsettings.log` | 2 tests / 0.023s | -1 | 0 |
| `ConversationActivityStoreDeviceTest` | `/tmp/amber-s9-class-activity-store.log` | 1 test / 0.185s | -1 | 0 |
| `PerAppSuccessPathDeviceTest`（ART 假 snapshot） | `/tmp/amber-s9-class-perapp-art.log` | 4 tests / 0.228s | -1 | 0 |
| `VideoContainFitDeviceTest`（补充，非 Dialog） | `/tmp/amber-s9-class-containfit.log` | 2 tests / 0.609s | -1 | 0 |
| `ScreenOcrDeviceTest`（离屏部分证据） | `/tmp/amber-s9-class-ocr-offscreen.log` | 4 tests / 2.537s | -1 | 0 |

`PerAppSuccessPathDeviceTest` 第一次 120s 内 log 0 字节、host perl alarm exit 142。`am force-stop` + 一次 `KEYCODE_WAKEUP` 后重跑成功；**只认重跑这份完整 log**。无障碍结束后仍为 DSH。撤销生产守卫后的隔离复跑见 §11.5。

### 11.3 待用户解锁后再跑（未宣布完成）

设备仍 `mDreamingLockscreen=true` / PIN。不轮询、不索取 PIN。解锁并保持亮屏后立即跑：

1. `ScreenOcrProductionCaptureDeviceTest`（debug，然后同包 profileable）：前台 fixture + 生产 `DefaultScreenOcrEngine(context, ScreenCaptureManager(context))` + Amber 无障碍截图 `screenshotSource=accessibility_screenshot` + AX 不给词 + 可点区域映射当前树唯一目标。离屏 `view.draw` 不得再冒充此条。
2. `ConversationActivityDeviceTest`（`ChatDeviceFixtureRunner`）：四态面板截图 + 点击证据。
3. `VideoPlaybackDeviceTest`（`ChatDeviceFixtureRunner`）：Dialog 截图 + pause/resume/close 点击 + 列表 row-12。contain-fit 数学不是 Dialog 证据。
4. `JevScreenDeviceTest.nativeSnapshotsRejectStaleAndVerifyLocalActions`（plain 或 fixture runner 均可 native；`realJev` 需 fixture runner）：临时开 Amber 无障碍，精确恢复 DSH。无外部 key **只**阻塞联网 `realJev`。
5. `PerAppRealControllerDeviceTest`：隔离 `JevScreenFixtureActivity` + 真实 controller + scripted transport。不对微信等真实业务包操作。

解锁后不轮询锁屏、不索取 PIN。无障碍用完恢复 DSH。

### 11.4 本轮完成 / 仍需用户动作

**已经完成（独立可做）：** 报告矩阵更正；`ecab66e9b` 撤销生产守卫；双 runner + androidTest no-op Koin/disable/restore；锁屏小组隔离复跑见 §11.5。未 push / 未开 PR。三个用户未跟踪文档未动。

**仍需用户动作：** 解锁并保持亮屏，才能跑 §11.3 前台项。不得把本文件标成全部完成。

### 11.5 撤销生产守卫后的锁屏隔离复跑

命令（plain）：

```bash
adb shell am instrument -w -r -e class app.amber.agent.JevSettingsDeviceTest \
  app.amber.agent.graphite.test/app.amber.agent.AmberAgentAndroidTestRunner
```

命令（fixture）：

```bash
adb shell am instrument -w -r -e class app.amber.agent.JevSettingsDeviceTest \
  app.amber.agent.graphite.test/app.amber.agent.ChatDeviceFixtureRunner
```

本轮从 **DEFAULT（enabledSetting=0）** 冷启动复跑。`setComponentEnabledSetting` 单独不够：disable 返回后约 80ms `onReceive` 仍跑。no-op `ReminderScheduler` Koin（空 store、无 AlarmManager）让已在途 TIME_SET 不崩、不改用户闹钟，再 disable/restore 这一只 receiver。

| 项 | log | OK | CODE | exit | 说明 |
| --- | --- | --- | --- | --- | --- |
| plain `JevSettingsDeviceTest` | `/tmp/amber-s9-iso-plain-jev.log` | 2 tests / 0.019s | -1 | 0 | `AmberAgentAndroidTestRunner`；`newApplication app=android.app.Application`；logcat `original enabledSetting=0 (current=0)` → disable → `restored enabledSetting=0` |
| plain `PerAppSuccessPathDeviceTest` | `/tmp/amber-s9-iso-plain-perapp.log` | 4 tests / 0.208s | -1 | 0 | 同上 runner；force-stop 后 DEFAULT 冷启动未崩 |
| fixture `JevSettingsDeviceTest` | `/tmp/amber-s9-iso-fixture-jev.log` | 2 tests / 0.008s | -1 | 0 | `ChatDeviceFixtureRunner`；`newApplication app=…ChatDeviceFixtureApplication`；同样 restore 0 |
| reminder 接收器 | host dumpsys | — | — | — | 每 class 跑后 `disabledComponents` 只剩 WorkManager `RescheduleReceiver`。`ReminderRescheduleReceiver` 不在 disabled/enabled 列表（DEFAULT=0）。`SystemJobService` 仍在 `enabledComponents` |
| 无障碍 | `enabled_accessibility_services` | — | — | — | 仍仅为 DSH |

`firstInstallTime=2026-10-28 19:02:18` 保留。`lastUpdateTime=2026-11-01 05:46:18`（本轮只重装 androidTest APK）。完整 host 记录：`/tmp/amber-s9-iso-run3.log`。前台项仍等解锁，未轮询 PIN。
