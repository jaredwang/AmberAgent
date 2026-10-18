# Phase 4 最终独立审查

状态：**PASSED_WITH_EXPLICIT_EXTERNAL_GATES**。

最终结论：Phase4 的 7 个实施 owner 已完成独立源码/逻辑/生产调用链审查；指定真实缺陷均已按 owner 精准修复，并有最终受影响行为门槛及实际默认/窄屏大字设备证据。最后 Council/DeepRead 窄屏14图、Terminal两width10图和触区JSON均已逐一亲看，未发现本次指定范围尚未关闭的真实问题。允许关闭 Phase4 并进入 Phase5；不要求继续追加候选或重复已过范围。

最终 source/config/resource 快照 `phase-4-verified-source-snapshot.json`2532项已逐项实际SHA核验，全部一致、0漂移。最终主APK6ac522ef…、测试APK05dd69f8…；Live在其最终源码不变条件下沿用本期已独立审过的两个视口。没有把源码/编译/安装替代行为或像素验收，也没有把外部门槛写成已测。

下方“最终门槛”与末尾最终收口取代过程中保留的历史pending/red描述；后文按时间记录的失败、误报和修复证据用于追溯，不再代表当前未完成状态。

本报告以 `packets/P4-sequence.md`、各 P4 implementation/domain/UI/device 报告、当前生产调用者和迁前 source 为范围。没有把 P1–P3 整体 dirty diff 当作本阶段改动。审阅者直接审 Terminal/Workspace/Settings，并委派三个明确只读子轨审 Council、DeepRead/Board、Live/SubAgent，再整合实际调用与误报依据；没有修改生产/测试、运行 Gradle/ADB、操作设备或读取其它产品。

## 最终门槛

| 门槛 | 最终独立结论 | 实际证据及限制 |
|---|---|---|
| 7 owner source/logic/caller | 通过 | 真实入口、唯一状态、取消/epoch/CAS/durable及职责深度完整；最终2532输入hash全匹配 |
| Settings/Council/S/Terminal指定owner修复 | 通过 | Settings2、Council短交付/最终模块32、SubAgent28、TerminalAdmission4/Session2、Workspace4等fresh真实行为；不重叠相加 |
| 最终受影响app行为 | 通过，保留实际执行分批事实 | `phase-4-final-specified-behavior-and-apk`20类118项117green、唯一旧Hero flag失败；`phase-4-final-hero-glyph-and-terminal-target-apk`严格glyph1green替代该无效flag，其他117有效断言保持 |
| Importer/document | 通过指定合同 | Importer11、Office17＋Bounded2实际全绿0skip；DOCX/PPTX空表strict首次在green执行，未虚称先前strict红 |
| Council readonly/roster/chrome/attachment | 通过指定源码、定点和两width像素 | 首批历史readonly2/Member2/Chrome2green；final4117图＋final3207图已亲看；live模式panel结构锚定source审，不冒称历史fixture实际打开live菜单 |
| DeepRead真实Kernel fallback/Reader | 通过指定合同 | fallback6包括P2carry/取消/timeout/restore；真实history Room Reader两width7图、严格首屏glyph1green，无Work请求；不等于真实provider/custom模板已验 |
| Live saved/config/Presenter/Filler | 通过本期范围 | Filler7/Presenter3/Accessibility adapter2及两width实际历史/去重/配置/recreate；未启动Companion/overlay |
| Terminal actual shell/UI | 通过两width完整指定路径 | 最终41113.095s/3209.648s各1green0skip；真实标题单触→Dialog、输出、public Stop→durable/lease→fresh、Back同Chat、recreate；触区边界如末尾说明 |
| 构建/安装/恢复 | 通过已声明范围 | main/unit/androidTest compile、FullDebug两APK构建/覆盖安装；每case task-owned cleanup/原数据恢复，wm/density/font精确恢复 |
| 外部集成与设备环境 | **明确未验，非本阶段通过声明** | 真实provider/CLI、overlay/第三方填入/通知托盘、真实SAF/Alpine session/Termux/SSH、OEM/物理设备/性能/跨端；模块SSH8项Assume skip不计执行通过 |

## Council：domain 与 Timeline/UI

实际链为默认 DI 的单一 CouncilRoomManager→内部 Planner/HostTurns/AutoRun/既有 Executor；Home open、VM send/answer/restart、task cancel 仍回该 Manager。ModelCouncilManager 依赖已存在的 ModelCouncilExternalCliRunner 接口，默认 DI 仍解析原 ExternalCliModelCouncilRunner；CLI builder 进入真实 runner，不是另起不被消费的模块。

Planner 负责分类/角色规划/解析，不持 room/task；HostTurns 负责 research/opening/review/steer 的执行和解释；AutoRun 负责一个已 claim run 的顺序流程；TaskProjection 只计算投影。协作者接执行器、sink 和明确命令，不接整个 Manager/Repository 或万能状态袋。per-room mutation、jobs、closing、auto/assembly claims、mode override、ask deferred/resume 仍归 Manager。插话 watermark 从旧 map 完整迁成单次 AutoRun 的局部 cursor：处理前消费、返回 cursor、下一轮续接，旧 map/reset 已移除，没有双状态。

流式和 active mutation 仍回 Manager→Store；后台工作携带 captured writeContext，真实 Repository gate 保留原 epoch。upsert 是立即 flow 发布＋debounced persist，close 是同步 persist＋evict；不将 flow 发布冒称 SQLite 已 durable。first checkpoint 的 publish 前 ensureActive 与短 NC publish/register/bookkeeping 没有因提取扩大到 provider/lock wait/close。register 取消继续抛，普通 task IO 原降级合同保留。close 取消并 join 同一个所属 run。

FULL 空 roster 的 research→roles→classify→opening→guest→synthesis、已有 roster 不重复 research、NO_COMMENT/WITHDRAWN、ASK_USER 同 Manager deferred→linked USER reply、等待/插话暂停预算及 terminal settlement 保持。新五项测试源码使用公共 Manager 和实际 Executor/scripted runner，不是只测提取 helper。

Page Live 绑定原 VM answer/send/close，Terminal 以 vm=null 进入同 body；Timeline 的 receiver 可空，AskUserCard 仅未答且 receiver 存在才给输入，历史问题文本保留。active trim→callback→clear 保持原合同。timeline list/follow/gesture/IME/programmatic token/followEvents/poppedKeys 仍由 host 独占，叶组件只接消息/identity/附件/引用/ask/speaker 及窄 callback。merged 时间排序/key、renderer visible-frame 回调、reopenToken→key 与 VM 重订阅保留；未发现整个 VM 绑回叶子的新增路径。

domain 冻结10文件、UI/测试冻结14文件与对应 hash/parity 全部匹配。该结果不代替最终统一执行或长名/引用/roster/历史 question 的像素验收。

## DeepRead：生成/缓存/Writer 与 Reader/TodayBoard

BoardModule 仍注册唯一 DeepReadAgentRunManager；实际 Worker→AgentRunner→Adapter→Manager.run/runSection 保留 runId/events/Worker force 首次尝试合同。Manager 独占 topic mutex、background dedupe、cache/template/model admission 和 restore token；AgentRunner durable run/CAS 与进程 topic 锁职责不同。协作者没有新建第二套锁/cache/claim/AppScope/settings writer。

一次 admission 创建同一 SectionWriterTools，Magazine 各 stage 共用其 mutex/required counters 和 read→transform→Room save。纯 WriterContent 不持 Repo/mutable output。实际 durable 链仍是 HotListRepository.saveDeepRead→withOwnerWrite→withCurrentWriterOrCancel→DAO，保留最新 pin/source metadata；epoch 在模型调用前捕获、后台 fill 继承，未跨网络占全局 restore gate。

Magazine collectRun 的普通 stream 错误确实重建 streamOutput=false 的真实 Kernel session，并从原 stage messages 开始，未带失败 stream partial assistant。selected model、isolated hidden settings、runId/events、tool names、auto approval/execution policy/空 transformers 保留。Cancellation/timeout-like 继续透传，不被当 fallback 成功。P2 carry fixture 的 RecordingKernel 只是记录后 delegate DefaultRunKernel，script gateway complete 发真实 writer tool call，下一轮观察已执行 writer；整篇 oracle 检查四段顺序/READY/完整 Room/原 messages/partial 不污染，不是字段映射替身。

新增单段 retry fixture 保留 ready siblings；synthesis fixture 走实际 ProviderCatalog.complete 与 cache/force metadata。restore fixture 在真实 provider barrier 后改 Room/推进实际 gate，再放旧 writer，检查真实 SyncRestoreWriteRejectedException 与恢复正文；没有 fake writer 自行拒绝来模拟 durable 边界。

Reader 实际 RouteActivity→Screen→ReaderContent→Article/TemplateHost，retry 回同一 Scheduler。唯一 LazyListState、cache/history keys、saveable initialForceConsumed、entryHadReady/sawGenerating/prevComplete/seal 及 effect 顺序保留。fromHistory 自动入口不生成，显式 retry/regenerate 仍是用户命令。首用确认、history loading/missing/empty、坏 synthesis payload、missing custom template、partial error/局部 retry 分支保留。WebView rendered key/failed-image 集合/allowed image-link/font inputs/原 fallback 保留，不将 renderer failure 写成生成状态。

TodayBoard 唯一 route 收 snapshot，panes→一个 SettingsActions→原 Repository/SettingVM/Scheduler。ROOT/GENERAL/MODEL/HOT_LIST/STYLE/REVIEW full/standalone 导航保持；focus rule 与 hot-list keyword 是不同 owner。Actions 使用 route composition scope 和 vm::updateSettings，weight 先显示后 durable ack reload、preset 批次后只调度一次的原合同保留。原通知/日历说明 remember 快照不是此次新增权限策略；未因未新建 stable key 或原快照时序构造假 finding。

domain freeze/UI snapshot 共27条与当前一致。文章/表格/大字布局及完整路由需当期设备证据，不能以源码或缓存场景冒称真实 Worker/provider 网络已验。

## SubAgent：mailbox、launcher 与 Room 结算

真实 Tools→单例 Manager→TurnLauncher→InProcessAgentRunner→TurnAgent→Runner/Kernel→Manager settlement→Room。requestedRunId 在生产 Runner 原样保留，payload 先注册再 launch，失败移除；Manager 先发布 turnRunId 再 observe。observer timeout 保留 pendingTerminal=TIMED_OUT→cancel→finish，callback 完成后 finally 按本 turn ID 移除 payload。取消仍非 suspend，未加 join、未持 thread mutex 等 Kernel。

具体 RuntimeRun 独占 Channel<ThreadMessageRecord>。drain/finish 在同 thread lock 核 RuntimeRun 身份，迟到旧 callback 不能消费新代 channel；send 的 enqueue＋DELIVERED 完成后才放锁。terminal 仅回退本代尚未 drain 的实际 messageId，DAO 同时限定 threadId/IDs/DELIVERED；已消费继续 PERSISTED，相同文本不混淆，历史/其它 thread 不重排。approval pause 不执行最终 mailbox 结算，cold restore 原全量 requeue 不被替换。

captured writeContext 保留到 finish/interrupt→Room durable gate，没有迟到时重捕新 epoch。WebMount scope 在原 finally 释放。P2 isolated settings snapshot 保留。新 missing-handler/真实 observer timeout fixture 源码确经 Manager/InProcessAgentRunner/Room，检查 payload/terminal/scope；初审时未提前宣称执行通过；最终SubAgent28项fresh XML已亲核全绿。最终10文件 hash 一致。

## Live：Manager、Filler、Presenter、Notifier 与页面

真实 DI/Tools/页面 VM 到原 Manager 和 Store 的链已接通。Manager 仍独占 analysis generation/runId/analysisJob/engine/pendingSnapshot/现场终态接受；Filler 独占进程确认与 denylist，平台写入继续走 Accessibility verified readback；Presenter 独占 BubbleWindow 与重挂显示态；Notifier 用原通知和 RouteActivity deep-link。helper 接具体依赖/窄 callback，没有整 Manager 回传或复制 live session。

页面拆出的 controls/config/result/history/primitives 不接整 VM。延迟删除/Undo 仍归 page scope，确认态仍在 AnimatedContent 外；保存/撤销走真实 LiveCardStore/DAO，analysis result 走原 EventStore，冷/热通知入口回同一 DeepLink owner。16文件冻结 hash 一致。

late delta、start/stop/start restore IO、durable result 先于现场接受等候选在迁前已存在，未取得本轮新可达证据，也未见提取扩大；本次不凭它们追加泛化 generation 框架。源码结论不能代替 overlay attach、第三方采集/填入、通知权限/托盘或真实 provider。

## Terminal/Workspace：完整资源 owner 与具体 IO

RunToolCatalogFactory→LocalTools→TerminalTools→默认 AgentInfraModule 单例 TerminalRuntime 保留；四个 session API 真正调新 SessionManager。WorkspaceModule 仍单例 WorkspaceManager，Tools、ShareHandler uploads、WorkspaceFileVM/Preview、SettingSandbox 与 Sync consumer 继续调用原 API。内部 helper constructor 创建，不另加 Koin 对象图或平行实现。

TerminalRuntime 独占 jobs/admissionMutex/installRunning/终态 CAS/completionNotified、TaskStore/activity/output 投影和本地 job lease 最终结算。SessionManager 连 sessions map/sessionMutex/Process/reader/writer/lease/output cap/retention 一起迁移，原 Runtime 没有第二份 session 状态或仅供旧反射 fixture 的废转发。LocalTerminalProcess 独占单 job native Process/reader/wait/TERM-KILL；只接 jobId/timeout、具体 ProcessBuilder、status 读取和 output sink。TermuxAdapter 只负责 probe、Intent/PendingIntent/service payload/Bundle decode，没有 task/timeout/job map。SSH 不被顺手套成 generic backend。

本地仍先 QUEUED→RUNNING CAS、TaskStore running、acquire mirror、spawn；reader join1秒、timeout/cancel kill、exit code 和 syncBack 后终态顺序保留。resource await 的 COMPLETED 只表示进程退出，Runtime 仍将 exit7 判 FAILED。ProcessBuilder suspend 安装依赖/取消边界、资源引用发布与 old body 对照，未确认提取新增控制流缺口。process 活着不先归还 lease，错误分支先 terminate；session reader exit/stop 可同时触发 release，原 WorkspaceMirrorLease AtomicBoolean 只执行一次。新的实际 owner 测试真实 Workspace/lease，只有 native Process 为 fake；host process 两项确实 /bin/sh，但不是 Android/Alpine 证明。

register 失败修复仍仅包真实 AgentTaskStore.register，backend/activity 尚未接手时短 NC＋admissionMutex 按 id/对象移除本次 reservation，才清该 install flag、再抛原错。无全程 NC 或吞错 retry。当前 startJob 与已独立审过的准入修复后 body 一致，first checkpoint 原3项真实红→绿有效；第四项真实 startSession spawn IO 后可 fresh-acquire lease 的新增fixture已在最终Admission4项fresh XML通过。

WorkspaceManager 独占 prefs/URI/_state/mirrorMutex/activeTerminalMirrors。Documents 接具体 ContentResolver 与窄 requireRoot，保持原按需解析和错误次序；MirrorCopy 接具体 resolver/私有根/每命令 DocumentFile，不持第二套锁/状态。配置切换在原锁检查 active lease，先 flush 旧 SAF/reset，再持久新 URI/state，最后释放旧 permission；first bind 保留 staged uploads。acquire 在锁内 prepare+active++，release 在原 NC+IO/同锁 finally active--，AtomicBoolean 防重复释放旧 lease 对新 lease 计数造成污染。

destructive syncToMirror、保留 mirror-only 的 refresh、不删除 SAF 缺项的 flush 各自保留；没有把全部 CRUD 加全局锁或跨 native/network 持 mirrorMutex。canonical/symlink、CLI exclusion、上传 unique/sanitize、bounded read 与 search1000文件/每文件1M/总8M预算、Cancellation 透传及 stop 输出保留。15个最终生产/测试文件 hash 与 freeze 一致；66项命名 body parity 仅作搬移完整性辅助，另已直接核 Process handoff/Termux payload/真实 callers。

Chat 的 showWorkspaceSheet 既有无赋 true 已由 HEAD137f57e2 对照为原有未接入口，不当作本次漏迁；本地 mirror 成功也不冒称真实 SAF 文件选择/预览/permission 流程通过。SSH loopback fixture 缺环境的 Assume skip 保持明确边界。

## Settings：取消分类短修复

最终 diff 仍仅 SettingsFlowExt 与 SettingsAggregator 两处 runCatching.onFailure 先重抛 CancellationException。普通非取消错误保留原 print/log/halt fatal 合同；未新增旧快照兜底/retry/错误 state。各 Prefs 与 Aggregator 仍唯一 AppScope collector/publication 和 writer mutex。真实 Terminal setup/teardown 曾触发普通取消误 halt，修后原业务测试实际执行；不是通过遗留测试 scope 逃避取消。

SettingsFlowExt 原2项 fresh green 与 first-owner 审阅可沿用；Aggregator 不冒称有独立纯单测，其真实 teardown 证据另属 Terminal。Flow.catch 取消透明的其它 helper 没有因关键词再扩大改动。

## 首次审查的证据边界与最终关闭

初审后所要求的最终XML/构建安装、Council readonly/附件/引用/roster、DeepRead partial/ready/reopen、Live saved/config、Terminal native输出/stop/fresh/recreate在两width的当期PNG/semantics/owner现已全部完成；对应闭环见最终表和末尾，不再等待新的阶段内证据。

设备 fixture 的 opt-in、UUID 数据、默认 Koin route/VM、持久化与 cleanup 断言已读。Council seeded history 不覆盖真实模型/CLI/auto-round；DeepRead fromHistory 缓存 route 不覆盖 provider/真实通知点击/custom template；Live saved history 不覆盖分析/overlay/第三方 verified fill；Terminal ANDROID_SHELL/mirror 不覆盖 Alpine interactive session、Termux/SSH、实际 SAF 授权。四者各自不能替代 provider/权限/OEM/物理设备/跨端验收。

下方保留指定问题发现与精修的过程证据；这些修复及受影响验证现已闭环，顶部状态和最终表已经同步完成。


## 当期候选执行与411像素增量（首次正式设备证据）

已直接解析 `evidence/phase-4-complete-candidate-board-compile-fixed` 84个app XML：452tests、20failures、0errors、0skip，即432项绿。明确已绿为 DeepReadGenerationFallbackTest6（整篇真实fallback/取消/timeout/restore/单段/synthesis）、CouncilTimelineInteraction2（历史无receiver和active trimmed submit）、TodayBoardSettingsPane2、LiveDraftFiller7、LiveBubblePresenter3。其它20项由root按实际根因处理；URL identity/Ranker等已授权原owner取精修，fixture初始化/旧canonical期望不冒称产品20bug。未核后续green前不宣布统一行为通过。

Council 当期 `phase-4-council-411` UUID `ec076542-05d7-4137-b827-772bbba98161`，instrumentation实际1pass0skip；7个owner均411dp/font1/1080×2400，实际RouteActivity→HomeContinue→CouncilRoomPage→默认KoinVM。7张PNG逐一亲看并对照semantics：01/02/03/07 question文本完整、没有历史answer编辑器/Composer；文件chip、引用续接两行、avatar/message边距未见相互覆盖。04确实可滚回opening与时间线首段。长姓名ellipsis本身不被误报为裁切bug。cleanup的原Conversation/Settings/launch恢复与fixture room删除均true。

**历史确认、现已修复：MembersSheet长姓名挤出role，零宽role竖向膨胀。** 05/06真实PNG中guest行出现大块无内容空白、role不可见、model label被压到底部。semantics #732姓名x213..816，后续role Surface#733 x816..816宽0高894px，role Text#734 x834..834宽0高888px，model#735 y1750..1791，members card高1106px。实际MemberRow内Row先测无weight的姓名，再测CouncilRolePill；后者在零剩余宽度折成大量行并撑高。HEAD137f57e2相同body存在，故为本期实证的既有缺陷，不是提取新增。最窄修复属MemberRow局部姓名/role空间分配或自然换行，必须保role可读及status/model布局，不需改全局Pill/字号/状态；已即时通知root先真实layout红→窄修→受影响JVM/411与窄大字复验。仪器1green不能据此关闭该像素问题。

DeepRead 当期 `phase-4-deepread-411` UUID `p4-deepread-565637ac-1fbe-4ed9-bd46-7b3d3c08b00f`，实际1pass0skip/13.233s，7图逐一亲看。01/02已有overview与三个pending各自完整，不因生成未齐清正文；03/04 timeline日期/竖线、analysis各段和标题分隔对齐；05滚到extended source后标题/链接/箭头有空间且底部可达；06保留原滚动位置，07冷重开仍有完整analysis。semantics滚动05/06 value=max504，故屏幕顶端被滚出的hero文字不被误当正文不可达。所有owner为411/font1/真Window1080×2400、generationWorkCount=0；partial输出overview READY，后续输出四段READY/COMPLETE。cleanup原文章、Settings、launch恢复均true。此范围可关闭默认宽度缓存Reader像素/重开门槛，未打开外链，也不替代真实provider/WorkManager生成/custom-template/系统通知点击或窄大字门槛。


Live411 `phase-4-live-411` UUID `221e52db-b650-459d-9374-af911b7d8188` 实际1pass0skip/8.717s，3图及owner/semantics已亲看。saved行时间/包名/摘要ellipsis与删除X独立，模式从Conservative→Aggressive切换后背景/文字与右侧text-only→screenshot对应，recreate后saved/config保留；固定行的边距/分隔/开关没有重叠。此默认宽度的保存历史/配置像素可关闭，Companion始终未启动、无overlay分析或权限验收；原saved/settings/launch恢复true。

Terminal411 `phase-4-terminal-workspace-411` UUID `1b421d47-255e-4091-8930-4cf5716fad40` 实际1pass0skip/13.871s，5图逐一亲看。真实ANDROID_SHELL文件中UTF8中文/emoji、Sheet input/output及fresh job文本清晰自然换行，header椭圆/箭头/1/1有独立空间，底部结果没有被导航条遮住。owner准确记录RUNNING→CANCELLED→另一jobCOMPLETED，semantics对应Running/Cancelled/Succeeded；原文件/任务/Conversation/Settings/launch/workspace binding/Activity projection与native terminal清理均true。scope中stop由真实public API执行，不把它写成UI Interrupt按钮验收，SAF/externalBackend均false。

这批Terminal截图有一个**取帧证据限制，非新产品finding**：03-stopped PNG与02仍同running旧像素，而03 semantics已Cancelled且output有Command cancelled/Terminated。故不能据03PNG声称取消状态glyph/图标已画出；已通知root在后续既定capture使用现有draw/frame fence取得稳定当期帧，不修改产品。RUNNING图标Check另已读生产SandboxStepStatusIcon原else刻意使用Check且语义是Running，不凭图标构造“假成功”产品bug。05命名peek但实际仍展开Sheet，按实际recreate Sheet证据记录，不把文件名当已关闭peek形态。默认宽度输出几何当时已确认，取消态像素后由strict Dialog最终两width稳定帧关闭。模拟器在四fixture完成cleanup/pull后退出属于执行环境后续事件，不倒推四项为app失败，也不把尚未执行320当通过。


## 指定修复源码与Live窄大字增量

Live `phase-4-live-320-large-font-live-first` 当期UUID `d1fc9049-d803-4985-925e-5c29acbb04ae` 实际1pass0skip/11.801s，3张PNG逐一亲看并核owner：320dp/font1.5/Window840×2400。开关、header、saved行删除X、config模式边界彼此分离；已有单行metadata/模式副说明以ellipsis保留其既定行数，Conservative/Aggressive主体仍可辨，未出现零宽竖排或相互覆盖。页面底部仍可滚动，不将首屏下方Analysis model未全露出当不可达。mode改变/recreate真实保存，与same Settings/Store合同一致；cleanup全true，`phase-4-narrow-device-configuration-live-first.json`原1080×2400/420/font1→窄大字→精确原配置恢复true。此保存历史/配置窄大字范围关闭，不扩大为分析、overlay、verified fill、通知或provider已验。

Council指定MemberRow增量已直接读diff：只将内层name/role Row换FlowRow，横向原7dp、纵向4dp，其它avatar/status/model/字号/姓名单行ellipsis/全局Pill不动。P4-member-layout-fix.json与源码一致。两width fixture从真实Sheet进入私有MemberRow，检查角色实际width、line/end/glyph、height与model相对位置，保留姓名ellipsis而非强制完整长姓名；真实两红对应原零宽问题。该source与后续fresh绿态及两width新PNG均已接受，指定roster已关闭。

Ranker指定原bug：JevCouncilPoolRanker给模型的objective/context各最多1000字符与models完整metadata，但旧cache anchor仅objective300/context200/IDs；Coordinator真实按anchor及config缓存，因此后半任务变化或同ID能力变化可误复用旧分数。fresh候选XML三红均expected2 transport calls/actual1，原同输入cache断言有效。最窄修复anchor=`route|chunkIndex|state`，state是已构造且有界的同一JSON，无第二份规范化/散列算法或新cache owner；实际Coordinator完整使用anchor，不再二次裁前缀。排序/threshold/shadow/calibration/stale逻辑未改。source可接受，最终12case已亲核fresh全绿。

DeepRead URL指定原bug：生产Tool/slash→createDeepReadOpenEvent→Route→Scheduler/Worker的URL topic identity旧将整个URL lowercase，大小写敏感path/query误合并。fresh DeepReadOpenRequestTest5中path/query两项同topicId真红。仅移除sourceUrl key的lowercase；trim/HTTP(S)校验、title-only原lowercase及digest格式保留，不顺带引入新URI canonical规则。source可接受，最终OpenRequest5项已亲核fresh全绿。

Worker指定原bug：两个error catch（foreground重试耗尽、generation/admission失败）都调用title materialize，会把同标题不同来源文章连metadata克隆到当前topic。真实Room+Worker.doWork两项expected空summary/actual他源正文真红，第三项同topic partial保留原绿。两个catch现在sourceUrl不空用getFreshDeepRead(topicId)，无URL仍原materialize(title)；与Manager已有allowTitleFallback合同一致。失败/重试 output、当前topic READY部分、原其它topic/metadata、epoch/durable writer和通知策略不变。source可接受，最终Worker3项与fallback6均已亲核fresh全绿。以上均为HEAD已有缺陷，不写作此次提取新增；历史20fail保留原批事实，最终受影响复验已闭合，不虚称原452项重新整轮执行。


## Importer / document strict 指定增量独立复核

实际入口 DeepReadCreateSheet 的文档选择回调→DeepReadSourceImporter.extract，仍在原route scope处理typed Success/Failure。当前7文件与 `P4-importer-strict-source-snapshot.json`逐项SHA一致。原真正中断fixture `phase-4-member-layout-and-importer-stream-real-red` 已亲读XML：真实delivered bytes/IOException/readCalls oracle通过，最后仅temp文件残留断言红；不是早期AFD子类未被ContentResolver消费的假中断。Council两width角色实际width红也在同归档。

Importer temp创建后立即进入覆盖open/copy/parse/return/error/cancel的同一try/finally。读缓冲每次最大剩余64MiB＋1，超过cap立即return TOO_LARGE，use/finally保证流与temp清理；readLimit不会变0（首次超cap已return），unknown/谎报SIZE不能只靠header逃过。40k/40001正文截断及typed失败保持；复制循环/解析前后ensureActive，catch原样抛Cancellation。同步parser本身不因新增入口变成可抢占协程，不把解析后ensureActive写成即时中断native/XML。

四个strict extractText确被Importer实际调用；旧DocumentAsPromptTransformer/WorkspaceArtifactTools仍原parse/parserPdf。strict和legacy共用各自具体解析体，不是另一份parser。坏包/必要entry/XML失败strict抛→READ_ERROR，空输出→NO_TEXT；legacy原诊断/成功正文保持，无诊断文本黑名单。PPTX optional notes坏仍保留slide body，PDF空页在加自动标题之前跳过且StructuredText/Page/Document finally仍释放。PDFnative成功/空页没有本次真实执行证据，不以坏PDF例/JVM编译代替。

**历史确认、现已精修并验证：strict曾把自动格式标记当正文。** 两只读子轨分别核DOCX/EPUB和PPTX/PDF，本审阅者直接复读后确认同目标内漏口：合法DOCX/PPTX一行一空cell表格由appendTable生成 `| |`/`| --- |`；有效EPUB spine中的XHTML若只有空h1/li/strong，parseXhtml仍生成 `#`/`-`/`****`。strict复用这些格式串而isBlank=false，Importer会Success。该formatter原HEAD存在，属本轮新增strict“无正文为空”合同遗漏，不是legacy提取新回归。现Office空EPUB只测无spine、PPTX只测无shape，尚未覆盖空格式容器。

已即时告知root建议原owner仅补三个真实ZIP strict/legacy fixture先取红，再仅在strict具体解析入口记录实际text/alt贡献或跳过全空table；保legacy输出与合法纯标点文本，不清洗/黑名单Markdown字符串、不加通用registry。当时没有据source冻结提前关门槛；后续真实执行与oracle修正经过见document最终章节，Office19/Importer11最终green已关闭。


## 修后Council与DeepRead窄大字21图逐图复核

当期三个目录各7图均逐一亲看，owner/semantics/cleanup逐组核对，不混原红/其它width：

- `phase-4-council-member-fixed-411` UUID `f4dfe563-427f-42c4-b24d-b0f453f91a5b`，实际1green0skip/7.35s，全部411/font1/1080×2400。05/06角色完整单行，role Text width316/height37px，model独立下一行；原900px竖膨胀消失，Restart与synthesis完整且不需被空白推离。01–04/07历史只读/附件/引用/滚回首段/重建保持。
- `phase-4-council-320-large-font-repaired-ui` UUID `32ae388f-e552-4675-a9c2-c11ca58fcaf7`，实际1green0skip/18.62s，全部320/font1.5/840×2400。05/06 role自然两行完整，width273/height112px，model下一行，status独立右侧；name/model原ellipsis保留，无巨空白。对应两width严格glyph JVM2green的最终XML已亲核；指定roster source＋真实两width像素已关闭。
- `phase-4-deepread-320-large-font-repaired-ui` UUID `p4-deepread-addb5821-67d5-40ba-aefc-c9542fe73623`，实际1green0skip/11.4s，全部320/font1.5/840×2400。01/02overview与pending保留，03 timeline日期/竖线，04/05analysis/extended reading文字/URL和箭头正常；06已严格scroll回title后重建首屏，07冷重开analysis，均真实可读。本次成功不混早前系统ANR或savedScroll未composed的fixture失败。

三组cleanup原数据/Settings/launch恢复均true。`phase-4-narrow-device-configuration-repaired-ui.json`明确Council/DeepRead exit0、Terminal exit1，最终1080×2400/420/font1精确恢复true；不能写成三者全部通过。Terminal该轮owner已核没有真正打开Dialog：旧substring selector误吃peek，Back从Chat回Home符合原合同，正在只修fixture的isDialog/scopedoutput与同Chat断言；不据此报产品导航回归或用重开页面绕过。

### 本批一次集中确认的3个既有像素问题

以下均已与HEAD和真实bounds二次核实，并一次性反馈root按原owner先红→窄修；没有继续追加其它候选：

1. **Council顶栏字幕纵向裁切。** 当前Page固定height56dp，title Column clip且上下4dp，font1.5两行自然高度超过约束，`Open discussion`字形下沿切掉。不是横向ellipsis/语义缺字。HEAD同约束。最窄测试为真实Page subtitle glyph自然底<=node高，修复限最小高度与相配mode panel top57dp锚定，不能只抬bar漏panel。
2. **Council文档chip整段文件名不显示。** 当前窄图只看到 `File ·`；Text maxLines1默认softWrap/Clip把空格后`p4-local-evidence.txt`移下一行再全裁，semantics完整不等于glyph完整。HEAD同body。最窄测试检查第一可见行实际含filename已知前缀，修复限此chip明确单行不换行＋ellipsis或局部自然换行，不要求长文件名全显。
3. **DeepRead首屏masthead被固定导航按钮遮挡。** 真semantics左label[79,116,427,173]与Back圆[47,132,147,232]、右label[434,116,761,173]与Share[693,132,793,232]相交。Hero top36dp＋list top8dp，按钮另statusbar＋top10dp，字号变大后没有共同reserve。HEAD同布局。固定toolbar本身是原设计，首屏盖字不是必须保留的合同；只需纯文字hero首屏与两按钮避让/窄排，不能扩大为所有滚动态正文永不经过overlay。

**排除ask末行与手势条的产品finding：** 窄图01/02/03/07的scroll value5215/max5315还可向后100px，fixture只scrollToNode＋assertIsDisplayed；Timeline原有64dp底padding。应在真实末尾稳定取帧再判断，不为截图未滚到底修改inset。该现象不列第4产品问题。


## Document / Council chrome 最终源码与真实定点门槛

已核 `P4-office-platform-fixture-fixed-snapshot.json`7文件全部hash匹配。新增strict空正文修复现在直接落在具体parser：DOCX/PPTX的table只有strict且所有cells空才跳过；legacy仍输出原格式表格。EPUB在TEXT/CDSECT/ENTITY及非空image alt处记录真实正文贡献，strict完全无贡献返回空；合法`#`/`-`正文及alt仍有真实fixture保留，不靠格式输出黑名单。ENTITY_REF处理平台parser.text为空字符串时启用已有Html实体fallback，旧版只在null启用会漏实体，实际XML/Html边界对应而非臆测兼容。PDF未再变化，仍保持此前strict/legacy/resource finally审查结论。

执行证据需准确分开：`phase-4-strict-empty-body-and-session-diagnostic` Office17中5fail，DOCX/PPTX两空表格例首先败在legacy expected空格误oracle，尚未执行后面的strict断言；不能把这两项写成strict逻辑红。该轮EPUB实体实际parser.text为空字符串、以及多行fixture将XML声明放在offset12的非法XML另有实证，随后原owner修正相应来源/fixture。当前 `phase-4-last-ui-layout-red-and-office-fixture` Office17＋Bounded2全部真实green/0skip，新的validZIP空容器strict实际执行在这轮green；原成功正文、entity、顺序/whitespace、optional notes、损坏包、legacy诊断均保有效断言。`phase-4-council-chrome-green-reader-layout-red` Importer11亦真实green0skip，copy/temp/cancel/cap/40k typed主链闭合；strict无正文漏口的指定source/定点行为可关闭，不冒称PDFnative成功/系统chooser已验。

Council chrome `P4-council-chrome-layout-fix.json`源码已直接核对：TopBar仅fixed56→min56，title/字体/按钮callbacks不改；Document chip仅softWrap=false＋Ellipsis，文件名可见前缀由真实TimelineMessageRow约束下glyph测试验证。顶栏变高的配对mode panel从旧外层Box固定top57，改为同一Column先TopBar、剩余weight Box内Timeline/ModePanel直接overlay，天然接在实际bar下，无新测量状态、重复listowner、offset或通用slot。modeControlsEnabled、switchMode、close、answer/composer真实callbacks保持。

两真实chrome红（subtitle linebottom超node、filename前缀不可见）已在 `phase-4-last-ui-layout-red-and-office-fixture`亲读；随后 `phase-4-council-chrome-green-reader-layout-red`两项全绿0skip，MemberRow严格glyph2项同期继续全绿。此source＋定点行为及后续当期411/窄大字chrome新图均已关闭。DeepReadHero真实bbox红已由最窄reserve＋严格glyph green/新图关闭；Terminal严格Dialog两width最终完整链亦已关闭，详见末尾。


## Hero reserve source 与最新411三组19图

最新候选 `phase-4-hero-reserve-candidate-apks.json` main6ac522ef…/test848178d9…由root构建安装。Article唯一行为增量是原LazyColumn top8dp→WindowInsets.statusBars实际top＋20dp；结合原纯文字Hero36dp与固定按钮statusbar＋10dp/38dp高度，首屏masthead位于按钮下方保8dp。padding归同list可滚出，不另持滚动/测量state、不改变固定toolbar/reader commands、无font缩小或新breakpoint。该source最窄直接解决实测重叠，source可接受；真实gap与strict glyph替代synthetic overflow flag后的最终XML均已亲核通过。

已逐一亲看当期19PNG并核各组owner/cleanup：

- `phase-4-council-final-411` UUID00f3e239-3597-4436-b974-f0b645fd852e，实际1green0skip/7.897s。7图中默认TopBar两行完整、附件完整filename、roster角色自然换行后仍紧凑；历史question仍无输入，reference/attachment/scroll/recreate保持。ModePanel结构锚定source已核，但历史终态场景不冒称实际打开live mode menu。
- `phase-4-deepread-final-411` UUIDp4-deepread-4e107cba-4097-4b94-b599-09885f41e103，实际1green0skip/8.501s。7图中partial/ready首屏masthead在Back/Share圆下方，文字实际无遮挡；标题/实体tags/pending/timeline/analysis/reading沿原顺序、间距可读，recreate/冷重开内容不丢。滚动后hero文字经过fixed actions为原允许合同，不另造缺陷。
- `phase-4-terminal-strict-dialog-411` UUID7351b850-1d94-4824-be29-f2aee4c1297c，实际1green0skip/10.219s。5图已亲看，02真Dialog显示运行output，03灰X与取消语句/Terminated已真实画出，semantics同为Cancelled且有IsDialog；04fresh任务成功读取原文件并输出FRESH，05recreate真Sheet保留。严格fixture以isDialog＋scoped output，不再把peek误当Sheet；Back前后同Chat身份有实际断言。旧03取帧不一致限制在本轮关闭。真实runtime stop/public lease/TaskStore durable后fresh链不冒称点击UI Interrupt或外部backend。

三组owner均411/font1/真Window1080×2400，cleanup保留原数据/设置/启动参数；Terminal另原mirror含uploads、task、Conversation、binding、Activity projection、native终态恢复均true。此19图未见新增真实问题；随后最终320与Hero严格glyph定点XML也已完成亲核。当前按照最终表关闭Phase4，不再扩大完整source或已过UI范围。


## 最终收口：最后两宽度像素、触区和实际执行

当期C/D最终320两组14PNG已全部亲看：Council UUID `e6e5aec1-b4a7-4ef9-a9d0-9b2012504c87`，`phase-4-council-320-large-font-final-chrome-hero`1green0skip/7.569s；副标题Open discussion下沿完整，成员数允许原横向ellipsis，文档chip实际出现`p4-local-eviden…`而不再只File，角色两行/metadata/status/Restart/synthesis紧凑。header下mode overlay锚定已在source复核，不引入测量state。历史ask仍只读，末尾截图位置按可继续滚动合同，不追加inset修复。

DeepRead UUID `p4-deepread-af40501c-3de5-4f70-be38-be7a53e6c6c8`，`phase-4-deepread-320-large-font-final-chrome-hero`1green0skip/7.805s；01/02和06首屏masthead真实位于Back/Share圆下面，文字完整无遮挡；其余timeline/analysis/reading自然换行，内容/冷重开保持。03等滚到目标段后的masthead可经过fixed按钮，属于已明确保留的滚动合同，不误称reserve失效。全7owner320/font1.5/真Window840×2400，generationWorkCount0及cleanup恢复true。

Terminal最终相同严格fixture两width10PNG已全部亲看：`phase-4-terminal-workspace-320-large-font-final-title-touch` UUID `7821bd1b-e3cc-42e8-8b7d-051381b9c713`1green0skip/9.648s；`phase-4-terminal-final-title-touch-411` UUID `e5a1f769-1d67-4d04-826c-0f6982ad17a7`1green0skip/13.095s。两轮均一次真实title触摸打开IsDialog，精确output限定Dialog，运行→灰X取消与semantics一致，Back关闭Sheet后同Chat，fresh job持久完成/recreate。411取消文字完整出现；320当前viewport显示output前段，完整文本由scoped语义/真实文件与task断言确认，不声称一张PNG同时画出整段长output。底部是可滚动code区域，未发现glyph错位或不可达证据。

两份320target JSON实际记录row center(504,2038.5)落disabled previous箭头扩展touchBounds498..624/1976..2102，而title center(431.5,2038.5)在该范围外；成功入口是title，不冒称父Row任意位置点击。411title center562.5/2148也避开嵌套箭头。两个opening各gestureCount=1，无retry、语义onClick或重开页面绕路；没有产品UI改动来迎合fixture。所有cleanup字段真实为true，包括原mirror含uploads、tasks/Conversation、Settings/prefs、binding、Activity projection、native terminal；两组配置JSON精确恢复1080×2400/420/font1true。

最终XML已亲读：20类118项中原117green和唯一Hero old overflow flag fail的真实分批保留；该Hero fixture现按默认Start真实glyph自然边界＋bounds/回调/内容检查，`phase-4-final-hero-glyph-and-terminal-target-apk`1green0skip，stdout保存3份实际layout metrics，没有重定位/缩放字体。Council模块当前3XML合32项0fail0skip，T/W归档5类21项0fail但SSH8skip；均不与其它轮重复加总。初候选84类452/20fail是历史诊断批次，非“最终452项一轮全绿”声明；20fail相应owner/fixture均经所列实际复验闭合。

**最终无剩余本阶段阻塞项。** 外部门槛仍明确保留：provider凭据与线上生成、真实Council/外部CLI、DeepRead来源网络/自定义模板WebView/系统通知点击、Live overlay权限/第三方采集和verified fill、通知托盘、Camera/系统chooser/真实SAF持久授权、Alpine interactive/proot安装/Termux/SSH、OEM/物理设备/性能/跨端。现有未接Workspace sheet只标记且未删除/新增入口，不将本地mirror成功写成SAF完整验收。报告仅关闭本阶段计划内已实证范围，正式结论为 **PASSED_WITH_EXPLICIT_EXTERNAL_GATES**。
