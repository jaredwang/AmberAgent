# 第二轮施工结果（OpenCode）

状态：**partial**。缓存预热循环与交互回归已修复并有可重复证据；分块选区/阈值几何已补齐。长混合 Markdown 的帧时间仍未解决，渲染器切换时出现一次可见的阅读锚点跳变，均如实列在末节。

- 起点：`61c6f7a8fe21494aba4af4d18776cd7792ce788e`（分支 `codex/chat-streaming-performance`）
- 代码提交：`87d4595750c512923684eb58565a0425b6e71650`
- 本报告与 README 紧随其后单独提交；未 push、未创建 PR。
- 设备：`392288d7` / `25128PNA1C`，与上一轮同一真机；`fullDebug` arm64。

## 1. 实现改动

### 1.1 Markdown 准备队列的有界交接

`ChatVirtualItemCache.preparePendingMarkdown()` 不再无条件递增 revision 后重排队：

- `MarkdownParseCache` 公开确定性的 `isMarkdownContentCacheable()` 与 `prepareMarkdownContent()`，返回 `PREPARED / UNCACHEABLE / FAILED`。
- 超过 1,200,000 字符的内容**不再解析**，直接走无样式原文 fallback 并缓存该 fallback item；不再请求、不再循环。
- 每个内容指纹最多 3 次准备尝试（指纹只保存长度/哈希，不保存大文本或 AST），失败/淘汰后到上限即终止。
- 每次最多处理 6 项，避免一次准备把整段历史全部解析；剩余项通过一次 `preparationRequests` 自增继续，仍有界。
- 相同文本由 `pendingMarkdown` 去重；正文变化产生新指纹，可重新准备；解析异常只影响该项，不阻塞同批其他项；取消时保留未处理项。
- `preparedRevision` 只在确有结果或失败需要重建时递增。

新增 6 个确定性 JVM 测试（不依赖超大 fixture）：不可缓存不请求不解析、被缓存拒绝只尝试一次、单项失败不阻塞且重试封顶、容量淘汰有界停止、正文变化重新准备、跨节点相同文本只准备一次。

### 1.2 分块渲染的稳定部分复用

- `StreamingChunkPlanner` 增量维护已提交边界；只有解析快照变化或尾部非前缀时才全量重切。已提交的 `AnnotatedString` 跨帧复用。
- reveal 只重建/着色活动尾块，不再每帧重建整条 `AnnotatedString`。
- Layout 缓存每个接缝的 bridge 测量，只有相邻 `TextLayoutResult` 实例变化才重测；宽度并入 key。
- `remember(base, suffix, windowChars, baseColor)` 避免 reveal 收干期间输入未变时重复规划。
- 公开 API、生成所有权/取消契约、持久化格式未改；仍不延迟或丢弃输出。

### 1.3 交互回归修复（均为测试同步/定位缺陷，非生产缺陷）

- **取消后 marker 不可见**：时间线正确钉在最新内容，360 字符回复的首行已 compose 但被裁到视口上方；原测试只等 `isDisplayed()`，永不满足。改为真实拖动时间线到消息头，再长按；断言未放宽。
- **停止按钮坐标漂移**：原测试用流开始前测得的按钮位置加“测量后读取的 IME 高度”做补偿；某轮测量时 IME 已收起而高度仍读到 936，stopY=3394 超出屏幕，取消没有发生（整段 3785 字符完成）。改为等 IME 真正隐藏后重新测量可见的 `停止` 控件并点击其中心。
- 两处都保留真实产品 UI 操作；重新生成仍走长按菜单，不用服务调用伪装。

## 2. JVM 验证

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew \
  :app:testFullDebugUnitTest --tests '*StreamingTextChunksTest' \
  --tests '*ChatStreamingPerformanceTest'
```

- `StreamingTextChunksTest`：8/8 通过（含新增增量规划、跨帧复用、仅尾块 reveal、快照替换）。
- `ChatStreamingPerformanceTest`：11/11 通过（含新增 6 个准备队列测试）。
- 全量 `:app:testFullDebugUnitTest`：3145 tests，14 failed，15 skipped。失败为既有/波动项：`AntigravityOAuthSessionTest`(2)、`ChatRunCoordinatorTest`(1)、`MarkdownRendererSnapshotTest`(11)。在 `61c6f7a8f` 的独立 worktree 复跑同三类为 41 tests / 8 failed（OAuth 2 + Markdown 6）；当前树同子集为 7 failed，失败集合每次不同，属异步解析/顺序波动，与本次改动路径无关。日志：`opencode-diagnosis/jvm-all.log`、`jvm-subset-current.log`；基线 worktree 日志在 `/private/var/folders/.../opencode-baseline-tests.log`（未入仓）。

## 3. 真机交互回归

同一测试 APK 连续 3 轮（主 APK `e187…`，测试 APK `6bca…`），每轮两个独立进程均 `OK (1 test)`；另用最终提交 APK 复跑 1 轮：

| 轮次 | 取消长度 | Room 一致 | 重新生成 | 复制 SHA-256 | 80→120 | 新进程 | 设置哈希前后 |
| --- | ---: | --- | ---: | --- | --- | --- | --- |
| 1 | 408 | 是 | 3286 | `1eac2e…be0b0` | 是 | 3286 | 相同 |
| 2 | 408 | 是 | 3286 | `1eac2e…be0b0` | 是 | 3286 | 相同 |
| 3 | 396 | 是 | 3286 | `1eac2e…be0b0` | 是 | 3286 | 相同 |
| 最终提交复跑 | 384 | 是 | 3286 | `1eac2e…be0b0` | 是 | 3286 | 相同 |

- 取消长度均严格小于脚本回复 3785；长按重新生成的 3286 字符中文/emoji/组合字符/Markdown 与源文和 Room 一致。
- 产品“选择并复制 → 复制全部”哈希 `1eac2e291640eb589cdda5a042008a4d60a4b9dcbc9fa8940e1e16e53fdbe0b0`；剪贴板随后恢复。
- 切 120 节点历史：初载 80，滚动后 120，最早节点校验；切回原会话文本一致；新进程重开文本一致；测试会话事后删除。
- 模型 DataStore 哈希每轮前后均为 `4447cbebe21028a21fb79ad189e6667f36576334b9de0753faa263a36fd4b38e`；UI 启动偏好由脚本备份恢复。原始日志：`opencode-final-regression-1..3/`、`opencode-commit-regression/`。

## 4. 分块、选区与锚点（新增真机 UI 证据）

最终提交 APK 上 4 个 device test 全部通过（42.706s）。

- **跨 Text 原生拖动选区**（`ChatStreamingChunkUiDeviceTest`）：845/795/779 字符 4 块，跨第 1/2 块接缝原生长按拖动；复制 859 字符，范围 10..869，SHA-256 `7edeb7df…9628d8`，与渲染文本逐字一致，包含接缝换行、中文、`👩‍💻` ZWJ、`e+◌́` 组合字符及粗体/链接行。走系统选择工具（截图可见两个原生 handle 与“复制”菜单），未用产品“复制全部”代替。剪贴板前后恢复。
- **3072 阈值与窄宽/字体缩放**（`ChatStreamingThresholdUiDeviceTest`）：320dp；1.0/1.3/0.85 三档 fontScale 下，阈值前 1 个 Text，跨阈值 5 块、继续增长 8 块；首行 baseline 分别保持 `54→54→54`、`68→68→68`、`46→46→46`（容差 0.5px）；首块文本始终是阈值前文本的前缀。5000 字符无换行文本保持 1 个 Text（334 行），即确定性 fallback。
- **阅读锚点**（同一 device test）：离底拖动后记录锚点段落屏幕位置，生成跨多个 768 边界：`2245 → 2245px`（0px 漂移，容差 2px）；排空并切到 `MarkdownBlock` 后 `0px`，随后静止 1s 仍 `0px`。**渲染器切换位移为 -2245px**（锚点被带到视口顶部），是一次性跳变而非持续漂移，列为本轮 partial；见第 7 节。证据：`opencode-chunk-ui/final/`、`opencode-chunk-ui/commit-ui.txt`。

## 5. 性能（本轮起点 61c6f7a8f）

口径：同一真机、同一旧性能夹具测试 APK（`3ed038f…`，基线与优化两侧完全一致）、同一内容/节奏、`CHAT_RENDER_LAYOUT=true` 让两侧都用生产 `streamingTailLayout`，`fullDebug`，fullDebug 的 FrameMetrics 在 2.5s 预热后重置。刷新率用系统 peak/min + MIUI `user_refresh_rate/miui_refresh_rate` 临时固定为 60，trap 恢复；每轮 JSON 均 `refresh=60.000004`，结束后系统设置恢复为 `peak=120, min=null, user=120, miui=120`。

交替 5 对（B=61c6f7a8f，O=最终提交）：

| 轮 | 短答 P95 B/O ms | 中文 P95 B/O ms | 混合 P95 B/O ms |
| --- | ---: | ---: | ---: |
| 1 | 18.08 / 22.37 | 22.94 / 21.40 | 59.96 / 58.88 |
| 2 | 18.88 / 20.75 | 22.88 / 21.76 | 57.96 / 107.42 |
| 3 | 22.37 / 18.21 | 18.79 / 16.60 | 79.13 / 86.88 |
| 4 | 21.29 / 18.72 | 22.97 / 18.35 | 87.03 / 78.61 |
| 5 | 21.53 / 22.24 | 19.02 / 20.18 | 110.40 / 107.17 |
| 中位数 | **21.29 / 20.75** | **22.88 / 20.18** | **79.13 / 86.88** |

中位数其它指标（B/O）：

| 场景 | P50 ms | P99 ms | >32ms | gfxinfo 卡顿 | GC 次数/耗时 | 分配量 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 短答 | 9.59/9.39 | 33.81/30.48 | 1.49%/0.77% | 1.05%/0.99% | 1/72ms / 1/75ms | 3.86/4.09MB |
| 中文 | 12.64/12.43 | 27.31/24.57 | 0.23%/0.46% | 0.55%/0.55% | 3/72ms / 2/53ms | 139.09/114.93MB |
| 混合 | 43.96/40.71 | 91.70/116.91 | 73.0%/63.5% | 4.17%/3.49% | 19/526ms / 16/486ms | 595.07/529.74MB |

结论与限制：

- **中文**：P95 中位数 22.88→20.18ms；5 轮中 4 轮优化侧更低；分配 139→115MB、GC 3→2 次/72→53ms。方向一致的收益。
- **短答**：P95 中位数 21.29→20.75ms，逐轮互有胜负，不能声称稳定改善；分配略升（+0.23MB），GC 持平。
- **混合 Markdown**：P95 中位数反而 79.13→86.88ms，仍为 36%–75% 帧 >32ms；不能声称长文本卡顿已解决。但同轮对比第 1/4/5 对优化侧更低，第 2 对出现 57.96→107.42 的反向样本。基线自身在 5 轮中从 ~60 漂到 ~110ms，存在明显的时间/设备状态趋势（温度 31–39°C，未见锁频证据），因此**不能把中位数差全部归因于代码**，也不能据此宣称优化有效。分配量 595→530MB、GC 19/526→16/486ms、gfxinfo 4.17%→3.49% 是可复算的下降；P95 无稳定收益。
- 上表为 FrameMetrics 逐帧 JSON 复算；gfxinfo 覆盖预热/窗口且口径不同，>16.667ms 也不等价于 Android deadline 卡顿；不由回调数量推 FPS。

原始数据与复算：

- `opencode-perf/baseline-r1..r5/`、`optimized-r1..r5/`：每场景 `short/chinese/mixed.json`（逐帧、阶段、GC）、`*-gfxinfo.txt`、`*.ctrace`、`refresh-before.txt`、安装与 SHA。
- `opencode-perf/pairs-summary-final.json`、`summary-final.txt`：5 对逐轮与中位数；`scripts/summarize-chat-perf-pairs.py` 可复算。
- `reference-animate-r1/` 是本轮早期用旧高度动画策略跑的历史参考，**不进入成对结论**。

## 6. APK、命令与路径

最终提交构建（`opencode-diagnosis/commit-build.log`）：

- 主 APK：`artifacts/chat-performance/opencode-final-commit.apk`，SHA-256 `278450d04126c0f922084bccbfb74ef2c8fd04656465a0ce01b6e4c0b40dcb12`。
- 测试 APK：`artifacts/chat-performance/opencode-regression-test-commit.apk`，SHA-256 `a5e4fc6993b8598937b424f7edc789e79f26d8533e5caa2a1119d4d8966c2463`。
- 该对 APK 已安装并完成 4 个 UI test + 1 轮交互回归；正常应用随后从该主 APK 冷启动，`topResumedActivity=…RouteActivity`，PID `16968`（`opencode-commit-launch/`）。
- 性能测量用的主 APK：起点 `opencode-baseline-61c6f7a8f.apk` `3ee4d891…b056`；优化侧 `opencode-final.apk` `e187d4b5…2c29`（与本提交重建仅 `classes32.dex` 的调试行号/打包差异，其余 entry 逐字节相同；该二进制完成了 3 轮交互与首轮 UI 测试）。性能夹具 `opencode-perf-fixture.apk` `3ed038fd…1db1a`。

复现命令：

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew \
  :app:testFullDebugUnitTest --tests '*StreamingTextChunksTest' \
  --tests '*ChatStreamingPerformanceTest' \
  :app:assembleFullDebug :app:assembleFullDebugAndroidTest -PchatDeviceFixture=true
CHAT_RENDER_LAYOUT=true scripts/chat-render-perf.sh baseline \
  artifacts/chat-performance/opencode-baseline-61c6f7a8f.apk \
  artifacts/chat-performance/opencode-perf-fixture.apk \
  artifacts/chat-performance/opencode-perf/baseline-rN
CHAT_RENDER_LAYOUT=true scripts/chat-render-perf.sh optimized \
  artifacts/chat-performance/opencode-final.apk \
  artifacts/chat-performance/opencode-perf-fixture.apk \
  artifacts/chat-performance/opencode-perf/optimized-rN
python3 scripts/summarize-chat-perf-pairs.py OUT.json B1 O1 B2 O2 ...
scripts/chat-device-regression.sh artifacts/chat-performance/opencode-final-regression-N
adb shell am instrument -w -e class \
  'app.amber.agent.ChatStreamingChunkUiDeviceTest,app.amber.agent.ChatStreamingThresholdUiDeviceTest,app.amber.agent.ChatStreamingChunkLayoutDeviceTest' \
  app.amber.agent.graphite.test/app.amber.agent.ChatDeviceFixtureRunner
```

## 7. 未完成 / 偏差

1. **长混合 Markdown 帧时间未解决**：P95 中位数 79–87ms，>32ms 36%–75%。本轮改动降低了分配与 GC，但 Tail 仍受整段 Markdown 解析/映射与长文本布局支配；不宣称卡顿解决。
2. **渲染器切换锚点跳变**：本夹具中生成期间 0px 漂移，切换 `MarkdownBlock` 时一次性 -2245px（锚点被带到视口顶部）；切换后静止无持续漂移。该位移未修复，属 partial。根因方向：settled block 树的段落间距/总高度与单 Text 不同，`holdReadingOnTailGrowth` 对高度差的补偿把视口推向消息端部；产品时间线的 item 结构可能不同，但本夹具使用了生产同款原地切换。
3. **未测量发布类变体**：仓库存在 debug 签名的 `benchmarkRelease/nonMinifiedRelease`，但本轮成对性能只用 `fullDebug`；未改用户签名、未索取密钥。帧率结论不适用于 release。
4. **全量 JVM 套件不绿**：14 个既有/波动失败已用起点 commit 复现，未纳入本轮修复范围；相关定点 19 项全绿。
5. 未 push、未创建 PR；原始证据均在 `opencode-*` 目录，未覆盖上一轮数据；未提交用户配置、密钥或通知截图。
