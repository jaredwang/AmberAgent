# Phase5 独立审查

状态：**PASSED_WITH_EXPLICIT_EXTERNAL_GATES**。

P5最终生产包、集中失败类修复及最后四个真实大字UI问题已完成独立审查。指定源码入口、唯一state owner、取消/restore/持久化与本阶段设备门槛闭合；未发现本次范围内未关闭的confirmed finding。最终Settings修后411与320/font1.5各2项实际绿，六张指定修后PNG已逐张亲看，详见第12节。全app历史500类3178项中的22失败通过限定cohort58项及History4实际复验关闭，14跳过如实保留；没有重跑或虚报整3178全绿。正常配置三产品最终APK已构建，Full已安装。真实账号、外部服务、系统chooser/SAF、物理/OEM及跨端等仍按第8节明确保留。

本 reviewer 只写本报告；未改生产/测试、未运行Gradle、设备、server或浏览器、未读其他产品。复用P6提前收口与W-react正式报告，新增两个只读child分别核Archive/MCP/Prompt与Provider Auth/Catalog/Apply；Startup/DI按root指令直接复用已完成审查，不重复展开。Backup session/UI与SSH/WebMount最终freeze后由本reviewer补实际源码/调用，未把其实施暂态算作bug。

## 当前门槛

| 范围 | 当前结论 | 实际证据与边界 |
| --- | --- | --- |
| Archive/epoch、P-auth/Catalog/Apply、MCP/Prompt | source/实际持久caller及受影响行为关闭 | Archive30、OAuth/Catalog/MCP及Prompt有效断言实际执行；真实账号仍外部门槛 |
| Backup VM/Session/Google/Page/UI | 唯一source/verification/completion及Cleanup5关闭 | 实际Room/files五项；设备仅预览/launcher取消，未执行用户数据restore |
| SSH/WebMount settings | session/原Store/Runtime/LoginController/installer链闭合 | 双宽设备、installer2及真实AndroidWebView17行为；不代表真实SSH/backend账号验收 |
| Responses/platform/settings模块 | fresh51类352项0fail/0error/0skip | 与app gate分开，不重叠累加 |
| W-react | PASSED_WITH_EXPLICIT_EXTERNAL_GATES | 正式包报告与指定六张浏览器PNG已独立审，不重复扩大 |
| Route/A-platform/W-bridge/Startup | source与本轮指定运行门槛关闭 | Route双宽各3、恢复/入口/平台行为证据复用；权限/外部账号边界保留 |
| 全app及22项修复cohort | 原3178中的22已由受影响复验关闭；14skip保留 | cohort58pass＋History4pass，未重跑全3178 |
| 最后四个Settings UI问题 | 原320图实证后已最窄修复 | 修后411/320各2green，三张指定PNG每宽亲看，未改状态/字号/主题 |
| 整体Phase5 | **PASSED_WITH_EXPLICIT_EXTERNAL_GATES** | 无本次已确认但未关闭问题；外部未验项见第8节 |

## 1. Archive与旧fork补偿

真实链是Editor→Store/FilesManager与SyncArchiveManager→Codec/PayloadExporter/FileRestoreJournal，后者默认实例被生产Manager实际消费，不收整个Manager或复制restore state。freeze `P5-backup-domain-production-snapshot.json`10/10匹配。

`restoreTables`仍在同一SQLite事务替换表、canonicalize ownership并写同token DB marker；journal不另开事务，也不以文本phase单独证明commit。DB未提交失败回滚旧files；DB已提交后gate标记，再做secrets/managed files/FTS/settings/journal finish，后置失败保新DB/files与journal并抛partial-commit。startup仍按同token marker选择保新数据/FTS重建或未提交rollback。导出表与artifact locators同一个read snapshot，外部files原本不与之形成原子快照；没有因拆分伪造更强保证。

旧fork失败清理在既有captured restore context取epoch，catch保原NonCancellable、已落conversation保护与join，只新增显式epoch参数传进独立appScope删除。FilesManager实际unlink与managed row仍过原同一writer gate；没有重新捕获新epoch、额外引用扫描或UUID版本机制。该修复针对root归档的真实archive接管copy后旧fork补偿误删红，现Archive30实际绿；源码与运行分别核对。

## 2. Provider Auth、Catalog与Apply

freeze `P5-provider-auth-final-hashes.json`15项当前全匹配。具体Codex/Gemini client继续写原加密AuthStore，无平行token cache。跨client按provider共享generation锁及refresh mutex；logout推进generation与clear在同锁，authorization开始/成功推进generation，HTTP在锁外，保存前核捕获generation。旧失败路径不clear/save新会话。Gemini两个onboarding写分支通过同generation校验，只把project/tier并入当前token，保旋转凭据；authorize返回前再核generation。

真实foreground工具仍经原factory进入具体Catalog命令。fetch在Settings写锁外，回到Aggregator最新persisted快照内比较请求身份，endpoint/凭据/authMode/Google路由变化拒绝旧目录，展示名/enabled/models不误算网络身份。Apply的目标解析/歧义/名称冲突/create/修改在既有writer的最新快照内执行；明确业务拒绝在实际write前抛ApplyRejected，IO和取消不被业务catch吞掉。原风险/审批metadata、redaction和唯一Settings writer保留。

Console迁出仍保原provider/effect keys、busy/generation、autoStart、latest-provider检查与onCommit，未重建P1 provider草稿。root旧实际Codex3/Gemini5迟到token和Catalog2红作为需求证据；最终app修后回归已见真实XML绿，真实OAuth账号/系统browser回跳继续外部gate。

## 3. MCP与Prompt

freeze `P5-mcp-prompt-implementation.sha256`21/21匹配。Settings Page→SettingVM.importMcpServers、SkillDetailVM与McpManagementTools三个实际publisher均await同一`publishMcpImport`，最终names在Aggregator原writer的latest transform内整批检查，冲突在writeSettings前中止；applied audit只在awaited publish后。preflight取消直接传播，不访问后续candidate或写最终outcome，temporary client原finally close保留。网络不进Settings写锁，无第二个事务锁。

Prompt Page所有save/toggle/delete/move/import回到真实VM按ID命令，从writer内最新collection计算，未把渲染时旧整表覆盖回去。move按实际UUID解析最新下标，已缺失ID不误移其他项；单对象import与原父/nested editor局部确认、父取消合同保留。MCP的import/busy/transaction归完整Dialog，basic/tools归同EditState；没有增加第二持久collection或将整VM绑传叶子。根已归档原MCP3/Prompt2实际红，修后集中行为与第12节有效双宽设备已关闭。

## 4. Backup最终session/UI

当前生产VM直接构造`BackupRestoreSession`和`BackupGoogleConnection`，Page直接消费两个owner，无旧恢复方法转发facade。VM保provider上传/下载/列表/冲突/delete与导出，session持唯一Local外部URI+可选retained copy、Google downloaded file+revision或Provider file+ID，以及preview/verification/applying/completed；旧并行source布尔/文件/request字段已移除。

真实路径为Local launcher→session.inspect/verify，Google/WebDAV/Folder完成真实下载/校验后→acceptDownloaded→同一verify/apply。inspect/verify返回对照捕获source实例，失效verification/stage及自有temp清理，不写后来source。首次Local verify读URI，失败重试用同会话retained encrypted file做fresh verify，不复用消费后的plaintext或猜外部路径。apply使用最终确认preserve toggles和捕获source/revision，原ArchiveManager仍独占verification消费/restore gate/journal；metadata写完→清自有encrypted source→sticky completed。partial/metadata失败保源、丢verification、退回fresh verify；外部用户URI始终不删。

Page以completed显示重启Dialog，不依赖Compose是否观察过Loading，也不把普通verify/upload Success当恢复完成。preview applying与passphrase verifying禁止dismiss/重复提交，原IME/scroll/preserve disabled逻辑保留。WebDAV dirty草稿与SAF/launcher仍在唯一route，各pane/dialog仅收明确数据/callback。GoogleConnection归授权PendingIntent/inFlight/session/待授权export，取消仍清该待上传请求；metadata后才延续原upload。原Google/WebDAV/Folder transport、sidecar/conflict未复制到session。

已读真实Cleanup5（两原清理+source切换真实apply+partial重新verify+未观察Loading的真实Page完成）及其Room/files断言，测试直接迁新owner、不是fake UiState成功；原初审只读源码；现五项实际绿已核，仍不称设备用户数据apply通过。Source A cancel→B apply是顺序场景，不冒称所有并发provider晚结果已验。

## 5. SSH/WebMount最终settings

`P5-ssh-webmount-implementation-hashes.json`14条（13生产+fixture）当前匹配。Sandbox→SettingSshProfilesSection→composition session→原SshProfileStore/TerminalRuntime真实连接；原Store Flow直接lifecycle收集，不镜像durable profile。session只持选择/editor/trust/delete/command与实际job snapshot，jobId-keyed effect轮询真实readJob，composition离开取消轮询不代替remote job owner。Stop等实际Runtime，trust仍传完整probe.profile+fingerprint到原CAS，credentialRevision/mutationMutex/先pin后credentials路径未改。Editor秘密普通request-keyed remember，原保存/取消清理顺序保留。

无参WebMount route仍由实际AppNavigationEntries调用，只在route Koin integration；session直接暴露原Manager/Registry Flow，协调页面target/busy/cookie与OAuth revision及具体命令。SiteCard不接服务全对象，LoginDialog仍唯一真实LoginController的start/destroy、WebView generation key与SecureOn。done/import只有detector SignedIn继续；有station时仍真实probe确认，dismiss cookie推断与成功保存顺序沿原合同。global关同步清eval，重开不恢复eval，任务重开仍用原SessionOwner sessionId。Pool/SessionHandle/JS bootstrap/domain OAuth owner均不因本包再造。

Slash installer的存在检查与append现在都在Aggregator原latest transform内，忽略大小写且保用户content，追加模板保持末尾newline；不加按钮锁或新registry。必须如实区分：原installer两项曾在初始flow等待失败，未到竞态assert，不能写成实际业务红；root依据已确认重复入口/check-write合同批准直接修后统一验收。新fixture保真实DataStore barrier、一true一false/durable一条/用户内容断言，installer2现已实际绿。31项body/token parity仅支持迁移对照，不能代替像素。

## 6. 已审稳定包与Startup复用

Responses facade/Encoder/Decoder/Transport的每流唯一reconciler、一次重连、cursor-before-emit、实际EventSource finally取消；Route冷热OAuth-first/唯一backStack/consumed/share receipt；A-platform唯一OperationOwner与fresh同root action；Bridge唯一manifest/bootstrap/realm/ref/RPC均复用`P6-final-boundary-audit.md`第6节完成的独立source结论，不重复全dirty diff。

Startup复用P6已完成结论：startKoin内同步interrupted-files恢复早于WorkManager与eager consumers、先注册NovelRestoreBridge；runtime recover→chat replay→complete，失败fail，取消重抛；JS后置best effort；BackgroundSchedulers维持原process scope、Standalone条件、延迟解析与lifecycle owner；Channel常量/consumer按原实际配套，未新建startup registry或权限。

W-react继续以`P5-web-react-independent-review.md`为包级正式结论：真实受控浏览器与六张指定PNG已独立亲看；REST/SSE/POST/附件归属修复闭合。typecheck、SPA构建与fmt原20未改文件失败、旧Cancel edit locator错误/修正重跑区别均保留，不以本P5汇总抹掉历史事实。

## 7. 已到实际执行证据

独立解析`evidence/phase-5-complete-responses-and-platform-modules/_execution.json`与全部fresh XML：**51类352项、0failure/0error/0skip，exit0**；ai33类237、settings14类103、tools-api4类12。模块组覆盖实际Responses/codec/transport/settings/platform API合同，**不覆盖app内新OAuthSession/ProviderConfigTools/BackupSession/MCP/Prompt/Startup/Koin或设备**。不得用352替代后续app gate，也不与重叠补跑重复相加。

在最初整包source审查时，已独立重算`evidence/phase-5-complete-source-checkpoint.json`的**2803项输入SHA256，零缺失、零漂移**。它确认当时审查对应root整包freeze；其后第9节指定修复是新的有限增量，最终应换用新freeze，不能将旧2803零漂移写作修后制品已经对应。后续APK及设备仍须实际证据，不能仅凭source hashes替代执行。

Bridge Node/Chrome与W-react浏览器执行复用其正式报告，均有本地受控边界，不能推成Android WebView/外部站点账号已验。后续整app/cohort与最终APK实际证据分别见第9、11、12节，不将早期模块绿冒充完整产品绿。

## 8. 最终结论与外部边界

本阶段已完成源码、真实生产入口、行为、构建/安装与指定当期像素独立核收。第12节四个真实UI问题已闭，无剩余本次confirmed finding；不新增无证据防御或架构工作。scope/预算与NovelVM1564例外由P6独立报告收口，未以通过为由声称所有文件≤1500。

真实provider/OAuth账号或验证码、外部Google/WebDAV/SSH主机与站点、系统SAF/chooser/camera/新增权限、物理/OEM、真实性能/跨端及实际Android Web backend等未覆盖边界明确保留。备份设备fixture拦截ActivityResult intents，证明dispatch/cancel，不是系统文件选择UI或用户全量restore验收；真实Room/files/archive事务证据与设备预览分开。offscreen WebView17是实际Android行为，不是像素验收。保留14skip及History fixture不覆盖JSON preview SQL的界限。以上外部项不伪称已验，不阻塞本次已授权模块化范围的交付。

## 9. 全app历史22失败：有限增量与关闭事实

已独立解析`phase-5-final-whole-app-normal-config`全部500类XML：3178项、22failure、0error、14skip，实际3142pass。P5新增主链在这批有实际绿，但不能因此称整task通过。之前`phase-5-complete-app-behavior-corrected-fixture`只有9类88项0fail1skip且exit1；Prompt扩展fixture误join长期stateIn observer的挂起由root实际jcmd诊断，不是产品命令deadlock，也不能将局部87pass写作整轮通过。

只对已失败类复验的`phase-5-concentrated-failure-repairs`实际11类62项：58pass、4failure、0error、0skip。唯一四红为ConversationHistoryBudgetTest，异常均为真实`no such function: json_extract`，栈在Robolectric ShadowNativeSQLiteConnection→ConversationDAO_Impl.getConversationSummaryById。新增summary precondition直接抛出，四个Tool预算/returnedChars/truncated/usedChars断言仍未到达；该结果证明fixture SQLite缺JSON1的前置，**既不是预算修后红，也不是History绿**。后续仅修该已证fixture前置，保留全部有效预算/授权assert。

本轮增量源码review只限root已指定的原失败：Memory2、WebDAV/SAF4、Regex7、MiniApp2、History4；Cot2/Reminder1为locale fixture，不变产品资源/语言策略。Memory/Backup与Regex/History由两个独立只读child定点完成，未确认修复引入的新问题，不新增候选矩阵或架构scope。

- **Memory事务**：真实DI给Repository同一AppDatabase/DAO；具体applyDreamMerge/applyDreamSupersede先取原restore writer、再用原Room withTransaction包完整target/replacement/archives/events，内部DAO mutation避免嵌套拿不可重入gate。仅事务成功后把真实新revision发布到Applier records map，失败不污染后续suggestion；逐suggestion catch/继续及CancellationException传播保留，无NonCancellable/手动补偿框架。null-DB分支是原Fake DAO构造兼容，不冒称可提供真实事务。freeze3/3匹配，原AtomicityTest与HEAD byte-equal，当前cohort两真实Room回归实际绿。
- **WebDAV/SAF**：delete仍archive→sidecar，只将404视资源已不存在继续，其余HTTP/网络/取消原样抛回VM原失败分支；archive500保sidecar可发现，sidecar500保原错误及UI重试，不加探测/自动重试。SAF两次createDocument使用同授权tree下的根document URI，原temp写入/两次rename publish/失败回滚及持久权限保留。freeze4/4匹配，原测试与HEAD byte-equal，当前provider相关失败已在cohort实际绿；不等于真实SAF设备或WebDAV账号已验。
- **Regex值key/替换契约**：program与visual cache均按规则完整值列表比较，顺序/scope/visual及原锁/LRU预算保留；val规则由原UI copy更新，无平行失效state。指定`\\x`、`$1bar`、超capture-count数字引用走既有JVM batch，Java replacement错误仍只跳该rule而继续后续，pattern preflight/plain-native/null fallback原路径保留；真实消费者仍为RegexOutputTransformer和视觉渲染。AssistantRegexReviewRegression7及MessageRenderRegexCache2当前实际全绿，周边Preflight19亦绿，但镜像detector不替代真实JNI/所有Java-Rust形式等价验收。
- **History预算源码**：expand在整个返回messages窗口共享remainingChars、末条取剩余正文，grant收费为实际返回text字符；read保role/id/分隔符并截真实transcript前缀，不再只返回truncated marker。授权/TTL/source_id/Room归属和取消路径未改。原4红在summary前置挡住，root基于schema与真实代码矛盾授权直接窄修；后续第11节真实工具4项已执行预算assert并绿；不能把前置SQL红写成预算红转绿。

MiniApp已独立核真实链：Theme.kt按app setting计算darkTheme并提供LocalDarkMode，Runner调用rememberMiniAppThemeProvider，AndroidView.factory把首次callback交给真实MiniAppBridge，host.getTheme每次实际调用它。原system dark/首次MaterialTheme闭包确实与host override及后续重组不一致；新实现仅用LocalDarkMode构造当前完整MiniAppTheme DTO、rememberUpdatedState更新DTO，同时保留remember callback身份，Bridge拿同一callback即可读当前颜色/hostDark，没有新增theme writer或生命周期。原两红有效assert保持，当前cohort该两项已实际绿；这不等于真实MiniApp页面像素/外部bridge全能力已验。

Cot当前真实两个UI入口调用localizedStatusVerb，statusVerb明确为英文非Compose fallback；fixture改显式English资源保留排队/receipt/turn/status断言。Reminder仍context.getString按locale生成prompt，fixture显式zh保中文精确业务断言。当前cohort这三项实际绿；没有将产品固定成某种语言。

## 10. 已到Route当期双宽像素（已关闭此项）

已亲看`phase-5-route-final-411`与`phase-5-route-final-320-large-font`各4PNG（share-warm、share-recreated、notification-warm、oauth-warm）及当期execution/instrumentation/semantics。两轮均真实3tests/0skip通过，411为14.674s，320/font1.5为19.076s；目录在执行前归档旧证据、按当期UUID取图，无混用旧baseline。

实际share显示新草稿，编辑并recreate后保留edited文本及attachment；notification页显示精确目标会话标题（长标题按原规则省略），OAuth回跳不替换edited草稿。两宽header/back/model选择、欢迎区、Choose model提示与输入按钮无错位/遮挡；320大字体的长草稿自然换行，OAuth图的真实IME上方输入与发送仍可达。320长附件chip位于原horizontalScroll Row，semantics有ScrollBy/ScrollByOffset及Remove attachment实际bounds，右端在当前viewport仅部分可见是现有横滚合同，不误报固定裁切，也不宣称所有附件动作无需滚动即可见。

这关闭本次Route指定视口与真实入口门槛，不替代系统通知托盘授权、真实OAuth账号或任意share source验收。Settings与WebView17的后续修正fixture及实际证据见第11–12节；先前已识别selector/legacy-opaque身份错误没有归为产品回归。

## 11. 最后History、freeze、APK与Android WebView实证

`phase-5-history-final-real-room`的fresh XML已独立读取：4tests、0failure/0error/0skip，4.529s；四个名称分别为requested budget/truncation、whole-window shared budget、long-first-message prefix、actual-returned grant usage。现在才可写实际预算assert已执行。前次JSON1失败不改写成预算红。

最终fixture仅用DAO委托将未消费的summary preview SQL换为同一真实Room库`getConversationById`的metadata投影与真实node count；repository/消息节点/FTS/grant/source_id和全部原预算/usedChars/exhaustion断言保留，不加入driver/dependency/生产fallback。它明确**不验证ConversationDAO的JSON preview SQL**。

最后四UI修复前的2803输入已独立重算：零缺失/漂移；相较pre-gate恰15文件变化。8个生产文件为Memory两owner、WebDAV/SAF、History、Regex processor/visual cache、MiniApp theme，均在第9节指定有限审查中；其余7为fixture，已区分真实持久barrier/observer清理/locale/有效输入/legacy数字ref合同，没有未覆盖新增生产source。最后另有三处纯layout生产文件增量及对应新freeze，见第12节；此前15文件与后续三文件没有混写为同一快照。

已读取`phase-5-final-repaired-normal-product-apks.json`并对三个arm64实际APK独立重算匹配：Full `ebd88634ca0916d1dfeb6403d86f5097cc9c2466b671fb85a416be9a6af31dba`、Novel `6962cd8ef46cbf41082644bfc0a75ec729a369de83452a9b9b544047fbcfac90`、DeepRead `efed7d6461f5f9a7d57ec5cad153c5804bc5c1951b8059b24f0f4139b2056714`。正常配置，制品记录taskDummyOAuthMarkerInDex=false。`phase-5-final-repaired-device-install.json`记录Full与测试APK均adb install Success；构建/哈希/安装与运行验收分别记录，未把JVM dummy OAuth invocation产物当交付。

`phase-5-final-actual-android-webview`实际instrumentation为17tests/0skip、3.658s，execution passed=true，真实WebMountParity/ LateDialog用例均执行。该组是offscreen WebView行为gate，未产截图目录，pullExit1只说明目录不存在，不能抹去17执行，也不能冒称WebView像素通过。早期opaque ref自带realm/snapshot却期待missing_snapshot的错误oracle已改为实际legacy数字ref，零dispatch/assert保留，生产未因此放宽身份策略。Settings最终有效nested-entry及随后四个真实UI修复的双宽实证见第12节。


## 12. 四个真实UI问题与最终受影响双宽闭环

原`phase-5-settings-final-valid-320-large-font`虽3项行为绿，逐图检查确认四个实际问题，未把行为绿等同字形通过：MCP预览Normal risk被挤成逐字竖排；同Dialog Approve and import被挤窄裁切；Prompt Mode Injection两行被固定高度裁下沿；Backup WebDAV Read snapshots仅露Read且无省略提示。均已回到原owner精准修复，未扩到无关组件。

实际源码增量只有三个文件。McpImportDialog的预览头与动作组用FlowRow按可用宽自然换行；PromptTabs保持原主题/selection/callback，只用minHeight、intrinsic共享行高及内边距容纳两行；BackupProviderPanels仅WebDAV两按钮Row→FlowRow。未改字号、全局按钮、业务条件、state owner、持久化或主题。`final-source-checkpoint.json`相较`final-source-checkpoint-before-four-ui-fixes.json`恰这三文件变化、无增删；2803项当前SHA256独立全部匹配、零漂移。

修后实际设备为`phase-5-final-ui-fixed-settings-411`两项/0skip/33.284s、`phase-5-final-ui-fixed-settings-320-large-font`两项/0skip/27.74s。只复验原有MCPPrompt与Backup两个受影响方法，保持parent/nested有效keyword/content→确认父Entries1→父取消、recreate后无持久草稿、预览无apply/launcher取消等原断言；SSH/WebMount未变，复用同阶段双宽既有实际绿及已审图，不声称最后APK再次执行其方法。

本reviewer亲看两个新case各自01Backup、06MCP、09Prompt，共六张指定PNG，并结合当期semantics：320的Save configuration和Read snapshots均完整、自然两排；Normal risk完整另行、没有竖排大空白；Approve and import完整独占下一排，Cancel/Preview可达；Mode Injection两行字形完整、没有底部裁切。411三处保持自然单行或原布局，无新对齐/裁切问题。MCP输入框本身是可滚动编辑viewport，长JSON不等于全部在当前帧展开；未用该图证明外部服务联网。

四个case cleanup均记录settingsCanonicalUnchanged、launchPreferencesRestored、conversationRowsUnchanged=true，restoreApplied=false；Backup onlyOwnedDeviceIdRestored=true，MCP没有该清理动作，字段false不误报恢复失败。`final-ui-repaired-device-config.json`1080×2400/density420/font1.0前后精确一致，exactRestored=true。

最终正常三产品构建成功，`final-normal-product-apks.json`中Full=`d6a5eaad20248b1ecbf3df82a7c25ab7122bcaa5208c7e3883376979530bbaa0`、Novel=`d19e5e0476d7998fd2bd6c811598f55bcf1ad184d62a89428589f383d2767c9f`、DeepRead=`dc75e97d32530f53fe95b8c7f017d754cd70d79f60fbe5f7fbec04005e6f9fff`；三个arm64实际文件SHA256独立重算全匹配，正常配置/dummy marker=false。Full最终安装记录见`final-ui-repaired-device-install.json`。Chat2/Novel1当前关键route在前一Full `ebd88634`实际绿，之后只上述layout增量，故按影响范围复用，不冒称在新d6a5 hash重跑。

**最终判定：PASSED_WITH_EXPLICIT_EXTERNAL_GATES。** 本次源码与设备指定问题均闭合，后续按第8节保留未验外部门槛，不追加无证据优化。
