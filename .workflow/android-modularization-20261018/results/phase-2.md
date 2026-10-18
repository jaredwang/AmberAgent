# Phase 2 聊天运行职责收口

状态：Phase2已完成，独立逻辑与当期设备像素审阅通过，明确保留外部门槛。基线137f57e2，所有改动留在任务分支工作区，未提交/推送/发布。

## 生产边界

ChatService由4514行收为646行稳定外部facade；ConversationStore唯一持会话/分页/初始化/checkpoint/tombstone/restore规则，PendingMessageQueue唯一持队列/锁/revision/持久写，ConversationEditor负责编辑/分支/删除/fork，ChatErrorStore唯一错误投影。Session只持原Job寿命和只读Queue flow，未复制可写队列。

ChatRunCoordinator（1378行）独占dispatch/active/paused/outcome/done与Stop/resume；ChatApprovalActions独占trust/unknown决策；ChatRunHooksFactory逐activation捕获epoch/durable/settings/task/response上下文并负责内容checkpoint→terminal→event CAS→成功后处理；ChatRunPresenter持Android前后台observer、通知、FGS、任务展示。Presenter不决定业务终态，Factory构造不持Coordinator，create只接明确Stop命令。

Resolver真实依赖Store/Queue/Hooks/Catalog/Coordinator，不再回取Service。Projector、ChatTurnAgent与AiAuxiliaryGenerator消费同一Store（ConversationAccess）；GenerationModule/ToolExecutionModule迁移实际注册并接入真实App及两组Koin验证列表，startup恢复屏障顺序保留。Gradle模块图未新增项目。

RunToolCatalogFactory与MemoryRunToolFactory组合已有真实工具；LocalTools的image/WebMount具体组合作为独立职责，foreground/child目录边界、child WebMount scope与dynamic recipe保留。没有把23个依赖换成万能Context。

GenerationRunSession/RoundRequest及实际Transformer/Memory/Context消费者改用窄设置投影；Chat、Novel、DeepRead、SubAgent首轮/report retry在本域override后取同一run snapshot，完整Settings不流入Kernel/Transformer核心。ProviderAttemptExecutor拥有每次尝试的wire-fit/audit/adopted finish，GenerativeUiRepairPolicy拥有修复规则；Kernel仍持单一messages/step/terminal，仅把每run history与approval planning分离。

## 已有真实缺陷与迁移交接修正

- 原Runtime三红：匹配Stop停止drain但保留pending；取消及terminal发布均等待真实handler settle。原5条行为断言全部保留并通过。
- Stop未确认远端取消时保持WAITING_EXTERNAL/cursor；无handler不留内存latch。复用实际event pause→RUNNING CAS与Runner竞争，成功claim才写terminal再发布外部pause；同run新activation先claim则旧Stop不覆盖。重复Stop复用原响应pause状态集合。
- 取消cleanup期间新输入先进入现有durable Queue，新drain在旧Job join后才执行；匹配旧Stop可停止当前drain，但不能丢掉已接受输入。没有新增Job/run映射或状态机。
- 通知PendingIntent的run/tool/token原来只有extras，FLAG_UPDATE_CURRENT可重绑已保存旧动作；现在同一broadcast identity含原真实身份，保存旧Stop/旧问题/旧nonce的实际Notifier shadow回归均绿。系统通知栏实际桥接仍单列设备权限门槛。
- TaskStore.cancel回调锁外等待后原回写可污染successor；联合捕获spec/callback并复用既有expectedSpec CAS，成功/失败/不可取消均不写新代，拒绝后返回当前snapshot。模块13项绿。
- HEAD已有Memory pinned重复预算和SubAgent report retry漏判审批已在真实owner窄修，原断言未削弱。

## 当前验证

- 最终I/H/通知/展示/DI/TurnAgent/Canary：phase-2-runtime-final-verified，13类66项0失败0跳过；Coordinator7/Runtime5全绿。对应原红位于coordinator-behavior、stop-admission-checkpoint、stop-repeat-activation-red、stop-closure-red，不删除红证据。
- Store/Editor/Queue、窗口/variant/restore、工具child scope、真实Engine attempt/repair/OCR/snapshot、Settings及Kernel44+Canary的已验证批次见各P2包报告和独立review；没有把重复执行数量相加冒充覆盖。
- app/unit/androidTest Kotlin编译与fullDebug app/test APK assemble成功，日志phase-2-runtime-final-verified.log；phase-2-source-snapshot.json的148份输入哈希在构建与独立review核对一致。
- phase-2-runtime-final-build仅根命令参数放错任务，执行前失败且0freshXML，不作为源码或测试红绿证据。
- 首版phase-2-acceptance-apks.json及最终phase-2-queue-ui-build.json分别记录对应APK；最终app fd917876555ff5c644fcb0daac386846519b3d92f223d9a33228d01482f1b530 / test037cffb2f870522b6ba3309578d1c3ced0df9fc442f05b37760a47d14966920d均install-r-t成功，且以下当期设备路径真实通过。

## 明确未验与后续

系统POST_NOTIFICATIONS当前granted=false、appOps=ignore，未修改权限；真实tray展示/旧PendingIntent.send/内容点击C尚未验。实际通知构造shadow测试不冒充设备接受。外部provider/OAuth/远端真实取消与OEM保活仍不由本地SSE证明。

DeepRead真实RunManager stream→nonstream fallback行为fixture归Phase4；fork失败补偿的显式restore epoch上下文缺口尚未证明可达误删，归Phase5沿真实命名/restore复核。HEAD已有Service疑似dead merge/transformer声明仅标记保留，不未经授权删除。Phase3已知Input四红及既有State替换草稿合同待本阶段完成后修复。

独立审查：phase-2-independent-review.md，最后411/321 PNG、fresh XML、owner/Room/server、restore/clock和真实HOME证据已由独立subagent亲自核对，结论PASSED_WITH_EXPLICIT_EXTERNAL_GATES。

## 最终设备及像素验收

- final-run-411dp：A真实composer→queue2→HOME（Process CREATED/Presenter false）→warm同Activity/同run/原queue→Stop durable CANCELLED且queue保留→Dialog Resume→两个不同后继run依次wire→Room内容，OK1 23.362s；B实际ask_user→Activity重建→选择不发送→Submit→相同runId完成+持久Answered，OK1 10.718s。
- final-run-321dp：A同链含真实后台与warm返回，OK1 20.163s；321 B此前clock-run-321dp已实际通过。全部无skip，未用静态Pending卡或mock产品路径替代真实Runner/Resolver/Store/Room。
- 每case实际Koin ConversationAccess===ConversationStore，Service flow===Store flow。persistent Settings、启动pref、测试clock恢复均true；设备size/density原1080x2400/420无override已finally原样恢复，字体/系统权限未动。
- 当期01–08 PNG、Compose语义、fresh AX XML及owner JSON已由独立reviewer逐图检查。真实发现队列header Move down仅21x180px被挤四行、Cancel不可见，已仅改mode独立首行+FlowRow动作；最终两宽每卡三动作完整单行、6触区不重叠，411 Move down恢复164x45px。preview四行省略保持原设计。
- 04停止/06完成以真实generationJob==null及Send可达稳定帧采样，PNG/XML一致，队列2→0与disk一致。取消cleanup中job尚未null的Generating是原UI合同，不误报为产品状态错误。
- fixture原对象Settings==original包含Transient lambda引用而误报恢复；改为全persistent JsonElement+!init，启动pref另验。原streaming视觉withFrameNanos持续更新，A只控制测试mainClock并有限显式推进、finally恢复，不改产品动画、不冒充实时帧性能。旧误报/primary保留/诊断证据全部保留。
- 最终phase-2-final-source-snapshot.json共149份任务输入，包含UI定点修与最终device fixture；runtime gate旧148份快照保持独立，不混称最后UI增量已重新跑同一JVM。
