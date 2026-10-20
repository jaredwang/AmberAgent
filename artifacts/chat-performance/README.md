# Android 会话页流式性能优化验证

日期：2026-10-20。分支 `codex/chat-streaming-performance`，基线 `25c3f68c1cfca9ded53db62554125d9c28a69b36`。未推送、未创建 PR。

代码和 JVM 验证已完成；真机主 APK 安装和启动命令成功。**真机前后帧耗时、卡顿率、主线程等待与 GC 对比未完成**：设备安全锁屏无法由普通 dismiss-keyguard 解锁，隔离测试包安装被手机拒绝。下列 CPU 数字来自 Mac 上同一 JVM 的微基准，不能当作 Android 帧率收益。

## 改动与边界

- `StreamingSingleTextMarkdown` 的首次解析、后续完整解析、AST 到富文本映射都在 `Dispatchers.Default`。使用每消息、每样式的顶级块映射缓存，只重建变化块的样式；源文出现 `]:` 时保守禁用块缓存，以保证随后到达的引用定义可以改写早期链接。高亮结果变化也会失效缓存。
- 流式中间前缀不进入历史 Markdown LRU。只有已完成输入可以进入历史缓存。逐帧追加和 reveal 合并为一次 builder；寻找 reveal 窗口只读尾部至多 48 个码点，不再先拼接整条消息。释放器每帧只创建一次可见字符串；积压计数最多扫描 432 个释放单位，超过此值速率本来就已饱和，原有速度、延迟、单位和 Unicode 边界规则保持一致。
- 单个 `Text` 的最终不可变字符串拼接和 Compose 文本布局仍随消息长度增长。保留完整后台解析和单个 Text，避免拆成多个 Text 后改变换行、间距、跨块引用、选择和复制语义。本次没有宣称消除所有 O(n) 工作。
- 仅在静止钉底时启用尾消息高度动画；拖动、惯性和离底阅读使用自然布局与现有锚点补偿。延迟补偿执行前再检查是否已经回到底部。
- 最近流式消息的单项保护改由实际可见性和视觉实例控制。生成中、可见或仍有活跃显示实例时保持单项；结束且离屏、视觉实例排空/销毁后解除。新一轮生成不会按“最近两个”计数强制改变仍可见旧消息的 key。离屏销毁显示实例沿用原行为，重新进入时展示完整的持久消息。
- Markdown/HTML 首次缓存未命中时立即呈现完整原文，在后台准备样式。时间线切片遇到缺失解析时排队后台预热，完成后按 revision 重建。生产 UI 路径不再调用同步解析或等待 `Future.get()`；同步解析 API 及其 single-flight 等待仍供后台预热和非 UI 调用使用。
- 纯尾部更新共享历史 entries、消息索引列表和查询表。新增头段只有尾消息/排队项，历史索引查询仅加位移，不再复制整份历史列表和 map。保留历史节点身份扫描来识别等长历史替换；未向 domain/persistence 增加新版本或变更契约。
- 未修改公开组件签名、持久化格式、生成生命周期、模型配置或用户数据库。首次异步样式准备期间可能短暂显示 Markdown 原始标记；不通过延迟显示或丢弃输出换取性能。

## 验证命令与结果

以下命令全部成功，最终 **187 tests、0 failures、0 errors、0 skipped**：

```sh
AMBER_RENDER_MICROBENCH=1 JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew \
  :app:testFullDebugUnitTest \
  --tests '*Streaming*' --tests '*MarkdownToAnnotatedStringTest' \
  --tests '*ChatTimelinePlan*' --tests '*ChatTimelineEffectsTest' \
  --tests '*SearchPresentationAnchorTest' --tests '*ChatMessageVirtualItemsTest' \
  --tests '*ChatMessageLongPressTest' --tests '*MessageRenderRegexCacheTest' \
  --tests '*ChatGenerationFinalizationTest' --tests '*ChatGenerationRoundEngineTest' \
  --tests '*StoredResponseStopCancelTest' \
  :app:assembleFullDebug :app:assembleFullDebugAndroidTest --console=plain
bash -n scripts/chat-render-perf.sh
git diff --check
```

回归包括：混合 Markdown 每个前缀的缓存/新映射一致性；中文、emoji、组合字符、跨前缀/尾部边界的 surrogate；原文和链接注解的复制保持；替换内容；后到的引用定义；连续 250 次尾部更新的历史结构共享和跳转索引；加载/可见/排空保护；后台准备后恢复长消息虚拟化。现有生成结束、取消、长按菜单/复制、视觉正则、工具和长消息切片测试也通过。生成与持久化的端到端真机流程没有完成，不能用这些 JVM 测试代替。

原始构建及测试日志保存在本目录（被 `.gitignore` 忽略）：`verification.log`、`lifecycle-regressions.log`、`final-device-fixture-build.log`。完整 JUnit XML 在 `app/build/test-results/testFullDebugUnitTest/`。

## CPU 微基准（Mac / OpenJDK 25，非 Android 帧数据）

同一 warmed JVM、每组 9 个样本。最终原始样本：`jvm-microbenchmark-final.xml`。

| 负载 | 原路径中位耗时 | 优化路径中位耗时 |
| --- | ---: | ---: |
| 100 节长 Markdown，20 次尾部快照映射 | 8.811 ms | 2.258 ms |
| 大积压，100 次帧计数 | 31.000 ms | 1.050 ms |

测量曾发现逐节点完整语义指纹缓存反而更慢（5.68 → 14.93 ms / 20 快照），因此该实现已删除，改为原文块键和引用定义保守失效。该次原始样本在 `jvm-microbenchmark-semantic-fingerprint.xml`；中间复测在 `jvm-microbenchmark-refined.xml`。这些数字用于选实现，不预测真机 P95、卡顿率、GC 或主线程等待。

## 真机与产物

设备 `392288d7` / `25128PNA1C`。SDK 自带 arm64 adb 在当前 Darwin 27 上曾因 `libunwind: stepWithCompactEncoding - invalid compact unwind encoding` 崩溃；以 `arch -x86_64 /Users/hugo/Library/Android/sdk/platform-tools/adb` 运行后连接与主 APK 安装成功。

最终主 APK：
`/Users/hugo/Desktop/git/AmberAgent/app/build/outputs/apk/full/debug/app-full-arm64-v8a-debug.apk`

最终隔离测试 APK：
`/Users/hugo/Desktop/git/AmberAgent/app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk`

真机命令：

```sh
arch -x86_64 /Users/hugo/Library/Android/sdk/platform-tools/adb -s 392288d7 install -r \
  app/build/outputs/apk/full/debug/app-full-arm64-v8a-debug.apk
arch -x86_64 /Users/hugo/Library/Android/sdk/platform-tools/adb -s 392288d7 shell am start -W \
  -n app.amber.agent.graphite/app.amber.agent.RouteActivity
```

`final-install.txt` 为 Success，`final-launch.txt` 为 Status: ok，`final-pid.txt` 确认进程存活。`final-keyguard.txt` 仍为 `showing=true` / `SCREEN_STATE_OFF`；不能声称界面可见或手势验证完成。`final-test-install.txt` 记录 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`。未修改手机安全设置。安装均为 `-r`，没有清数据。

本目录保留 `baseline.apk`（修改前）、`optimized.apk`、`optimized-test.apk` 及 `apk-sha256.txt`，方便同设备复测；这些二进制没有加入 Git。锁屏截图包含无关通知，已删除，报告仅保留相关文本证据。

## 补齐真机对比

手机主人解锁并允许测试 APK 的 USB 安装后，在本仓运行：

```sh
scripts/chat-render-perf.sh baseline artifacts/chat-performance/baseline.apk \
  artifacts/chat-performance/optimized-test.apk artifacts/chat-performance/device-baseline
scripts/chat-render-perf.sh optimized artifacts/chat-performance/optimized.apk \
  artifacts/chat-performance/optimized-test.apk artifacts/chat-performance/device-optimized
```

两次使用同一个隔离测试包、同变体/内容/手势。baseline 分支选择原高度动画组合，optimized 分支调用生产的新高度策略。测试只创建内存 Compose 页面，使用本地确定文本，不访问会话仓库、设置存储或网络。不会调用原有 emulator-only、会改模型配置的 `ChatStreamingPerfProbeTest`。

脚本覆盖 short/chinese/mixed 三组；记录静止钉底、View 派发的拖动/惯性、排空和历史滚动阶段的原始 FrameMetrics、布局耗时、GC 前后快照、gfxinfo 和可用时的 sched/dalvik trace。trace 可用于检查主线程调度与等待；尚无实际 trace 可作结论。测试会等待最终 `onSettled`，帧样本不足则失败。刷新率、字体、模型、系统配置均保持原状态。测试包在旧基线 APK 上的完整运行兼容性仍需这一步实际验证。

尚需真实会话手动/隔离服务端到端确认：生成时上滑阅读锚点、结束后再滚动、取消/重新生成、切换会话、历史分页、选中复制和重启后的持久化内容。当前没有这些真机证据，也没有前后帧率/卡顿/主线程等待/GC 改善结论。
