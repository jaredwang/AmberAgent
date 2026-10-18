# Phase 3 独立完整审查

最终状态：**PASSED_WITH_EXPLICIT_EXTERNAL_GATES**。

Phase 3 的生产职责拆分、真实调用链、关键行为回归、构建与本阶段可运行 UI 门槛已闭环。独立review发现和后续真实gate暴露的问题均已取得对应失败证据、精准修复并复验；最后Manuscript标题竖排已在最终同一APK的320大字/411默认完整Novel route中关闭。允许主agent完成本阶段记录并继续Phase4，不需要额外审批。

本报告是 Phase 3 完整生产包的独立审阅，不以此前首 checkpoint 或 Input 局部通过代替本阶段通过。基线为 `137f57e2d5c9dc3b924866db2d7b2a369bb78478`，范围限定本阶段 Novel domain/Ghostwrite/VM/panes、Input/Importer/Page、Markdown Kotlin/Rust、Timeline、Widget、Sandbox、MessageTools。没有将整个 dirty tree 中已关闭的 P1/P2 重新计入本阶段。

审阅者只读生产与测试，没有修改源码/测试、运行 Gradle 或操作设备。三个窄独立轨分别审 Novel domain/Ghostwrite、Markdown/Widget、Timeline/Sandbox/MessageTools；主审补 VM/panes、Input/Page 实际调用链并整合结论。`phase-3-second-source-snapshot.json`、迁前证据与 body parity 用来定位范围及归因，实际入口、状态与副作用另行追踪。

## 当前结论与已关闭项

最终审阅明确保留 **1 条本次提取引入的审计取消回归**，并记录READY替换、双destroy、copy/paste取消、Worker最后润色恢复、Sandbox身份、VChart零宽及Novel两处大字布局问题的真实处置，不能改写成“本轮没有问题”。所有本阶段已证问题现均关闭；没有未处理的confirmed生产阻塞。后文保留失败与修复过程，历史“待验”描述均以本节最终表和末尾结论为准。

| 项目 | 当前事实 | 最终判断 |
|---|---|---|
| 最后一章 Polish 恢复 | 真实 Worker 红态 pointer commit expected3/actual2；修后1项绿 | 已关闭，真实Worker/owner/epoch/零provider证明 |
| Sandbox load 身份 | Store/presentation两项真实红→7+6绿；最终device2绿及稳定图已亲看 | 已关闭，last-good图片回退保留 |
| Input READY 内容替换与取消 | READY两项真红→绿；copy/paste真残留红→Importer10及邻接共43绿 | 已关闭，文件/row/历史引用/迟到草稿归属完整 |
| Widget 双 destroy | 实例count2红→count1绿，真实Card/Dialog/返回/action实例1→2→1 | 已关闭，borrow handle不再销毁owner实例 |
| AuditSession Stop | 原嵌套 context 被合并导致 prompt cancellation 当 saveError，真实旧VM测试红→绿 | 原两层 context 已恢复；VM24全绿，见 finding 3 |
| VChart零宽 | 新真实设备red零宽/白帧→两行assetfix→非零canvas/真实PixelCopy/柱图绿 | 已关闭，本地library与数值/autoFit合同无额外框架 |
| Novel Editor与目录header | 两处真实大字红；Editor2+Header2+Pane4绿；最终320/411完整route各绿 | 已关闭，最终两视口像素与调用回调已核 |
| 行为/构建 | 候选完整67类459项及随后受影响43项、Editor/Header/Pane8项分次复验，原失败均对应关闭；app/test实际build/install | 已闭环，保留分次证据，不虚报未执行的新完整suite |
| UI | Rendering411/320、Sandbox2、Widget6及VChart、真实Chat2、最终Novel两宽均有实际运行和亲看图 | 本阶段可运行门槛已关闭，外部门槛见末尾 |

## 已确认问题

### 1. 最后一章润色落盘后、剧情指针落盘前退出，Worker 恢复会跳过配对修复

严重度 P2；原有，非本次提取引入。位置为 `NovelWorkspaceGhostwriteWorker.runJob` 满进度完成分支（当前约 91–98 行），与 `NovelWorkspaceBatchRunner` 入口 dangling-pointer repair、每章 commit→pointer 配对链。

生产路径：Polisher 已成功保存最后一章的 `POLISH` ledger commit，但 pointer 尚未保存时进程退出，或暂停后恢复。`Jobs.progress` 从润色 commit provenance 得出已达 target；Worker 重读 durable job 后直接 `finishJob(COMPLETED)` 并返回，没有进入 BatchRunner 入口修复。章节正文已存在，UI 可显示批次完成，但分支仍为 plot-stale，后续 Write 被 freshness gate 拒绝。另开 Polish 可在 prepare 阶段修复，故不夸大为永久损坏。

已核 HEAD 同样的提前返回。现有 `runBatch entry repairs a dangling polish pointer...` 只测试 1/3 进度且直接调用 BatchRunner，不能覆盖 Worker 满进度分支。最窄证明应持久化 target=1 job（先 save 再 load，保留 createdAt 秒序列化合同），实际 `commitPolishedChapter` 而不写 pointer，再走真实 `Worker.doWork`；断言零 provider、正文逐字不变、恰补一条 pointer、COMPLETED 且 `isPlotStale=false`。修复应在该 Worker 完成分支同一 running owner 内修复并核实，不要求先解析 Settings/model，不把 provider 放入本地 monitor。

### 2. last-good 缩略图 URL 被误用于当前 load 身份，重新打开 A 可实际保留 B

严重度 P2；原有，非本次提取引入。位置为 `SandboxActivityPresentation.matchesPreview`、`WebViewOperationStore.matches/open` 与 `WebOperationPreview` 的匹配后 native renderer 绑定。

最短可达序列：open A 并捕获有效缩略图 → open B，B 在首张缩略图前沿用 A 的 lastGoodPreviewUrl → 再 open A。Store 因 lastGood URL 匹配而返回 B 的 loadId，requestedUrl/url 仍为 B，工具打开 A 后的 read/wait 继续读 B。ChatPage 历史步骤切换也允许在 B 加载时选择 A；preview 错判当前 load 与 A 匹配，将 B 的新 loadId 交给按 A URL 创建的 WebView。后续 A 导航回调携带已错绑的新 ID，不能依靠旧 ID gate 排除。

迁前 `P3-sandbox-before.kt.txt` 已含相同匹配规则，Store 本次未改。last-good 用于图片回退合理；问题只在它进入身份集合。最窄修法是从 Store/preview 当前身份判断移除 lastGoodPreviewUrl，保留 `bestThumbnailFile()`。已有 TemporaryFolder 可覆盖 A 有缩略图→B→A 的新 ID/requested A，以及 B.matchesPreview(A)=false 且旧缩略图依然可用，无须新状态机或 WebView 抽象。

### 3. AuditSession 合并 context 使 Stop 后的已保存报告被显示为保存失败

本次提取引入，已修复。旧 VM 的 `withContext(NonCancellable) { withContext(IO) { save + load } }` 提取时被改成 `withContext(NonCancellable + IO)`。Stop 取消父 Job 后，组合 context 的 IO 返回会切回取消的父上下文，prompt cancellation 抛异常；保存可能已完成，结果却没有赋给 messages，并被现有 catch 标为 saveError。真实可达入口为 Novel 页面一致性检查→Stop→保留两章中已验证部分 report。

既有 `NovelMarkdownWorkspaceViewModelTest.stopping a two chapter audit retains validated progress and reopens its report` 在统一红态中 expected null、actual `StandaloneCoroutine was cancelled`，不是新弱断言。最终候选恢复原两层 context，IO 回到 NonCancellable owner 内拿到结果；同 epoch save/read、旧 epoch 不发布及取消继续传播的合同保留，未将整段 audit/provider 设为不可取消。旧 VM24项全部绿。本项应明确计入真实提取回归，不能只报原有问题。

### 4. VChart sandbox 默认字符串宽度使实际 canvas 为零宽（设备发现，原有，已修）

PixelCopy/几何诊断排除了宿主不可见或无布局：DOM root约412 CSS px、native1080px可见且alpha1，而canvas bitmapWidth/rectWidth均0。旧oracle只看canvasCount1会假绿。`phase-3-vchart-zero-width-red` 的新真实设备断言已取得红：bitmapWidth0、PixelCopy非白0；不是截图时间等待可解决的问题。

本地bundled VChart getCurrentSize/`aj` 源码证明非null spec.width直接覆盖容器测量；sandbox原来把省略width默认注入字符串`'100%'`，不符合当前库的numeric size合同。最终asset diff仅删除该默认注入，并将显式`'100%'`删除以复用原autoFit/ResizeObserver；数值width、默认height320、library及网络规则不变，未加resize重试/延时兜底。`P3-vchart-zero-width-local-api.json` 是修前诊断记录，其status未应用字段不能当最终源码状态。

`phase-3-vchart-numeric-size-green` 新app真实1项绿0skip已核execution/JSON，并亲看完整window PNG与Window区域PixelCopy：A/B蓝色柱、0–25刻度、横轴A/B均实际出现，左右/上下留白完整、标签无裁切、两柱底线对齐，比例12/24正确。bitmapWidth1081、rectWidth412.18、height320、loading none、PixelCopy非白205与图一致。VChart本地library/实际canvas像素门槛可关闭，不把此前DOM绿计作实际渲染通过。

### 5. Novel320大字编辑顶栏把Cancel折行并裁掉末字（设备发现，原有，已修）

`phase-3-novel-complete-route-device-320-large-font` 真实global840px/font1.5路径在编辑动作断言取得红。已亲看failure PNG：Cancel变为“Cance”加下一行末字；action-layout JSON进一步证明node147×105、paragraphHeight125、第二行bottom125>node105，与先前Center paragraph原始overflow flag误报不同。`NovelMarkdownChapterEditor`固定56dp顶栏按1:2:1给动作/标题空间，原HEAD同body，属于本次宽字体验验收发现的原有问题。

最终修复只给Cancel/Save自然宽、title剩余weight1/max2与header min56；专用Save固定高改同min高，没有改全局按钮/字号/VM/保存回调。新EditorLayout2+PaneInteraction4实际全绿且app/testassemble成功。本审阅已亲看修后global320完整route的03/08及411同版本回归03/08：Cancel/Save单行完整、title完整对齐，旧裁切消失。320 glyph JSON为Cancel node168×62/paragraph62单行，Save112×62单行，与图相符。

修后两完整route均真实绿0skip：`phase-3-novel-responsive-complete-320-large-font`18.288s与`phase-3-novel-responsive-complete-device-411`18.895s，保留main/fork/material实际文件与5commit证明。本 finding关闭，Novel整体仍因以下目录header问题待验。原失败轮font raw null归一1.0的边界照实保留；最终320轮original1.0→1.5→1.0且wm1080/420恢复exacttrue，见configuration JSON。

### 6. Novel320大字稿本标题被动作挤成8行竖排（独立像素review发现，原有，已修）

亲看上述修后320完整12图，唯一剩余真实问题是01稿本目录的Contents被逐字排成C/o/n/t/e/n/t/s，撑出约600px高的header；06/07背后的同页也可见，并非动画或单帧错觉。`NovelWorkspaceManuscriptPane` 的同一Row里title.weight1被Batch polish/count/more这组自然宽动作挤到极窄，HEAD原大Page同结构。411该页正常，不把别处正常ellipsis或reader双行动作误报为问题。

本轮其余11图已集中检查：Editor取消/保存、reader前后、Controls/BranchDialog、fork保存/回main隔离、material IME保存与重开均无另一个已证需修问题；持久/cleanup/config restore证据正确。后续真实HeaderLayoutTest在旧布局expected1line/actual8取红，最终将外层Row变为FlowRow两块（原标题+完整动作组Row），End/4dp/CenterVertically自然分排，无breakpoint、新状态、字号或callback变更；polishOwned/writeOwned/busy gate、menu/clearError/undo等实际动作全保。新Header2+Editor2+Pane4实际8绿0skip，app/testbuild/install完成。

已亲看 `phase-3-novel-final-route-320-large-font` 的01与06背景及semantics：Contents节点756×78单行，动作组在下一排，Batch polish/count/more各自完整分离，列表在其下不遮挡；`phase-3-novel-final-route-device-411`01保留单排默认对齐，03 Editor仍正常。最终同APK96a9405d两完整route分别18.245s/18.637s、各1绿0skip，5commit、main/fork/material saved及cleanup合同仍过；最终global配置精确恢复true。该最后finding关闭。

## 指定修复的有限增量复核

- Worker 在 runJob 入口捕获 restoreEpoch；满进度分支由 RestoreBoundary.write 包住 withRunningOwner，在同 monitor 内执行既有 dangling repair、核 stale 并 durable transition。普通 Write 路径不增加模型调用；Polish 真 gap 失败不冒称完成。不存在 provider/Settings/foreground 新依赖，也未新增 NonCancellable。
- Sandbox 仅两处身份序列移除 lastGoodPreviewUrl，requested/committed/url/display/toolCallId 规则仍在；lastGood 字段与 bestThumbnail 回退无改动。生产 Store.open 与 preview.matchesPreview 均消费修后判断。
- READY 替换由 `ChatInputState.setContents` 计算仅 READY、file URL、不再被 replacement contents 引用的去重 URI，先撤销旧 token再换内容；`ChatVM.replaceInputContents` 收返回值交 FilesManager，实际历史编辑与 queue move-to-input 已调用。HostDraftComposer 的直接 setContents 仍受空草稿/原 restoredParts 与 suspended read后双重 gates，不能把未接受草稿的附件拿走。迟到 import 未完成副本仍由 Importer 拒绝后清理；accepted 文件不经此路径删除。
- Dialog dispose 现在只清 `webView=null`，不再 stop/destroy 借来的 handle；Safe/Rich/Guizang concrete runtime 原 dispose 仍负责 callback(null)/实例销毁。真实 WidgetDialogLifecycleTest 由 count2 红变 count1 绿。
- AuditSession 增量仅恢复原嵌套 context，见 finding 3。没有通过吞 cancellation、删有效断言或扩大不可取消区间使测试通过。

亲读的红证据：`evidence/phase-3-complete-packages-regressions-red`。亲读的候选绿/剩余失败证据：`evidence/phase-3-final-candidate-behavior-and-build`。后者 XML 合计67类459项4fail0error0skip，其中 VM24、WorkerRecovery1、Sandbox13、WidgetLifecycle1、Input State13/Submission13及 READY 两项均绿；ChatAttachmentImporterTest为9项4fail。根已回报 app+androidTest APK 实际 build/install 与新 native so hash；本审阅没有自行构建或安装。

### 最后 Input 取消边界增量（已闭环）

原HEAD的普通copy与long paste都采用 `uri = withContext(NonCancellable + IO) { materialize }`，返回 composer 已取消时可在外层赋值前丢掉已创建URI，使catch没有局部handle清理。真实FilesManager/Room与ContentResolver stream fixture复现普通copy，真实DAO.insert暂停复现长paste；后者已亲读 `phase-3-importer-cancellation-red` XML：10项唯一红，预期无新增upload，实际残留一份新`.txt`。这是原有边界，不能把fixture最初未进入实际read的失败与该真实残留混为一谈。

最终仅将 copiedUri/document 赋值移入原有 NonCancellable+IO block，文件IO范围、token、complete与cleanup owner均不变。局部handle在回到取消父上下文前已记录，catch可将未交付副本交给app-scoped FilesManager；cancel继续抛出。complete成功后同步将局部handle清null，之后的取消不回收已归State/提交方的文件；token撤销/草稿替换后的late complete仍被拒绝，只删其私有副本。替换中仍引用的READY URI不释放，历史附件和source原文件均保留；cleanup仍走实际upload路径与managed row删除，不对任意URI/工作区镜像新增删除。

已亲读 `phase-3-importer-cancellation-green` 五类XML：Importer10、State13、Submission13、Warning3、HostDraft4，共43项0fail0error0skip，时间05:23–05:24 UTC；含longpaste真DAO门控、普通cancel、late replacement、历史file+row及保留READY引用。实际主调用仍来自ChatInput importer与paste callbacks；未为测试新增生产seam。本次输入源码/行为门槛关闭，主agent另报同次app构建通过；不冒称真实SAF/camera/裁剪/provider全部验收。

## 生产职责、状态与持久化核对

| 范围 | 独立核对结果 |
|---|---|
| Novel 纯 owner | Committer/AuthorEdits/Proposal/Material/Discard/ChapterRestore、Reviewed/Reconcile/Undo 等真实迁入 feature/novel-workspace；模块没有 Android、Settings、RunKernel、app UI、Runtime/Coordinator 依赖。ProposalContext 只保留 dir/branchId/slug/epoch，DTO 未藏第二份 Jobs 状态。实际 Runtime/VM consumers 已接到新 owner。 |
| Novel 事务 | 同一 Jobs monitor 下的 execution、branch/head/tree CAS 保留；RestoreBoundary 与 Jobs 同 monitor。ledger 前失败补偿正文/plot/plan/undo并保留 primary 与 suppressed，ledger 后 canonical 正文不倒退，reconcile 读 tree/provenance 再补 receipt。receipt/progress 仍属 Jobs；provider suspend 未移入 monitor。 |
| Ghostwrite | DI 原 Coordinator → Worker/Controller → BatchRunner → WriteChapterWorkflow → Generator/Review → commit owner；Polisher 直接调用 ChapterCommits。没有将整个 Coordinator 或 Runtime 回传进小协作者。已核缓存候选、最终 CAS、outer written/onChapter、nextPlan、无输出润色、暂停/取消路径；除 finding 1 未确认迁移遗漏。83 个 scoped source 与 second snapshot 一致。 |
| Novel VM | VM 仍唯一拥有 UI StateFlow、导航/branch focus、turn/reload/refresh jobs 与 admission。Queries 负责文件投影和 polling cache，无 VM/Runtime 回依赖；VM 在 epoch/cacheGeneration/directory/slug 检查之后 acceptRefresh，缓存未形成第二份 UI 状态。SessionHistory 的 recoverToSessions 是明确持久恢复，append 仍在 RestoreBoundary 并按 message ID 替换/ack。 |
| Novel turns/audit | InteractiveTurns 接真实 launcher/runtime，局部 text/reasoning 只属一次 flow；已接受用户消息仅短 NonCancellable+IO append，后续 preflight/model 可取消。Completed assistant append 保留输出 ID 去重与 epoch；plan success 核真实 head/plan 改动。AuditSession 在同 epoch 保存已验证部分 report，Stop 继续传播 cancellation、最后回报 UI；未把整份 VM 传入。原有 cancel UI catch 等行为对照基线，不凭一般风险扩大新框架。 |
| Novel panes | Page 保留 Koin VM、lifecycle collect、restoreVersion key、poll、project/实际 branchSlug 局部状态及协调。Chat/Manuscript/Catalog 接窄 state 与命名业务 callback，无全 VM/action bag。send 只在 accepted true 清 draft，Stop 不清草稿；raw editor 保留 expectedRaw CAS 与成功 ack，reader 按 jobId/written/terminal transition 刷新。 |
| Input/Importer | VM.inputState → Page 单一 remember(inputState) SubmissionOwner → ComposerHost → ChatInput；confirmEdit/普通/长按/Widget action 仍接真实 VM。Importer 只持同 State 与文件 IO，token 的 conversation/id/READY gate、短不可取消 copy、拒绝或取消删除私有副本、accepted 后归 State 均保留。launcher 捕获 camera/picker/crop owner，结果不能写入另一 conversation。READY 替换新增归属证明仍待独立复核，见 gate 表。 |
| Submission | 点击前捕获 snapshot，校验后核 captured attachments 是否仍归同 conversation；accepted 仅消费当次 import/parts，later text/import/edit target 保留；历史附件 URL 例外只来自 captured editing，不要求所有 part 均 tracked。独立 Widget action 不改变当前 editingMessage。先前31项局部绿有保存 XML，但不是本批统一全绿。 |
| ChatPage | Page 保留路由、DI、外部 files/text、image event、单一 input owner/listState；Scaffold 协调 page，ComposerHost 实际使用同 owner，TimelineHost 才收完整 conversation。InputActions 是配置/admission owner，按每次 composition 接最新 gates；TopBar/Panels 接具体 flows/commands，不另建 conversation/session state。外部 share mirror 复用与一次 effect 合同未在本阶段重写。 |
| Markdown | 默认 MarkdownBlock→Legacy；MarkdownDocument 拥有 composition parse/motion/collector。共享 ParseCache 保留完整 key、预算及 in-flight owner；StreamingParser 保留 stable-prefix、绝对 offset、synthetic suffix 与完成 freeze。HTML 是独立 parse/generate 入口，共用代码外 LaTeX 规则但没有误混 AST 路径。 |
| Rust/JNI | prefs/bootstrap/JNI ABI/构建配置对基线未变；生产 AST preprocess 与 HTML normalize/generate 实际使用迁移后规则。Rust diff 仅 closed tilde fence 与 ASCII hostname 尾界，已有 cargo 20 项绿日志；本审阅未执行。 |
| Timeline | Page 同一 LazyListState → Host → ChatList/Normal → 唯一 LazyColumn/effects/anchor/prefetch/items。两个缓存仍按 conversation 归属，regex signature/tail reuse 与预算保留；selection/export 生命周期没有新镜像。pending emissions 读同一 updated state，waiting/compact/tail item 与历史 key+offset anchor 保留。 |
| Widget | Safe/Guizang/Rich 实例、ready/height/effect key/callback(null)/dispose 各归具体 runtime，Dialog 借 capture handle。导出追到实际 JPEG helper 与 archive→FileProvider/chooser，无通用 Context 袋。精准 bundled VChart URL 拦截与 canonical archive rewrite 保留各 runtime 原网络差异；双 destroy 已按上方真实计数修复，设备往返待验。 |
| Sandbox | capture、preview、hidden renderer 的实际生产入口已核；loadId gate 与图片 fallback 本身保留，身份混用是 finding 2。同 load hidden+visible dispose 令 rendererActive false 没有生产读取者，未按可见缺陷报。主线程 PNG/异常 recycle 等均为原有且无实测，本阶段不自动追加优化。 |
| MessageTools | 普通/virtual 两路径传同 theme target，实际 MessagePartsBlock 消费 ThemeTryOn。Pending/Denied 优先、UNKNOWN、AskUser early return、单份图片 carousel、details/deny 与 capsule 语义保持，未发现提取仅有 helper 却未接消费者。 |

## 证据边界与分批验收记录

主要参考 P3 各 complete/implementation/checkpoint 报告、second source snapshot、迁前 body 与直接测试正文；这些只辅助定位，不替代生产入口检查。当前 richtext/message/Rust 的 94 个 scoped 文件仅 Card hash 在 snapshot 后变化，其 Card 主 body 与迁前文本逐字一致；Dialog 的后续生命周期修复仍需按最终冻结增量再审。

行为证据现为候选完整67类459项加最后受影响5类43项复验；后者已关闭候选Importer失败及新增paste真红。先前首批失败与compile错误均保留为过程事实，不称新行为通过；组件fixture修正未删改真实业务断言。最终应引用这些分次保存XML、源码snapshot与实际APK，而非合并成并未执行的新完整suite数字。

已开始亲看本轮 P3 PNG，仍未完成最终 UI 验收。`phase-3-widget-final-candidate-device` 的 execution.json 明确本次5项实际设备绿、0skip且先归档旧截图；JSON证实 VChart library/canvas、Safe第三payload/resume、两类slides页码2/current1。03蓝色 SVG 矩形在 PNG 确实出现，符合最终 payload。但01 PNG仍为“Rendering chart...”，05 PNG整白，04 PNG已有Second page而可见工具条仍0/0；这些图与DOM状态不同步，现阶段不能作为最终渲染像素通过，也不能只凭此先定产品缺陷。已要求在真实 Card→Dialog→返回补验中取得稳定 presented frame/重抓相关图，先排除capture时机，不修改产品迎合截图。

后续 `phase-3-widget-presented-device` 6项实际设备绿、0skip已亲读 execution 与 JSON/semantics，并亲看01/04/05/06a/b/c：04的Second page和2/2现已真正显示；06真实卡片 stream→final→Dialog→close→action有完整像素链，Dialog标题、ZIP/JPG/Close三触区在semantics中彼此分离，对齐且未裁切。native实例计数1→2→1、inline identity不变、expanded detach1、action callback1与图相符。该批01与05 PNG仍整白，即便helper已用postVisualState→OnDraw→下一frame，也不能按DOM绿认定最终图表/slide像素通过；已再次将两张待present定点证据反馈主agent，不先改产品。当前只关闭04/06相应像素门槛。

`phase-3-widget-rich-window-diagnostic` 后续05 slides窗口PNG与真实Window区域PixelCopy已亲看：两图均实际显示Second slide、B内容卡、居中2/2及左右导航；标题/卡片左边距一致，内容完整，底部控件互不重叠且留在页面内，末页next的disabled样式与2/2一致。JSON的当前slide left0/width412、deck scrollLeft412、native可见1080×2201/alpha1、PixelCopy success及非白采样与图一致；05 slides像素门槛可关闭。该目录01 VChart仍是零宽canvas/零bitmap、Window PixelCopy全白，旧测试绿仅来自DOM断言，不计chart像素通过，仍待owner定点诊断实际测量时机。

之后只追加必要增量：指定修复后的最后证据、Importer剩余9项复验、当期设备图。需要分别注明 Novel panes 组件交互与完整导航/VM route；Markdown/HTML 思考与流式稳定帧；Widget 展开/关闭/返回/实例生命周期；Input IME/长附件/编辑确认/裁剪；Timeline anchor 与 Sandbox 真预览。未知 HTTPS 设备 fixture 直接调用真实 WebView client 的 shouldInterceptRequest 只能证明策略返回空，不冒称浏览器实际负向联网。真实 provider、系统 chooser/SAF/camera/权限/OEM 等未执行门槛须显式保留，不新增权限以换取通过。

Sandbox candidate 01–04 PNG已亲看：本地网页内容真实可见，展开后内容对齐完整，返回仍保留缩略图，移除后只剩fixture文字；缩略图裁切来自 fixture 指定72×96容器，不算产品错位。05窄大字peek的Stop、前后与12/24可见，runtime/input/title有预期省略。该轮 execution 为失败，不能称两项绿：unmerged semantics 显示 merged pill 中心x624落在Stop子按钮590–637，title本身422–568；Android performClick 注入中心导致实际取消而未开sheet。owner仅将fixture改为unmerged标题实际pointertap并保留sheetOpen、cancelCount0→Interrupt后1、output、Back断言，生产未改。需等该真实入口复验后再判断sheet像素，不把未打开的sheet当产品空白缺陷。

`phase-3-sandbox-target-fixed-device` 后续2项实际绿、0skip（13.811s）已核 execution，亲看05 peek与07 stable sheet及完整unmerged semantics。07实际稳定呈现，标题按设计省略而中断文字完整，Interrupt触区568–742、前768–810、12/24为836–933、后959–1001，均分离且对齐；input/output两卡边界同宽、上下留白明确，完整fixture输出可见，无遮挡或底部裁切。实际标题tap打开、Interrupt只调用1次、output与Back断言全部保留。本次 Sandbox 组件生命周期/稳定布局门槛可关闭，不把06 opening动画帧作为最终画面，也不冒称整个聊天route/provider验收。

Rendering 的 `phase-3-rendering-frame-fence-fixed-device` 首次完整411dp默认字方法已绿；亲看该视口01–09与06a全部PNG、layout JSON/semantics及result。静态列表两项/表格三行对齐；Kotlin反引号与tilde代码中的标签、URL、数学分隔符确实为字面内容，code工具条完整；Legacy AST inline strong/em字面是固定HEAD/corpus原合同，而true HTML block确实解析为BLOCK_HTML_PARSED；MarkdownNew实际strong/em样式呈现。最终05的新列表/表格/tail完整出现，06旧列表/表格/代码等仍在，未发现此次布局错位/间距/裁切问题。citation回调123456、capsule回调1、全部final markers与21次visible frame回调均保留真实断言。

04 active PNG主要显示旧blocks；JSON另外证明streamingInput/visualActive=true、append前后visible frame从0到1，因此只能说明真实active owner继续出帧，不把这一张截图夸称新tail全部可见；最终05已实际显示STREAM_TABLE/STREAM_TAIL_COMPLETE。该411组件视口门槛可关闭，`wholeChatVerified=false/providerVerified=false`边界保留。同批320dp/font1.5仍在active滚动moving-target超时，尚未完成后段，须等fixture按真实静态max/增长合同精修后的独立设备结果，不以411代替。

本阶段实际 Chat route 证据 `phase-3-real-chat-route-regression-411` 已亲核：最新P3 app执行原本地SSE真实provider transport/真实Koin两method全绿0skip（36.7s），目录虽沿用modularization-phase-2名称，但execution先归档旧设备目录，owner.case为当期a7bc0dbe/c8d6cf74，不能误认旧P2截图。01、02 IME/后台返回、03队列、04停止、06 FIFO、07/08 ask_user PNG及owner/semantics/AX均亲看。IME上方输入框与发送按钮完整可见，未打草稿保持可编辑；queue两卡三动作单行、模式独立首行、无触区重叠，弹窗留白正常。Stop后Send反向label/空active与保留Room queue一致，FIFO后队列清空；ask_user长选项完整，回答后显示选项及ANSWERED，无新的裁切/错位问题。

当期background证据为Presenter foreground=false/Process CREATED，同run及两条durable queue ID保留，warm返回仍同owner；Room queue与UI IDs一致，恢复后的请求/答案合同由原完整assert验证。两case的持久Settings/launch恢复均true。此证据关闭P3 Page→Scaffold→Composer/Input→Timeline与真实运行owner的411整链门槛；仅本地SSE模型，不能冒称外部provider、camera/SAF/系统chooser/裁剪ActivityResult、通知/OEM等外部验收。组件Markdown/Widget/Sandbox的独立场景也未被此普通文本route重算为全功能route覆盖。

Rendering窄大字最终证据 `phase-3-rendering-scoped-clock-device-320` 已亲看全部01–09/06a PNG并核JSON/semantics：本轮实际1项绿0skip（7.222s），viewport840px/320dp、font1.5明确。列表正常换行，TABLE_READY/DONE两列字形完整；长代码保持字面并由实际HorizontalScrollAxisRange 169/379px提供横向可达，不把当前视口右缘裁切误判成内容丢失。true HTML block完整解析，New strong/em样式与Legacy inline literal分流清楚；final新列表/STREAM_TABLE实际出现，旧blocks在06仍保留，完整TAIL_COMPLETE存在语义且受真实marker断言检查，不能要求一个竖向视口同时容纳全部长内容。

active04 JSON在真实visualActive=true时记录before/after frame0→1、当前scrollTarget=value=max154；PNG为该帧已显内容，未宣称整批tail文字已经呈现。static/final使用标准autoAdvance，只有active窗口手动推进，最终result仍保持citation123456、capsule1、visibleFrames21、allFinalMarkers=true。该320dp/font1.5组件布局门槛可关闭，没有为fixture滚动时钟问题修改生产；不重测已关闭411，也不扩称全聊天/外部provider渲染验收。

Novel真实411完整route `phase-3-novel-ime-text-device-411` 已亲看01–12全部PNG及owner/semantics/action glyph JSON：稿本列表、reader、主线编辑/保存、Controls、Branches/Create dialog、分支编辑/保存、回主线隔离、材料编辑/重开均真实呈现。Cancel/Save、reader前后按钮、Dialog Cancel/Create、material Delete/Save均有完整字形且触区分离；正文行距/页内边距正常，IME编辑与材料Save不被键盘遮挡。Controls下部可滚动内容不按当前视口裁掉即报丢失；已见分支入口与相应真实导航。

该case经真实生产入口/默认Koin VM，不是注入假pane callback；先保存main正文，再fork并保存不同正文，回main仍是原main编辑，材料保存后重开内容相同。owner activeBranch从main→alternate-path→main、treePaths与ledger head配合实际文件断言；persistence三项saved=true/commitCount5，cleanup任务项目删除、existing IDs保留、settings/launch恢复全true。设备1项绿0skip（18.805s）与证据相符。该早期411门槛之后又按finding5/6取得最终同APK两宽完整route，最后目录布局及配置恢复已关闭，不留Novel待验项。

## 最终边界与交付判断

最终指定源码增量均已独立复核，不只有worker自报；真实生产入口、唯一owner、restore/CAS/锁与durable路径没有未处理的本阶段confirmed问题。行为证据按完整候选与受影响复验分别记录；设备证据分组件与完整route，最终Novel两视口、VChart真实pixels均已亲看。因此最终结论为 **PASSED_WITH_EXPLICIT_EXTERNAL_GATES**。

以下项目未被本阶段验收冒认：外部provider真实凭据/模型输出与网络环境；真实camera/SAF/UCrop系统ActivityResult和系统分享/保存chooser；未授权通知权限下系统tray、OEM/物理设备差异、性能/长时稳定性；其它产品/跨端契约与既有平台或跨端测试skip。Rich unknownHTTPS fixture直接调用真实client策略，只证明空响应策略，不证明一次浏览器实际负向联网。没有新增权限、清除用户产品数据或读取兄弟产品来换通过。

本阶段review到此收口。后续Phase4按原计划继续，不自动追加未复现风险、风格重排或假想框架。
