# Android 模块化实施计划

## 目标与授权

基线：137f57e2d5c9dc3b924866db2d7b2a369bb78478。用户已授权完善计划并从 Phase 0 连续执行到所有阶段完成；每阶段结束独立 subagent 检查逻辑闭环、完整生产接线和 UI 错位/对齐/边距/间距/尺寸，再精准修复有证据的问题。不逐阶段等待确认。

允许修改本 Android 仓源码、测试和必要构建接线，运行测试、构建及独立 Debug 验证。不得读取其他产品仓库；不得擅自提交、推送、发布、卸载/清除产品数据、新增权限、删除无关/疑似死代码。保留现有 provider、Room/WorkManager/Keystore/Compose/Koin、序列化与恢复契约。

## 成功条件

1. 手写生产源码尽量 <=1500物理行；若再拆会破坏同一状态/生命周期职责，必须独立审查并记录具体例外，不压缩格式或机械凑行。1000行是审查信号，不作为机械拆分门槛。词典/翻译/历史schema/锁文件/第三方库单独统计。原始52个大文件与20个超1500文件逐一处置；初筛中已发现的小文件反向耦合也要改。
2. 一个状态/锁/CAS/epoch/事务只保留一个 owner。新协作者不得接收整个旧manager或万能Context；不增加透传层、重复状态、通用registry框架、假想兼容/重试。
3. 原生产入口直接使用新职责；不能通过打开Split调试占位或保留另一套未使用实现声称完成。既有独立runtime/settings事务owner保留内聚性。
4. 关键取消、审批、终态、分页窗口、附件归属、restore epoch、Novel commit/receipt、WebView实例/策略与路由契约不变。
5. 每阶段必须有独立review结果、问题处置、相关真实行为测试及compile证据；UI修改需当期截图/语义树/Compose布局证据。无法运行的门槛明确标记，不能用编译或静态源码断言替代设备/provider验收。
6. 最终受影响模块测试、Koin/全应用编译组装、可用设备关键流程回放；工作区归属清楚，报告实际完成与未验证项。

## 阶段与依赖

| Phase | 工作包（每包单一文件归属） | 前置 | 验收重点 |
|---|---|---|---|
| 0 基线 | P0-plan；P0-baseline；P0-device；P0-review | 无 | 当前失败/环境与52文件清单；精确测试选择；UI取证路径；计划闭环 |
| 1 清晰边界示范 | P1-intelligent-schema-state；P1-intelligent-renderers；P1-memory；P1-provider-forms；P1-home-model-list | 0 | 6个超限入口缩小；card/draft/CAS唯一；生产接线与基本布局不变 |
| 2 聊天运行 | P2-tool-catalog；P2-conversation-store-queue-editor；P2-run-lifecycle-presenter-dispatch；P2-kernel-round-policy；P2-generation-settings-projection；P2-di-bindings | 1 | Resolver/Projector不回依赖ChatService；取消/审批/恢复/持久化；真正解除DI对象环 |
| 3 Novel与渲染 | P3-novel-panes；P3-novel-usecases-commit；P3-widget-runtimes-export；P3-chat-input-page；P3-markdown-timeline；P3-message-tools | 2 | Novel原子提交/CAS；附件与WebView生命周期；流式keys/锚点/布局 |
| 4 功能域 | P4-council；P4-deepread-board；P4-live；P4-subagent-terminal-workspace | 3 | 单一room/thread/topic/run owner；close/claim/restore/retry/取消；状态不随展示丢失 |
| 5 IO与入口 | P5-backup-sync；P5-provider-auth；P5-route-screen；P5-webmount；P5-responses-web；P5-mcp-ssh-webmount-settings；P5-prompt-editor；P5-app-startup-owner | 4 | 恢复journal/临时文件；OAuth迟到；Intent/原生对象；bridge、SSE、Web草稿 |
| 6 收口 | P6-budget-dependencies；P6-remaining-source-tests；P6-full-verification；P6-final-review | 5 | 20个超1500全部处置；52项逐项复核；小文件耦合；依赖/布局/产品关键流程 |

阶段顺序固定，阶段内仅并行无文件冲突的工作包。每个Phase拆成可验证小批次；同一owner的状态迁移串行。UI不重新设计，先保留主题、尺寸、间距及已有可达交互；只有截图/实际布局/可达源码证明缺陷才改。

## 具体范围归属

`production-scope.csv`逐项登记52个原始生产大文件及阶段/处置。Phase2明确覆盖短文件GenerationRunSession/TransformerContext的全Settings耦合、DataSourceModule注册职责与LocalTools（AmberAgentApp启动恢复屏障归Phase5）；同事务SettingsAggregator不拆writer。Phase3覆盖ChatMessageTools展示职责。Phase5先深读SettingMcp/SSH/WebMount/PromptPage实际owner，再迁完整编辑/导入/连接用例，保留McpImportTransaction。Phase6仅对NovelOutcome/NovelEnums等声明/兼容serializer与单一草稿表单复核合理保留，不临时扩大状态机重写。

## 每阶段UI证据矩阵

共同默认：Android API34 arm64模拟器，紧凑手机约411dp宽；需要比较变化时再用横屏/宽布局约800dp、320dp窄宽与fontScale1.5，检查后恢复原设备设置。使用现有主题tokens，不修改设计风格。每阶段至少一轮独立subagent查看当期截图，结合Compose语义/坐标断言；不把静态源码当像素验收。

| Phase | 最小实际UI路径 | 重点布局/行为 | 现有设施 / 证据 |
|---|---|---|---|
| 0 | 当前首页→设置→显示/主题→空聊天；Provider模型表单/Novel/DeepRead基线按后续需要留样 | 源码版本/包名/主题/视口对应；截图非loading/错误页 | UiRefreshDeviceSmokeTest、RedesignNavigationSmokeTest；本次构建独立fullDebug |
| 1 | 首页列表/搜索与模型选择；Memory各子页及编辑；Provider表单；声明卡片的决策/表单/探索/学习 | tab/按钮/输入对齐、边距、长文与窄宽布局、切换/编辑/失败草稿、卡片输入反馈 | 现有navigation smoke与新增定点IntelligentUI Compose场景；必要对齐断言 |
| 2 | 聊天发送/排队/停止/审批续跑；通知归属与后台返回 | 状态提示/队列/输入可用性；旧通知不影响新run；UI消费唯一owner | ChatRuntimeOwnership/Dispatch行为测试 + 本地fixture导航回放；无凭据不伪称provider验收 |
| 3 | Novel聊天/稿本/分支/控制面板；正文Markdown表格/列表/HTML/思考；Widget流式→展开→返回；附件裁剪/编辑 | 滚动锚点/稳定key、长内容截断、弹窗边距、WebView存活、IME/底栏、横竖布局 | ChatListNormalAnchor/Markdown snapshots；Novel与Widget定点UI截图/生命周期 |
| 4 | Council时间线/成员/审批；DeepRead文章/模板/重试/部分错误；Live卡片/浮窗；Terminal输出/会话 | 内外滚动、不同卡片高度、对齐间距、浮窗切换与离页状态 | RedesignNavigationSmokeTest与各域定点fixture；真实overlay前提单独记录 |
| 5 | Backup各来源/验证预览/取消；Provider登录状态；冷暖deep-link；WebMount/ZCode；Web会话编辑附件 | 表单/错误提示可见、按钮busy、键盘遮挡、预览边界、手机Web工作台 | 归档/路由/JS脚本行为+screenshots；OAuth账号/真实手势有环境才验 |
| 6 | 重跑所有受影响产品入口与关键跨页路径；Full/Novel/DeepRead构建 | 无新增空白/占位路由；主题/字体/横竖屏实际抽查；保留数据与清理归属 | 汇总阶段证据，不为凑数量重复相同检查；最终独立逻辑+UIreview |

## 生产不变量

- Chat：分页窗口不覆盖未加载历史；queued input顺序及durable dequeue；旧通知不取消successor；dispatch等真正runner cleanup；审批续跑复用runId；先内容持久化再terminal。
- Novel：UI gate -> domain owner/CAS -> durable persistence；同monitor/restore epoch/claim execution identity；正文、ledger、checkout、receipt联合提交；不跨provider等待持锁。
- Provider/Input：唯一编辑草稿；OAuth generation/provider identity拒绝迟到；附件复制接受后才转移归属；crop source等import完成；跨会话结果不污染新草稿。
- Render：现有AST与HTML两路径职责保留；静态/流式缓存、block keys、callbacks、滚动state与anchor不变；三个Widget runtime保持各自安全策略，不引入宽松默认。
- Restore/Automation：verification单次consume；仅清自有临时副本；已提交DB/file保留journal；唯一操作mutex/stop；AccessibilityNodeInfo不跨observation持有；Intent冷热共parser、OAuth优先、share不重放。

## 验证与review规则

1. 改前用已有测试/实际行为固定本包契约；已有bug先复现。重构进行前后比较，不为每个新文件造镜像测试。
2. 主agent统一串行执行Gradle，worker不启动竞争的app编译。模块纯JVM检查可在明确无资源冲突时并行；详细结果保存在results，不把build输出全塞对话。
3. 每Phase完成后启动独立subagent，复核本阶段完整diff、调用者和state owner；另检查UI截图与布局。reviewer不得只看新文件，必须追到生产入口。
4. 只修确认问题，记录拒绝的误报与原因。按本阶段diff做一次code-simplifier检查，受影响检查通过后不反复扩展。
5. 源码contains/固定文件路径测试迁移时保留有效契约，逐步替换为owner行为；不能删断言掩盖回归。
6. 运行门槛分开记录：JVM、module/app compile、APK/native、安装、启动、真实provider、UI、保留数据；静态分析不冒充实际运行。

## 现有审查与范围

research/保存本轮已审阅底稿（主链基于e204dd93，137f57e2增量已复核）。evidence/baseline-inventory.json 为137f57e2文件统计。settings少量扩展表单只初筛，实施对应包时补完整主链，不能直接机械拆。

## 交付与持久状态

state.json记录阶段、包状态、review和验证；每阶段results/phase-N.md记录变更、不变量、实际测试、UI证据、问题处置和遗留项。文档结果必须与代码当前状态一致。用户要求持续推进，遇到可绕过环境限制继续独立工作；不可把尚未完成阶段标为完成。

## Phase0独立review处置

已接受phase-0-plan-review的三项必要修订：52文件有唯一主包；短文件问题明确进入P2/P5；UI场景矩阵补窄屏/大字体与真实runner。未引入接口框架或新权限。生产始终保留startup恢复屏障。

初始90项定点基线9失败已保存evidence/phase-0-first-baseline.json；其中ChatRuntimeOwnershipTest缺少测试Application隔离，重复启动真实Koin。已精准指定基础Application，保留全部原有断言。运行收尾与输入提交断言分别列入P2/P3修复，需独立隔离结果核实，不因基线红而删除断言。

## Phase1独立复核新增的具体后续项

- Home的短Room事务提交后返回前取消窗口已有当前Room实现证据，加入本Phase持久化回归，只将insert+owner标记设为短NonCancellable区间；gate等待和openRoom保持可取消。
- Phase4 Council必须复核openRoom中store.upsertRoom后registerTask/jobsLock挂起时的半提交归属，不能靠把整个openRoom/网络变不可取消或放进全局gate规避。成功返回Ok后的任务不因UI导航取消而删除。

Phase1范围登记校正：ProviderConfigTools.kt 的主处置包明确归 Phase5 provider-auth（配置命令/认证依赖）；Phase1实际范围为Provider表单和纯策略。其原清单的B1/B5混合描述已改为唯一主phase，不以P1表单拆分声称工具命令owner完成。

Phase1回收闭环补充：已将“Council已接管但openRoom未返回Ok时不得删除会话”的保护前移本阶段，由真实peek owner判断，真实Room红回归验证；Phase4继续处理domain task注册半提交，不以待办掩盖Phase1新增风险。

Phase2 G2交接约束：DeepRead已保留先`streamOutput=stream`再做model快照映射的生产顺序；完整RunManager stream→non-stream fallback尚无精确行为fixture，列为Phase4 DeepRead包必须完成的本地行为验证，不能以字段映射或外部无凭据作为豁免理由。Novel完整上下文override、SubAgent首轮/report retry同隔离snapshot需保留既有真实Kernel/Runner回归。

Phase2 F交接约束：fork异常补偿仍沿原FilesManager.deleteChatFiles(copies).join()；该API在appScope启动，未显式传原restore epoch，现仅确认上下文传递缺口，尚未取得恢复后同路径新归属被误删的可达失败。Phase5 backup/restore文件包必须沿真实copy命名/归属与restore路径复核，必要时以失败测试证明后精准传epoch，不能以“已移动Editor”视为此契约已验。

Phase3输入准备的当前源新鲜基线：4类30项中6个已有失败，除了原Submission四项，ChatInputState.setContents的替换草稿清ready imports/拒绝旧late completion两条现有断言也已取得红证据（phase-3-input-baseline）。同属唯一draft/import identity收口，纳入P3，不增加第二状态owner；Crop2与Warning3已绿。Novel storage本次基线为Gradle UP-TO-DATE，归档24类158项缓存绿及输入哈希，不能称本轮重新执行。

后续已在P2设备诊断期间对独立novel-workspace存储模块做新鲜--rerun-tasks基线：24类158总项、157通过/1跳过/0失败；跳过的是需要AMBER_W15_ROOT及真实iOS导出树的跨端runner，本任务不读取兄弟产品仓，不能据此声称跨端验收。fresh-baseline证据替代缓存作为P3对照。


## 用户加速指令与最终收口约束

最新用户要求加速，不持续停留局部测试。Phase5剩余所有职责包已完整冻结；采用一次集中全app回归、正常配置Full/Novel/DeepRead+AndroidTest构建、主要设备路径与独立review，只针对实际失败修正并补受影响检查，不再添加候选或微型测试矩阵。Phase6提前完成原52逐项、短文件职责、模块DAG及保留文件审查，复用各阶段未变代码证据，待Phase5通过直接闭合最终预算/产品关键路径，不重开架构范围。

预算例外：NovelMarkdownWorkspaceViewModel.kt1564行，超过目标64行。独立P6审查已追到唯一UI state、admission、jobs、reload/epoch验收与恢复publication gate；Queries/Interactive/Audit/History/TurnRequests/domain commit已实际分离。剩余拆20–30行片段会割裂同生命周期，凑64行会新增无共同变化原因的协作者，因此按用户“尽量且有质量”保留，最终报告必须明列，不能声称所有单文件<=1500。

此前跨阶段carry的历史约束以各阶段最终报告为准：DeepRead真实Kernel stream→nonstream fallback已于P4六实际用例验收；fork旧补偿恢复epoch误删已于P5真实archive restore复现并显式传旧epoch修复，最终运行结果待集中gate；原placeholder/dead-code候选只登记不删除。

## 最终执行结果

Phase0–6均已complete并逐阶段独立review通过（PASSED_WITH_EXPLICIT_EXTERNAL_GATES）。原52项全部complete_reviewed，最终手写生产1609文件355469行，11个>1000、1个>1500、最大NovelVM1564为已说明的64行内聚例外。正常Full/Novel/DeepRead+test构建及既定真实路径验收完成；最终四UI问题仅三布局文件修复、双宽受影响2+2实际通过且独立看图关闭。最终状态、精确测试边界、APK/hash与外部未验见state.json、results/phase-6.md及phase-6-independent-review.md。没有提交/推送/发布。

提交前统计说明：19个新增源码文件仅移除末尾空行，总物理行据当前文件更新；可执行内容和原构建/行为验收不变，详见commit-preflight.md。
