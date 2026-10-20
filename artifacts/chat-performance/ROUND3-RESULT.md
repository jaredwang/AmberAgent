# 第三轮施工结果

状态：**收尾完成；两项偶发 JVM 失败的用例级归因未闭合（见第 2、6 节）**。阅读锚点改为“排空后由 owner 决定切换到正式渲染”，取代上一版“完成后永久保留降级渲染”；最终 APK 三对性能达标，交互回归连续 3 轮全绿。第三轮前半由 gpt-6.1-sol / high 施工，收尾（本次）由 Claude 施工；主代理独立验收。

- 起点 `198fa1e5b09240ccdae3dcad7020576bf09eae58`（生产代码 `87d459575`）。
- 分支 `codex/chat-streaming-performance`。代码提交：`b4477f42b`（含上一位未提交的队列/分块/pacing 修复与本轮交接修复）。
- 不 push、不创建 PR；未清用户数据，未修改用户模型、签名密钥或安全设置。

## 1. 生产修复

### 阅读位置与结束呈现（第三轮收尾重做）

测量统一为 **Text 未裁剪的 `positionInRoot` + 同一字符的 `TextLayoutResult.getBoundingBox`**。旧语义 bounds 会把离屏字符裁到 0；旧测试的布局回调缓存还会留下已经替换的 chunk。两者都不能作为跨呈现切换的真实字符坐标。统一坐标后，原“排空即切 `MarkdownBlock`”在离底阅读锚点场景实测 `2330 → -1032px`（**-3362px**），证据 `round3-anchor-unified-red-manual/`、`round3-diagnosis/red-fixture-source.kt`。

上一版为避开这次跳变，让生成完的消息**永久保留**流式单 Text 呈现直到 lazy item 离开 composition。流式呈现是降级渲染（图片为 `[label: alt]` 文本、表格为等宽纯文本、公式为原始分隔符、代码块无卡片/复制按钮），最新回复通常一直停在屏幕上，用户不离开会话就看不到正式渲染。验收退回，本轮已移除该行为（`rememberRetainedStreamingPresentation` 删除）。

**现方案：排空后由会话页 owner 决定何时切换到 settled `MarkdownBlock`。**

- 块侧 `rememberStreamingPresentationLifetime`（`StreamingPresentationHandover.kt`）：流式期间始终用单 Text；生成结束（完成、取消、该块不再是尾块、重新生成后再次结束）后先排空 pacing buffer，再在 `Dispatchers.Default` 预热 settled 解析（首帧就是格式化树，不闪 plain fallback），最后等待 owner 闸门放行才切换。没有真正显示过流式呈现的块（widget/mini-app/延迟解析路径）不进入保留。
- owner 侧 `TimelineStreamingHandover` + `streamingSettleAllowed`（`ChatStreamingHandover.kt`），由 `chatTimelineItems`（拥有 `LazyListState` 的层）为每条助手消息创建，经 `LocalStreamingPresentationHandover` 下传。闸门只在有块等待时由 `snapshotFlow` 轮询 `layoutInfo`，不引入每帧重组；空闲/已切换消息零开销。

规则与理由：

1. **钉底**（`firstVisibleItemIndex == 0 && offset <= 24dp`）：立即切换；reverseLayout 原生底边锚定保持钉底。
2. **消息不可见**（不在 `visibleItemsInfo`）：立即切换；首个可见 item 之下的 item 变高不会移动可见内容。
3. **离底且消息可见**（锚点在消息内，或消息完整/部分可见而锚点在上方旧消息）：**推迟**，直到回到钉底、消息离开视口或 item 离开 composition（重建即 settled）。回到底部必然结束推迟，不会无限期停留在降级呈现。

为什么规则 2 没有按任务建议对“完全可见/锚点在别处”立即切换：本时间线是 `reverseLayout`，LazyList 以**首个可见 item（最底部）的底边**为锚。可见消息变高会把它**上方**的全部内容（包括位于旧消息里的阅读锚点）推走——任务说明中“下方 item 高度变化不会移动锚点”的前提在 reverseLayout 下对可见消息不成立。已分别验证：

- JVM（Robolectric 真实 LazyColumn）`ChatStreamingHandoverTest.gateAndHoldFollowReverseLayoutGeometry`：无补偿时上方 item offset 随消息变高而变化；
- 真机逐帧：先按“立即切换 + 既有 `holdReadingOnTailGrowth` 事后补偿”实现，`ChatStreamingChunkUiDeviceTest` 记录上方旧消息每次 layout 的位置为 `[1564, 1804]`（基线 1804）：切换帧上移 **240px**，下一帧才被补偿回来（证据 `round3-v3-dev/ui-4/`）。补偿必然晚一帧，因此可见状态下的离底切换无法做到不跳，改为推迟。推迟后同一场景逐帧 0 次位移（`maxDeviation=0`），回到底部后切换为 settled（表格单元格节点出现，回复高度 504→744px）。

取消、重新生成走同一块级生命周期与闸门（规则 4）。

**结束帧 hold 断档修复**：真机复跑发现完成场景偶发 `1195 → 1129px`（-66px）。原因是生成结束那一帧 `isLoadingMessage` 已为 false，而 `drainAmortizeGrace` 与块的等待上报都要下一帧才生效，`holdReadingOnTailGrowth` 节点被摘掉又重建，正好丢失随最终合并帧发布的尾段增高。现在 hold 由 `rememberHandoverReadingHold(isLoadingMessage || awaiting)` 锁存并延续 1.5s，节点不再断档；修复后同一完成场景（含最终帧在结束时发布的 4260/4279 情形）连续通过。另外 hold 对完全位于视口上方的已组合 item 不再补偿（那种变高不影响可见内容，补偿反而会移动画面）。

降级渲染只存在于“生成中/排空中/离底且该消息可见”这三种状态；已知代价是：用户离底且最新回复仍在屏上时，正式渲染要等其回到底部或把回复滚出视口才出现。

### 准备队列

删除独立的 32 项重试 LRU，改为**由缓存条目拥有任务生命周期**。计数随任务保留到成功消费、确定终态或拥有它的条目淘汰；重建时先增加新引用再释放旧引用，不能意外重置预算。

- 同时最多 32 个活跃内容、累计最多 1,200,000 字符、每 pass 最多 6 项；不保留 AST。
- 容量不足时明确缓存该条目的确定性 fallback，不反复请求准入。
- 每个准入任务实际准备最多 3 次；包括“报告成功但准备结果立即淘汰”。持续失败自动用同一任务重试，不需要反复依赖重组。
- 终态释放正文，只留条目拥有的轻量指纹；条目淘汰同时释放 metadata。
- 正文变化是新任务；取消异常向上传播并保留队列，取消不消耗失败预算。

33/80 失败文本的测试明确断言：32 项各调用 3 次，其余项准入失败调用 0 次；随后队列和请求计数保持静止。另覆盖 80 项成功/失败混合、缓存容量 0 的成功后立即淘汰、字符预算、条目淘汰、准备期间新请求、取消恢复及失败后改正文。

### 混合长文本

起点主线程 atrace 定位到 Text 测量、StaticLayout、绘制及重组；原始 trace 没有“CPU 采样栈”的含义，分析结果是主线程 section 的 elapsed/self wall time。

- 解析映射时在 Default worker 准备 chunk 边界/Span 切片，跨快照复用文字、样式、链接、接缝均相等的 chunk 实例。
- 主线程仍按原节奏展示全部字符；把活动自然行 chunk 的目标长度由 768 降至 256，减少每次追加需要重新布局/绘制的尾块。3072 起用阈值、无换行/CRLF fallback、Unicode 切割规则和接缝几何保持原契约。
- 同轮 trace，Text measure 单次 elapsed 均值约 `7.91 → 2.52ms`；StaticLayout 约 `3.22 → 1.09ms`。计数和原始 trace 保留，不能把 section self wall time 当 CPU 时间。

### 完成后的 pacing 生命周期

上一版收尾检查发现：流式 Text 在完成后仍被保留时，旧 pacer 的 `sawStreaming` 还会把已完成消息的追加编辑当成新积压，重新打字。新增同实例“生成→排空→静态追加编辑→下一次生成”单测，先在正确同步时钟的旧行为上失败，再于完全排空时清除该代 pacing 状态。静态编辑立即同步，下一次真实生成仍正常 paced；没有更改速度、节奏或丢输出。证据 `round3-diagnosis/retained-edit-red-synchronized*`、`final-lifetime-verified-build.log`。最终性能与设备验收重新使用含此修复的同一 APK，前面中间 APK 数据全部保留。

本轮去掉了“永久保留”，但规则 3 的推迟仍会让已完成消息在离底可见期间继续使用流式呈现（时长不定），期间静态编辑同样会被误判为新积压，所以该修复与 `StreamingDisplayLifetimeTest` 保留，行为不变。

## 2. JVM 与失败归因

最终源码定点：`ChatStreamingPerformanceTest` **18/18**、`StreamingTextChunksTest` **9/9**、`StreamingDisplayBufferTest` **15/15**、`StreamingDisplayLifetimeTest` **1/1**、新增 `ChatStreamingHandoverTest` **4/4**（闸门规则 + Robolectric 真实 reverseLayout 几何）、`ChatTailGrowthHoldTest` **1/1**，合计 **48/48**，在 5 次全量里每次都全绿。

全量 `:app:testFullDebugUnitTest --continue`（最终源码，`round3-v3/jvm/full-1..5`）：**3158 tests / 15 skipped**，失败 11、14、13、13、15。同 Java、同参数在起点干净 checkout 跑 11 次（`baseline-full-1..11`，3145 tests）：失败 13、11、13、14、10、9、11、14、12、13、14。逐例频率见 `round3-v3/jvm/attribution-v3.txt|json`，比较脚本口径同 `round3-diagnosis/case-attribution.json`（失败用例 ∈ 起点失败集合即视为起点可复现）。

- 13 个已知用例（OAuth latch 2 项、ChatRunCoordinator 时间戳 1 项、MarkdownRendererSnapshot 多项）在本轮起点 11 次中全部再次复现，没有新增稳定失败。
- **`ProviderConfigToolsTest` 两项（当前 1/5）**：异常为 `Dispatchers.Main is used concurrently with setting it`，发生在该测试类自己的 `tearDown → resetMain`（同一行）与它残留协程的竞争。起点定点重跑 8 次中有 1 次同类同异常，但落在同类另一方法（`apply rejects non-https base_url without partial commit`）；用例名不同，所以只能按**机理**归因到起点，用例级没有复现。本轮没有改动 `core.ai.tools`。
- **`MarkdownTreeParityTest.nativeTreeRendersEquivalentlyToJvmTree[20-html-block]`（当前 1/5）**：JVM 侧落在 plain-text fallback（`## HTML Blocks` 原文），与已知 `MarkdownRendererSnapshotTest` 的“`waitForIdle` 后异步解析仍未完成”属同一机理；起点 11 次全量、8 次定点都**没有复现该用例**（起点同期还出现过此前集合外的 `snapshot[10-inline-code]`，说明受影响样本随负载漂移）。这项按要求算**未完成的用例级归因**，没有改测试，也没有放宽断言。
- 定点重跑：起点/当前各 8 次 `ProviderConfigToolsTest + MarkdownTreeParityTest`，当前 0 失败。

上一位施工者的历史归因表保留如下（起点 5 次 / 当前 5 次，口径不变）：

| 具体用例（类见下文） | 起点失败/运行 | 当前失败/运行 | 断言/实际差异 |
| --- | ---: | ---: | --- |
| `late invalid grant cannot clear a session created after logout` | 5/5 | 5/5 | mock 请求入口 latch 在 2s 内未触发（46/77 行），未进入 session CAS 断言 |
| `late successful refresh cannot replace a session created after logout` | 5/5 | 5/5 | mock 请求入口 latch 在 2s 内未触发（46/77 行），未进入 session CAS 断言 |
| `matching old Stop preserves input accepted while the cancelled handler settles` | 3/5 | 4/5 | 317 行队列/持久化深比较只差 createdAtMs（示例 9487→9494）；Second 和 id 一致 |
| `semanticsDumpMatchesGolden[02-headings-all-levels]` | 2/5 | 2/5 | `expected:   text=Heading Level 1` / `actual:     text=# Heading Level 1` |
| `semanticsDumpMatchesGolden[03-emphasis-nesting]` | 2/5 | 2/5 | `expected:   text=Emphasis and Strong Emphasis` / `actual:     text=## Emphasis and Strong Emphasis` |
| `semanticsDumpMatchesGolden[04-links-inline]` | 2/5 | 1/5 | `expected:   text=Inline Links` / `actual:     text=## Inline Links` |
| `semanticsDumpMatchesGolden[05-links-with-titles]` | 1/5 | 0/5 | `expected:   text=Links with Titles` / `actual:     text=## Links with Titles` |
| `semanticsDumpMatchesGolden[08-fenced-code-no-lang]` | 5/5 | 5/5 | `expected:` / `actual:     text=$` |
| `semanticsDumpMatchesGolden[09-indented-code]` | 5/5 | 5/5 | `expected:` / `actual:   $` |
| `semanticsDumpMatchesGolden[11-gfm-table-simple]` | 2/5 | 2/5 | `expected:   text=GFM Tables — Simple` / `actual:     text=## GFM Tables — Simple` |
| `semanticsDumpMatchesGolden[12-gfm-table-aligned]` | 2/5 | 3/5 | `expected:   text=GFM Tables — Alignment Markers` / `actual:     text=## GFM Tables — Alignment Markers` |
| `semanticsDumpMatchesGolden[13-nested-lists]` | 3/5 | 2/5 | `expected:   text=Nested Lists` / `actual:     text=## Nested Lists` |
| `semanticsDumpMatchesGolden[14-ordered-list-start]` | 1/5 | 2/5 | `expected:   text=Ordered Lists — Non-Default Start Number` / `actual:     text=## Ordered Lists — Non-Default Start Number` |
| `semanticsDumpMatchesGolden[15-task-lists]` | 1/5 | 2/5 | `expected:   text=Task Lists (GFM Extension)` / `actual:     text=## Task Lists (GFM Extension)` |
| `semanticsDumpMatchesGolden[16-blockquotes-nested]` | 1/5 | 0/5 | `expected:   text=Blockquotes — Nested` / `actual:     text=## Blockquotes — Nested` |
| `semanticsDumpMatchesGolden[17-thematic-breaks]` | 5/5 | 5/5 | `expected:` / `actual:   **` |
| `semanticsDumpMatchesGolden[20-html-block]` | 5/5 | 5/5 | `expected:   text=HTML Blocks` / `actual:     text=## HTML Blocks` |
| `semanticsDumpMatchesGolden[21-inline-html]` | 5/5 | 5/5 | `expected:` / `actual:   **` |

- OAuth 两项均在等待 mock 请求入口的 latch 处失败，尚未运行登录代际断言；不是据此推断 session CAS 已坏或已修复。
- Coordinator 的流和持久化深比较只差创建时间戳，输入文本/ID 相同；本轮未修复这个既有时间敏感失败。
- snapshot 的原文标题与格式化标题差异，与异步 fallback 在 `waitForIdle` 后仍未完成的采样相关；`$`/`**` 尾部差异在两侧持续存在，未更改 golden。`15-task-lists` 还单独两侧各跑 2 次均绿，证明全量/顺序敏感；其本轮起点全量也确实失败，未将未复现项目笼统归为既有。
- 每个失败的完整 assertion 和 stack 在 `round3-diagnosis/case-attribution.json` 以及各 `*-xml/`；原始日志全部保留。最初 `baseline-repeat` 在当前工作树运行且有编辑时间交叠，**不计归因证据**；有效重复来自起点独立 checkout。

## 3. 最终 APK 的成对性能

协议在测量前写入 `round3-final-v3-pairs/protocol.json`：最终 APK `round3-final-v3.apk`（`b9b37f78…aa41`）对比基线 `round3-baseline.apk`（`278450d0…cb12`），夹具 `opencode-perf-fixture.apk`（`3ed038fd…db1a`），两侧 `CHAT_RENDER_LAYOUT=true`，60Hz 临时上限，电池 ≤33°C、cpu0 ≤37.5°C（20s 轮询），顺序 B/O、O/B、B/O。六次运行全部 `OK (1 test)` ×3 profile，冷却记录在各 `*-cooling.txt`（开测时电池 25.8–27.0°C、cpu0 ≤37.5°C），没有中断或残缺数据。复算：`scripts/summarize-chat-perf-pairs.py round3-final-v3-pairs/pairs-summary.json B1 O1 B2 O2 B3 O3`，判定见 `acceptance.txt`。

| profile | 对 | B P95 / >32ms | O P95 / >32ms | P95 变化 |
| --- | --- | --- | --- | ---: |
| short | 1 (B/O) | 16.373 / 2.70% | 18.133 / 1.38% | +10.7% |
| short | 2 (O/B) | 17.681 / 1.43% | 16.554 / 2.11% | -6.4% |
| short | 3 (B/O) | 25.313 / 1.40% | 21.620 / 1.47% | -14.6% |
| chinese | 1 | 21.650 / 0.45% | 16.669 / 0.45% | -23.0% |
| chinese | 2 | 17.236 / 0.45% | 17.261 / 0.92% | +0.1% |
| chinese | 3 | 20.027 / 0.68% | 16.675 / 0.46% | -16.7% |
| mixed | 1 | 56.051 / 33.98% | 35.224 / 7.33% | -37.2% |
| mixed | 2 | 54.667 / 33.43% | 41.555 / 9.18% | -24.0% |
| mixed | 3 | 56.138 / 32.14% | 36.081 / 6.60% | -35.7% |

- mixed P95 中位数 **56.051 → 36.081ms（-35.6%）**，≥15% ✔；P95 与 >32ms 同时下降 **3/3** 对 ✔。
- short P95 退化 >10% 的对数 **1**（第 1 对 +10.7%）≤1 ✔；chinese **0** ✔。**主门槛达标。**
- 中位数（summary.txt）：short P50 9.755→9.141、P95 17.681→18.133、P99 45.489→43.642；chinese P95 20.027→16.675、GC 3/77ms→2/51ms、分配 115.0MB→99.3MB；mixed P50 22.633→14.026、P99 75.86→50.742、janky 1.2%→0.68%、GC 14/552ms→12/447ms、分配 640.9MB→517.8MB。帧数据来自 FrameMetrics 每帧时长，没有由回调数推 FPS。
- 夹具直接渲染 `StreamingSingleTextMarkdown`，本轮交接逻辑不在这条热路径上；交接只在生成结束时执行一次，平时不增加每帧工作。

**补充（不替代主门槛）`amberRenderFromZero`**：`round3-final-v3-fromzero/`，夹具为最终测试 APK（两侧相同），仅 mixed，回复从 0 字开始，可见源 ≥8192（越过 3072 分块阈值）后再拖动。6 次都稳定跑完并 settled（最终 11645 字）。P95：113.8→109.0（-4.3%）、123.2→108.1（-12.3%）、113.1→132.4（+17.1%），中位数 -4.3%；>32ms：26.26→19.06、51.37→18.93、51.65→21.34，三对都下降。结论：从零增长阶段尾部 P95 收益不明显，第 3 对还反向；>32ms 占比稳定下降。原始数据保留。

## 4. 最终 APK 的设备验收

**先红后绿**（坐标统一为 `positionInRoot + TextLayoutResult.getBoundingBox`）：

- 红：`round3-v3-red/`。红构建 = 起点 + 上一位“永久保留”生产代码 + 本轮新测试（`red-source.diff`，app `3157b3a1…a93e`，test `red-test-3.apk` `4cd789e8…653e`）。`final-red/` 中 4 个新方法全部失败，原因都是降级呈现永远没有被替换：`productionPinnedCompletionShowsSettledRichMarkdown` → `Timed out waiting for settled rich renderer after completion`；completion/cancel 锚点 → `…settled renderer after returning to the bottom`；`productionVisibleReplyDefersUnderOlderReadingAnchor` → `…settled rich renderer after returning to the bottom`。`attempt1/` 和早期两次红跑暴露的是测试自身缺陷（等待最终合并帧、旧消息虚拟化导致锚点不在 composition），已修正测试，原始失败保留。
- 绿：`round3-final-v3-regression-1/2/3`，每轮 `installed-apks.sha256` 记录设备上实际安装的 APK SHA（app `b9b37f78…aa41`，test `be27b643…af8e40`），用户模型设置哈希前后一致。三轮均为 6/6 `OK (1 test)`：
  - `cancelRegenerateSwitchHistoryAndCopy`：取消（部分前缀落 Room）、长按重新生成、80→120 历史分页、切换会话、选中复制（SHA 一致）。
  - `reopenDurableReplyInFreshProcess`：新进程重开，最终持久化内容一致。
  - `productionPinnedCompletionShowsSettledRichMarkdown`（a+c）：生成中可以看到降级的图片标签；完成后不做任何操作，出现代码卡片“复制”节点、独立表格单元格 `CELL_ALPHA`、图片标签消失；时间线 `VerticalScrollAxisRange.value == 0`（绝对底部），尾部字符底边 985px，1s 后仍为 985px。
  - `productionCompletionPreservesOffBottomReadingAnchor` / `productionCancel…`（b）：锚点 1195→1195→1195px（≤2px），断言**推迟中**（锚点仍在 ≥256 字的流式块内）；真实上滑回到底部后切换到 settled（锚点成为独立段落节点）；Room 一致。
  - `productionVisibleReplyDefersUnderOlderReadingAnchor`（规则 2/3）：上方旧消息锚点 451→451→451px，可见回复推迟切换，回到底部后出现 settled 富文本。
- 夹具 UI `round3-final-v3-ui/`（最终 APK）：`ChatStreamingChunkUiDeviceTest` **6/6**。idle/drag/fling 在结束时推迟，相对锚点不变，回到底部后切换；pinned 立即切换并保持在底部（index 0，offset ≤24）；跨 chunk 原生选择复制；新增逐帧检查 `visibleReplyHandoverUnderOlderAnchorHoldsEveryFrame`：上方 item 0 次位移，回到底部后 settled，回复高度 504→744px。被否决的“立即切换 + 事后补偿”方案逐帧证据在 `round3-v3-dev/ui-4/`（240px 单帧跳变）。
- `round3-v3-dev/` 保存全部开发期中间失败，包括完成场景 -66px（hold 断档，已修）和回到底部时偶尔停在 12px 的情况（测试改为重复真实拖动直到 value 为 0）。
- 产品启动 `round3-final-v3-launch/`：安装 SHA `b9b37f78…aa41`；`am start` 启动 `RouteActivity`（COLD，1338ms），焦点为 `app.amber.agent.graphite/app.amber.agent.RouteActivity`，不是测试壳；打开已有会话“安卓客户端架构解析”，正式渲染正常（截图只存本机，不提交）。

## 5. APK、命令与边界

- 产品 APK `artifacts/chat-performance/round3-final-v3.apk`：SHA256 `b9b37f7835046488974106bd4378173fe6f02507d98c61c2f474bc5cdb10aa41`，2026-10-20 19:53:55 +0800 打包。
- 测试 APK `artifacts/chat-performance/round3-final-v3-test.apk`：SHA256 `be27b6434c2d81cd581f7418f12eb22fb4d81d86496d8a2c11d4d69432af8e40`，19:45:17 打包。
- 两者都在最后一次源码修改之后构建（`round3-v3/final-build-source-status.txt`：构建时工作区 diff SHA `b8310f3c…7861`，提交前复核相同）。

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
./gradlew :app:testFullDebugUnitTest --tests '*ChatStreamingHandoverTest' --tests '*ChatTailGrowthHoldTest' \
  --tests '*StreamingTextChunksTest' --tests '*ChatStreamingPerformanceTest' \
  --tests '*StreamingDisplayLifetimeTest' --tests '*StreamingDisplayBufferTest'
./gradlew :app:testFullDebugUnitTest --continue --rerun-tasks
./gradlew :app:assembleFullDebug :app:assembleFullDebugAndroidTest -PchatDeviceFixture=true
CHAT_RENDER_LAYOUT=true CHAT_PERF_TEMPERATURE_MAX=330 CHAT_PERF_CPU_TEMPERATURE_MAX=37.5 \
  scripts/chat-render-perf.sh baseline|optimized APP.apk artifacts/chat-performance/opencode-perf-fixture.apk OUT
CHAT_RENDER_FROM_ZERO=true PROFILES=mixed CHAT_RENDER_LAYOUT=true … scripts/chat-render-perf.sh …   # 补充
APP_APK=…/round3-final-v3.apk TEST_APK=…/round3-final-v3-test.apk scripts/chat-device-regression.sh OUT
```

`scripts/chat-device-regression.sh` 默认方法：`cancelRegenerateSwitchHistoryAndCopy reopenDurableReplyInFreshProcess productionPinnedCompletionShowsSettledRichMarkdown productionCompletionPreservesOffBottomReadingAnchor productionCancelPreservesOffBottomReadingAnchor productionVisibleReplyDefersUnderOlderReadingAnchor`。脚本新增：可选 `APP_APK/TEST_APK` 安装并记录本地 SHA；必记设备实际安装的 SHA；每个方法前发一次唤醒键（面板 60s 熄屏会让 Activity 中途停止，见 `round3-v3-dev/reg-6`；只是输入事件，不改设置）；只拉取 `production-*.json` 测试证据。`chat-render-perf.sh` 新增可选 `CHAT_RENDER_FROM_ZERO`，并兼容 bash 3.2。

结束状态：`peak_refresh_rate=120`、`user_refresh_rate=120`、`miui_refresh_rate=120`；无残留 gradle daemon、`am instrument` 或性能脚本进程；用于红构建/起点归因的临时 worktree 已移除。原始数据都在本机 `artifacts/chat-performance/round3-*`，不提交 APK、trace、截图或偏好备份。

## 6. 验收状态

| 项 | 结论 |
| --- | --- |
| 不再永久保留降级渲染；钉底完成立即正式渲染并保持底部 | 通过（红→绿，3 轮） |
| 离底锚点（完成/取消）≤2px 且断言推迟，回到底部完成切换 | 通过（红→绿，3 轮） |
| 旧消息锚点 + 下方可见回复 | 通过；规则 2 按 reverseLayout 实测改为推迟，理由见第 1 节 |
| 准备队列有界 / chunk 复用 / pacing 生命周期 | 通过（定点 48/48） |
| 全量 JVM 新增失败 0 | **部分**：多数失败在起点复现；`ProviderConfigToolsTest` 两项只做到同类同异常的机理归因；`MarkdownTreeParityTest[20-html-block]` 1/5，起点 19 次未复现，用例级归因未闭合 |
| 最终 APK 3 对性能门槛 | 通过（mixed -35.6%，3/3；short 1 对 >10%，chinese 0） |
| 交互回归连续 3 轮 | 通过 |
| 产品页启动 | 通过 |

剩余风险与未测项：

- 离底时，如果最新回复一直可见，正式渲染要等用户回到底部或把它滚出视口才出现。这是为避免 reverseLayout 下单帧跳变做的取舍。
- `holdReadingOnTailGrowth` 的补偿本身晚一帧。切换后图片异步加载等高度变化（以及流式期间每次增高）仍可能有单帧位移；本轮只测量了切换帧，没有逐帧量化流式增长过程。
- 钉底时切换会让视口上方内容随底边锚定移动，这是跟随模式的预期行为。
- 测试用图片地址不可加载，没有覆盖真实图片加载后的高度变化；数学公式没有单独断言。
- 没有在 release-derived/benchmark 变体上重测本轮交接逻辑；性能只覆盖 fullDebug 夹具，夹具不经过交接路径。
- 第 2 节两项 JVM 归因未完全闭合。
