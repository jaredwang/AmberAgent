# Phase 1 独立实现复核

本次为独立只读审查；只写本文，不编辑生产代码、不运行 Gradle。以当前工作区对照 `137f57e2`，审查计划、P1 各包结果、settings/rendering 研究底稿及实际生产调用者。其它 agent 正在实现/验证，以下结论对应复核时源码。

最终独立判定：**PASSED_WITH_EXPLICIT_EXTERNAL_GATES。Phase 1生产接线、状态owner和本轮确认问题已闭环；三项最终定点证据均已核对，无新增真实缺陷。已独立目视本期Home/声明卡片/Profile/Memory/Provider配置/模型选择空态；真实外部provider、账号及有数据模型设备态边界见文末，未伪称通过。**

## 已确认问题及修复复核

以下R1–R4保留发现时证据，描述的是修复前实现；目前均已精准修改。ModelMenuInteractionTest 11项已在`phase-1-jvm-summary.json`记录通过。

### P1-R1：被过滤的前置 provider 使 badge 跳转位置少算一个 Lazy item

位置：`ModelList.kt:237-250` 对照 `404-417`。

`providerPositions` 根据 `hasProviderGroup` 决定是否计 group-gap；实际 LazyColumn 根据原始 `providerIndex > 0` 发 group-gap。搜索仅命中后置 provider，或首 provider 的当前模型类型/隐藏规则使模型集合为空时，两个谓词不同。

直接按生产发项规则计算的确定例子：A 无匹配，B 两个模型，C 一个模型，无收藏。实际 items 为 `[gap:B, header:B, model:1, model:2, gap:C, header:C, model:3]`；真实 header 坐标 B=1、C=5，计算结果 B=0、C=4。点击 badge 会落到间隔/前一模型，follow 也使用错误组边界。`selectedModelPosition` 当前使用 providerIndex，与实际 emission 一致，不应只修 providerPositions 而让其它坐标再分裂。

建议精准统一发项与坐标的 gap 规则，不引入列表状态层或通用 registry；以“首个可见组之前存在过滤组”的真实列表行为覆盖。

当前修复：providerPositions改为mapIndexedNotNull，计gap与实际Lazy emission/selectedModelPosition使用相同原providerIndex谓词。独立核对没有改变现有间距，也没有只修一套坐标。

### P1-R2：badge follow 协程捕获首次 provider 坐标与 providers

位置：`ModelList.kt:467-485`。

`LaunchedEffect(lazyListState)` 只以稳定的 LazyListState 为 key，collector 闭包捕获首次 `providerPositions`、`providers`。搜索、折叠、收藏变更都可使其后续重组得到新坐标，但该 effect 不重启；随后滚动仍按旧组坐标和旧 provider 顺序推动横向 badge。此处是普通非 state 局部 val，不会自动读取最新值。

精准方向：effect key 纳入实际消费的坐标/provider列表，或使用既有 Compose 最新值机制。前者更直接；不要新增重复 owner。

当前修复：LaunchedEffect keys补providerPositions/providers；重启collector会读取当期坐标，原debounce、listState与滚动入口保持。

### P1-R3：provider 坐标的 remember 遗漏 currentModel

位置：`ModelList.kt:221-227`、`244-248`。

`currentModel` 决定默认哪些组展开，却不在 `providerPositions` 的 remember keys 中。组件在 provider/搜索/收藏未变时收到新的 currentModel，实际 Lazy emission 立即采用新默认展开，而坐标缓存保留旧高度。常见“点选择后立即关 sheet”的路径不等于 currentModel 永远不会更新；公开组件的外部 currentModel 参数与实际 emission 已明确允许该更新。

精准方向：加入缺失 key，配合 R1/R2 使用相同真实发项契约验证。不需要扩大状态机或改变选中项 owner。

当前修复：currentModel已加入providerPositions的remember keys；没有增加选中项状态副本。

### P1-R4：badge follow 不把组标题本身算在当前组

位置：`ModelList.kt:474-475`。

查找使用 `index > headerPosition`。当某个后置 provider 的 header 恰好成为首个可见 item，collector 仍选前一组；若横向列表很长，会停在前一 provider 附近。主列表进入该组第一条模型才更新。

精准方向：组范围应包含 header 的下界。此为原有可达边界问题，风险低于 R1/R2，可与同一坐标逻辑修复。不要为此重写滚动结构。

当前修复：header查找改为index >= position；第一项index=0仍走原badge归零，后置组header自身已归入当前组。

四项真实Compose回归已在生产旧逻辑下取得红证据：`evidence/phase-1-corrected-regressions-red.json`。独立读取该证据与测试实现：过滤首组的header物理位置实测56/64px差8px；currentModel变化后目标header不可见；搜索/折叠后的badge跟随超时。测试现在以生产LazyColumn和真实badge/header bounds为oracle，并直接滚动校准目标坐标；没有继续把VerticalScrollAxisRange.value误当item index。早期`phase-1-regressions-red.json`中`expected 2 but 8542`等原始scroll-range数值不是有效视觉坐标证据，以纠正后的oracle为准。修复后11项通过的汇总已核对，设备有数据态未冒称通过。

### P1-R5：Home durable insert返回取消与占位清理

当前修复已复核：已取得restore gate后，仅在NonCancellable内执行短Room insert和inserted标记；gate等待和openRoom保持可取消；随后ensureActive阻止已取消UI继续创建；finally在已释放gate后执行cleanup，避免嵌套gate死锁。NonCancellable保留SyncRestoreWriteEpoch，Repository delete durable边界再次校验同一epoch。

`HomeCouncilRoomPersistenceTest`新增实际repository insert后、IO返回原dispatcher前取消creation Job的fixture，明确断言协程仍取消、openRoom未调用、DB无空row；并保留普通取消后清理、restore adopted row保留。调用边界确实对应当前Room 2.8.4的transaction executor→cancellable continuation返回路径；不是凭假想扩大不可取消范围。未把openRoom或网络放入NonCancellable/gate。

### P1-R6：Home cleanup初检之后restore导致原Err被替换

复现链：helper isWriteAllowed为true后，Repository加载会话期间restore，durable delete按旧epoch抛SyncRestoreWriteRejectedException；原finally将它作为CancellationException重抛，覆盖openRoom Err/原异常，生产UI因此走取消路径而丢失原Err反馈。

当前修复只在cleanup单独catch SyncRestoreWriteRejectedException并作为stale no-op，普通CancellationException仍重抛，普通清理错误仍记录日志。新fixture在deletePlaceholder回调内触发restore并最终进入durable gate，断言仍返回同一Err且未删除；纠正红证据中该fixture确实因SyncRestoreWriteRejectedException失败，修复没有放宽epoch约束。当前源码与fixture已定点复核，最终Home XML通过证据见R7。

### P1-R7：取消发生在Council接管后，Home不能再删除该行

这是新增取消清理需要排除的明确边界：Manager先store.upsertRoom，同步发布slot.flow.value，后续registerTask/jobsLock仍可能挂起；若取消发生在返回Ok前，opened仍false，原cleanup会删掉已由Council接管的conversation。

当前最小修复：helper增加明确hasRoom查询；生产传roomManager.peekRoom(id)!=null；只有inserted、未返回Ok、尚无room owner三者同时满足才清理。没有引入通用接口或重写Council事务。Manager.peekRoom直接委托Repository.peekRoom；restore使active slot失效时，旧epoch仍禁止删除恢复数据。

新增真实Room fixture使用CouncilRoomRepository.upsertRoom→挂起→取消，hasRoom走真实peek；取消后conversation仍存在，flush后council_state仍可从DAO读取。独立读取`evidence/phase-1-final-fix-check/`三份JUnit XML：HomeCreation 7、HomePersistence 4、KoinGraph 1均failures=0/errors=0；对应`results/phase-1-final-fix-build.log`为BUILD SUCCESSFUL，包含fullDebug相关编译与AndroidTest APK组装。这些结果证明该定点范围；不外推为全部P1设备/provider已验。

## 已核对的生产链路与 owner

| 范围 | 追踪入口与结果 |
|---|---|
| Intelligent UI 协议/解析/状态 | `GenerativeWidgetParser` 继续调用 `IntelligentUiParser`；Card 仍按 cardKey 从唯一 process store 取得 state；输入、tab、decision、study 都写同一 CardState；没有第二份草稿或 runtime。 |
| 80 类 renderer | `IntelligentUiCard` → 单一 `IntelligentUiComponentView` → 分类 renderer；所有递归布局继续传同一 document/state/action callback；study 分支继续现有 renderer。没有新的 remember/effect/lifecycle owner。 |
| Memory | RouteActivity 原入口 → `SettingAgentMemoryPage` → 子页/Dialogs；条件 flow 订阅保持；route 持编辑/删除草稿，VM 持 mutation，Repository 持 CAS/durable。失败留草稿、保存/删除中禁用、成功才关闭、冲突更新 revision 的调用完整。Library 保留唯一 LazyColumn/listState/openDocId/BackHandler。 |
| Provider | Detail 继续以 provider.id 持唯一 draft，Config 将输入回 `onInternalChange`，显式保存和 OAuth commit 回原 onEdit；模型 override 仍有独立确认/取消边界。转换与 endpoint 默认策略来自新纯策略文件。Google import launcher 仍在 endpoint Lazy item 的 composition 中，按钮仅回调，未挪到纯表单里持 Context/IO。 |
| ModelList/Selector | 所有原公开 selector/accordion/段控签名保留；sheet 生命周期仍由 Selector 持有；favorite 写回 SettingsAggregator；选中 id 仍由调用者持有。只有上述坐标/effect 问题需修。 |
| Home | Header/List/Rail/Row 进入原首页 LazyColumn，search/HDR/focus 状态仍在页面；Council 创建进 VM 对应 use case，Continue durable存在性检查进入动作 owner；导航仍回页面执行。Home 不再承担 Profile 全量 Settings 更新。 |
| Profile | ProfilePage → ProfileVM → SettingsAggregator 字段级 update；只改 nickname/avatar，避免旧整份 Settings 快照覆盖其它字段。原 avatar 文件回收仍使用 FilesManager；Koin 注册 ProfileVM 及 HomeCouncilRoomCreator 的接线已在 ViewModelModule。 |

HomeCouncil现已切到最终短insert/mark/finally/epoch实现，上述R5/R6记录独立定点复核。已成功返回Ok的房间不因UI未导航而被当作空占位删除；本报告仅复核Home gate/取消/restore范围，不扩大到Phase4 Council内部。

## 独立等价性核查

除阅读调用链外，直接从 `git show 137f57e2:<path>` 获取原文件，分别与当前拆出文件拼合后的非空源码行多集比较；仅归一 package/import 与 private/internal。结果：

- IntelligentUiComponents + 分类renderer/style：全部源码行零增删。
- IntelligentUiProtocol + Parser/CardState/StateStore：全部源码行零增删。

该结果结合 dispatcher、递归调用和状态写入口深读，支持迁移未改布局表达式、输入/action语义和 state owner；它不证明像素视觉或设备交互已通过。Memory 的独立抽查覆盖 route/dialog/Library 和投影，既有逐声明等价报告与当前源码一致，未发现漏接回调。

## 不成立或暂不能下结论的风险

- renderer 函数为跨文件调用从 private 改 internal，不构成新增 public API 或双 owner；不要求为每类建立接口/DI。
- Provider 的 Google launcher 从字段函数移到同一个 endpoint Lazy item，局部函数层级变化不等于生命周期被移动到另一个页面；未确认生命周期回归。Auth 的进一步 owner 整理仍归 Phase 5。
- Profile 回收旧头像仍与原行为一致；本次没有凭空增加回收重试/兼容机制的依据。
- 旧 reorderable attachment、未用 helper 疑似死代码不能据本次拆分授权删除；无需为清行数处理。
- `IntelligentUiCard` 长 reset/submit labels 的横排footer存在源码可见的压缩风险，但当前还没有本期渲染断言/截图证明具体裁切，不能当作已确认视觉缺陷。主 agent 已有320dp/fontScale1.5定点设备场景。

## 本期独立UI目视与有限维护复核

本轮按code-simplifier及Kotlin/Compose条目对当前Phase1 diff做一次有限维护检查：生产入口、owner、effect keys、取消/恢复边界、跨文件可见性、回调与副作用容易定位；未发现需要额外新增架构/抽象的障碍。不删除疑似死代码，不追加样式统一。报告中已有问题的定点修复复核属于同一次Phase审阅闭环。

独立打开并目视`evidence/phase-1-ui/second-run/`以下本期原始PNG，结合semantics与源码核对：

| 本期图 | 目视与状态归属结论 | 证据限制 |
|---|---|---|
| ui-refresh-smoke/09-home-compact-light、10-home-search-expanded、11-home-search-collapsed | Home顶栏、Continue、五入口、会话行左右对齐/边距一致；长会话标题正常换行，feature长label按既有单行ellipsis；展开搜索后输入及唯一结果在IME上方，收起后列表回归。new conversation浮动按钮在既有底部覆盖区域，列表仍可滚动；未见新增错位。 | home-compact-run.txt明确OK(1 test)。这是紧凑竖屏路径，不能代表平板/全部字号。 |
| modularization-phase-1/02-action-footer-320dp-font150 | FlowRow使长reset/submit各自占完整行，标签完整、图标与文字同行，两块不重叠，底部有明确间隔；05/06短按钮仍同行，未无故改变宽屏结构。 | second-run日志仍是旧raw overflow断言；最新glyph-bound断言须重跑，不能据旧失败当布局未修，也不能据图片宣称完整用例已绿。 |
| 01-recursive-split-numeric、04-recycled-card-restored | 左输入与右metric无重叠；42/175及6/25与同一CardState绑定一致，数字后缀和底部按钮可见，tabs边界清楚。 | 对应numeric与recycle设备场景已在second-run日志通过；卡片来源是生产renderer fixture，不是provider生成验收。 |
| 05-decision-selected | 两项边框/选中圆点、长标签换行、建议badge布局合理，无相邻重叠；selected来自同一CardState，只有显式submit回callback。 | 更新后的标题glyph断言未重跑，旧hasVisualOverflow不能当作裁切证据。 |
| 06-study-answer-progress | 选项正文、正确反馈、1/1与100%进度和底部按钮均可读；state仍归CardState，答题不自动发送。旧图A/B圆标贴底问题经prefix显式11sp行高精准修复。 | 最终`acceptance-run`图已独立目视：A/B完整居中；实际glyph right27.954544在29px node内、line bottom58在58px node内，无ellipsis；study单case已OK。 |
| manual/profile | 头像/昵称居中，编辑图标与昵称同排，统计五列和图表/图例无重叠；昵称/头像仅经ProfileVM字段级写入SettingsAggregator，没有复制Home业务状态。 | 本期只验证展示，未编辑真实昵称或头像。 |
| manual/memory-overview、library、recall、worker | 子页左边距、分组、开关对齐、长说明换行正常，Library是同一LazyColumn，底部内容允许滚动；未发现需要新增布局结构的证据。 | 当前是空数据，候选/topic/档案非空场景仍需定点测试与后续适用证据。 |
| manual/memory-editor-ime | 真实产品route中的草稿及分类可见，Cancel/Save在键盘上方，未被IME遮挡；草稿仍归route，mutation/CAS归原VM/Repository。 | 手工取消后没有保存产品数据；保存失败/禁用由fixture及重测证明。 |
| 07-memory-failed-save、08-memory-saving-disabled | second-run旧图为淡入浅色帧，未被判为生产透明度bug；最终`acceptance-run`稳定图已独立目视，错误/草稿、saving、分类和底部按钮清晰。失败帧Save/Cancel有正常可用对比，saving帧输入/开关/按钮正确变灰。 | 最终两个device case OK，保留真实失败草稿、重试不dismiss、保存禁用输入/Save/Cancel等断言；fixture配色不冒充真实产品主题，产品route另有manual IME图。 |
| manual/memory-compaction | 旧图70%%/85%%为真实文案问题，六语言该key精准改literal单%且formatted=false；最终acceptance PNG独立目视为70%/85%，对应XML全文也无%%；其它阈值/保护行对齐不变。 | 实际设备显示验证使用英文；其余五语言资源修改同一key，未声称逐语言设备截图验收。 |
| manual/providers、provider-openai-form | Provider列表图标/名称/模型数量、ONLINE/DISABLED两组对齐一致；OpenAI空key配置的identity、协议胶囊、API Key/OAuth、秘密字段和endpoint对齐正常，scroll容器允许下方表单继续滚动；没有因拆分新增嵌套滚动/双草稿。空key明确显示Not connected。 | 没有编辑key/发起OAuth；没有打开会自动listModels联网的Provider Models tab，不能把ONLINE分组或配置展示当网络连接成功。 |
| manual/models-prompts、model-picker | 原真实设置入口打开sheet；标题、说明、搜索与空态纵向间距清楚，底部sheet没有错位；空态完整可读，背后页面的长任务label按既有ellipsis。 | 本机可用认证模型为空；这只证明设备空态，不能声称有数据选择/搜索/收藏/badge设备验收。有数据坐标与交互由生产ModelList的Compose ModelMenuInteractionTest 11项覆盖，`phase-1-jvm-summary.json`明确记录无失败；两种证据分开保留。 |

TextLayout oracle已独立核对：新helper检查didExceedMaxLines、每行无ellipsis、line left/right/bottom在真实node内。旧日志的decision标题node宽550px、glyph right548.5px，而multiParagraph保留766px约束；其raw didOverflowWidth=true不是实际glyph裁切，不应为这个flag改生产宽度/字号。新helper并未移除真实内容边界和ellipsis断言。

Study圆标当前源码只给prefix Text补`lineHeight=11.sp`，22dp圆形、fontSize、选项/答题state和其余布局未动；静态核对属于问题对应的最窄修复。最终A/B glyph/截图与学习case重跑均已核对，没有追加另一轮UI重构。

## 最终三项定点收尾证据

本期已取得上述产品图；系统launcher图与Phase0图未被计入。最后三项已按真实当前证据关闭：

- `acceptance-run/study-run.txt`：studyAnswerUsesTheCardOwnerAndUpdatesVisibleProgressWithoutSending，OK(1 test)，6.936s；独立打开新06图，A/B字形完整居中。`layout-41.txt`/`layout-42.txt`给出真实line bounds与无ellipsis，raw overflow仍是paragraph可用宽度问题，没有被误判。最后生产变化仅prefix行高，定点重跑该case覆盖受影响路径，未为凑数扩大全suite重测。
- `acceptance-run/memory-run.txt`：两个Memory device case均OK，6.874s；独立打开新07/08稳定图，未见裁切、重叠或淡入证据不足问题。既有错误、草稿、重试、禁用等行为断言仍在。
- `acceptance-run/manual/memory-compaction-single-percent.png`与`.xml`：独立目视和解析XML确认Defaults显示70%/85%且无%%，资源修复进入实际APK。

已读取`acceptance-run/evidence-provenance.json`：06与A/B layout的device_epoch晚于本次study_start；07/08晚于本次memory_start，均被标为当轮证据。acceptance目录内01–05等较早文件没有被误当本次study/memory重跑产物。

未发现新的真实问题。该Phase独立review通过，可以进入下一阶段；本报告不是全目标Phase0–6完成声明。

明确保留的覆盖边界：Provider真实Models tab/listModels/OAuth未执行，有数据模型列表没有设备截图，使用生产Compose11项交互证据；Profile真实头像/昵称修改未操作；真实provider生成没有验收。上述范围不能被样例renderer截图、编译或空态替代。

本次问题均沿实际生产发项/协程/restore durable边界或当期截图确认，修复未增加架构层、重复owner、重做UI或假想防御。独立审阅和一次有限维护检查闭环，不追加新review范围。
