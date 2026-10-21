# 第四轮开发与验收计划

施工者：Grok CLI `grok-4.6`，reasoning effort `high`。验收者/项目负责人：Claude。
起点：`973df5a25`（生产代码 `b4477f42b`），分支 `codex/chat-streaming-performance`。
主线基准：`main` = `462c7f92411ea357e36e3d054f3b45a2e003657d`（本分支的 merge-base）。
起点 APK：`round3-final-v3.apk`（SHA256 `b9b37f78…aa41`），起点 benchmarkRelease：`round3-final-v3-benchmarkRelease.apk`，性能夹具：`opencode-perf-fixture.apk`（`3ed038fd…db1a`）。

用户授权：实现、相关测试、真机调试、本地提交。**不 push、不建 PR、不改写已有提交历史**（不 rebase/amend/reset 已有提交）。

## 0. 验收者复核结论（本轮的出发点）

1. **快照回归是本分支引入的，不是“既有失败”。** 同一 JDK、同一命令：
   - `main`：`MarkdownRendererSnapshotTest` 32/32 通过，`MarkdownTreeParityTest` 32 项 0 失败（12 skipped）。
   - 本分支 HEAD：`MarkdownRendererSnapshotTest` **6 失败**（08, 09, 10, 17, 20, 21）。
   - 第二、三轮的归因基线 `61c6f7a8f`、`198fa1e5b` 都已包含引入问题的 `fa03d938e`，所以“起点可复现 ⇒ 既有”的推论无效。今后**所有归因一律以 `main` 为基准**。
2. 失败分两类：
   - **(a) 原始 Markdown/HTML 标记闪现**（10, 20 等 `## Inline Code`）：`MarkdownDocument.kt:97-99` 缓存未命中时先显示 `plainMarkdownSnapshot`，`MarkdownNew.kt:61` 先显示原始 HTML 文本。生产上，回滚历史时会先看到 `##`、`**`、`<div>`，再变成正式样式。这是用户可见的回归，不只是测试取样太早。
   - **(b) 末尾多出 `$` / `**` 文本节点**（08, 09, 17, 21）：完整正式树之后多一个节点。根因尚未定位。**线索（假设，需证实或推翻）**：`main` 上历史消息初始化直接用 `MarkdownParseCache.getOrParse`，并且 `lastParsedContent = semanticContent`，所以从不重新解析；现在缓存未命中时 `lastParsedContent = null`，后续解析走 `streamingParseCache.finalParse(...)`（`StreamingMarkdownParser.kt:298`）和 `streamingLiveSuffixFor(...)`（`StreamingMarkdownParser.kt:66`），这条路径过去只在流式结束后运行。失败样本都含有 `$`、`$$` 或 `***`，正是 `preprocessMarkdownContent` 和流式修补（补 `**`/`$`）会改动的字符。
3. 第三轮遗留的产品/验证缺口：
   - 离底且最新回复可见时，降级呈现会一直保留，直到用户回到底部或把回复滚出屏幕。
   - `holdReadingOnTailGrowth` 在 `onGloballyPositioned` 之后才 `dispatchRawDelta`，补偿晚一帧。流式增高、图片异步加载都可能有单帧位移，至今没有逐帧量化。
   - 没有覆盖真实图片加载后的高度变化，也没有公式断言。
   - 从零生成（from-zero）mixed 的 P95 中位数只改善了 -4.3%，有一对反向 +17%；短答无稳定收益；混合 P95 仍约 36ms。
   - 整段无换行或带 ParagraphStyle 的超长消息仍是单个 `Text`，没有测量。
   - 只测了 fullDebug，没有测 release 类变体。
   - 全量 JVM 失败的归因不完整（`ProviderConfigToolsTest`、`MarkdownTreeParityTest[20-html-block]` 等）。

## 1. P0：快照回归 (b) 末尾多余节点

- 先写**红测试**：在 JVM（Robolectric/Compose test）里用样本 08、17 的原文渲染 `MarkdownBlock`，等待后台解析真正完成（用确定性的同步手段，例如显式等待准备完成的信号或 `Dispatchers` 注入，不要用 sleep），断言语义树末尾没有多余的 `$` / `**` 节点。先在当前 HEAD 上确认它失败，并把失败输出保存到 `round4-diagnosis/`。
- 定位根因（证实或推翻第 0 节的线索），在报告中写清楚：哪个函数、哪种输入、为什么 `main` 上不出现。
- 修复**生产代码**。禁止修改 golden、禁止放宽断言。修复后红测试转绿。

## 2. P0：缓存未命中时原始标记闪现 (a)

**验收标准：**
1. 对于不超过缓存预算的内容，settled 的 `MarkdownBlock` / `MarkdownNew` 在**任何一帧**都不向用户显示原始 Markdown 或 HTML 标记（`##`、`**`、`` ` ``、`<div>` 等）。
2. 主线程上不允许出现大段解析：在真机 trace 中，组合/测量阶段的同步解析单次不超过 **4ms**（设备 `25128PNA1C`，记录 trace 证据）。
3. 回滚历史的流畅度不得变差：新增“历史回滚、缓存冷启动”性能场景，P95 和 >32ms 占比相对起点 APK 不得出现可重复的 >10% 退化。

**建议方向**（由施工者用测量结果选择，并在报告中说明理由）：
- 在真机上测出解析耗时与长度的关系，定出阈值 N：内容不超过 N 时，缓存未命中就在组合中同步解析（成本受阈值约束）；超过 N 时，用不显示原始标记的占位方式（例如保留同等测量的不可见文本，或骨架），后台准备好后再替换。替换导致的高度变化必须走现有的阅读位置保持逻辑，并在真机上测量。
- 加强时间线的提前预热，让即将进入视口的条目在组合之前就已经解析好（现有 `ChatVirtualItemCache` 的准备队列）。
- `MarkdownNew` 的 HTML 文档同样适用上述规则。

**测试：** 修复后，`MarkdownRendererSnapshotTest` 与 `MarkdownTreeParityTest` 必须在当前树上：定点连续 10 次全绿，全量 5 次中每次都绿。不得修改 golden（`main` 用同一套 golden 是全绿的）。只有当生产代码按设计对超过 N 的内容保留异步路径、而某个样本恰好超过 N 时，才允许测试改为等待一个**已文档化的**后台准备完成信号，并且必须在报告里说明哪些样本、为什么。

## 3. P0：失败归因以 `main` 为基准

- 在 `main`（`462c7f924`）的独立 worktree 中跑全量 `:app:testFullDebugUnitTest --continue` 至少 5 次；在最终源码上同样跑至少 5 次。同一 JDK、同一参数。
- 按**用例名**统计每个失败用例在两侧的出现频率。最终源码的失败集合必须是 `main` 失败集合的子集。凡是 `main` 上同名用例从未失败、而本分支失败的，都由本分支负责修复（包括 `ProviderConfigToolsTest`、`MarkdownTreeParityTest[20-html-block]`、`ChatRunCoordinatorTest`、OAuth 两项，先查清它们在 `main` 上是否失败）。
- 确实与本分支无关、但在 `main` 上同样失败的用例，记录断言和堆栈，不需要修复。
- 输出 `round4-jvm/attribution.json|txt`（每个用例：main 失败/运行次数、HEAD 失败/运行次数、断言摘要）。worktree 用完后删除。

## 4. P1：离底且可见时，正式渲染无跳变地即时切换

目标：取消“离底可见就无限期推迟”。在不移动读者所看内容的前提下尽快切换到 settled 渲染。

**建议方向：**
- 先在不可见层或 `SubcomposeLayout` 中测出 settled 树的高度，然后把渲染器切换和滚动补偿放在**同一帧**提交。候选手段：`LazyListState.requestScrollToItem(index, offset)`（先确认项目使用的 Compose foundation 版本支持它，并验证它在同一测量轮生效）；或者把补偿从 `onGloballyPositioned` 之后的 `dispatchRawDelta` 挪到测量阶段。必须遵守 `ChatTailGrowthHold.kt` 里关于 Issue #21 的约束（不能在 OnPositioned 回调中同步滚动，否则会 NPE）。
- 用户正在拖动或惯性滚动时不与手势竞争，可以等滚动停下后再切换，但停下后必须在有限时间内切换。

**验收标准**（真机逐帧，坐标统一使用 `positionInRoot` + `TextLayoutResult.getBoundingBox`，同第三轮）：
- 锚点在上方旧消息、下方回复可见：切换前后**每一帧**锚点位移 ≤2px。
- 锚点在回复内部：锚点所在的顶级块（按源偏移映射）顶部每一帧位移 ≤2px。块内部的几何确实变化（例如锚点在表格里）时，以块顶为准，并在报告中说明。
- 钉底完成：立即切换，并保持在绝对底部（沿用第三轮的断言）。
- 拖动/惯性中结束：滚动停止后 2 秒内完成切换，切换时满足上述位移标准。
- 如果有证据表明同帧无跳变切换不可行，保留当前的推迟策略，但必须提交可行性证据（逐帧数据、失败方案），并把这一项标为 partial。不得为了通过而放宽阈值。

## 5. P1：消除补偿晚一帧

- 先量化：新增真机逐帧测试，记录离底阅读期间流式增高、以及图片异步加载完成时，锚点每一帧的屏幕位置。输出最大位移、超过 2px 的帧数。
- 如果存在超过 2px 的帧，用与第 4 项相同的同帧补偿机制修复。修复后目标：每帧 ≤2px。
- 有证据表明不可行时，按 partial 报告，给出数据与原因。

## 6. P1：真实图片与公式覆盖

- 在设备夹具中使用**能真正加载**的本地图片（例如测试 APK assets 或 `file://` 资源，不访问外网），覆盖：流式期间图片标签降级 → settled 后图片加载完成造成的高度变化 → 阅读锚点逐帧位移（钉底和离底两种情况）。
- 增加公式（行内 `$…$`、块级 `$$…$$`）在 settled 后正确渲染的断言，并与 P0-1 的 `$` 修复交叉验证（公式不能被当成多余节点，也不能显示成原始分隔符）。

## 7. P1：性能

**基线：** `round3-final-v3.apk`；夹具与测量协议同第三轮（两侧 `CHAT_RENDER_LAYOUT=true`，60Hz 临时上限并用 trap 恢复，包括 MIUI 字段；电池 ≤33°C、cpu0 ≤37.5°C 才开测；B/O、O/B、B/O 交替；协议在测量前写入 `round4-perf/protocol.json`）。

**场景与目标**（每项至少 3 对）：

| 场景 | 目标 |
| --- | --- |
| from-zero mixed（`CHAT_RENDER_FROM_ZERO=true`） | P95 中位数降低 ≥15%；且至少 2/3 对中 P95 与 >32ms 占比同时下降 |
| 稳态 mixed | P95 中位数退化不超过 5%（保住第三轮的 -35.6%） |
| short、chinese | 不出现可重复的 >10% P95 退化（3 对中最多 1 对超过 10%） |
| 历史回滚、缓存冷启动（新增，见第 2 项） | 不出现可重复的 >10% P95 退化 |
| 超长无换行单段（新增，≥6000 字） | 先测量并报告；如果能在**几何零误差**（逐行基线对比，同第一轮接缝测试的标准）前提下改善，就实现；否则报告原因 |

- 先用 trace 定位 from-zero 阶段的主线程热点，优先处理能测量到的热点。
- 禁止减少负载、延迟显示、丢弃输出来换帧率；禁止为达标修改统计口径；不得由 FrameMetrics 回调次数推 FPS。
- 每轮报告 P50/P95/P99、>32ms、GC/分配，并给出原始数据路径。

## 8. P1：release 类变体

- 先检查 `app/build.gradle.kts` 中 `benchmarkRelease` 的签名配置。确认使用 debug 签名后才能使用；**禁止索取或修改用户签名密钥**。
- 用 benchmarkRelease 构建最终 APK，对比 `round3-final-v3-benchmarkRelease.apk`，跑 short / chinese / 稳态 mixed / from-zero mixed，各至少 3 对。报告结果；不要求达到第 7 项的目标，但出现 >10% 的可重复退化必须定位原因。
- 如果夹具在该变体下无法运行，明确报告缺少什么，不得绕过。

## 9. 回归与设备验收

- 定点 JVM：第三轮的 48 项，加上本轮新增测试，全部通过。
- 真机交互回归（`scripts/chat-device-regression.sh`）连续 3 轮全绿，包括第三轮的 6 个方法，以及本轮新增的切换/图片/公式方法。必须走真实 UI→CAS→Room，不得用服务调用替代 UI 操作，不得只靠加长超时修复。
- 夹具 UI 测试（`ChatStreamingChunkUiDeviceTest` 等）全绿。
- 最终 APK 与最终源码一致：记录 SHA；安装后冷启动正常的产品页面（`RouteActivity`），不能停在测试壳。

## 10. 环境与边界

- 项目：`/Users/hugo/Desktop/git/AmberAgent`。先读 `AGENTS.md`、`/Users/hugo/.codex/RTK.md`，以及本目录的 `README.md`、`OPENCODE-RESULT.md`、`ROUND3-PLAN.md`、`ROUND3-RESULT.md`。不读其他产品仓库，不另起施工代理。
- ADB：`arch -x86_64 /Users/hugo/Library/Android/sdk/platform-tools/adb -s 392288d7`，设备 `25128PNA1C`。
- JAVA_HOME：`/Applications/Android Studio.app/Contents/jbr/Contents/Home`。
- **设备策略（2026-10-21 更新，以本条为准）：本轮真机不连接。**
  - 先做所有不需要设备的工作：第 1、2、3 项，以及第 4–6 项的代码实现和 JVM（Robolectric 真实 LazyColumn）测试。
  - 需要设备的**功能/几何/交互**验证（第 4、5、6 项的逐帧位移，第 9 项的交互回归和夹具 UI 测试）改用本机模拟器 `amber_native_16kb_api35`（arm64，API 35）：
    `~/Library/Android/sdk/emulator/emulator -avd amber_native_16kb_api35 -no-snapshot-save -no-audio &`，等待 `adb -s emulator-5554 wait-for-device` 且 `getprop sys.boot_completed` 为 1。脚本通过 `SERIAL=emulator-5554` 指定设备。模拟器走 TCP，不需要 `arch -x86_64` 那个 USB 绕行，但用了也无妨。
  - **模拟器数据不能作为性能验收依据。** 第 7、8 项只在模拟器上做冒烟（确认脚本、夹具、from-zero、新场景能够端到端跑完并产出 JSON），报告中把性能目标标为“待真机”，不得写成通过；也不要在模拟器上修改刷新率等系统设置。
  - 报告中每条设备证据都要注明是模拟器还是真机。真机复验清单（命令、APK、预期结果）单独列一节，供负责人在真机连上后执行。
  - 不要循环安装，不要绕过锁屏或修改安全设置；需要用户操作时，在报告里写出具体的一步操作。
- 不清除用户数据，不修改用户模型设置，不调用收费的外部模型；继续使用独立的测试 Application、files、Room、DataStore 沙盒。备份并恢复剪贴板和 UI 偏好；模型设置前后比较哈希。
- 临时刷新率限制必须通过 trap 恢复（包括 MIUI 字段），结束时核对。
- 只有一个施工者修改当前分支。基线 worktree 只用于测量和归因，用完删除，不得覆盖当前工作树。
- 原始证据写入 `artifacts/chat-performance/round4-*` 目录，不覆盖、不删除历史数据，包括失败证据。不提交 APK、trace、截图、用户数据或偏好备份（目录里的 `.gitignore` 已经忽略它们，只放行 Markdown 报告）。

## 11. 交付

- 本地提交：先单独提交本计划文档；代码按逻辑拆分成若干提交；最后提交报告。提交信息说明改了什么、为什么。
- 报告 `artifacts/chat-performance/ROUND4-RESULT.md` 包含：
  - 起止提交；
  - 每一项（第 1–9 项）的结论：通过 / 部分 / 未通过 / BLOCKED，并附证据路径；
  - 根因说明（第 1、2 项）；
  - 以 `main` 为基准的归因表；
  - 测试命令；
  - 逐帧位移数据；
  - 每轮性能的原始数据路径和复算命令；
  - 最终 APK 路径与 SHA；
  - 剩余风险。
- 没有满足全部标准，不得标为完成。不需要发送常规进度，也不需要逐步请求批准，独立完成即可。

## 12. 验收方式（负责人）

验收者会独立完成以下检查。报告中的数字必须能够从原始数据复算：
1. 审查 diff。
2. 在 `main` 与最终 HEAD 上分别重跑 Markdown 快照测试和定点测试。
3. 用 `scripts/summarize-chat-perf-pairs.py` 从原始 JSON 复算性能。
4. 抽查逐帧数据。
5. 设备连接时，抽跑交互回归。
