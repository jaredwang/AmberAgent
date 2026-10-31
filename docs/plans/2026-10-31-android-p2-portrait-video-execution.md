# Android 选定差异补全 · P2 竖屏视频播放适配执行记录（2026-11-01）

> 本文件是 P2 竖屏视频专用记录，不修改 `2026-10-31-android-selected-parity-handoff.md`。
> 本轮只做 handoff §7。不做 per-App 记忆、不安装/启动/操作真机。
> 完成后停等主代理独立代码验收；未通过继续返工。真机横竖视频/关闭/退后台仍待最后统一验证。

## 1. 基线

- 工作分支：`codex/android-p0-ocr`
- 父提交：`b42ea9c2a fix(android): P2 Jev 脚本判断消费与核验独立路径`（主代理代码验收通过，见 P2 Jev 记录 §9；设备仍待统一验证）
- 三个既有未跟踪文档保持不提交：
  - `docs/plans/2026-10-26-chat-history-performance-phase2.md`
  - `docs/plans/2026-10-31-android-selected-parity-handoff.md`
  - `docs/plans/2026-10-31-ios-android-feature-diff.md`
- 未 `reset` / `clean` / `rebase`，未 push / 未开 PR。
- 未引入 media3 / ExoPlayer / 新 Gradle 依赖。

## 2. 调查：改前 `VideoPlaybackDialog` 实际行为

生产代码在 `ChatMessageMessagePartsBlock.kt`（现已抽到 `media/VideoPlaybackDialog.kt`）。改前现场，不以文档为准：

| 行为 | 改前 |
| --- | --- |
| 打开 | 时间线芯片点击才设 `playing=true`，才组合 Dialog / `VideoView` |
| 显示比例 | `Surface.fillMaxWidth().aspectRatio(16f/9f)`，忽略 prepared 尺寸 |
| 内部测量 | 普通 `VideoView` + `fillMaxWidth`；Compose 把 view 铺满 16:9，竖屏大黑框，横屏碰巧接近 |
| 控制条 | 未挂 `MediaController`，无播放/暂停 overlay |
| 失败 | 文案 +「用其他应用打开」；**无重试** |
| 关闭 | `onDismiss` / `AndroidView.onRelease` / `DisposableEffect.onDispose` 调 `stopPlayback` |
| 退后台 | `ON_STOP` → `pause()`；无 `ON_START` 自动 resume |
| prepared | `onPrepared` **无条件 `start()`**，即使宿主已 `ON_STOP` |
| URL 变化 | `remember(url)` 重建部分状态；`DisposableEffect` 只 key `lifecycleOwner`，旧 listener 可能残留 |

时间线芯片保持：固定 44.dp 缩略图、`ContentScale.Crop` 1:1，封面到位前后尺寸不变。本轮未改芯片。

## 3. 数学与旋转语义

`VideoDisplaySize.kt`：

- `effectiveVideoSize(reportedW, reportedH, rotation)`：`<=0` 得 `0×0`；90/270 交换轴一次；0/180 保持。
- `normalizeRotationDegrees`：模 360，负值回绕，就近对齐到 `{0,90,180,270}`。
- `rotationToApplyOnce`：**最多转一次**。若 coded 尺寸已知且与 reported 已经轴对调（player 已把 90/270 吃进 `getVideoWidth/Height`），返回 0，避免再转 90°。
- `resolvedVideoContentSize`：无效尺寸才回落 **16:9**。
- `displaySizeFromPlayerMetrics`：把上述规则接到 `MediaPlayer` 的 reported + `MediaFormat.KEY_WIDTH/HEIGHT/ROTATION`。
- `containFitVideo`：contain，不裁切不拉伸；结果 `width≤availableW` 且 `height≤availableH`。竖屏用高度限制，禁止 `width*(h/w)` 撑破 Dialog。
- `videoDialogAvailablePx` / `fitPlayerInDialog`：窗口减去 system insets 与双侧 extra padding。

生产接线：`MediaPlayer.getVideoWidth/Height` + 视频轨 `MediaFormat`（缺省 0）。`ContainVideoView.onMeasure` 用同一套 contain-fit，外层 Compose `Surface` 也按 fitted 像素设宽高，避免只改 Surface、View 内部仍被铺满拉伸。

## 4. 生命周期

`VideoPlaybackStartGate`：

- `onPrepared` 仅当 `generation` 匹配且 `startAllowed` 为 true 才 `start()`。
- `ON_STOP`：`pause()` + `startAllowed=false`，迟到的 prepared 不会后台出声。
- `ON_START`：只恢复允许位，**不**自动 resume（与改前一致）。
- URL 变化 / retry / `onDispose`：`invalidate()` 使旧 generation 的回调失效；先摘 listener 再 `stopPlayback()`。
- `key(url, retryNonce)` 包住 `AndroidView`，重组不留旧 player。

## 5. 失败路径：前后对照

- **改前：** 失败后仅「用其他应用打开」，无 Dialog 内重试。
- **改后：** 保留外部打开；最小补齐 `R.string.retry`，递增 `retryNonce` 重建 `VideoView`（未加新文案 key，未加 media3）。

点击才播放、关闭释放、后台暂停均保留。

## 6. 关键文件

生产：

- `app/src/main/java/app/amber/feature/ui/components/media/VideoDisplaySize.kt`
- `app/src/main/java/app/amber/feature/ui/components/media/VideoPlaybackStartGate.kt`
- `app/src/main/java/app/amber/feature/ui/components/media/ContainVideoView.kt`
- `app/src/main/java/app/amber/feature/ui/components/media/VideoPlaybackDialog.kt`
- `app/src/main/java/app/amber/feature/ui/components/message/ChatMessageMessagePartsBlock.kt`（芯片未改；Dialog 改为调用上面的 composable）

测试：

- `app/src/test/java/app/amber/feature/ui/components/media/VideoDisplaySizeTest.kt`
- `app/src/test/java/app/amber/feature/ui/components/media/VideoPlaybackStartGateTest.kt`
- `app/src/test/java/app/amber/feature/ui/components/media/ContainVideoViewTest.kt`

无新依赖、无 Room / 设置迁移、无 media3。

## 7. 测试命令与结果

```bash
./gradlew :app:testFullDebugUnitTest --offline \
  --tests 'app.amber.feature.ui.components.media.VideoDisplaySizeTest' \
  --tests 'app.amber.feature.ui.components.media.VideoPlaybackStartGateTest' \
  --tests 'app.amber.feature.ui.components.media.ContainVideoViewTest'
# 3 suites / 27 tests / 0 failures / 0 errors / 0 skipped
# /tmp/amber-p2-video-tests.log  BUILD SUCCESSFUL

./gradlew :app:compileFullDebugKotlin --offline
# /tmp/amber-p2-video-compile.log  BUILD SUCCESSFUL
```

覆盖：横/竖/方形；0 / 负 / 异常 / `Int.MAX_VALUE`；90/270/-90/450 旋转；player 已交换轴不再二次旋转；insets+padding 后竖屏高度不超过可用槽；`ContainVideoView` 在 EXACTLY 约束下 contain-fit；STOP / invalidate / 旧 generation 不 start。

未跑 `:app:assembleFullDebug`，未安装真机（本轮 Paseo 明确禁止）。

## 8. 最终真机待验点

本轮不安装、不启动、不操作真机。统一 APK 时至少验：

1. **竖屏视频**：弹层按 9:16（或真实比例）完整显示，无固定 16:9 大黑框，无裁切、无拉伸，高度不超过含系统 insets / 横屏 / 小窗后的 Dialog 可用区；失败重试与外部打开仍可点到。
2. **横屏视频**：比例正常，完整显示。
3. **方形视频**：完整显示，不拉成长条。
4. **关闭**：关 Dialog 后无继续出声。
5. **退后台**：`ON_STOP` 暂停，无后台出声；回到前台不偷偷自动 resume。
6. **时间线**：返回后滚动位置不被抢走；芯片 44.dp 占位不跳。

## 9. 停等验收

P2 竖屏视频施工完成（独立提交），停等主代理代码验收。未通过继续返工。本轮不进入 perApp，不操作真机。
