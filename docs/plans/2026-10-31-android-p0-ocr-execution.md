# Android 选定差异补全 · P0 端侧 OCR 执行记录（2026-10-31）

> 本文件是本轮施工的专用记录，不修改 `2026-10-31-android-selected-parity-handoff.md`。

## 1. 基线与分支

- 工作分支：`codex/android-p0-ocr`（从 `8d6bdd8e7` 创建）。
- 合并 `origin/main@a2a159aa12c51864c476d7232823c3ec167b67ee`（`--no-commit` 检查后提交），无冲突：
  两端改动文件不重叠；本地消息菜单/返回键修复、三条设备测试与远端手机自动化 Phase 1-4 均保留。
- 合并提交：`9f5e4e0f1 merge: 整合 origin/main 手机操作闭环基线（保留本地消息菜单修复与设备测试）`。
- 未使用 `reset/clean/rebase`；三个既有未跟踪文档保持原样。

## 2. P0 实际完成内容

### 2.1 生产识别器改为可编译 ML Kit

- 新增依赖 `com.google.mlkit:text-recognition-chinese:16.0.1`（bundled，随 APK 打包，无需运行时下载模型）。
- `gradle/libs.versions.toml` 新增 `mlkitTextRecognition` 版本与 `mlkit-text-recognition-chinese` 库别名；`app/build.gradle.kts` 引入。
- 新增 `MlKitScreenTextRecognizer`（`app/src/main/java/app/amber/core/automation/ScreenOcrService.kt`）：
  - 使用 `TextRecognition.getClient(ChineseTextRecognizerOptions...)`、`InputImage.fromBitmap`、`Text.TextBlock/Line` 导航，全部编译期符号；
  - 删除旧的 `Class.forName` ML Kit/GMS Vision 反射路径与 `AndroidOnDeviceOcrRecognizer`；
  - 按行输出 `RawOcrRegion`（行级包围盒更适合点击定位），无行时回退块级；
  - 客户端懒创建并缓存，`close()` 释放；`process` 结果通过 `Task.awaitResult()` 挂起等待；
  - 失败映射为稳定诊断码：`not_enough_space` / `model_hash_mismatch` / `model_incompatible` /
    `model_not_found` / `recognizer_unavailable` / `network_issue` / `recognition_timeout` /
    `recognition_cancelled` / `invalid_image` / `recognition_failed`；
  - `recognizer_closed`、`bitmap_recycled` 也显式返回，不伪造空成功。
- 实际支持的 script：简体中文、繁体中文、拉丁（英文）。**不声明**日/韩/俄/天城文；代码内
  `ScreenOcrCapabilities` 明确列出 `supportedScripts` 与 `unsupportedScripts`。
  APK 内模型资产名 `taser_tflite_gocrchinese_and_latin_*`、`Hani_ctc`/`Latn_ctc` 可复核。

### 2.2 来源与降级状态显式化

`feature/runtime/api/.../ScreenOcrModels.kt` 新增：

- `ScreenOcrSource`：`BITMAP_OCR` / `EXTENDED_ACCESSIBILITY` / `UNAVAILABLE`；
- `ScreenOcrOutcome`：`NOT_TRIGGERED` / `RECOGNIZED` / `DEGRADED` / `UNAVAILABLE`；
- `ScreenOcrObservation.source/outcome/diagnostics/recognizer`、`ScreenOcrBlock.source`；
- `ScreenOcrObservation.redactedText()`（逐块脱敏后的模型/回执文本）。

判定规则：

- 位图 OCR 有文字 → `RECOGNIZED` + `BITMAP_OCR`；
- 位图 OCR 缺失/失败/无文字，仅扩展无障碍元数据 → `DEGRADED` + `EXTENDED_ACCESSIBILITY`
  （诊断码同列，如 `screenshot_unavailable` / `model_not_found` / `ocr_no_text` /
  `bitmap_recognizer_unavailable`）；
- 两者都没有 → `UNAVAILABLE`，绝不返回“成功但空”。

生产调用方与回执均已透传：

- `ScreenPrimitiveTools.appendOcrTelemetry`：输出 `ocr_source` / `ocr_outcome` /
  `ocr_recognizer` / `ocr_diagnostics`；块级 `source`；`ocr_text` 改用脱敏文本；
- `ScreenExpectation.verifiedScreenActionResult`：同样输出来源/降级/诊断；
- `JevScreenGoalRunner`：结果与 Jev 请求 state 均带 `ocr_source` / `ocr_diagnostics`，
  `readableScreen` 与 `ocr_blocks.text` 全部走 `SensitiveScreenTextClassifier`；
- `ScreenToolReceiptPresentation`：解析 `ocr_source/outcome/diagnostics/recognizer`，
  只有 `bitmap_ocr` 才显示“端侧 OCR”；`extended_accessibility` 显示“仅无障碍扩展元数据
  （非位图 OCR）”，`UNAVAILABLE` 显示“未产生可用文字”，诊断码单独展示。

### 2.3 生命周期、取消与脱敏

- `DefaultScreenOcrEngine` 持有截图 bitmap 的所有权，识别结束后 `finally` 回收一次；
  取消时用 `NonCancellable` 等待 ML Kit Task 落定后再回收，避免“识别器仍在使用已回收 bitmap”；
- 识别落定后 `coroutineContext.ensureActive()`：被取消的观察不会返回任何 block，
  调用方拿不到旧帧结果去派发动作；
- 截图提供方异常 / 返回 null 分别产生 `screenshot_failed` / `screenshot_unavailable`；
- `SensitiveScreenTextClassifier` 从 app 模块移入 `feature/runtime/api`，引擎、工具、Jev、回执
  共用同一套分类与脱敏规则；密码/OTP/银行卡不再进入 `ocr_text`、`visible_text`、回执摘要。

### 2.4 既有语义保持不变

- `AUTO` + 树覆盖率充足：零截图、零 OCR（单测断言调用计数为 0）；
- `OFF`：不截图、不 OCR；
- 低覆盖率、显式 `ON`、动作后 `verify_text` / 查询未命中 / 滚动等待等 post-action fallback 才触发；
- 归一化坐标仍按同一次观察的有效位图尺寸计算，旧 snapshot 不参与派发。

## 3. 依赖与体积

- 新增：`com.google.mlkit:text-recognition-chinese:16.0.1`（bundled 模型）。
- APK 内新增：`lib/arm64-v8a/libmlkit_google_ocr_pipeline.so` 约 10.55 MiB（未压缩）+
  `assets/mlkit-google-ocr-models/` 约 2.4 MiB，合计 26 个条目、约 12.93 MiB 未压缩。
- 运行时影响：首次识别需加载 bundled 模型（约数百毫秒到 2 秒），之后复用客户端；
  不依赖 Play Services 模型下载，离线可用。
- 无数据库/设置迁移。

## 4. 测试与构建证据

定点 JVM：

```bash
./gradlew :feature:runtime:api:testDebugUnitTest \
  :app:testFullDebugUnitTest \
  --tests "app.amber.core.automation.*" \
  --tests "app.amber.core.jev.*" \
  --tests "app.amber.feature.tools.*" \
  --tests "app.amber.feature.ui.components.message.ScreenToolReceiptPresentationTest"
# BUILD SUCCESSFUL
```

覆盖点：

- 覆盖充足不截图/不 OCR；低覆盖中文+OTP 位图 OCR 与坐标归一化；
- 中文 + Latin 同一帧识别；
- 识别器缺失 → `DEGRADED` + `extended_accessibility`；
- 识别失败（`model_not_found`）→ `DEGRADED`，不冒充成功；
- 截图不可用 → `UNAVAILABLE` + `screenshot_unavailable`；
- 识别成功但无文字 → `ocr_no_text`；
- bitmap 识别期间未回收、观察结束后回收；取消后不返回 block 且 bitmap 已回收；
- 生产构造（真实 Context + ScreenCaptureManager、无截图会话）显式降级；
- ML Kit 类在 classpath、旧反射类已不存在、错误码映射、敏感脱敏；
- 回执把 `extended_accessibility` 与 `bitmap_ocr` 区分展示。

全量 JVM：

```bash
./gradlew :app:testFullDebugUnitTest
# 3377 tests, 15 skipped, 2 failures
```

仅有 2 个既有失败：`AntigravityOAuthSessionTest` 两个并发用例。已在独立 worktree
检出合并基线 `9f5e4e0f1` 复现（未含本轮改动），确认为既有问题，与本轮 OCR 改动无关。

构建：

```bash
./gradlew :app:compileFullDebugKotlin          # BUILD SUCCESSFUL
./gradlew :app:assembleFullDebug               # BUILD SUCCESSFUL
./gradlew :app:minifyFullReleaseWithR8         # BUILD SUCCESSFUL（无 Missing class 警告）
```

R8 说明：仓库未提交 `google-services.json` 与 release 签名，直接 `assembleFullRelease`
会在 `processFullReleaseGoogleServices` 处失败。为完成“反射移除后 R8 无缺失类”验证，
本轮临时放置了仅用于本地校验的 `app/src/full/google-services.json`（占位 Firebase client），
跑通 `minifyFullReleaseWithR8` 后**已删除**，工作区无残留；未生成/保留 release APK。
`mapping.txt` 中 `MlKitScreenTextRecognizer` 与 1151 处 ML Kit 文本识别符号完整保留。

## 5. 未完成的验证（阻塞项）

按用户本轮指令，**未安装、未启动、未操作任何真机**，因此以下验收项仍未完成：

- WebView / Canvas / 自绘页面在 release（或等价非 debuggable）构建上的真实 OCR 文字与可点击区域；
- 设备首次模型加载耗时、低内存设备表现；
- 截图权限/无障碍服务在真机上的实际降级路径。

P0 现状：**代码与 JVM/构建验证完成，真机验证未完成**，不得标记为完全完成。

## 6. 已知限制

- 仅支持中文（简/繁）+ 拉丁；其他 script 需要另行引入 ML Kit 模型。
- 位图 OCR 与扩展无障碍结果会在同一次观察中合并；来源通过 `source` 区分，调用方不得
  把 `EXTENDED_ACCESSIBILITY` 当位图 OCR。
- ML Kit Task 不可取消：取消时等待当前帧识别落定后再回收 bitmap（通常 <1s），
  换取无 use-after-recycle；`DEADLINE_EXCEEDED` 由 ML Kit 自身错误码映射为
  `recognition_timeout`。

## 7. 本轮变更文件

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/java/app/amber/core/automation/ScreenOcrService.kt`
- `app/src/main/java/app/amber/core/di/AgentInfraModule.kt`
- `app/src/main/java/app/amber/core/jev/JevScreenGoalRunner.kt`
- `app/src/main/java/app/amber/feature/tools/ScreenPrimitiveTools.kt`
- `app/src/main/java/app/amber/feature/tools/ScreenExpectation.kt`
- `app/src/main/java/app/amber/feature/ui/components/message/ScreenToolReceiptPresentation.kt`
- `feature/runtime/api/src/main/kotlin/app/amber/core/automation/ScreenOcrModels.kt`
- `app/src/test/java/app/amber/core/automation/ScreenOcrFallbackTest.kt`
- `app/src/test/java/app/amber/core/automation/ScreenOcrProductionContractTest.kt`（新增）
- `app/src/test/java/app/amber/feature/ui/components/message/ScreenToolReceiptPresentationTest.kt`
