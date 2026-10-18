# Phase 0 独立计划审查

审查基线：`137f57e2d5c9dc3b924866db2d7b2a369bb78478`，分支 `refactor/modularization-phases-20261018`。审查范围为本 Android 仓库；读取根 AGENTS、当前计划、六份 research 底稿、inventory、后补 production-scope.csv，并对关键短文件依赖、DI 环、UI 验证设施抽样核对当前源码。未运行 Gradle、未修改产品源码、未提交或读取其他产品。

## 判断

总体顺序和授权边界合理：先明确文件/状态边界，再处理聊天状态和运行环，再处理渲染/Novel，最后整理 IO/入口及依赖。现有 owner、锁和 durable 契约的保留要求具体，未发现需要新框架或新增 Gradle 模块才能启动的阻塞。

进入 Phase 1 前需要把下列三项补进执行计划。它们是计划可执行性缺口，不是已复现的产品 bug：

1. **P1：工作包与文件/完成口径仍不够确定。** 新增 `production-scope.csv` 已完整列出 52 个 >1000 行生产文件、20 个 >1500 行文件，解决了原计划未逐项登记的问题；但部分记录仍写 B1/B5、可后做、B2 后续、可保持，尚未确定首个实施阶段与最终处置。尤其 ModelList 被归到 provider 鉴权，Prompt 只落 B6，容易使阶段完成口径失真。按下表明确归属，并把 B 编号统一为 Phase/P 编号。
2. **P1：底稿中已证实的短文件耦合没有独立工作包。** `GenerationRunSession.settings`、`TransformerContext.settings`、`DataSourceModule`、`AmberAgentApp`、`LocalTools` 的职责问题不能仅由 P6“复核小文件”兜住。需要前置分配 owner 和依赖，避免 P6 才发现还要重新改 P2–P5 的核心入口。
3. **P1：阶段 UI 门禁仍是概括标签。** 当前“流式 keys/锚点/布局”等文字无法判断实际跑过哪个入口、状态、尺寸。需要补具体点击流、可复用测试和证据类型；尤其 WebView、系统 Insets、IME 和 Live/Terminal 路径不能用语义文本黄金代替实际视觉证据。

没有发现需要暂停、向用户额外确认或扩大授权的事项。完成以下计划修订后可进入 Phase 1。

## 文件到工作包的必要修订

下面不是要求全部文件都拆；“review 后保留”也是明确处置，但必须说明内聚性、当前消费者与后续变更位置。

| 文件/对象 | 明确归属与处置 | 原因和完成标准 |
|---|---|---|
| `ModelList.kt` | **P1-model-picker**，不再写 B1/B5 鉴权 | 这是选择器/搜索/收藏/排序/模型行/能力标签和推理等级展示；`ModelSelector:129`、`ModelList:487`、`ProviderAccordionModelPicker:953` 是已有自然边界。保留唯一选中 ID、搜索和列表 state。OAuth 登录会话不属于此包。 |
| `SettingProviderConfigPage.kt`、`ProviderRedesignPrimitives.kt` | **P1-provider-forms** 先移纯表单、配置转换/默认地址规则；**P5-provider-auth** 再迁具体 OAuth 协程 owner | 两阶段修改同文件允许，但 P1 结果要列清仍保留的 OAuth 入口，P5 修改不应回拷表单/草稿。草稿继续归 Detail 页面现有入口。 |
| `ProviderConfigTools.kt` | **P1-provider-forms** 仅跟随稳定转换规则；**P5-provider-auth/config-commands** 深审命令/刷新/配置更新 | 不把未复现的 whole-Settings 并发风险当 bug。若命令部分保持内聚可保留，明确理由与 `SettingsAggregator.update` 的唯一持久写入归属。 |
| `DefaultRunKernel.kt`、`ChatGenerationRoundEngine.kt` | **P2-kernel-round-policy**，串行放在 store/lifecycle 稳定之后 | Kernel 先抽 run-scoped duplicate history，再抽已有 fresh/resume 共享审批判定；dispatcher 仍是实际执行 owner。RoundEngine 先分 Generative UI policy/template，再分有明确 attempt 语义的协作器。完整 non-stream complete 路径须有真实 fixture，不能只跑 streaming fake。 |
| `ChatMessageTools.kt` | **P3-chat-tool-presentation**，跟 ChatPage/活动投影包相邻、文件单一归属 | `ThemeTryOnToolCard:190`、tool status/title mapping `:254–477/:687`、`ChatMessageToolStep:855` 是不同变化原因。按主题预览、工具展示投影、通用 capsule/step 分；不迁权限/执行状态 owner，不重画已有徽标/间距。 |
| `SettingModelPage.kt`、`SettingProviderModelSettingsForm.kt` | **P1-provider-forms** 审查并给 `retained_cohesive` 或拆独立 dialog 的结果 | 已在 1500 内且单 Model 草稿内聚，不必为了“全部处理”强拆三份 VM。`Provider override`/独立 prompt sheet 可按实际维护收益提取。 |
| `SettingExperimentalWebMountPage.kt` | **P5-webmount-settings** | 先追当前 `WebMountSessionOwner/Manager/Login`，再拆 login dialog、cookie fallback、OAuth/custom-site editor 与纯卡片。已有 cookie/OAuth revision 和 busy 不能复制。 |
| `SettingMcpPage.kt` | **P5-mcp-settings** | 拆服务器列表、配置 editor、tools 与 import preview；保留已存在的 `McpImportTransaction`。`McpOAuthSection:402` 仍通过现有 MCP owner，不并入新的通用 OAuth 框架。 |
| `SettingSshProfiles.kt` | **P5-ssh-settings**，依赖 P4 Terminal/session 边界 | profile editor/host trust/job poll 不等于 provider OAuth；UI 执行和信任状态继续依赖 TerminalRuntime/SshProfileStore。只因 1050 行不强拆，若保留也需深读后记录。 |
| `PromptPage.kt` | **P5-prompt-settings**，不要只留 P6“按需” | 当前 `ModeInjectionTab:262`、`ModeInjectionEditSheet:527`、`LorebookTab:704`、`LorebookEditSheet:938`、regex editor `:1135` 有明确编辑领域。按 Mode/Lorebook/Regex UI 分文件，保留 PromptVM、import/export 和同一编辑草稿，避免新全局 prompt manager。 |
| `NovelOutcome.kt` | **P6-declarations-review → retained_cohesive** 为默认结论 | 单个 sealed outcome 与其完整兼容 serializer，共 1243 行，不存在新状态 owner。除非 P3 实施已经造成相关维护问题，否则保留共同契约比迁一个大 serializer 更稳。wire 断言必须保留。 |
| `NovelEnums.kt` | **P6-declarations-review**，可保留；若拆只按独立 wire 契约 | Project、Session、Material/Injection 等独立 sealed union 可分文件，但 serializer 与对应类型留近。保持 package、类型、SerialName、未知值 round-trip；不新建兼容委托层。 |
| `DeepReadTemplateRenderer.kt` | **P4-deepread-board → retained_cohesive** 可接受 | 无可变运行状态，纯 markdown/HTML/template/CSS 契约，1279 行；不因声明内容长就扩大重构。保留输入/输出、安全规则测试。 |
| `ChatInputComposers.kt` | **P3-chat-input-page** 审查后可保留内聚 composer | 先处理实际附件生命周期和业务接线，不能只为 1079 行把相互依赖的输入控件拆成透传层。 |

其余 scope 记录的阶段归属可沿用：P1 Intelligent UI/Memory/Home，P2 ChatService，P3 Novel/Markdown/Widget/Input/Timeline，P4 Council/DeepRead/Live/SubAgent/Terminal，P5 Backup/Sync/Route/Automation/Responses/Web。最终 inventory 不能只检查原来 52 个文件；还要统计新建生产文件，避免把大 Kotlin 变成超限模板 JS/HTML。

## 必须补入的小文件耦合工作包与依赖

### P2-generation-contract（跟随 kernel/round policy，先做实际字段审计）

当前源码证据：

- `core/ai/generation/api/.../RunKernel.kt:36–38` 的 `GenerationRunSession` 接完整 Settings。
- `core/ai/transformers/api/.../Transformer.kt:11–15` 的 `TransformerContext` 接完整 Settings。
- 实际 run 调用方是 ChatTurnAgent、NovelWorkspaceRuntime、DeepReadAgentRunManager、SubAgentRunner，不能只迁 chat 后保留另一套 legacy session。
- transformer 实际读取 time reminder、regexes、messageTemplate、MiniApp 配置；Placeholder/OCR/PromptInjection 还把 Settings 向下传递。需沿这些调用追到字段和 owner，不能只用 `GenerationConfig(val settings: Settings)` 换名。

先登记实际字段与生成时快照语义，再决定哪些能按当前配置对象投影成不可变输入。保持 provider 选择、transformer 输入和 request snapshot 同轮一致；复用 `TransformerSettingsSnapshotTest`。若某个宽依赖有实际契约原因，允许明确保留并给证据；不得为了去掉一个 import 创建通用配置镜像和万能服务集合。更改公开输入时一次接完四个生产消费者、受影响模块和测试。

### P2-tool-catalog-local（与现有 P2-tool-catalog 合并）

`LocalTools.kt:34–58` 有 23 个构造依赖，`:141+` 是产品工具目录，`:188+` 是 WebMount 作用域/开关；`feature/tools/api/.../ToolRegistry.kt:33/64/70/189` 同时容纳 metadata、effect、policy、risk。优先复用现有工具族 owner 提供工具集合，让 catalogue 仅组合。不能每个工具建 module，也不能再造工具执行/审批 owner。catalog 真实 foreground/child/debug 工具名、重复名、gate 和 run scope 要验证。

### P2-di-composition 与 P5-startup-restore（明确两个阶段的修改边界）

- `DataSourceModule.kt:103+` 目前混 Settings/Keystore、DB/FTS、MiniApp、kernel/HTTP、sync。P2 可按已有 owner 拆注册块和本次接线，保持 composition root；SQL/HTTP 的构造细节回对应 factory。无需为了同一 Koin 模块名字造接口。
- `AgentRuntimeModule.kt:185–188` 明确依赖环；`ChatSessionResolverImpl.kt:28–32/49/53/84` 实际依赖 ChatService；`ChatTurnAgent` factory 仍注入 ChatService。P2 完成必须同时解除 resolver、projector、turn-agent 对 service 状态的回依赖，不能只删 lazy 注释。
- `AmberAgentApp.kt:82–95` 的顺序是 Koin modules → interrupted archive restore → WorkManager factory，后面才 runtime/Novel 恢复。这是实际恢复屏障。P2 只为对应 DI 接线整理 composition；涉及 startup/restore owner 提取放 **P5-startup-restore**，在恢复会话边界稳定后处理，保留明确顺序，禁止 auto-initializer registry。`SyncRestoreStartupWiringTest` 及真实 interrupted-restore 路径要随之核对。

### P1-home-actions / P3-novel-commit（明确短文件也在阶段内验收）

- `SessionHomeVM.kt:156–169` 直接做全量 Settings 写入与旧头像文件删除；`SessionHomePage.kt:276–339` 直接创建 Council 占位会话、处理失败回收。P1 先把动作交给唯一用例/现有 owner，再拆 Header/Rail/List，保留 restore epoch，不能仅搬 Composable。
- `NovelWorkspaceMaterialActions.kt:23/40/54/63/93`、ChapterRestore、DiscardedChapters 依赖完整 Runtime；Runtime `:102–121` 又被 proposal/author edits 回调提交。P3 先明确窄 committer/author-command 接缝再迁 owner，状态/monitor/CAS 一并迁，避免“短 helper + 旧大 Runtime”原样保留。

## 每阶段最小 UI 门禁

全局比较前提：基线与阶段结果尽量使用同设备、主题、字号和 fixture。普通宽度使用现有 373dp/812dp 设施；横向布局、弹层或长文本容器变化时增加 **320dp + 150% 字体**，高风险弹层再测 200%/短屏。不是要求每阶段跑完整全产品矩阵，而是从对应阶段实际变更中选择必要场景。

| Phase | 必须闭环的生产点击流/状态 | 复用证据及视觉检查 |
|---|---|---|
| 0 | 固定现有首页/聊天/Novel/设置导航基线，登记设备、包 ID、主题、屏幕和字号 | 保存截图路径与采集命令；当前失败/环境缺失单列。 |
| 1 | 首页长标题继续卡→搜索展开/取消；Provider 列表→详情→模型→编辑弹层取消；Memory 子页切换/长候选列表；Intelligent UI 递归 tabs/split→输入联动 metric→提交草稿→滚出返回 | 复用 `HomeCompactLayoutTest` 的 320dp/150% 字体、不重叠/右对齐/卡片高度；`SettingAgentMemoryLayoutTest` 的第 120 行 lazy 滚动；`ProviderModelRowTest`。实际检查长标签、表单宽度、按钮可点和卡片输入保留。 |
| 2 | 聊天审批→停止→切会话→返回；队列/运行/终态与输入状态同步；键盘弹起 dock 收起，旧运行不污染新会话 | `ChatSoftTimelineSmokeTest#softTimelineConversationSwitchAndGlobalTaskDock` 已有截图；`ChatToolApprovalCardNoticeTest`、`ChatMessageToolStatusTest`、`ChatMessageTouchTargetTest` 保留 48dp target/展开锚点/inset 约束。 |
| 3 | Novel 项目→工作区→章节→读/编辑→取消/保存；材料/审核弹层；Chat 附件/编辑/长按发送；Markdown 静态与半 code fence/表格/图片/公式的流式→完成；Widget 三 runtime 展示→流式更新→展开→退回→后台/前台→离开 | Novel Page/Reader/Material/Review 测试、`ChatListNormalAnchorTest`、`ChatTailGrowthHoldTest`、Markdown corpus。核对底部导航/IME 遮挡、横向溢出、段落/表格间距、WebView 高度/空白、实例存活与恰一次销毁。WebView 需要实际设备截图和运行证据。 |
| 4 | Board→历史→DeepRead 缓存/部分失败/重试→返回；Council 空房/长时间线→成员面板；SubAgent dock 展开/关闭→切会话；Terminal 执行/停止/离开返回；Live 结果/保存/悬浮窗隐藏重现 | `RedesignNavigationSmokeTest`、`CouncilTimelineLayoutTest`、`BoardActionSheetLayoutTest`、`SubAgentDockInteractionTest`。Live/Terminal 当前没有等价设备 smoke，按本次改动补最窄回放，检查长内容按钮可达与 process 状态保留。 |
| 5 | Backup 长 WebDAV 目录/加密验证/恢复进度与失败、弹层取消/保存；OAuth 返回原编辑页且迟到不污染新 provider；冷/热 Intent 正确路由和返回链；WebMount handoff→取消→返回；Prompt/MCP/SSH editor 修改/取消与 keyboard | `BackupLayoutTest`、`BackupWebDavDraftTest`、`WebMountSessionHeaderTest`、`WebMountTaskCardTest`、`WebMountParityDeviceTest`、`WebMountLateDialogDeviceTest`。Web UI 至少窄屏/桌面各一次会话切换、编辑附件 round-trip 与草稿保留；实际截图核对顶栏/Insets/IME。 |
| 6 | 按实际变更选择跨域导航、恢复后旧结果拒绝、核心保留数据与关键流程最终回放 | 不把再次通过编译当 UI 验收；结果必须列实际入口/状态、尺寸、截图或布局断言路径、review 发现与处置。 |

现有 Android smoke 使用真实产品 Application 的开关是 `-PuiSmokeTest=true`（`app/build.gradle.kts:145`）；默认 runner 是 plain Application。`AmberAgentUiSmokeTestRunner.kt:16` 启动真实 Koin/产品入口。已有 `UiRefreshDeviceSmokeTest`、`RedesignNavigationSmokeTest`、`ChatSoftTimelineSmokeTest` 用 `uiAutomation.takeScreenshot()` 保存到 app external files，可沿现有路径拉取并视觉 review。

`MarkdownRendererSnapshotTest.kt:108` 是语义树文本黄金，会归一化图片，不能用于证明图片/WebView 高度、实际边距或像素布局。测试存在不等于本次已运行通过。

## 阶段 review 与完成口径

- 每阶段开始时保存该阶段 diff 基点/工作包归属；结束时 reviewer 审整阶段 diff 和生产调用者，不能只审最后一批 worker 新文件。并行工作包不能在同一状态 owner 上各建一套缓存、mutex 或草稿。
- `production-scope.csv` 增加明确 `primary_phase`、`work_package`、`disposition` 即可，不必引入复杂追踪系统；最终状态区分 `refactored`、`retained_cohesive` 与尚未完成。保留结论给一句源码/契约理由。
- 用户授权连续执行完整阶段，不等于授权改变数据格式、权限、默认开关或运行终态语义。计划当前没有此类扩张；保持现有声明。旧 Split scaffold/疑似死代码仅标记，不以清理名义删除。
- 构建/安装/启动、mock gateway、真实 provider、视觉回放和 retained data 分开记录。若环境门槛确实不可达，保留未验收状态与证据，继续其余独立工作，不能把未验证产品行为写成完成。

本次只产出该独立审查文件；产品源码、测试和构建未修改，未执行 Gradle。计划修订由主 agent 合并后再进入实施。

## 修订落地复核

按主 agent 要求，仅核对本轮三项修订及基线记录，不扩大审查。结论：**三项计划缺口已得到执行层面的处理，计划审查通过；Phase 0 构建与实际 UI 基线仍由主 agent 完成并单独记录。**

- `production-scope.csv` 当前恰有 52 条、52 个唯一生产路径，其中 20 条基线超过 1500 行；每条均有 `primary_phase` 和 `packet`。ModelList 已有独立 P1 主包，ChatMessageTools 明确 P3，Prompt/MCP/SSH/WebMount 明确 P5；声明/单一草稿文件明确 P6 内聚性复核。描述列保留旧 B 编号作为底稿，不再作为执行阶段来源，以 `primary_phase/packet` 为准。
- `plan.md` 阶段表已加入 P2 generation/settings projection 与 DI bindings、P3 message-tools、P5 settings/prompt/startup owner；具体范围段明确 LocalTools、TransformerContext、GenerationRunSession。AmberAgentApp 启动恢复屏障明确归 P5，保留现有 startup 顺序。P6 不再承担临时状态机重写。
- UI 矩阵已按 Phase 0–6 列出入口、状态、布局关注点与证据设施，并列 320dp、fontScale 1.5、横/宽布局和真实 runner；每阶段要求独立 subagent 看当期截图。后续结果按实际设备配置记录即可，不能把未运行矩阵当验收通过。
- `evidence/phase-0-first-baseline.json` 记录初始 90 项/9 失败：4 个输入提交行为失败、1 个 dispatch 终态先于 runner cleanup 的断言失败、4 个重复启动 Koin 环境异常。当前 Git diff 中 `ChatRuntimeOwnershipTest` 仅增加 `@Config(application = Application::class)`，全部原有断言保留，没有产品改动。该隔离修复后的实际结果尚待主 agent 运行；P2/P3 仍须独立复核真实失败，不能把原始 9 项全称为产品缺陷，也不能因基线红而跳过。

本次复核没有运行 Gradle或查看新增 UI 画面，不对构建/安装/视觉基线作通过结论；未改其他文件。
