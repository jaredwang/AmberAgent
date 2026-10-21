# 第四轮施工结果

状态：**未全部达标**。P0 快照回归与以 `main` 为基准的 JVM 归因已闭合；P1 同帧 hold 在 JVM 与模拟器「上方旧消息锚点」上成立，生产离底/消息内字符坐标与本地图片加载未过关；第 7、8 项性能按设备策略标为待真机。本轮功能/几何/交互证据全部来自模拟器 `amber_native_16kb_api35` / `emulator-5554`，不是 `25128PNA1C`。

- 起点 `973df5a25`（生产代码 `b4477f42b`）。最终 HEAD 在提交本报告之前为 `3ca5d8b69`；本报告是其后一条提交。
- 分支 `codex/chat-streaming-performance`。主线基准 `main` = `462c7f92411ea357e36e3d054f3b45a2e003657d`。
- 不 push、不创建 PR、不 rebase/amend/reset。未清用户数据，未改用户模型、签名密钥或安全设置。模拟器冒烟使用 `SKIP_REFRESH_CAP=true`，未改 peak/min/user/miui 刷新率。
- 施工期间 `adb devices` 后半段出现真机 `392288d7` / `25128PNA1C`。按计划第 10 节未用它做性能验收或交互复验。

## 0. 逐项结论

| 项 | 结论 | 证据 |
| --- | --- | --- |
| 1 P0 末尾多余 `$`/`**` | **通过** | `round4-diagnosis/red-settled-parse.log`（红 5/6）→ `MarkdownSettledParseRegressionTest` 9/9；快照 10× 全绿 |
| 2 P0 原始标记闪现 | **部分**（JVM 首帧通过；4ms 与历史回滚 P95 **待真机**） | `cacheMissFirstFrame*` 绿；`parse-threshold.json` N=8192；无 25128PNA1C trace |
| 3 P0 以 main 归因 | **通过** | `round4-jvm/attribution.json` `subsetOk=true`；HEAD-only 失败为空 |
| 4 P1 离底可见即时切换 | **部分** | JVM 逐帧 ≤2px；模拟器上方旧消息 `maxDeviation=0`；生产消息内锚点跳 103/147px；夹具消息内跳 −2946px |
| 5 P1 消除晚一帧 | **部分** | 与第 4 项同一套 hold；图片增高未测到（图片节点未组成） |
| 6 P1 图片与公式 | **部分** | 公式 `[Latex]` + 子节点 `$E=mc^2$` 有；图片 semantics `image=none` / 0×0 |
| 7 P1 性能 | **待真机** | 模拟器冒烟 6 个 profile 均 `OK (1 test)`，数字不作验收 |
| 8 P1 benchmarkRelease | **待真机** | APK 已用 debug 签名打出；`applicationId=app.amber.agent`，现有夹具 APK 打 `graphite`，未跑成对 |
| 9 回归 | **部分** | JVM 定点 61/61；交互回归 3 轮均在第 3 个方法失败；夹具 UI 7 测 3 过 4 败；RouteActivity 冷启动是产品首页 |

没有把任何模拟器 FrameMetrics 写成「通过」。没有改 golden、没有放宽断言、没有加长超时、没有减负载换帧率。

## 1. 生产修复

### 1.1 Settled 解析不再走流式修补（第 1、2 项）

**根因（相对 `main`，已用红测试证实）：**

`main` 上历史/`MarkdownBlock` 在组合里直接 `MarkdownParseCache.getOrParse`，并把 `lastParsedContent = semanticContent`，因此缓存未命中也不会再解析。本分支缓存未命中时 `lastParsedContent = null`，后续冻结走 `StreamingMarkdownParseCache.finalParse`。`finalParse` 在本轮之前对完整正文调用 `repairStreamingMarkdownTail`：源里奇数个 `$` / `**`（围栏代码提示、`***` 主题分割）会被补上，于是正式树后面多一个文本节点。这就是快照 08/09/17/21 的多余 `$`/`**`。

同一条未命中路径的第一帧是 `plainMarkdownSnapshot`（`MarkdownDocument.kt`）或原始 HTML 字符串（`MarkdownNew.kt`），所以 10/20 等样本在 `waitForIdle` 时看到 `## Inline Code` / `## HTML Blocks`。这是用户可见的原文闪现，不只是取样过早。

红测试（先在修复前 HEAD 上跑，`round4-diagnosis/red-settled-parse-xml/`，6 测 5 败）：

- `finalParseOfSample08DoesNotAppendDollarNode` / `sample08SettledDumpHasNoTrailingDollar`：末节点 `text=$`
- `finalParseOfSample17DoesNotAppendStrongCloser`：预处理被补了 closer
- `cacheMissFirstFrameDoesNotShowRawHeadingMarkup` / `cacheMissFirstFrameDoesNotShowRawHtmlMarkup`：首帧 `## …`
- `sample17SettledDumpHasNoTrailingStrongCloser` 在红跑中意外通过（dump 等到了格式化树）；未因此放宽其它断言

**修复（`3302b964c`）：**

1. `finalParse`：稳定块为空（从未流式的历史）或含 HTML 时改为 `MarkdownParseCache.getOrParse`；不再对 settled 正文做 `repairStreamingMarkdownTail`。
2. 可缓存且 UTF-16 长度 ≤ `MARKDOWN_SYNC_PARSE_MAX_CHARS`（8192）的内容，缓存未命中就在组合线程同步 `getOrParse`，第一帧就是格式化树。32 个 corpus 样本都 < 5 KiB，与 `main` 的同步路径对齐。
3. 可缓存但超过 8192：`pendingMarkdownSnapshot`（透明 Text + `clearAndSetSemantics`，占同等测量、无语义）。后台 `LocalMarkdownParseDispatcher`（默认 `Dispatchers.Default`）完成后再替换；高度变化走现有 hold。
4. 不可缓存超大内容仍用可见无损原文 fallback。
5. `MarkdownNew`：≤N 时 `remember` 里同步 `parseMarkdownHtmlDocument`；超过 N 时第一帧透明占位，不把 `<div>`/`##` 交给用户。

N 的选择见 `round4-diagnosis/parse-threshold.json`：本轮没有真机 4ms composition trace，8192 是保守上界（覆盖 corpus、对齐 `main`），**不是**按 4ms 标定的。真机需重测后才能把第 2 项的主线程预算标通过。

测试注入暂停的 dispatcher，用格式化 dump / 节点类型等待，不用 `sleep`。

### 1.2 同帧阅读 hold 与交接闸门（第 4、5 项）

第三轮 `holdReadingOnTailGrowth` 在 `onGloballyPositioned` 之后 `dispatchRawDelta`，reverseLayout 下可见回复变高会先把上方内容推走一帧（真机 240px）。Issue #21 禁止在 OnPositioned 里同步滚动。

本轮 hold 改成 `LayoutModifierNode`（`c5997134f`，随后 `46bdd72b0` 混合）：

- **空闲：** 在 `measure` 里对高度差调用 `LazyListState.requestScrollToItem(index, offset)`。该 API 可在测量阶段调用，同一帧的下一轮 layout 用补偿后的 offset。不能在 measure 里 `dispatchRawDelta`：会在测量中 `forceRemeasure`，Robolectric 抛 `IllegalArgumentException`（`round4-diagnosis/hold-same-frame-rerun.log`，4 个 hold/handover 用例失败）。`hold-same-frame.log` 是更早一次并发 Gradle 的 Kotlin daemon OOM，不是 IAE 证据。
- **拖动/惯性：** 不调用 `requestScrollToItem`（会取消手势，`ChatListNormalAnchorTest` 在 idle-only 构建上失败：期望 134.0 实为 74.0，差 60dp）。高度差记在 `onGloballyPositioned`，协程里 `dispatchRawDelta`，避免 Issue #21 的同步 NPE。
- `applyingHold` 防止 `requestScrollToItem` 触发的嵌套 measure 重入。
- `streamingSettleAllowed`：空闲一律允许切换（含离底可见）；`scrollInProgress` 时推迟，停下后下一次 gate poll 切换（远小于 2s）。

可行性边界（因此第 4、5 项标部分，不放宽 2px）：

- hold 补偿的是 **Lazy 条目高度** 导致的列表 offset，不是 Markdown 块树替换单 Text 之后、同一源偏移字符的 `getBoundingBox`。
- 模拟器上「上方旧消息 item 顶」逐帧 0px，且 `swappedWhileVisible=true`；「回复内部字符」在切换帧跳约 −2946px（见第 4 节）。这是渲染器几何换了，不是 hold 没进同一测量轮。

### 1.3 本地图片 Coil FileFetcher（第 6 项，未闭合）

Coil 3 的 OkHttp fetcher 拒绝 `file://` 字符串。夹具把 480×320 PNG 写进 app `filesDir` 并以 `file://` 嵌入。`coilImageData`（`d1d560e6d`）把 `file:` URL 映射成 `java.io.File`，走 FileFetcher。JVM `CoilImageDataTest` 3/3、`ParseImageSrcTest` 保留 `file://` src。

模拟器上 PNG **在 URI 对应路径存在**（5307 字节，`run-as` 可见 `files/amber-fixture-image.png`），但 `hasContentDescription(FIXTURE_IMAGE_ALT)` 仍为 none，夹具 image 节点 0×0。FileFetcher 映射是必要步骤，不足以在模拟器上组成带 alt 的 Image 节点。未加长超时。假设（未在本轮证伪）：节点被虚拟化出视口、alt 未下到 AsyncImage，或解码后未进入 composition。真机复验见第 10 节。

检测改为宽高比 > 1.2（夹具 3:2，占位是 120dp 正方形）；高密度模拟器上 180px 绝对值分不出占位和加载成功（`e2420f52b`）。

## 2. JVM 与失败归因

定点（最终混合 hold 源码，`round4-jvm/targeted-48-plus-hybrid-correct.log`，`targeted-48-plus-hybrid-xml/`）：

| 类 | 通过 |
| ---: | ---: |
| `ChatStreamingPerformanceTest` | 18/18 |
| `StreamingTextChunksTest` | 9/9 |
| `StreamingDisplayBufferTest` | 15/15 |
| `StreamingDisplayLifetimeTest` | 1/1 |
| `ChatStreamingHandoverTest` | 3/3 |
| `ChatTailGrowthHoldTest` | 3/3 |
| `MarkdownSettledParseRegressionTest` | 9/9 |
| `ChatListNormalAnchorTest` | 3/3 |
| **合计** | **61/61** |

快照：`MarkdownRendererSnapshotTest` + `MarkdownTreeParityTest`，最终源码连续 10 次 `status=0`（`round4-jvm/snapshot-10x-final.log`，约 11:00–11:09），混合 hold 后再确认 1 次 `rc=0`（`snapshot-hybrid-confirm.log`）。未改 golden。超过 N 的样本走已文档化的后台完成信号（`overSyncBudgetCacheMissDoesNotShowRawMarkup`）；32 个 corpus 样本都在同步路径上。

全量 `:app:testFullDebugUnitTest --continue`，同一 `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`：

- `main` worktree 5 次（`round4-jvm/main-full-1..5`）：每次 3120 tests / 14 skipped；失败 3、2、3、2、3。
- 最终源码取 **head-full-3..7** 共 5 次（`headRange=[3,7]`）：每次 3168 tests / 15 skipped；失败 3、2、2、3、2。`head-full-1/2` 是 `requestScrollToItem`-only 构建，`ChatListNormalAnchor` 失败，**不计入**最终归因，XML 保留为混合 hold 之前的证据。

| 用例 | main 失败/运行 | HEAD 失败/运行 | 断言摘要 |
| --- | ---: | ---: | --- |
| `AntigravityOAuthSessionTest.late invalid grant cannot clear a session created after logout` | 5/5 | 5/5 | `AssertionError`（latch） |
| `AntigravityOAuthSessionTest.late successful refresh cannot replace a session created after logout` | 5/5 | 5/5 | `AssertionError`（latch） |
| `ChatRunCoordinatorTest.matching old Stop preserves input accepted while the cancelled handler settles` | 3/5 | 2/5 | 深比较只差 `createdAtMs` |

`headOnlyFailures=[]`，`subsetOk=true`。`ProviderConfigToolsTest`、`MarkdownTreeParityTest[20-html-block]` 本轮 main 5 次与 HEAD 5 次都未失败。OAuth / Coordinator 按计划记录、不修。归因 worktree `/tmp/amberagent-main-round4` 已删。脚本 `round4-jvm/attribution.py`。

## 3. 模拟器性能冒烟（第 7 项，待真机）

协议先写入 `round4-perf/protocol.json`：`kind=emulator-smoke`，`acceptance=pending-real-device`，`skipRefreshCap=true`，`chatRenderLayout=true`，`SERIAL=emulator-5554`。数字**不能**对照第 7 项目标判定。

命令：

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
SERIAL=emulator-5554 ADB_ARCH=native SKIP_REFRESH_CAP=true CHAT_RENDER_LAYOUT=true \
  PROFILES='short history-cold' \
  scripts/chat-render-perf.sh optimized \
  artifacts/chat-performance/round4-emulator/apks/round4-full-debug.apk \
  artifacts/chat-performance/round4-emulator/apks/round4-full-debug-androidTest.apk \
  artifacts/chat-performance/round4-perf/emulator-smoke
# 其余 profile 同 APK 再跑 chinese/mixed/long-unbroken；from-zero mixed 输出到 emulator-smoke-fromzero
```

| profile | 目录 | frames | P50 | P95 | P99 | >32ms | chars | settled |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| short | `round4-perf/emulator-smoke/` | 152 | 3.990 | 11.044 | 13.514 | 0 | 111 | true |
| chinese | 同上 | 440 | 4.944 | 10.811 | 12.239 | 1 | 5185 | true |
| mixed | 同上 | 433 | 18.196 | 20.076 | 51.878 | 9 | 11645 | true |
| history-cold | 同上 | 149 | 18.252 | 23.127 | 27.371 | 2 | 98 | true |
| long-unbroken | 同上 | 442 | 9.106 | 10.285 | 18.619 | 0 | 6227 | true |
| mixed from-zero | `round4-perf/emulator-smoke-fromzero/` | 1969 | 18.118 | 51.474 | 67.915 | 243 | 11645 | true（initial=0） |

6/6 次夹具 `OK (1 test)`。超长无换行（≥6000）只做了冒烟，没有做第一轮接缝那种逐行基线零误差对比，也没有改单 Text 路径。from-zero 脚本与 JSON 能跑通。复算（真机成对之后才有意义）：`scripts/summarize-chat-perf-pairs.py`。

## 4. 模拟器功能 / 几何 / 交互（第 4、5、6、9 项）

设备：`emulator-5554`，AVD `amber_native_16kb_api35`，arm64 API 35。APK 见第 8 节。脚本 `SERIAL=emulator-5554 ADB_ARCH=native`，`-PchatDeviceFixture=true`。

### 4.1 交互回归 `scripts/chat-device-regression.sh`（模拟器，3 轮均未全绿）

默认 6 个方法。`rg -q 'OK (1 test)'` 使第 3 个方法失败后脚本退出，因此每轮只跑到 `productionPinnedCompletionShowsSettledRichMarkdown`。

| 方法 | r1 | r2 | r3 | 随后单跑 |
| --- | --- | --- | --- | --- |
| `cancelRegenerateSwitchHistoryAndCopy` | OK | OK | OK | — |
| `reopenDurableReplyInFreshProcess` | OK | OK | OK | — |
| `productionPinnedCompletionShowsSettledRichMarkdown` | 超时等待 settled rich | 生成 45s 超时 | 超时等待图片 loaded | Coil 后仍 `image=none` |
| `productionCompletionPreservesOffBottomReadingAnchor` | 未跑到 | 未跑到 | 未跑到 | **1191.0→1088.0**（−103px，`swapped=true`） |
| `productionCancelPreservesOffBottomReadingAnchor` | 未跑到 | 未跑到 | 未跑到 | **1075.0→1222.0**（+147px，`swapped=true`） |
| `productionVisibleReplyDefersUnderOlderReadingAnchor` | 未跑到 | 未跑到 | 未跑到 | 未单跑（夹具 UI 同场景 `maxDeviation=0`） |

钉底富文本超时日志（`round4-emulator/pinned-bounds/`，Coil + bounds 日志之后）：

```
timeout local fixture image loaded rich=(true, true, true)
image=none uri=file:///data/user/0/app.amber.agent.graphite/files/amber-fixture-image.png
```

语义里已有独立表格单元格、代码卡片、`[Latex]` 与子节点 `$E=mc^2$`，**没有** `FIXTURE_IMAGE_ALT` 节点。r1 的 `rich=(false,false,false)` 是更早 APK；Coil 之后表格/代码/公式已 settled，卡在图片。

### 4.2 夹具 UI `ChatStreamingChunkUiDeviceTest`（模拟器，7 测 3 过 4 败）

证据 `round4-emulator/chunk-ui/`。

通过：

- 跨 chunk 原生选择：`selection.json` `renderedLength=3201`（日志 chars=859 为所选片段）
- `visibleReplyHandoverUnderOlderAnchorHoldsEveryFrame`：`older-anchor-frames.json` `maxDeviation=0`，`swappedWhileVisible=true`，回复高度 442→656，回到底部 settled
- 钉底 viewport：`firstVisibleIndexAfter=0`，`scrollOffsetAfter=0`，`settledAfterDrain=true`

失败：

- idle / drag / fling 消息内阅读锚点：`Anchor jumped across generation end: -2946.0px`（`anchor-idle.json` `switchDelta=-2946`，`settledAfterDrain=true`）。坐标空间是未裁剪 `positionInRoot + getBoundingBox`。切换后字符落在 Markdown 块树里，与流式单 Text 的盒子不可比；列表 offset 已由 hold 补偿（上方 item 顶 0px）。
- `visibleReplyImageAndMathHandoverHoldsEveryFrame`：`image-math-frames.json` `latex=true`，`rawDollarParagraph=false`，`maxDeviation=0`，`swappedCell=true`，**`imageHeight=0` `imageWidth=0`**，同一 `file://` URI。

钉底 JSON 里字符 `switchDelta=-13251` 同样说明块树替换单 Text 后字符盒子会跳；该用例按列表 index/offset 判定，所以仍记为 viewport 通过。

### 4.3 产品冷启动

`round4-emulator/launch/`：`am start` `app.amber.agent.graphite/app.amber.agent.RouteActivity`，`LaunchState: COLD`，`TotalTime: 9617`。焦点 `RouteActivity`，不是测试壳。截图 `route-activity.png` 只留本机。

## 5. 第 8 项 benchmarkRelease

`app/build.gradle.kts` 中 `benchmarkRelease` `initWith(release)`，`signingConfig = signingConfigs.getByName("debug")`。未索取、未修改用户密钥。

APK `artifacts/chat-performance/round4-emulator/apks/round4-benchmarkRelease.apk`，SHA256 `44e42c24b9698021dcce19e5edabab49a66f622a107a694e032cdee565fa8f93`，67 302 284 字节，Android Debug 证书。`aapt` package **`app.amber.agent`**。

本轮功能/冒烟用的 graphite fullDebug package 是 **`app.amber.agent.graphite`**，androidTest 是 `app.amber.agent.graphite.test`。夹具默认 `PACKAGE=app.amber.agent.graphite`，**不能** instrument 这份 benchmarkRelease。未在模拟器上对 benchmarkRelease 跑 short/chinese/mixed/from-zero。缺的是匹配 `app.amber.agent` 的 androidTest APK，以及真机上与 `round3-final-v3-benchmarkRelease.apk` 的成对。

## 6. 最终 APK

生产代码最后改动 `d1d560e6d`（Coil FileFetcher）。其后 `3ca5d8b69` 只改测试（超时日志 + `ParseImageSrcTest`）。产品 APK 与最终生产源码一致。

| 文件 | SHA256 | 说明 |
| --- | --- | --- |
| `artifacts/chat-performance/round4-emulator/apks/round4-full-debug.apk` | `3ccaf8483eb69c014ff477742bd81e4e7023de79a7049fe1ed4de095173e6045` | graphite 产品 APK，112 586 233 字节，模拟器安装与冒烟用 |
| `artifacts/chat-performance/round4-emulator/apks/round4-full-debug-androidTest.apk` | `7724ce4fc57e069b6bb2d6ce1cd8c2e050b89fa85bbb6b1447528a7ca2449933` | 含图片 bounds 超时日志 |
| `artifacts/chat-performance/round4-emulator/apks/round4-benchmarkRelease.apk` | `44e42c24b9698021dcce19e5edabab49a66f622a107a694e032cdee565fa8f93` | `app.amber.agent`，debug 签名 |

APK、trace、截图按目录 `.gitignore` 不提交。

## 7. 测试命令

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'

# 定点 61
./gradlew :app:testFullDebugUnitTest --tests '*ChatStreamingPerformanceTest' \
  --tests '*StreamingTextChunksTest' --tests '*StreamingDisplayBufferTest' \
  --tests '*StreamingDisplayLifetimeTest' --tests '*ChatStreamingHandoverTest' \
  --tests '*ChatTailGrowthHoldTest' \
  --tests 'app.amber.feature.ui.components.richtext.tree.MarkdownSettledParseRegressionTest' \
  --tests '*ChatListNormalAnchorTest'

# 快照（禁止改 golden）
./gradlew :app:testFullDebugUnitTest --tests '*MarkdownRendererSnapshotTest' \
  --tests '*MarkdownTreeParityTest'

# 全量归因
./gradlew :app:testFullDebugUnitTest --continue

# 模拟器交互（本轮 3 轮未全绿）
SERIAL=emulator-5554 ADB_ARCH=native \
  APP_APK=artifacts/chat-performance/round4-emulator/apks/round4-full-debug.apk \
  TEST_APK=artifacts/chat-performance/round4-emulator/apks/round4-full-debug-androidTest.apk \
  scripts/chat-device-regression.sh artifacts/chat-performance/round4-emulator/regression-N
```

## 8. 提交列表（本轮，起点之后）

| SHA | 说明 |
| --- | --- |
| `b3dc8af9a` | docs：ROUND4-PLAN + `.gitignore` 放行 |
| `3302b964c` | fix：settled 解析去掉流式修补，缓存未命中同步/占位 |
| `c5997134f` | fix：measure 阶段 hold（随后被混合 hold 修正手势路径） |
| `247cd26dd` | test：同帧交接、本地图、公式、history-cold/long-unbroken 夹具 |
| `e2420f52b` | test：3:2 宽高比检测加载图 |
| `46bdd72b0` | fix：手势期间协程 `dispatchRawDelta`，不取消拖动 |
| `e21ec3ffd` | test：`SKIP_REFRESH_CAP` 模拟器冒烟不改刷新率 |
| `d1d560e6d` | fix：`file://` → Coil FileFetcher |
| `3ca5d8b69` | test：富文本超时记录 image bounds 与 URI |
| （本报告提交） | docs：ROUND4-RESULT |

相对 `973df5a25`：20 个路径，+1119 / −152。未改 golden。

## 9. 剩余风险

1. 消息内字符坐标在流式单 Text → settled 块树时跳变。hold 保的是条目顶/列表 offset。要满足「锚点所在顶级块顶 ≤2px」需要源偏移到块顶的映射，而不是字符在两种布局里的盒子相减。
2. 本地 `file://` 图在模拟器上文件在、节点不在。真机可能不同；在根因闭合前第 6 项和第 9 项钉底富文本不能标通过。
3. N=8192 未经 25128PNA1C 上 4ms composition 标定。超 N 的占位替换仍可能造成高度跳变，依赖 hold。
4. 交互回归脚本在第一处失败即停，3 轮数字不能当成 6/6。
5. benchmarkRelease 与 graphite 夹具 applicationId 不一致，第 8 项连冒烟都还没在该变体上跑过。
6. 全量 JVM 仍有 OAuth 5/5 与 Coordinator 时间戳偶发；已证明是 main 子集。

## 10. 真机复验清单（25128PNA1C）

设备连上后由验收者执行。ADB：`arch -x86_64 $ANDROID_HOME/platform-tools/adb -s 392288d7`。产品 APK 用第 6 节 graphite SHA；不要用模拟器 FrameMetrics 代替。

1. **安装并冷启动**  
   `adb -s 392288d7 install -r artifacts/chat-performance/round4-emulator/apks/round4-full-debug.apk`  
   `am start -W -n app.amber.agent.graphite/app.amber.agent.RouteActivity`  
   预期：COLD，焦点 RouteActivity，产品会话首页，不是测试壳。

2. **4ms 同步解析预算（第 2 项）**  
   冷缓存打开长历史。atrace：`adb -s 392288d7 shell atrace --async_start -b 32768 -a app.amber.agent.graphite gfx view`。  
   预期：组合/测量里单次同步 parse ≤4ms；超过则下调 N 或扩大占位路径。命令备忘在 `parse-threshold.json`。

3. **交互回归 3 轮全绿（第 9 项）**  
   ```
   SERIAL=392288d7 APP_APK=.../round4-full-debug.apk TEST_APK=.../round4-full-debug-androidTest.apk \
     scripts/chat-device-regression.sh artifacts/chat-performance/round4-device/regression-N
   ```  
   预期：6 方法均 `OK (1 test)`；钉底出现表格单元格、代码「复制」、**加载后的本地图（宽高比 >1.2）**、`[Latex]` 且无孤立 `$` 段落；离底完成/取消时阅读锚点每帧 ≤2px。

4. **夹具 UI**  
   `ChatStreamingChunkUiDeviceTest` 全绿。预期：上方旧消息 `maxDeviation≤2`；消息内块顶 ≤2px；图片非 0×0；公式 settled。

5. **性能成对（第 7 项）**  
   基线 `round3-final-v3.apk`（`b9b37f78…aa41`），夹具 `opencode-perf-fixture.apk`（`3ed038fd…db1a`）或本轮 androidTest。两侧 `CHAT_RENDER_LAYOUT=true`，**不要** `SKIP_REFRESH_CAP`，60Hz trap 含 MIUI 字段，电池 ≤33°C、cpu0 ≤37.5°C。顺序 B/O、O/B、B/O，每场景 ≥3 对。  
   场景：from-zero mixed（P95 中位数 ≥15% 且 2/3 对 P95 与 >32ms 同降）、稳态 mixed（相对第三轮 −35.6% 退化 ≤5%）、short/chinese（可重复 >10% 退化最多 1/3 对）、history-cold（可重复 >10% 退化）、long-unbroken（先报数；几何零误差才能改）。  
   复算：`scripts/summarize-chat-perf-pairs.py round4-perf/pairs-summary.json B1 O1 B2 O2 B3 O3`。

6. **benchmarkRelease（第 8 项）**  
   先 `assemble*BenchmarkReleaseAndroidTest`（或等价）打出 targetSdk/`applicationId=app.amber.agent` 的测试 APK。`PACKAGE=app.amber.agent` 跑 short / chinese / mixed / from-zero mixed 各 ≥3 对，对比 `round3-final-v3-benchmarkRelease.apk`。可重复 >10% 退化要定位。现有 `...graphite.test` APK 装不上这条变体。

---

## 4b 返工（真机 392288d7 / 25128PNA1C）

负责人第四轮验收不通过后的返工。本条优先于计划第 10 节「本轮真机不连接」。模拟器已在性能测量前 `adb -s emulator-5554 emu kill`。ADB 一律 `arch -x86_64 …/adb -s 392288d7`。未 push、未建 PR、未改写历史、未清用户数据、未改用户模型/签名。

已验收保持不动：第 1 项 settled 解析不走流式修补；第 3 项 main 基准归因；静止时 measure 中 `requestScrollToItem` 的同帧位置保持（另加 layout 后一帧 `dispatchRawDelta` 调和，因真机上对正在增高的 first-visible item 的 `requestScrollToItem` 不粘）。

施工提交（相对 Round 4 HEAD `00efb134a`）：

| SHA | 说明 |
| --- | --- |
| `8783ef675` | 回复内推迟 + 旧消息锚点即时切换 + 末条 hold 常驻 + idle 调和 |
| `1589a779f` | 本地 markdown 图非 0×0 |
| `1d15dd9ca` | 同步解析 N=256 |
| `b2d5f763f` | 闸门/图片/parse 测试 |
| `e98432f07` | 回归脚本失败后继续；summarize 发现 extra profile |
| `5b4262408` | 夹具 3 参数 `streamingTailLayout` JVM ABI（对齐 round3 debug） |
| `7c6e580f3` | release 保留该 3 参数方法（`@Keep` + 块体） |

APK：

| 用途 | 文件 | sha256 |
| --- | --- | --- |
| A–E 交互/夹具 | `round4-device/apks/fullDebug.apk`（hold-last） | `9f89b947b20916960ca04cf8fe71b15397c72da17b58f51a5c73b50e2b6e907f` |
| A–E 测试 | `fullDebug-androidTest.apk`（hold-last） | `e4af60fba74524319db89078feb2cbc89991e41cf3cabbb1bb4fffd5a937f442` |
| F 优化侧 | 同上路径，ABI 重建 | `6703fd6f1ee657388ecca0df34a36bbb98f949dd27fdef41d513f6b155f92804` |
| F 夹具 | ABI 重建 androidTest | `dcb823b5318607cde6679c92c018e80acda964e41df561e494c914db343240f0` |
| F 基线 | `round3-final-v3.apk` graphite | `b9b37f7835046488974106bd4378173fe6f02507d98c61c2f474bc5cdb10aa41` |
| G 优化侧 | `fullBenchmarkRelease.apk` `applicationId=app.amber.agent` | `2eb77687577c7e77e713af482f1d2a1926d44ff52de406eedd349a95dbb7a007` |
| G 夹具 | `fullBenchmarkRelease-androidTest.apk` targetPackage `app.amber.agent` | `73a7a49caa9f00257268a3d6869c6d117898d9759c3a3ee2936b214abbe26819` |
| G 基线 | `round3-final-v3-benchmarkRelease.apk` | `7e5fd02a687da281d9f216c6cd61b824374ad2072ee747d43dc54d80e7bc0076` |

### 逐项

| 项 | 结论 | 证据 |
| --- | --- | --- |
| A 第 4 项闸门 | **通过** | 完成/取消 `1195→1195`；旧消息锚点即时切换 `799→799`、`maxDeviation=0`、`swappedWhileVisible=true`。夹具 idle/drag/fling 起止相对坐标 `20198→20198` |
| B 第 6 项本地图 | **通过** | 钉底 `imageHeight=720`（3:2）；夹具 `1080×248` 宽高比 4.35。`coilImageData`：`file:`→`File`；http/https/content 仍是字符串 |
| C 第 2 项 N | **通过** | 真机 1k/2k/4k/8k 及 128–256–512。**N=256**（max 3.66ms）；512 已超 4ms |
| D 第 5 项逐帧 | **部分** | 条目顶/相对坐标起止 ≤2px。流式增高中间帧 max 216px；拖动手势 max 88px；惯性 max 56px。未放宽 2px |
| E 第 9 项回归 | **通过** | 交互 3×6/6；夹具 UI 7/7。脚本失败后继续跑完 |
| F 第 7 项性能 | **部分** | 协议先写；60Hz trap 含 MIUI 并恢复。稳态 mixed 达标；from-zero 15% 未达标；short 有 2/3 对 p95 回退 >10% |
| G 第 8 项 bench | **夹具已构建，成对无法跑** | 见下方缺失项 |
| H 全量 JVM | **通过** | `head-full-12` `subsetOk=true`（OAuth 2 条，main 5/5） |
| I 小修 | **通过** | `compensatedScrollTarget` 跨 item 收缩；`streamingSettleAllowed` 签名与语义一致 |

### A. 离底闸门

阅读线 = `layoutInfo` 中 `[viewportStartOffset, viewportEndOffset)` 的中点。落在该回复 item 范围内则推迟；落在上方旧消息且用户静止则即时切换，同帧补偿。未实现「按源偏移把锚点映射到 settled 顶级块」，因此回复内路径保持推迟（计划：做不到就保留推迟）。

- `productionCompletionPreservesOffBottomReadingAnchor` / `productionCancelPreservesOffBottomReadingAnchor`：3× `1195→1195`，`swappedWhileAnchored=false`。完成时 `scrollBefore=1105` `scrollAfter=1237`（条目 +132px 与 offset 对消，timeline 阅读线不变）。
- 原 `productionVisibleReplyDefersUnderOlderReadingAnchor` 更名为 `productionVisibleReplySwapsUnderOlderReadingAnchor`：旧消息锚点改为即时切换且逐帧 ≤2px（3× `before=after=799`，`maxDeviation=0`，`imageHeight=720`）。语义变化原因：阅读线在回复外时计划要求即时切换。
- 夹具 `ChatStreamingChunkUiDeviceTest` idle/drag/fling 均 `OK (1 test)`，未放宽 2px。
- 4b-final APK 上该旧消息用例曾 1/3 失败（Coil 增高时 hold 节点随 loading 拆掉）。末条 `streamingTailLayout` 改为 `isLastMessage || handoverReadingHold` 常驻后，hold-last APK 3 连续 6/6。
- JVM：`ChatStreamingHandoverTest` / `ChatTailGrowthHoldTest` 覆盖阅读线在回复内推迟、在回复外即时切换。

证据：`round4-device/regression-4b-hold-{1,2,3}/`，`round4-device/chunk-ui-4b-hold/`。

### B. 本地图片 0×0

根因（先红测试再修）：Coil 3 的 OkHttp 组件拒绝 `file://`；0 尺寸不发请求；测试 alt 只匹配无括号形式。`coilImageData` 把 `file:` / `<file:>` 映射为 `java.io.File`。http / https / content 仍传字符串，行为不变（`CoilImageDataTest`）。生产会走到 `file://` 的场景：助手 markdown 里的本地附件/生成图（夹具与钉底富文本）。预留 3:2 槽并以 `Size.ORIGINAL` 解码。

真机：钉底 `imageHeight=720`（1080 宽 × 3:2）；夹具 image-math `1080×248` 宽高比 4.35>1.2；图片加载导致的离底锚点 `olderTops maxDeviation=0`。

### C. 同步解析 N

`MarkdownSyncParseDeviceTest` 在 392288d7 上对 `getOrParse` 主线程取样。`parse-threshold.json`：

| 长度 | median ms | max ms | 全 ≤4ms |
| --- | --- | --- | --- |
| 256 | 2.80 | 3.66 | 是 |
| 512 | 4.93 | 5.14 | 否 |
| 1k | 6.93 | 7.51 | 否 |
| 2k | 10.79 | 12.93 | 否 |
| 4k | 16.94 | 19.12 | 否 |
| 8k | 26.26 | 30.29 | 否 |

N 从 8192 下调到 **256**。快照/settled 套件改为等到 formatted dump，golden 未改。

### D. 拖动/惯性逐帧

hold-last 夹具 evidence（`chunk-ui-4b-hold/evidence/`）：

| 用例 | growth max | >2px 帧 | gesture max | gesture >2px | 起止相对 |
| --- | --- | --- | --- | --- | --- |
| idle | 216px | 49/349 | 0 | 0 | 20198→20198 |
| drag | 216px | 47/233 | 88px | 117/117 | 20198→20198 |
| fling | 216px | 46/233 | 56px | 117/117 | 20198→20198 |

未修掉中间帧：hold 保的是 item 顶和列表 offset，单 Text 内部字符盒回流仍可跳。测试起止与交接相对坐标仍 ≤2px，未放宽断言。

### E. 交互回归

- `scripts/chat-device-regression.sh` 某一方法失败后继续跑完其余方法并写 summary。
- hold-last APK：`regression-4b-hold-{1,2,3}` 各 6/6，`rounds_failed=0`。
- `chunk-ui-4b-hold/summary.txt`：`ran=7 failed=0`。

### F. 真机性能成对（graphite fullDebug vs round3-final-v3）

`round4-perf/protocol.json` 在第一次测量前写入（锁屏中断与 ABI 重建有附注，不改「先写协议」）。`CHAT_RENDER_LAYOUT=true`，未设 `SKIP_REFRESH_CAP`。60Hz 覆盖 `peak_refresh_rate` / `min_refresh_rate` / `user_refresh_rate` / `miui_refresh_rate`。结束时恢复 `120 / null / 120 / 120`（`round4-perf/refresh-after-all-f.txt`）。预冷电池 ≤33°C、cpu0 ≤37.5°C。顺序 B/O、O/B、B/O，每场景 3 对。

第一轮因 `KEYCODE_SLEEP` 进入 MIUI NotificationShade，用户解锁后重跑（屏幕保持亮着）。第二轮 baseline 因夹具把 `streamingTailLayout(list,1,24)` 编成 5 参数方法而 `NoSuchMethodError`；补 3 参数 overload 后第三轮完整跑完。

数字（`round4-perf/device-pairs/summary.txt` 等）：

**稳态 mixed（目标：中位 p95 相对 round3 回退 ≤5%）**  
baseline 中位 p95=51.477，optimized=50.202（约 −2.5%）。over32 36.13%→19.1%。**达标**。

**from-zero mixed（目标：中位 p95 改善 ≥15%，且 ≥2 对 p95 与 over32 同降）**  
baseline 中位 p95=132.281，optimized=131.177（约 −0.8%）。over32 19.93%→21.02%。3 对里约 1 对两项同降。**未达标**。`round4-perf/device-fromzero/`。

**short（目标：可重复 >10% 回退最多 1/3 对）**  
中位 p95 22.641→18.82。分对 p95：22.641→26.499（+17%）、15.87→18.82（+19%）、22.93→18.088。**2/3 对 >10% 回退，未达标**。

**chinese**  
中位 p95 21.513→16.496。三对均下降。**达标**。

**history-cold**  
中位 p95 29.567→25.244。三对均下降。**达标**。`round4-perf/device-extra/`。

**long-unbroken（先报数）**  
中位 p95 37.211→37.540（+0.9%）。未改渲染几何。

### G. benchmarkRelease 成对

夹具已用现有 `testBuildType`/`-PdeviceTestBuildType=benchmarkRelease -PchatDeviceFixture=true` 打出，`applicationId`/`targetPackage=app.amber.agent`，runner `ChatDeviceFixtureRunner`。未改签名、未索取密钥（benchmarkRelease 走 debug 签名）。

用户允许 USB 安装后，主包与测试包均 `Success`。`am instrument` 在 `AndroidJUnitRunner.onCreate` 崩溃：

```
java.lang.NoSuchMethodError: No static method beginSection(Ljava/lang/String;)V
in class Landroidx/tracing/Trace;
（class 来自主包 app.amber.agent base.apk）
```

**具体缺失项：**

1. `round3-final-v3-benchmarkRelease.apk` 与当前 `fullBenchmarkRelease.apk` 的 `androidx.tracing.Trace` 都没有 `beginSection(Ljava/lang/String;)V`（dexdump 0 条；graphite `fullDebug` 有，故 F 能跑）。
2. 当前 androidTest 的 `AndroidJUnitRunner`（espresso 3.7 / junit 1.3）在 `onCreate` 调用该方法。类从主包加载，测试包里带新 tracing 也没用。
3. 不能改 round3 APK，不能改签名配置。给当前 bench 强行加入 tracing 1.3 也只能修优化侧，基线仍崩。
4. 降级全仓 `androidx.test` 会动其它 androidTest，本轮未做。

失败目录：`round4-perf/bench-pairs-aborted-user-restricted/`（MIUI 未点允许）、`round4-perf/bench-pairs-aborted-tracing-crash/`（安装成功后 runner 崩）。

### H. 全量 JVM

`round4-jvm/head-full-12-xml` + `attribution-4b-head-full-12.txt`：3193 tests，2 failed，`subsetOk=true`。失败即 main 上 5/5 的两条 `AntigravityOAuthSessionTest`。Coordinator 本 run 未失败（main 3/5）。源码含 Keep 提交。`d1d560e6d` 之后的全量。

### I. 小修

- `compensatedScrollTarget`：高度缩小沿更新的 item 走，不再 `coerceAtLeast(0)` 丢掉跨 item 补偿。
- `streamingSettleAllowed`：去掉未用参数和 `@Suppress`，签名与「是否允许 settled 切换」一致。

### 产品冷启动

4b 结束后安装 ABI `fullDebug.apk` 并 `am start -W -n app.amber.agent.graphite/app.amber.agent.RouteActivity`：COLD，TotalTime 1512ms，焦点 `RouteActivity`（不是测试壳）。
