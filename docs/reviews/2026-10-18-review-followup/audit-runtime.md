# Runtime / memory / Jev / child-thread follow-up audit

审查对象为本仓当前工作树（包含上一轮 B/C 修复和其他工作包既有 WIP）。只读追踪生产入口及状态 owner；未读取兄弟产品、未修改生产/测试、未运行 Gradle。以下回归均为建议的确定性测试规格，不冒充已执行结果。已停止扩展扫描。

## 既有修复复核

- B1：普通 same-run resume 现在也捕获 preLaunchSnapshot，并通过 `awaitChatRunDispatchEnd` 等待新 activation；修复了读取前一 WAITING_USER 的原缺陷。下述 R1/R2 是取消后的 dispatch/handler 归属问题，触发不同。
- B2：missing-target INVALIDATE 返回 nullable outcome，不再伪造 active replacement；existing/archived target 分支保留。
- B3：全 scope 关闭时在 public prompt 边界退出；已有 profile 还校验当前启用 scope 的 active source，方向正确。
- B4：已知 model window 按实际 output reserve 后余量计算，极小正窗口变为正常 fit rejection。
- C1–C4：没有发现已接受修复的新增回归；外部动作 effect 类别、editable raw value、可见精确验收及容器 ref 子树分别留在现有 owner，未扩成权限或身份框架。

## R1 / P1：带 runId 的通知 Stop 会自动运行已排队的后续消息

- 入口：`AgentNotificationActionReceiver.kt:42-53` 从通知转发 conversationId + 显式 runId → `ChatService.stopGeneration`。
- owner 缺口：`ChatService.kt:4234-4239` 仅在 `runId == null` 时取消 session dispatch。通知路径取消的是 registry 中的 handler Job 和/或 `AgentRunner` activation（`4226-4259`），独立的队列 dispatch 仍 active。
- 确定传播：`InProcessAgentRunner.cancel` 立即将 snapshot 发布为 CANCELLED（约 `415-425`）；`dispatchKernelTurn` 在 `ChatService.kt:1398-1405` 接受终态后正常返回；`runKernelDispatchLoop` 只检查是否有未处理 tool，随后 `1252-1256` 无条件 dequeue 下一条 pending user message 并启动新 run。
- 触发：聊天正生成无 pending tool 的普通文字回复，队列有一条 FOLLOWUP；点击系统通知 Stop。当前 run 取消后，FOLLOWUP 被消费并继续调用 provider/工具。
- 二次判定：不是队列的正常成功续跑。`stopGeneration` 上方 P1-06 契约明确 Stop 应进入 idle、保留队列、只由用户显式 resume；UI Stop 取消 dispatch 正是为保留该契约。runId 精确取消也不能成为自动启动下一 run 的理由。
- 最小修复：在已验证当前 run ownership 后阻止该 dispatch 继续 drain。可让 dispatch 的终态结果显式返回 CANCELLED 并在 loop 退出，或只取消确认仍属于该 run 的 dispatch；不能按 conversation 盲取消可能已转交的后继 user run。
- 回归：真实 notification Stop 调用路径，启动一个 held run 并排队 FOLLOWUP；传匹配 runId stop，断言 run CANCELLED、pending ID/内容不变、runner/provider 仅有首个 launch、session 最终 idle。再验证 stale/mismatched runId 不取消或阻止后继 run；普通 COMPLETED 仍继续 drain。

## R2 / P1：普通取消仅等 dispatch，旧 handler 的持久化清理仍可与下一次生成并发

- 入口：UI Stop → `stopGeneration` `4234-4236` cancel/join session Job，或 `regenerateAtMessage` `2169-2175` cancel/join old session Job。
- owner 缺口：`dispatchKernelTurn` 的 cancellation catch（`1406-1409`）只调用 `runner.cancel` 随即退出；没有调用新增的 `runner.awaitRunSettled`。`AgentRunner` handler 属于独立 runnerScope，并非 session dispatch 的结构化 child。runner 的 cancel snapshot 是提前信号。
- 后续真实写入：`ChatService.kt:1606` 的 `onRunFinished` 通过 `finalizeChatGeneration`（`202-203`）在 NonCancellable 执行；`1619-1630` 读取旧 currentConversation、更新并强制 checkpoint，后面仍写 terminal、notification、keep-alive。`checkpointConversation` / `persistConversationWindowInternal`（`2991-3053`）没有 generation owner revision。restore gate 只排斥旧 restore epoch，不能区分同 restore epoch 下两个 run。
- 确定触发规格：将旧 handler 的 onRunFinished/checkpoint 挂在可控 Deferred；取消旧 session dispatch 并 join，它会在 release handler 前完成；此时可进入 regenerate 的 truncate/save 或接受新消息。旧 handler 之后仍能持久化捕获的旧 conversation/改变新 run 的通知与 keep-alive。若新消息发生在旧 durable row 尚 RUNNING 时，还会被当作同 run resume，gate 拒绝而新消息没有真正生成。
- 二次判定：`ChatTaskRun.awaitTerminal/cancel` 已明确调用 `awaitRunSettled`，其注释说明 terminal snapshot 早于 handler cleanup。只有 task path 得到此 barrier；普通聊天的 `oldJob.join` 注释声称已等完 final writes，与真实 Job 树不符。不能用 in-process activation epoch 排除：epoch 只保护 runner 自身 CAS/snapshot，未保护 ChatService 的 conversation/notification 副作用。
- 与 R1 的区别：R1 不需要慢清理，是通知 Stop 后 drain 条件错误；R2 即使队列为空或 UI Stop 已成功，也会过早允许下一次 owner，破坏 finalization 顺序。
- 最小修复：在当前 run 的 dispatch 释放 ownership 前完成 runner settlement；取消路径需在 NonCancellable cleanup 下等待，避免 cancelled coroutine 的 join 再被取消。复用已有 awaitRunSettled，不新增重试、全局锁或 run 状态机。保留 WAITING_USER 的正常同 run resume，并确保等待只针对捕获的 runId。
- 回归：用真实 InProcessAgentRunner + held NonCancellable finalizer，断言 Stop/旧 dispatch join 和 regenerate/new work admission 不早于 finalizer release；release 后旧 checkpoint 先结束，再允许新 run 修改；保留普通 pause/re-approval、stale stop 与不同 conversation 的独立性。

## R3 / P2：语义召回重复插入置顶记忆，挤掉其他相关记忆

- 缺陷：`MemoryRecallStore.kt:114-117` 为 `budgeted(pinnedFirst.distinctBy { it.record.id } + ordered, settings)`，只去重 pinnedFirst；同置顶 ID 也在 reranker 的 ordered 中时仍重复。
- 生产入口：`ChatGenerationRoundEngine.kt:161-166` → buildPrompt → recallSelections；`RepositoryModule.kt:54` / `JevModule.kt:67` 注入真实 `JevMemoryReranker`，其 `98-117` 返回全部达到阈值的候选 ID，不排除 pinned。
- 确定触发：maxItems=2、pinned ID=1、普通相关 ID=2、rerank=[1,2]，返回 [1,1]；takeBudget（`276-290`）按项计费，prompt 重复置顶正文，ID=2 被丢。
- 二次判定：强制保留 pinned 是设计；同一 record 重复占条数/字符预算不是。现有 `MemoryRecallSemanticTest.pinnedSurvivesEvenWhenDroppedByRerank` 只覆盖 reranker 不选 pinned。
- 最小修复及回归：对整体 `(pinnedFirst + ordered).distinctBy(record.id)` 后 budgeted。复用 FakeReranker 返回 [1,2]，断言 IDs 精确 [1,2]、prompt pinned 内容一次且 normal 仍在。

## R4 / P2：Daydream 单次 supersede 部分落库，CAS 冲突却被返回为未应用

- 缺陷：`MemoryDreamApplier.kt:124-162` 在 `133-148` 先 addMemory replacement，`149-157` 再逐个 CAS archive；任一失败在 `159-162` 被 catch，这条建议不计入 appliedSupersedes，但 replacement 和已经归档的前置目标不会回滚。
- 生产入口：`SettingAgentMemoryVM.applyDreamPlan` `424-435` → applier；自动 `MemoryDreamRunCoordinator` 也用同 owner。applyMutex 只挡另一次 apply，用户编辑/tool/extraction 可在 applier `getAllRecords`（`40`）之后更新 revision；`MemoryRepository.upsertRecordInternal` `264-266` 确实抛 MemoryStaleException。writer gate 逐次锁写，未包围一次 supersede。
- 影响：返回未应用的 operation 实际新增 active fact，新旧冲突记忆同时召回；若只有这一条建议，VM 还会因为 `appliedPlan.hasChanges == false` 标 dismissed。
- 二次判定：这是同一 operation 内的部分成功，不是不同建议之间允许独立成功。applier `44-46` 说明 CAS race drops that op；`MemoryRepository.supersedeMemory` `495-561` 已用 Room transaction，`535-537` 明说避免 inserted-then-failed archive 的 orphan。Daydream 多目标路径绕过了该 owner。
- 最小修复：repository 用单个短 writer + Room transaction 完成一次 dream supersede，核对全部旧 revision，创建 replacement 并 archive 全部目标；成功后 applier 才更新本地 map / applied。不同建议继续独立，不包整份 dream、不自动重试。
- 回归：利用现有 `MemoryDreamApplierTest.FakeMemoryDao.failCasIds`，断言一目标 CAS 失败时没有 replacement、旧记录全部保持原状、该建议不计 applied，独立后续建议仍可成功。另用已有 Room 集成设施证明真实 transaction rollback，不能以 Fake DAO 代替回滚证据。

## R5 / P2：子代理 report 补交阶段的 Pending 被错误完成覆盖

- 入口：subagent_start → SubAgentManager.launchTurn → GenerationSubAgentRunner.run。
- 缺陷：`SubAgentRunner.kt:162-171` 只在第一次 kernel 后检查 Pending。没有 report 时 `183-209` 再跑 kernel，仍提供全部 `tools + reportCapture.tool()`；第二轮 Pending 后 `226` 直接 `resultOrFallback`，`SubAgentReportTool.kt:80` 的 fallback 为 COMPLETED。
- 触发/影响：首轮返回可见文字没有 report，补交轮请求一个需审批工具；动作未执行，但 child 被报告完成。`SubAgentManager.kt:946` 的 handleChildTerminal 曾 pauseNode，后续 finish / `ThreadGraphManager.kt:133` 又覆盖成 COMPLETED，审批等待丢失。
- 二次判定：补交 prompt 要求 report 不约束模型真实输出；工具实际仍可用。首轮同样 Pending 正确返回 APPROVAL_REQUIRED，第二轮没有改变该契约。
- 最小修复及回归：在两次 kernel 返回后共用 Pending 检查，保留 report/可见文本 fallback。runner 两响应测试首轮文本、次轮 Pending，断言 APPROVAL_REQUIRED、敏感动作未执行；manager 集成断言内存/持久化节点保持审批暂停，任务卡不发布完成。

## R6 / P2：子代理消息仅入 Channel 就计为交付，最终无工具回答期间的消息会丢失

- 入口：`SubAgentTools.kt:333` subagent_send_message → `SubAgentManager.kt:539-580`。
- 缺陷：`562-568` 仅 trySend Channel 成功便 markDelivered；真实 `drainMailbox` 在 `965-972`。`DefaultRunKernel.kt:547-565` 对无工具最终回答直接 break，跳过 `876` consumeSteerMessages。`ThreadGraphManager.kt:158-160` 完成时无差别将所有 DELIVERED 标 PERSISTED。
- 触发/影响：child 已交 report，正在最后无工具回答时收到消息；消息未进模型，下一 followup 只 claim QUEUED，故永久遗漏，父代理却收到 delivered。
- 二次判定：工具承诺运行时 mid-turn steer、空闲时留待 followup；PERSISTED 注释也明确为消费该消息的结果落地。已有 sendMessageToLiveThreadIsDeliveredAndPersistedWithResult 的 fake runner 没消费 steer，只断言状态，不能证明真实交付。
- 最小修复及回归：按实际 drain 的消息 ID 推进 DELIVERED；未消费消息保持/恢复 QUEUED，复用现有状态及 followup，不自动重跑。用 held 最终回答发送消息并完成，断言未错误 PERSISTED，followup 模型输入恰出现一次；已消费消息仍随结果正常 PERSISTED。

## R7 / P1+P2：Jev 配置变更后旧请求仍可出站，旧 401 还能暂停新 Key

- 生产入口：`SettingJevPage.kt:147-150` 主开关、`394` scopes、`588-591` Key 保存都调用 `onCredentialOrScopeChanged`；`JevDecisionCoordinator.kt:55-59` 仅清 cache/authPaused。
- 缺陷：config/scopes/Key 在 `79-94` 入口读取；调用可在 `149-165` 等 semaphore 或 per-run lock，获锁后 `168-181` 及重试出站前不重验身份。`JevRuntime.kt:70` 只事后阻止应用 stale 结果，阻止不了新增外发。
- P1 触发：同 run 第一请求 held、第二排队；撤 PERSONAL_MEMORY 或关闭 Jev 后释放第一请求，第二仍带旧配置发送个人记忆。重试 backoff 期间撤权也会新增出站。
- P2 触发：旧 Key 请求 held；保存新 Key 清 authPaused；旧请求迟到401，在 `214-216` 写全局 authPaused=true，后续新 Key 在 `93` 被永久拒绝直到再变更设置/连接测试。
- 二次判定：不是此前排除的 screen_stop/remove-app 在途工具契约。`docs/plans/2026-09-17-jev-android-integration-plan.md:41` 明确“主开关、数据范围或 Key 变化时由 coordinator 内部检查并拒绝/取消不再允许的用途，结果返回前重验身份与配置 revision”。排队/重试时尚未发出的请求仍继续出站不满足此契约；旧401污染新身份也独立成立。
- 最小修复及回归：既有 onCredentialOrScopeChanged 增加 revision；出站/重试前核验，旧请求不更新新身份的 auth/cache/cooldown。held transport 的“排队→撤权→释放”和“旧请求→换Key→旧401→新请求”证明新调用次数与认证状态；不添加新权限系统或全局自动重试。

## R8 / P2：议会排序缓存锚遗漏实际任务后半段及模型能力

- 生产入口：默认席次 model_council_start → `ModelCouncilManager.kt:119` 消费 JevCouncilPoolRanker 排序，决定实际席次。
- 缺陷：`JevCouncilPoolRanker.kt:53-62` 实际 state 包含 objective/context 各1000字、model name/能力/window；`73-81` cacheAnchor 只有 objective 前300字、context 前200字和模型UUID。
- 触发/影响：五分钟缓存期内，两个任务共用模板开头但关键约束在后面，或同UUID模型能力/window改变，实际 input 已不同仍确定命中旧排序，当前任务复用上一组合。
- 二次判定：不是低概率hash碰撞。原Jev计划要求完整输入/候选哈希；该缓存同请求状态有两个长度不等的投影，其他scope/model/policy分区无法区分。
- 最小修复及回归：直接从已构造完整 state 生成 anchor；分别仅改 objective 第300字后、context第200字后、同UUID能力，断言重新出站，完全相同state仍命中。

## R9 / P2：历史 session_expand 的实际返回绕过字符预算并低扣 grant（root 候选的独立二次判定）

- 公开生产入口：`ChatService.kt:3732-3735` 注册 ConversationHistoryTools；历史 worker 复用 parent tools，`PermissionDecisionResolver.kt:355-365` 在有效 SessionAccessGrant 下预批准历史读取。
- 明确契约：`ConversationHistoryTools.kt:170` 的 max_chars 为 Maximum output chars；`SessionAccessGrantStore.validate` `45-49` 将 allowedChars 限为 requested 和 grant remaining 的较小值。
- 缺陷：session_expand `219` 用受 maxChars 限制的 buildTranscript 仅计算扣账；`226-239` 实际 messages 数组却重新遍历完整 selected，每条独立 formatMessage.take(8000)，未消费共享预算。
- 确定触发：首条文本4000字、max_chars=1000、grant余量1000。buildTranscript `297-300` 只产出 `...[truncated]...` 约18字并据此 recordUse，实际 messages.text 仍返回4000字；多条可返回最多17条各8000字。truncated 又按 transcript.length>=maxChars 判定，短 marker 会报 false。
- 二次判定：这不是 JSON 元数据允许少量额外长度的区别，实际被授权的历史正文自身已数倍超限；即使无 grant，max_chars 契约也被违反。单次串行调用已经成立，不依赖并发授权问题，不建议扩展 grant 并发框架。
- 最小修复：让同一份受总正文预算约束的 items 同时承担返回、扣账和是否截断计算；不再以未返回 transcript 估算额度。回归首条长消息、多条跨项预算、grant余量小于requested及no-grant max_chars；断言实际 text 总长度、usedChars 与 truncated 一致。此文件由 root 修复。
