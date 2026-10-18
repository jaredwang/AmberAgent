# Phase 2 独立审阅

状态：**PASSED_WITH_EXPLICIT_EXTERNAL_GATES**。

本报告为正式Phase2独立审阅。已对照当前生产实现、真实调用者和`git show 137f57e2:<path>`，没有修改source/测试、运行Gradle或操作设备。runtime源码、唯一owner/实际入口、恢复epoch/CAS、队列落盘与异常副作用的独立审查已完成；本phase有限维护复核也已完成。最后亲看当期411dp/321dp PNG，按owner.case和width筛选对应XML/semantics/JSON，排除拉取目录混入的旧帧。已确认缺陷全部取得失败证据、窄修并闭环，阶段可进入下一phase；系统通知托盘及真实外部provider等验收范围如下明确保留。

## 当前结论

已审稳定部分不是按行数均分：Store、Queue、Editor、Presenter、工具目录、wire attempt、repair、run-scoped history/approval planner及settings projections均有具体变化原因。没有发现它们构造器回收ChatService、万能Context/运行状态袋，或复制旧实现而保留未接线新类。生产Resolver/Agent/Projector实际改用这些owner，Service主要保留兼容入口。

目前没有未关闭的已确认Phase2缺陷。独立审查期间发现的Stop/settlement/remote pause缺陷已逐个取得真实红、作owner内窄修并转绿；当期设备图暴露的原队列header挤压问题，也已在411/321dp修后真实图与glyph/触区断言中关闭。实际composer→Coordinator→Runner→Resolver/Store路径已执行，Koin对象图与同一Store/flow身份有设备实证。

## 已深读的真实调用链和owner

| 范围 | 当前生产路径与独立判断 |
|---|---|
| 会话Store | UI/Web/task的旧Service入口及ChatTurnAgent/Projector经同一ConversationAccess进入ConversationStore。Store唯一持sessions、分页/init mutex、checkpoint时刻、tombstone和deferred purge epoch；Session持实际conversation/timeline/job/ref，外部只拿只读flow，Store负责内容和分页写入口。生成读取merge full history但不把full发布给UI；window upsert按oldestLoadedIndex，不以tail覆盖完整历史。 |
| pending队列 | Queue唯一持每id mutable queue、revision/锁/channel/dispatcher；Session引用Queue同一StateFlow，未保留mutator或镜像。入队保留STEER前缀、FOLLOWUP FIFO；durable dequeue和COLLECT缩减在返回dispatch输入前等待写入；移回input同理。工具queue/status/cancel从实际Queue构造，不回Service。restore invalidates pending revisions，成功/partial commit清同一flow并写空队列。 |
| 消息编辑 | Service/Coordinator把编辑、fork、variant选择和删除消息交ConversationEditor；其compact操作直接进ConversationContextRepository，未反取AI engine；实际内容持久化进Store。编辑/variant切换仍拒绝active generation，fork复制文件形成独立conversation owner，失败只回收新副本；message delete先保存新引用，再清generated image。 |
| Android展示 | Hooks/Coordinator明确调用ChatRunPresenter；foreground observer和flow只有Presenter一份，notifications/FGS/task副作用归它，active/paused/terminal不归它。PendingIntent conversation/run身份与旧公开ID函数保持，start task cancel显式绑定本run；finish通过task spec.run_id验证，避免旧run收尾改后继task。 |
| 工具目录 | Resolver直接RunToolCatalogFactory；Service debug/兼容工具入口也调同factory。Factory不持conversation/run可变状态；传入具体conversation getter、queue tools、approval快照、policy/events。foreground provider/theme-import/MCP-management在child继承registry之后加入；tool_search动态provider沿同一目录更新。child只重绑local WebMount run scope，其他闭包identity保持。 |
| LocalTools | WebMountLocalTools真正接管global/eval gate、adapter筛选、Feishu scope重绑及endRun；ImageGenerationLocalTools接管image-model可用性、conversation输出归属及最近选中用户消息的本地图片解析。不是23依赖挪进一个Context，也没有引入通用ToolProvider registry。 |
| generation设置 | 真实五处RunSession构造（Chat、Novel、DeepRead、SubAgent首轮和report retry）在最终model和本域override之后映射。Kernel和RoundRequest不再收完整Settings；RoundEngine实际消费已解析provider/defaults、memory/context/transformer视图。没有rawSettings或通过闭包重新读取全量Settings。 |
| Transformer/Memory/Context | Time/MiniApp/regex/template/prompt/lorebook/nickname/OCR实际消费者都用窄字段；OCR nullable配置到真识别时才报原错误。MemoryRecallStore内部selection/budget用MemoryRecallSettings；Context prepare/compact用ContextGenerationSettings，自动压缩仍优先run model，manual Settings入口仍按原task model解析。对外Settings overload即刻映射，未新增备用实现。 |
| Attempt/Repair | RoundEngine按round新建ProviderAttemptExecutor，后者唯一维护attempt index、每次wire hard-fit、fit后audit、直接gateway及采纳finish；accumulator/33ms tail/full更新/outer retry仍在streaming编排。RepairPolicy只持具体文档与模板规则，不持Settings/Service；repair请求无副作用tools，失败attempt不保留adopted verdict。 |
| Kernel history/planner | 每次kernel invocation创建一份ToolCallHistory并按同run ledger seed；history不执行工具。ApprovalBatchPlanner返回fresh/resume patch与ready信息，不写conversation、不dispatch；Kernel继续持messages/step/terminal/steer/ledger顺序，AgentToolDispatcher唯一执行副作用。PREPARED先于审批展示、截断先于执行、all-or-wait、same-callId digest约束保持。 |
| Approval/Hooks | ChatApprovalActions唯一持trusted names和outcome-unknown投影，审批先Store持久化再audit并返回具体shouldResume；不持Session job、不launch Runner。HooksFactory每activation捕获restore epoch/durable/旧terminal/settings/task id/reported terminal/response request；consumeSteer经Queue，checkpoint成功后才清ledger payload，final content checkpoint→terminal owner→event CAS→success effects顺序可读。 |

## I与DI的独立追踪结论

实际路径：

```text
UI/Web/task → Service → Coordinator → Session drain job
                                    → Runner.launch
Runner registry的run-time factory → ChatTurnAgent → Resolver
Resolver → Store + HooksFactory + ToolCatalogFactory + Coordinator具体Stop命令
Projector → ConversationAccess (=同一Store)
Hooks → Store/Queue/Presenter/Approval及既有durable owners
```

DI定义已读：ChatModule注册真实Store/Queue/Editor/Presenter/Approval/Hooks/Coordinator；GenerationModule/ToolExecutionModule从DataSource/AppModule迁移真实定义，实际AmberAgentApp模块列表包含它们，startup interrupted-restore→workers顺序保持。ConversationAccess不再绑定Service；ChatTurnAgent factory改取同一接口，Projector从接口取Store；Resolver构造参数中没有Service。

Coordinator持Runner，Runner registry的agent factory是在run时调用，注册构造本身不解析Resolver；HooksFactory构造不持Coordinator，其create只接一个明确Stop命令，task cancel时调用。故原Service→Runner→Projector→Service的eager构造环在定义层已真实去除，不是换成新Lazy<Service>。最后已用真实设备composer/runner执行及Store/flow对象身份实证补齐，未把Koin静态verify或仅启动空首页等同运行对象图验证。

Coordinator当前active map、paused set、last outcome和generationDone各一份，Service直接透出同一flow。runId/handler Job与Session drain Job分开。当前实现有launchedRunId区别于提前注册的expectedPausedRunId，finally仅对实际launch执行NonCancellable awaitRunSettled，先cancel handler再await；它不在handler hook内部await自己。Queue下轮派发在该barrier后。匹配notification Stop同时取消drain，stale runId early return；挂起server cancel后还重核successor identity。原Runtime5和Coordinator7均在最后冻结版本实际转绿；下文保留增量红证据及对应窄修，不以局部barrier代替所有入口验证。

本轮已核验的具体契约：

1. 原RuntimeOwnership三红：matching stop停止drain且保留队列；cancel路径等待独立handler；terminal snapshot不早于handler cleanup释放dispatch。
2. 新Coordinator四行为：同drain不得抢先派发后继；迟到server cancel不得影响successor；persisted WAITING_USER且remote cancel未确认必须保持WAITING_EXTERNAL/cursor；task cancel绑定captured run。
3. 编译曾揭示ErrorStore调用/Store限定/resumePendingQueue残留helper及新增Editor注入的共享DI；已在最后冻结diff复核并经同次app/test compile/assemble，不把中间态接线遗漏算作最终剩余缺陷。

### I停止增量：已读源码和本轮实证的边界

`parkUnconfirmedStoppedApproval`针对没有handler的WAITING_USER：remote cancel未确认时，真实Room terminal进入WAITING_EXTERNAL，event owner按协议先pause→RUNNING再RUNNING→WAITING_EXTERNAL，不伪写terminal CANCELLED；保留response cursor，清理无handler可消费的内存Stop latch，保持activeConversation投影且清lastOutcome。取消approval工具展示后Bubble仍为RUNNING、`waitingApproval=false`。当前fixture同时断言真实Room terminal、实际event transition、cursor、投影、Bubble及latch；本轮122项日志除admission外均绿，与源码意图一致。此处不把WAITING_EXTERNAL误读为成功完成，也不把保留cursor当作泄漏。

取消后立刻send的已确认并修复问题：旧Session job已被cancel但其独立handler仍在NonCancellable cleanup，`isGenerating`已为false，新的`launchViaKernel`入口此前可创建另一dispatch并进入wire。生产可达入口是普通composer `sendMessage`，可见影响为同会话两轮副作用/持久化交错；属于本次settlement抽取必须封闭的admission边界。当前源码在launch入口捕获前一个job，并在新dispatch内部先join后进入loop；最终gate已绿。fixture在真实send入口保留旧cleanup，断言新wire未到且释放后第二条仍实际执行，不靠只检查job数量。

该join改法另一个窄边界已在`phase-2-stop-closure-red.log`取得真实红：等待旧cleanup的新job已进入Session，但firstMessage仍仅保存在job闭包；若旧run的matching通知Stop到达，合法取消当前drain后，这条已返回accepted的输入尚未append、也不在durable queue。实证`expected [Second], actual []`，可见影响是已接受输入丢失，不能误报为“matching Stop不该取消等待drain”。fixture hold旧settlement→send第二条→旧matching Stop→释放，同时断言未发新wire、内存队列与实际PendingMessageStore；等待既有writer后读取落盘，不人为调用persist制造成功。优先复用已有Queue保留交接输入，不建立第二份Job/run映射；最终同fixture已绿。

同run恢复覆盖也已取得真实红并修复：park的idle检查后Room pause/get可以挂起；同runId审批恢复在该窗口创建新Session job并claim RUNNING，旧`pauseIfLive`仍可把该RUNNING改WAITING_EXTERNAL，随后event第一次PAUSE→RUNNING拒绝被忽略、第二次LIVE→WAITING_EXTERNAL可再覆盖。末尾successor检查发生在写入之后，且runId相等不足以区分activation。最初Room pause入口gate已红；最终fixture进一步改为在Stop的event claim入口gate，窗口内新activation经真实PAUSE_STATES→RUNNING并断言Applied，未用emptySet绕过claim。`phase-2-stop-closure-red.log`7项3红，包含该例`expected RUNNING but was WAITING_EXTERNAL`。属于本次新增park路径的回归，能使正在运行的恢复activation被标为外部等待，影响UI、恢复和后续terminal提交。最终复用既有event owner pause→RUNNING CAS竞争且必须Applied，再写terminal pause，最后发布event pause；Runner先赢则旧Stop不写terminal，Stop先赢则在terminal完成之前不发布可重新claim的pause。最终定点fixture已绿，未在覆盖后补读或引入通用并发框架。

重复Stop也已取得真实红并在最终gate关闭：`repeated Stop must not leave a latch without a handler`。首次park后已无handler，但再次Stop走非park分支留下unconfirmed latch；不能以首次WAITING_USER→WAITING_EXTERNAL通过推断WAITING_EXTERNAL重入正确。应在同一Stop owner按真实已停park状态处理，不用建立第二份状态。删除时的session取消/等待已归Coordinator，Service不再直接取消job；其余删除/远端恢复的既有契约不扩大到本phase重设计。

最后冻结源码的有限增量复核：三项已红边界均已作对应窄修。send仅在旧dispatch取消未completed窗口把accepted输入先放原Queue，使用原独立appScope持久化，新drain仍join旧job后按原durable dequeue；合法matching Stop能停等待drain而不会清队列。`parkUnconfirmedStoppedResponse`先以event pause-only→RUNNING claim且要求Applied，再terminal WAITING_EXTERNAL，最后精确RUNNING→WAITING_EXTERNAL；Runner已先claim时不动terminal。claim返回后重核Session job身份，no-handler失败直接结束Stop，不回落mark/cancel同run新activation。重复Stop按既有RESPONSES_RESUME_STATES处理，未加DAO、锁、第二份状态或Job/run映射。已读当前测试保留所有原断言；源码上未发现该有限diff新增真实缺陷，最终`phase-2-runtime-final-verified`的Coordinator7项全部绿，三项真实红均已闭环。

## 原有真实缺陷的本轮窄修复核

### Memory pinned重复占预算

对照HEAD与当前实际rerank算法：旧式只对pinnedFirst去重，再拼ordered；同pinned在两边出现会被预算计两次。当前拼接后按record id去重，仍保持pinned优先和semantic顺序。实际RoundEngine→MemoryRecallStore→注入的semantic reranker可达；可见影响是重复记忆、另一有效记录被预算挤掉。属于原有owner缺陷，修复在预算前唯一selection owner，没有改UI或新增fallback。原测试/本轮summary作为验证证据，最终汇总须保留真实结果。

### SubAgent report-retry审批被误标完成

真实GenerationSubAgentRunner在首轮无report后再调用同Kernel/tools；retry仍可产生Pending。旧实现仅首轮检查pending，后续可能给Manager错误COMPLETED。当前同一pendingApprovalResult在两处复用，保留首轮visible answer和同一isolated generationSettings。实际影响是下游任务/ThreadGraph错误终态，修在生产domain result owner；不是靠UI标签假装修复。没有扩大Phase4SubAgent结构范围。

### TaskStore.cancel迟到回写污染successor

TaskScheduler/Presenter复用generation-conversation taskId，每轮spec含不同run_id。旧callback可挂起，successor start后旧cancel fallback把新任务改CANCELLED。当前用既有mutex联合捕获snapshot+callback，callback继续锁外等待，回写通过已有expectedSpec CAS；拒绝时返回当前snapshot。成功/失败/不可取消分支都只改同一动作归属条件。真实可见影响为后继运行中任务被错误取消/失败；原有缺陷，修复未新加状态机/锁。主agent已取得RUNNING→CANCELLED红并回报Task13项绿；独立读当前代码确认CAS是既有writer，不改变null-spec合同。不能据此宣称任意任务身份协议全部升级。

## 已二次核对不作为缺陷的点

- Store updateConversation的checkDeletedFiles兼容flag、save后清文件策略在HEAD已相同；不能把迁移后的文件名变化误报为本轮附件清理回归。
- Queue forget时清revision/lock、异步events、Stop保留pending属于原实现合同；本轮真实queue owner和durable-before-return已建立，不为无复现的并发假设新加重试/状态层。
- Session持generationJob/ref及只读queue view有真实寿命职责，不等于新增运行状态袋；Coordinator没有写conversation.state绕过Store，Hooks通过Store更新内容。
- Factory构造依赖较多源于组合已有具体工具；它不拥有各工具业务状态，不要求为了构造参数数值再拆通用factory框架。
- Settings convenience overload仍存在不等于generation保留全Settings：实际RunSession/RoundRequest/TransformerContext和Memory/Context核心已窄化；调用者拥有Settings时即刻投影是明确边界。
- kernel flow仍负责顺序状态，Planner/History是run-scoped协作者，不是第二执行引擎。不能因Kernel仍较长要求另建loop state-machine。
- Service保留疑似未调用的旧private merge helpers和transformer声明，HEAD也已存在；按用户规则只标记、不擅自删。它们未被新生产入口调用，不用于宣称新owner接线。
- fork失败补偿显式epoch传播的原约束已有Phase5文件/恢复包归属；本轮未凭无观测的restore路径扩大补偿机制。

## 已核对的实际验证与像素结论

- **源码/JVM：**`results/phase-2-runtime-final-verified.log`与`evidence/phase-2-runtime-final-verified/`保存最后冻结版本13类66项0fail/0skip，包含Coordinator7与RuntimeOwnership5；独立读取了实际断言及XML。覆盖Stop保留队列、旧handler settlement、accepted输入持久保留、同run claim竞争、无handler重复Stop、迟到server cancel与task captured-run。此前Store/Queue/Editor、Engine/Transformer、Kernel/Approval/Hooks、Memory/SubAgent实际owner行为测试亦已读。TaskStore.cancel为另模块13项实证。
- **构建/安装：**runtime gate同次assembleFullDebug与assembleFullDebugAndroidTest成功。后续唯一UI增量为ChatPage队列header，最新`evidence/phase-2-queue-ui-build.json`记录对应源码hash及app/test APK hash，构建和安装均exit0；独立再算两个source hash一致。设备最终使用app `fd917876…`、test `037cffb2…`。旧final-build日志是参数位置错误导致0XML，未计为源码红或测试通过。
- **真实入口/DI：**自有emulator5580真实composer发消息，经实际Koin单例、Coordinator、Runner、Resolver、工具目录和Room。owner-identity证据证明ConversationAccess===ConversationStore，Service和Store透出exact同一flow。外部OpenAI端点为本任务本地SSE fixture；不是只调用fixture HTTP冒充产品链。
- **411dp最终A：**`phase-2-ui/final-run-411dp` case `3fd4713a-62c0-4556-a255-134eb3d6fc72`，instrumentation `OK1 / 23.362s`，无skip。真实流式首轮→两条入队且不提前wire→IME保留草稿→HOME后台→warm返回同Activity/run/队列→Stop durable CANCELLED并释放真实job→队列保留→真实UI resume→FIFO两轮各不同run完成与完整Room用户消息/回复。background JSON为Process CREATED、Presenter foreground=false，warm图和owner保持同run RUNNING、两条原durable queue；不是只recreate Activity。
- **411dp最终B：**同目录case `c2f73c4d-3e0e-43cc-af24-b8cdaefba053`，`OK1 / 10.718s`，无skip。真实ask_user工具持久WAITING_USER，recreate后恢复；选项选择不请求provider，点击Submit后同run继续并完成，真实第二请求含所选tool_result，持久Answered包含所选中文。
- **321dp最终A：**`phase-2-ui/final-run-321dp` case `4f539b87-a8fb-4fea-9df0-7b004f0b90af`，`OK1 / 20.163s`，无skip。与A同链路含真实HOME/warm、队列布局断言及Room/FIFO。实际843x2400/density420、screenWidthDp321，结束恢复原1080x2400/420、无override，size/density恢复true；不把411图缩放冒充窄屏。
- **321dp B：**沿用同产品runtime的`clock-run-321dp`当期B case `3f46cd36-46f3-4ec3-828e-c072ab88b55f`真实绿证据；独立亲看该07/08长选项与已回答换行，未见裁切/错位。最后生产改动只有queue header，未影响ask_user布局或逻辑。

独立逐图审阅411/321dp的running、queue、IME、Stop、resume、完成和ask_user；最后针对修复增量再亲看最终411/321的03/04/06及411新B07/08，并逐个核对应width、owner.case、AX bounds。未发现新增真实错位、重叠、控件裁切或间距问题。321dp建议chip第三项部分可见属于横向滚动列表设计，不判为截断缺陷。

### UI真实发现及关闭证据

原队列弹窗header两宽度03都把Move down挤为`Mo / ve / do / wn`，Cancel完全不可见。411dp旧AX为21px宽/180px高，与PNG一致。生产入口为queue badge→Queued messages，源码mode Text与三按钮内Row争宽。最窄修复仅增加FlowRow import并把header改Column首行mode、下方FlowRow动作；callback、enabled index边界、动作顺序、preview均保持，不拆整个ChatPage或增加状态。

最终411与321的03都显示每卡Move up/Move down/Cancel完整水平标签，边距清楚，六动作均有单行glyph fit、可见及触区不重叠实际断言。411 Move down恢复164px宽/45px高；321也为164px/45px，Cancel bounds完整在卡片内。源码有限维护审查无新增问题。

旧04/06“仍Generating”的观察经源码/证据二次排除为生产缺陷：fixture原isActive!=true可在cleanup尚未completed时满足，但ChatPage合法保留loadingJob!=null到完成；AX又可能读旧cache。fixture改为真实generationJob==null并断言Send/无Stop，clearCache获取fresh root。最终两宽04/06的PNG与AX均为Send、无Generating/Stop；owner队列04仍两条、06为空且与durable一致。未为测试改产品生成状态或停止合同。

### Fixture准确性与未验范围

设备fixture全部业务断言保留。原waitForIdle遇既有Markdown streaming-tail逐帧reveal持续忙，仅测试clock在A首stream后关闭autoAdvance并有限推进、finally恢复；不改产品动画，不据此宣称真实帧率/流式渲染性能通过。Settings恢复改canonical JsonElement全持久字段等价并另检!init，避免@Transient Composable lambda引用导致data equality误报；各最终case的allPersistentSettingsRestored/settingsInitialized/launchPreferenceRestored/testClockAutoAdvanceRestored均true。只按provenance.currentFiles和owner.case/width取当期图，未混目录复制的旧帧。

明确外部门槛：

1. **C系统通知托盘未验：**设备POST_NOTIFICATIONS granted=false、appops ignore，`phase-2-notification-permission.json`有证据；本任务没有新增授权或修改权限。JVM PendingIntent身份/通知Stop/approval token与真实后台Presenter证据不能替代系统托盘点击、OEM后台限制或真机验收。
2. **真实外部provider未验：**本地SSE证明产品实际调用链及存储合同，不代表OpenAI真实账号、Responses远端存储/取消、OAuth、MCP外网或各厂商API。remote-cancel未确认及cursor合同有本轮owner-level真实Room/event测试，不冒认外部服务验收。
3. **设备与性能范围：**本轮为任务自有Android emulator、411/321dp、fontScale1；不是OEM真机、全部字体/语言、后台长时保活或真实流式帧性能结论。测试字体/权限未改，临时size已恢复。

上述门槛已明确标记，不妨碍本阶段重构与已授权本地行为验收闭环。达到本阶段完成标准后收口，不再自动追加广泛优化范围。
