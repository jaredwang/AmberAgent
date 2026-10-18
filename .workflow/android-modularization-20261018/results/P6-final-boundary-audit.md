# P6 提前收口：职责、入口与依赖独立审查

状态：**PASSED_WITH_EXPLICIT_EXTERNAL_GATES**。

这是跨阶段只读收口的最终结论。原52项真实职责/生产caller、短文件owner、模块DAG/DI、最后P5源码及指定行为/像素门槛已闭合，正式最终报告为`phase-6-independent-review.md`。P1–P4复用各阶段独立源码/行为/双宽像素，W-react复用正式包审。P5最后四个320大字真实UI问题已最窄修复，修后双宽各2实际绿且六张指定新PNG亲看；无剩余本轮confirmed finding。最终预算1609文件/355469行，>1000=11、>1500=1、最大NovelVM1564，明确64行内聚例外；没有将原52的行数下降代替模块质量审查。

仅写本报告，未修改生产、测试或 CSV/state，未删除任何代码，未运行 Gradle、设备、server 或浏览器，未读取其他产品。复用三名只读 child，分别核原52/预算/Novel例外、模块DAG/DI、已冻结P5核心链；本 reviewer 核 P6 五项保留文件、短文件/test seam/历史占位与 startup 顺序，并整合判断。采用 `codebase-design` 的职责深度/调用者认知成本方法，不用行数比值评价模块质量。

## 当前结论与剩余门槛

| 项目 | 最终结论 | 证据与边界 |
| --- | --- | --- |
| 原52项 | 每项职责归处与实际caller已核，原路径均存在 | 第2节逐项清单，P1–P5独立报告；root同步CSV/state |
| 手写生产预算 | 1609文件/355469行，>1000=11、>1500=1 | 最终过滤器独立复算完全匹配；NovelVM1564显式64行内聚例外 |
| 模块DAG/DI | 无生产模块循环/library→app，P2构造环实际断开 | 48includes/160生产边静态核查，真实Koin/编译按各phase执行证据分开 |
| 短文件职责 | 没有确认新增状态袋、平行durable writer或未接实现 | 具体settings/runtime/DTO/renderers/session真实消费已审，不仅按行数 |
| 测试缝/旧helper | 实际test seam、原有候选与暂态已区分 | 只标记、不删除；不将文本计数当编译器图 |
| 行为/设备/制品 | 指定范围已完成 | P5最终双宽受影响各2、新六PNG与2803hash全匹配；Chat/Novel旧hash复用边界详见正式P6 |
| 外部门槛 | 显式保留未验 | provider/账号/权限/SAF/系统chooser/SSH/OEM/跨端/性能，不以PASS伪称执行 |

## 1. 最终统计口径与独立复算

依据 `results/P6-budget-preparation.md` 的明确过滤器：`git ls-files -co --exclude-standard` 纳入未跟踪源码并去重实际存在路径；物理行按 LF 加非换行末行，空白和注释计入。手写范围包含 main Kotlin/Java、仓内维护 main JS/HTML/CSS、`web-ui/app` TS/TSX/CSS、native库Rust。排除 build/target/node_modules/.gradle/.workflow、MuPDF bindings，以及 preparation 逐路径列明的 bundled vendor（Guizang min.js、VChart min.js、上游 ui-generation JS/CSS、prism）；没有整个排除 assets/res。测试、schema、字典/资源、生成物单列，不用它们稀释生产预算。

最终`evidence/final-source-inventory.json`由root重算，本reviewer使用同一明确过滤器独立重跑，files/lines/阈值/最大路径全部一致：

| 指标 | 历史基线 | 最终手写生产 |
| --- | ---: | ---: |
| 全量手写生产文件 | 1335 | 1609 |
| 手写物理行 | 347820 | 355469 |
| >1000 行 | 52 | 11 |
| >1500 行 | 20 | 1 |
| 原52路径缺失 | — | 0 |
| 最大手写生产文件 | ChatService4514 | NovelVM1564（64行内聚例外） |

历史所有文本的110个>1000、词典最大348982行不是生产职责统计，不能与上述口径混报。当前11个>1000依次为NovelVM1564、bridge1487、CouncilRoomManager1470、SettingModelPage1445、ChatRunCoordinator1378、SubAgentManager1310、TemplateRenderer1279、NovelOutcome1243、NovelEnums1145、ProviderModelSettingsForm1144、MemoryRepository1037。后两类新增owner均纳入全量口径，不仅检查原52。BackupPage419/VM822、WebMountPage282、SshProfiles233已是完成拆分后的实际值；先前1705等工作中快照仅留在preparation历史，不再作为当前结论。

## 2. 原52逐项职责与当前处置

序号严格沿`production-scope.csv`顺序，表内行数已在最后P5包freeze后重新逐项读取。P1–P4已通过指正式独立报告，不是本轮重跑；P5现已独立正式通过；保留其行为与外部门槛界限，不以源码状态替代运行证据。P6保留项已在第3节核实际消费者与责任。

| # | 原文件（原清单路径唯一） | 当前行 | 当前入口 → 职责归处 / 处置 |
| ---: | --- | ---: | --- |
| 1 | ChatService.kt | 646 | Service → Store / Queue / Editor / Coordinator / Hooks / Presenter；P2已通过 |
| 2 | Markdown.kt | 109 | 默认 MarkdownBlock → Document / ParseCache / StreamingParser / Presentation / AST；P3已通过 |
| 3 | IntelligentUiComponents.kt | 137 | 原递归 dispatcher → 分类 renderer，共享 CardState；P1已通过 |
| 4 | NovelMarkdownWorkspacePage.kt | 618 | route → Chat/Manuscript panes与sheet/editor，同一VM；P3已通过 |
| 5 | SettingAgentMemoryPage.kt | 385 | route draft/反馈 → 子页与Editor，原VM/CAS；P1已通过 |
| 6 | ChatPage.kt | 411 | route/input/SubmissionOwner → Scaffold / ComposerHost / TimelineHost / InputActions；P3已通过 |
| 7 | CouncilRoomManager.kt | 1470 | 唯一room/jobs/ask/close → Planner / HostTurns / AutoRun，原Store；P4已通过 |
| 8 | NovelMarkdownWorkspaceViewModel.kt | 1564 | 唯一UI/admission/navigation/jobs → Queries / Interactive / Audit / History / TurnRequests；P3已通过，预算例外待登记 |
| 9 | DeepReadScreen.kt | 321 | route visit/seal/list/WorkManager → ReaderContent / template/article；P4已通过 |
| 10 | GenerativeWidgetCard.kt | 324 | Card → Dialog / Safe / Guizang / Rich / Capture / Receiver / Export；P3已通过 |
| 11 | webmount/bridge.js | 1487 | core RPC/ref/snapshot → network / Feishu / ZCode，同一bootstrap manifest；P5已独立通过 |
| 12 | SessionHomePage.kt | 426 | route → Header/Rail/List/Row；VM → CouncilCreator/ContinueNavigation；P1已通过 |
| 13 | IntelligentUiProtocol.kt | 452 | wire/catalog → Parser / CardState / StateStore；P1已通过 |
| 14 | BackupPage.kt | 419 | route/launchers/SAF/dirty草稿 → provider panes/restore/export/dialogs，同Session；P5已独立通过 |
| 15 | DeepReadAgentRunManager.kt | 553 | topic/admission/epoch → Magazine/Synthesis generator / ArtifactPublisher；P4已通过 |
| 16 | MarkdownNew.kt | 65 | HTML document → HtmlParse / Blocks / Inline / Styles / Export；P3已通过 |
| 17 | SettingProviderConfigPage.kt | 633 | 原Detail草稿 → Fields/Components/Policy/Codex与Gemini console；P5最终auth已独立通过 |
| 18 | ModelList.kt | 535 | picker owner → Query/Rows/Presentation/ModelSelector/ProviderAccordion；P1已通过 |
| 19 | SettingTodayBoardPage.kt | 228 | 原Settings writer → Board settings/source/template/focus；P4已通过 |
| 20 | LiveCompanionPage.kt | 368 | route → Controls/Configuration/Results/History，原LiveModeManager；P4已通过 |
| 21 | NovelWorkspaceRuntime.kt | 584 | runtime/tool-turn → Proposal/Author/Reviewed/ChapterCommits/Undo；P3已通过 |
| 22 | ResponseAPI.kt | 182 | facade → RequestEncoder/EventDecoder/EventTransport，原流内reconciler；P5已独立通过 |
| 23 | SyncArchiveManager.kt | 883 | restore/gate/DB事务 → PayloadExporter/ArchiveCodec/FileRestoreJournal；session整合源码与实际恢复行为已核，待P5统一收口 |
| 24 | ScreenAutomationTools.kt | 48 | catalog → 同一OperationOwner + Scope/Primitive/Flow/Collection tools；P5已独立通过 |
| 25 | SettingModelPage.kt | 1445 | 模型任务配置与参数sheet，具体Settings writer；P6可保留 |
| 26 | CouncilTimelineTab.kt | 659 | 唯一list/follow/gesture/IME → Message/Identity/References/Ask/Speaker；P4已通过 |
| 27 | BackupVM.kt | 822 | 传输/导出/列表/冲突 → RestoreSession唯一恢复来源与完成 / GoogleConnection授权continuation；P5已独立通过 |
| 28 | SubAgentManager.kt | 1310 | 唯一thread/run/mailbox/epoch → TurnLauncher/TaskProjection，原GraphStore；P4已通过 |
| 29 | RouteActivity.kt | 535 | 唯一backStack/consumed/lifecycle → Screen/Entries/IntentTargets/ShareDelivery；P5已独立通过 |
| 30 | SettingExperimentalWebMountPage.kt | 282 | 无参route/Koin/flow → SettingsSession/Card/LoginDialog/Cookie/Editors/Installer，原runtime owners；P5已独立通过 |
| 31 | PromptPage.kt | 143 | route → collection/editor/list；PromptVM在最新collection按ID执行；P5已独立通过 |
| 32 | DeepReadTemplateRenderer.kt | 1279 | 具体模板HTML/CSS与安全投影，无run/topic状态；P6可保留 |
| 33 | ChatInput.kt | 705 | interaction host → Importer/Launchers/Crop/Fullscreen/Usage/Commands；P3已通过 |
| 34 | SettingMcpPage.kt | 189 | route → Editor/Items/ImportModal；Transaction → awaited import命令；P5已独立通过 |
| 35 | NovelWorkspaceGhostwrite.kt | 187 | composition → BatchRunner/WriteWorkflow/Generator/Review/提交owner；P3已通过 |
| 36 | NovelOutcome.kt | 1243 | sealed wire outcomes与对应serializer；P6可保留 |
| 37 | ChatInputSandbox.kt | 111 | Preview host → ActivityPresentation/Sheet/Peek/Capture/Preview；P3已通过 |
| 38 | TerminalRuntime.kt | 812 | 唯一job/CAS/lease → SessionManager/LocalProcess/TermuxAdapter；P4已通过 |
| 39 | ChatMessageTools.kt | 364 | 原ToolStep → Presentation/Capsule/ThemeTryOn，原审批callback；P3已通过 |
| 40 | ChatListNormalSection.kt | 420 | 原list/layout → TimelinePlan/Cache/Effects/Items/Selection/Compaction；P3已通过 |
| 41 | NovelEnums.kt | 1145 | wire enum/associated case及serializer；P6可保留 |
| 42 | SettingProviderModelSettingsForm.kt | 1144 | 同一Model子草稿/三tab/invalid gate/确认；P6可保留 |
| 43 | web-ui conversations.tsx | 378 | Detail/Composer/Attachments/Timeline/Workbench；独立W-react包已通过，不重审 |
| 44 | ModelCouncilManager.kt | 968 | 独立batch owner → 既有CLI runner → CLI command builder；P4已通过 |
| 45 | DefaultRunKernel.kt | 769 | Kernel执行状态 → ToolCallHistory/ApprovalBatchPlanner；P2已通过 |
| 46 | ChatGenerationRoundEngine.kt | 596 | round/stream → ProviderAttemptExecutor/GenerativeUiRepairPolicy；P2已通过 |
| 47 | ChatInputComposers.kt | 761 | composer → 具体附件/编辑/配置，同一input/SubmissionOwner；P3已通过 |
| 48 | SettingSshProfiles.kt | 233 | lifecycle收集/section → ScreenSession/Card/CommandPanel/Trust/Editor，原Store/Runtime；P5已独立通过 |
| 49 | ProviderConfigTools.kt | 68 | 四工具factory → Status/Apply/Refresh/ModelSlot，同Settings writer；P5最终auth已独立通过 |
| 50 | ProviderRedesignPrimitives.kt | 865 | 视觉primitives；配置转换/endpoint policy已归具体Policy；P1已通过 |
| 51 | AmberAccessibilityService.kt | 195 | Manifest service → NativeScreen/LiveWindowReader/EditableText/LegacyTree；P5已独立通过 |
| 52 | DeepReadSectionWriterTools.kt | 610 | 同一writer mutex/counters → WriterContent解析/清理/合并；P4已通过 |

## 3. 五个原P6文件：保留有实际责任依据

- **SettingModelPage1445**：真实设置route消费；模型任务选择仍写同一SettingVM→Aggregator。各参数sheet持局部确认草稿，明确Save才提交，ImagePromptInjectionSheet通过实际AgentPromptConfigRepository读写独立提示配置，未把它伪称同一个Settings草稿；GroupDefaults在最新Settings transform内按group更新。它是同一产品设置面的若干具体sheet，并非所有函数共享一个draft，但没有第二持久writer或运行状态机。当前不为降行数把这些sheet拼成ModelMisc；将来改独立sheet时可以按明确用例移动，本轮没有必须拆分的具体风险。
- **ProviderModelSettingsForm1144**：SettingProviderModelTabPage的edit/blank EditState实际传同一个Model草稿；三个tab通过onModelChange返回同一Model，invalid-context gate归Sheet确认，headers/bodies/tools/provider override都是该Model编辑。没有把整个VM传入新helper，也没有网络/鉴权/持久化副作用混在表单内。可保留。
- **DeepReadTemplateRenderer1279**：ArticleHost、TemplateWorkbench/Settings与Typography真实消费renderCustom/renderTemplateArticle/renderEditorialSlant/renderSafeMarkdownHtml。公开输入是title/output/template/font/theme/locale，返回HTML及允许图片/链接；静态lazy parser/safelist/CSS是渲染配置，没有topic/run/claim/job writer。sample数据服务真实预览，非无caller占位；保留完整模板渲染责任。
- **NovelOutcome1243**：AppliedOperation/DocumentValidator/Codec真实消费sealed outcomes；同一个Serializer穷举wire case/字段。拆nested outcome层级会改变类型路径与serializer维护位置，而本轮没有新的行为需求；不为1000信号改wire shape或将两半when维护在无关文件。
- **NovelEnums1145**：项目/会话/计划/ledger记录真实引用协议枚举与关联值。各具体sealed serializer就近保存wire值、未知值或Swift关联对象规则；并非通用运行helper混装。现有NovelProjectCodec/CurrentWireCases测试是本仓实际消费证据，不代表跨端实机已验。保持声明聚合，不新增版本化Core制品或读兄弟产品。

五项当前工作树均未修改；本结论是保留理由审查，不声称本轮重跑其单测或这些全部UI设备场景。

## 4. NovelVM1564：明确64行内聚例外

当前VM仍唯一拥有 `_state`、project/branch/focus、turn/reload/ghostwrite jobs、reload/cache generation，以及UI admission与恢复后的publication gate。它不再自己实现filesystem查询、interactive execution、audit/history/recovery或请求组装：这些分别真实委托Queries/InteractiveTurns/AuditSession/SessionHistory/TurnRequests，协作者不收整个VM或MutableStateFlow。Queries的快照/cache只有经VM的epoch/generation/directory/slug验收后才accept；后台claim/CAS/调度在Controller/Jobs，正文/ledger/receipt在domain，不被UI预检取代。

检查了model resolution、本地化mapping、batch预检、控制面板query等剩余段：单独搬若干20–30行函数不形成当前真实共享责任，凑够64行会引入无共同变化原因的文件，或将同一生命周期拆成读写闭包/第二state袋。**建议按用户“尽量≤1500且有质量”的原意保留1564，最终明确登记64行内聚例外。** 不是所有文件已满足上限，也不是 `complete_reviewed` 自动豁免预算；root在最终plan/结果中须如实记录该例外，而非压缩格式或增加空转forwarder。

## 5. 模块DAG、DI与短文件责任

静态读取当前48个settings includes及逐模块project依赖，共161条引用；排除baselineProfile工具关系后160条生产边：无环、无library→`:app`、无未include目标、无API→domain/impl反向边，未发现sourceSets借app生产源码。baselineProfile.targetProjectPath=:app不计业务循环。1,528个main Kotlin/Java文件/2,411个顶层声明的import归属交叉检查未发现library唯一引用app声明或跨模块重复顶层类型候选。该图是静态证据，不冒充Koin运行或编译通过。

P2真实对象图仍成立：ConversationAccess绑定同一Store；Resolver/Projector不解析Service；Registry.register只存factory，Runner activation内才执行factory，runScopeFactory取Projector也在运行期；HooksFactory构造不持Coordinator，create时只接当前activation的Stop命令。这是实际拆开构造职责，没有用Lazy<Service>隐藏eager环。GenerationRunSession使用GenerationSettings快照，Transformer/Memory/Context具体settings与真实caller的P2结论保留；LocalTools是具体工具组合，stateful screen/webmount等仍用同一域owner，不因factory文件短就重复实例/锁。

Novel模块依赖单向：novel-workspace无project依赖，novel依赖它及既有ai/settings/app-infra；VM→Runtime→Proposal→Committer/Store实际入口保留，无pane自造durable writer。P4 Council/DeepRead/Live/SubAgent/Terminal/Workspace的planner/generator/filler/process/session/SAF等均被实际owner消费，没有回传整Manager、复制job map/锁或第二会话state。此处复用已通过的P3/P4状态与像素审查，不重开其完整dirty diff。

Root-startup当前源码仍在startKoin块内、WorkManager factory与eager创建之前同步恢复file journal，并先注册NovelRestoreBridge；RuntimeRecovery按 durable recover→chat replay→gate.complete 顺序，失败gate.fail，取消重抛，JS cleanup在其后best effort。新AppRuntimeRecovery是完整恢复用例，不持Application或Service；AppBackgroundSchedulers持原process scope的订阅与生命周期observer，依赖在原job内解析，未加入启动registry。真实Runner测试已有等待两个阶段/失败拒绝/JS失败仍admit三个用例；本轮只读其assert与DI caller，实际相关JVM/编译已在P5集中gate执行，仍不因source canary或本报告静态图便宣称任意设备启动已验。

## 6. P5稳定核心的提前源码结论

- **Responses**：OpenAIProvider原实例同时实现stream/complete/storedResponses，DI取同一实例；facade真实调用Encoder/Decoder/Transport，Transport不回调facade。每流唯一reconciler/queue/guard/responseId，原一次重连复用reconciler，cursor save-before-emit与finally取消EventSource保留；Codex真实Call/401一次refresh/逐行SSE路径保留。短build/parse入口有现有测试消费者，不是无证据可删转发。冻结8条hash已对齐。
- **Route**：NavDisplay实际entryProvider改到appNavigationEntries，Activity仍唯一backStack/consumed/lifecycle；冷/热OAuth-first与原multi-extra优先级保持，Share helper只解码交付。Chat receipt与同VM inputState同寿命，配置重建不重放，新的share/task走新Chat UUID。冻结8条hash已对齐。
- **Screen平台**：Koin→LocalTools→同ScreenAutomationTools→具体tools→同OperationOwner，原lock/stoppedRuns/结算归属不变；Manifest服务真实委托NativeScreen，同调用内fresh root/hash→resolve/refresh/validate/action→finally释放。测试反射指向真实新owner，不保留旧私有路径占位。冻结14条hash已对齐。
- **Bridge**：WebViewPool真实loader读取唯一manifest五份资产后拼同私有IIFE，SessionHandle原RPC pending/realm/ref/snapshot仍唯一；主窗/popup/document-start/reinject共用同字符串，缺资源沿原明确bootstrap失败。生产与Node/Chrome脚本共读manifest。冻结13条hash已对齐。
- **Archive/MCP/Prompt准备**：Archive原fork补偿新参数确从captured restore epoch传进独立appScope删除，恢复后旧owner不再以无epoch命令进入新域；此项原真实误删红由root归档，最终Archive30修后行为已实际绿。当前MCP具体suspend命令/三个publisher同latest-settings最终names检查、Prompt按ID命令已可定位，UI parent/nested草稿沿原责任；最终freeze/行为/当期双宽像素已按P5正式报告关闭，未以实现报告代替绿。没有因此泛加锁或新通用transaction框架。

最后Backup session/UI、P-auth token/catalog、SSH/WebMount settings已在完整freeze后补完增量source审查：唯一source/verification/completed、同generation token保存、latest-settings catalog/apply、原Store/Runtime/LoginController及installer真实入口均闭合，详见正式P5报告。全app集中已失败22项的有限修复也只在具体owner收口，没有按本audit新增泛化候选；History已完成最窄test-only同库summary metadata委托，真实预算4项最终实际绿；不覆盖JSON preview SQL，详见P5第11节。

**新增MemoryRepository1037的有限内聚判断**：它仍是durable memory records/candidates/events的唯一仓库writer，现新增dream merge/supersede具体事务是将原跨多次writer的同suggestion原子性收回实际AppDatabase owner。它共享原restore gate、CAS和私有DAO mutation，成功后才向Applier发布结果；没有接UI/网络/poll job或新全局状态。维持1037比将事务拆离数据库owner或传一组DAO/锁读写闭包更直接，且低于1500，不为降行继续拆。实际Memory2及周边Applier已在集中失败类cohort绿，出处与边界见P5第9节。

## 7. Test seam、历史占位与死代码候选

提前阶段按未跟踪JVM main声明建立token反向索引，只作候选生成；13个既有候选已区分如下。曾因实施窗口仅出现声明的BackupRestoreSession在最终freeze后已确认由BackupVM直接构造并被Page消费，暂态候选关闭，不再登记为未接线。不能由单次名称计数推导生产不可达。

| 分类 | 具体符号 | 处置依据 |
| --- | --- | --- |
| 原有候选随迁移保留 | ModelPresentation: V3CapabilityIcons/ModelTypeTag/ModelModalityTag/ModelAbilityTag；SandboxActivityPresentation: 两status颜色helper/terminalTranscript；NovelEditorComponents: ChapterBodyLoading/NovelChapterActionChip | 基线有同名实现，未证明新增未接线；只登记，不删除、不强接到已验UI |
| 实际测试缝 | generateMarkdownHtmlForTest / MarkdownTreeForParityTest / parseRawMarkdownForParityTest / preProcessForTest | MarkdownContentPreservation/Linkify/TreeParity/EdgeShape/AnnotatedString等真实test调用生产共享实现，非第二套解析器，不能按main-only usage删除 |
| 已关闭暂态候选 | BackupRestoreSession | 最终VM真实构造、Page与三provider交付/verify/apply真实消费；不是dead code或转发facade |
| 历史flag占位 | ChatPageSplit / MarkdownBlockSplit / GenerativeWidgetCardSplit / DeepReadScreenSplit | 四个USE_SPLIT常量仍false；本次新职责在默认原入口直接使用，没有靠打开旧占位证明模块化。保留原有路径，不擅删 |

没有新增有证据支持的删除任务或优化候选。接口、注解/serializer、同文件private helper、DI factory、测试缝与动态入口不能用文本零引用判死。后续仅因P5最终新源或实际失败做必要增量，不重复扩广审查。

## 8. 最终收口事实

P5最后四个实际UI缺陷由本reviewer从320/font1.5当期图确认，原owner只改McpImportDialog、PromptPage、BackupProviderPanels三处layout。修后双宽各2方法实际绿，01Backup/06MCP/09Prompt两宽六张新PNG亲看，风险标签、导入动作、两行Mode Injection及Read snapshots完整且可达。没有通过缩字/改主题/弱化业务assert或新状态解决。

最终`final-source-checkpoint.json`2803输入当前SHA256全部独立匹配，相较`final-source-checkpoint-before-four-ui-fixes.json`恰上述三个layout文件变化、无增删；最终inventory再次独立重跑1609/355469/11/1/1564精确匹配。历史22红由集中cohort58＋History4实际关闭，保留全3178未重跑及14skip事实。

正常三产品最终APK实际hash已独立比对：Full d6a5eaad…、Novel d19e5e04…、DeepRead dc75e97d…；dummy marker=false，Full安装对应最终hash。当前关键Chat2/Novel1在前一Full ebd88634实际执行绿、代表PNG已亲看；其后纯三文件layout增量按影响范围复用原运行，最后只复验受影响Settings双宽，未冒称新hash全链路重跑。真实AndroidWebView17是offscreen行为而非像素；Route双宽各3与指定8PNG已亲看。

本报告与`phase-6-independent-review.md`最终均为**PASSED_WITH_EXPLICIT_EXTERNAL_GATES**。无剩余本轮已确认问题，不新增优化候选；原有dead-code仅标记不删。外部provider/OAuth账号、SAF/chooser/camera/权限、Alpine/SSH、真实Web后端、物理/OEM/性能与跨端未验边界继续保留。构建、安装、实际用户行为与这些未验项分别报告。

提交前统计说明：19个新增源码文件仅移除末尾空行，总物理行据当前文件更新；可执行内容和原构建/行为验收不变，详见commit-preflight.md。
