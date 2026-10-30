# Phase 4 UI / conversation metadata 独立复审

当前结论更新：下述原 U2 阻塞在取得实际 RED 后已完成统一 owner 修正，末节源码复审无剩余阻塞；最新 GREEN 仍由唯一 Gradle coordinator 单独报告，不能从此复审推定。

复审者未实现 U1/U2；仅检查本轮 diff、phase4-ui.md、受控 RED 报告与实际 caller。没有修改生产代码/测试，没有运行 Gradle。本报告是 source/owner 复审，不冒充 GREEN 或设备验收。

## 结论：U1 无阻塞；U2 尚不能关闭

### 阻塞：标题/建议的实际后台 caller 仍能回放旧整份 conversation（P1，归入 U2）

位置：AiAuxiliaryGenerator.kt:73-86、132-147；ChatRunHooksFactory.kt:365-371。

generateTitle 在模型返回后，先从 ConversationAccess 读取整份 live conversation 并 copy(title/updateAt)，随后才进入 withRestoreWrite。generateSuggestion 同样先 copy(chatSuggestions/updateAt)，再进入该 gate。该 gate 的 restoreMutex/writeMutex 都可挂起，故不是一个没有让出点的局部更新。

生产可达性：成功 hook 独立启动这两种辅助任务；它们的 provider 请求可以在后继 chat run 已开始后才完成。AgentRuntimeModule.kt:181 创建 InProcessAgentRunner 时未覆盖默认 Dispatchers.Default scope；ChatTurnAgent.kt:108-125 会在 durable writer gate 外投影新的消息。恢复 epoch 只挡恢复前的 writer，不能挡同一 epoch 的后继流式消息。

确定的失败时序：

1. 用普通 writer 持有现有写 gate（不发生 restore，不改变 epoch）。
2. 实际 generateTitle 或 generateSuggestion 的 HTTP 结果到达，捕获当时的整份 conversation，然后等待写 gate。
3. 后继 ChatTurnAgent/实际 ConversationAccess 投影一条更新的 assistant tail。
4. 释放 writer；辅助方法执行 updateConversation，把第 2 步旧节点重新发布到 live state。
5. 后续 generation checkpoint 读取该旧 live state并持久化，或最终窗口收尾读取同一旧 state；新的 metadata-only rename 和 title rebase 无法恢复已经从 live state丢失的消息。

这不是要求所有状态都做通用 merge，而是 metadata owner 仍有真实生产入口在写整份消息。应先为实际辅助方法增加受控 provider/gate 回归，确认 RED，再让标题/建议只修改其所属 metadata；保留既有 force/空标题触发、suggestion 清空、FTS 事务及 captured restore epoch，不引入新的流水线或重试。

### 同一 U2 owner 的短投影交错：需在上述修复/回归时核对，未另计问题

当前新 updateTitle 在 Room 返回后调用 ConversationStore.updateConversationState；该 helper（469-471）是 value 读取、copy、updateConversation 赋值，并非 StateFlow CAS。真实 ChatTurnAgent 的 message projection 同样在 Default 上读取/merge/赋值，未被 durable gate 串行化。

因此当前 diff 还存在两个 source-level 交错：rename 读 live 后，stream 发布新 tail，rename 把旧 tail 带回；或 stream 读旧 title 并开始 merge，rename 已提交/发布新 title，stream 再发布带旧 title 的整份结果，后续 checkpoint 会把旧 title 当“当前标题”。后者的 merge 不是空操作，涉及真实 generatedMessages/node projection。

这不是宣称现有受控 RED 已覆盖该线程窗口。若修改此路径，需先用现有 MutableStateFlow/ReflectionFixture 边界暂停实际读取或投影，取得确定失败；仅要求各 metadata/message owner 的短更新针对当前 state 保留对方字段，不扩展成通用跨数据合并框架。已把此窗口单独通知主 agent，等待其按证据决定范围。

## 本轮实现中已核对正确的部分

- U1 confirmEdit 与 submit 共用同一 inFlight admission，确认回调的同步路径用 finally 释放；没有增加第二 busy 状态，也未改变 widget action、历史附件或 captured edit target 契约。受控 validation 回归直接击中原重复入场路径。
- 用户 rename 已从 ChatVM 实际入口改为 ChatService -> ConversationStore metadata command；repository.updateConversationMetadata 的标题、message_fts、conversation_title_fts 处于原 Room transaction，不删除/reinsert message nodes。
- 新 rename 使用既有 withConversationWrite。携带旧 SyncRestoreWriteEpoch 的 callback 仍由 gate 拒绝；没有 epoch 的显式用户动作按既有契约等待 restore 后执行。未绕过 restore gate。
- 初始化 window 的 checkpoint 在真正持有 writer 后只 rebases 当前 title 和较新 updateAt，保留传入 generation message window，不把整份 current live state当通用替代。该修改能修复已取得 RED 的“rename 后排队旧 checkpoint 回写旧标题”，包括不会进入 success-only persistFinalWindow 的失败/取消收尾。
- 未初始化 session 的旧 full-save fallback、真正 branch edit 的整份保存语义未被改写。metadata-only rename 不会因 placeholder 缺消息而重写既有 durable history；后续 ensureInitialized 仍从 repository 读取完整窗口。当前唯一 rename UI 位于 recap，空白新会话没有 recap eligible 入口，不能据通用 public 方法可接收任意 UUID 就认定新会话丢失回归。
- 新 ChatTitleRenameRaceTest 走真实 ChatVM/service/store/Room，并在同一个 DAO barrier 后检查 checkpointed title、live tail 与最终 durable tail；不是复刻生产实现的 helper。phase3-4-red.md 中 checkpoint 旧标题断言确实失败。现有 test 尚未证明上述辅助 caller 或非 CAS 并发投影窗口已修复。

## 待复核门槛

在主 agent 确认受控 RED、metadata owner 修正、相关 GREEN 后，再有限复审该追补。当前不建议将 Phase 4 U2 标记完成。没有要求新增全局锁、fallback、权限、依赖或重做 generic save/branch semantics。

## U2 追补实际 RED 与最终源码复审

主 agent/唯一 Gradle coordinator 已确认以下是有效行为断言失败，而非 fixture/编译问题：

- phase4-residual-red.log：实际 AiAuxiliaryGenerator 的 title 与 suggestion 两个 held-writer case，均把后继 live/durable tail 回放成旧节点。
- phase4-projection-red-residual-green.log：实际 ChatEventProjector 在 rename 后撤回 live title；实际 Store metadata transform 丢掉较新 streamed tail；实际 ChatTurnAgent 的旧读取投影覆盖已提交 title。该轮 auxiliary 两项已经 GREEN，三项投影断言仍为 RED，原子性疑点因此获得确定复现，仍合入 U2，不另计缺陷。

实现者在主 agent 放行后统一修正了既有 owner。复审仅检查这些新改动与相关 caller，没有新增生产/测试修改，也未运行 Gradle。

**最终源码结论：原 U2 阻塞已解决，无剩余阻塞。**

- ConversationStore.updateConversationState 使用现有 StateFlow.update CAS，对最新当前状态执行传入 transform；id 不匹配仍拒绝。ConversationAccess 把同一个既有 transform 能力明确为 abstract 契约，没有提供非原子的 fallback/default。ChatService 只转发到实际 store。
- 所有当前生产 transform caller 已检查：metadata title/suggestions、suggestion 初始清空、ChatTurnAgent message merge、finalized timestamp、hooks reasoning finish、UI approval flag 与 favorite maps 均为局部 copy/map/新值构造；外部 repository 写、通知、事件提交、收藏持久化不在可重试 lambda 内。ChatTurnAgent merge 仅构造未发布节点与当前 immutable message 列表，CAS 失败不会产生第二份 durable 写入或重复工具副作用。
- 原 ChatVM.updateConversation 的 lambda 有意返回整份 newConversation；真正初始化、完整保存/branch edit 的 updateConversation 也仍是显式整份替换。此次没有把所有 replacement 偷换成通用字段 merge，也未声称捕获的整份 replacement 自动变成当前状态。
- AiAuxiliaryGenerator 不再在 gate 外保存整份 conversation。title/suggestion 只把字段值交给 store metadata owner，并携带模型请求前捕获的 restore epoch。DI 已改为依赖实际 ConversationStore，不出现 service/store 回环。force/空标题触发、初始建议仅 volatile 清空、最多十条建议及错误传播语义保留。
- store 的 title/suggestions 共享同一个短 metadata owner：先进入既有 restore writer，检查 tombstone，使用原 repository metadata/FTS Room transaction，再 CAS patch 最新 live state。current title/suggestions 的非所属字段及较新 updateAt 保留。写入期间 live stream 更新的节点不会被旧 snapshot 带回。
- ChatTurnAgent 通过上述 atomic transform 对当前 state 调用既有 mergeMessages；generatedMessages、range 与事件顺序保留，工具/stream checkpoint 事件仍在成功状态投影之后提交。没有把整个 runtime 切到 Main，也没有给 network 增加全局锁。
- ChatRunHooksFactory 的 reasoning 收尾同样针对当前 state 做纯变换；在 CAS 后重新读取 checkpoint snapshot。initialized window checkpoint 只重取当前 title/suggestions 与较新 updateAt，仍写调用方拥有的消息 window；未初始化 fallback、真实 full-save 与 finalWindow owner 不变。
- ChatEventProjector 在既有 writer 内重新确认 message membership，然后只 CAS 更新 timestamp；不再先捕获旧 title/message nodes。repository metadata 更新保持 title=null 读取当前持久标题的既有行为，FTS 事务未拆开。captured restore epoch 仍由 gate 校验。
- timeline count bookkeeping 提取为 ConversationSession.refreshTimelineNodeCount，并对 timeline StateFlow 做 update。它从当前已投影 state 取 node count，用当前 loadState.copy 修改计数/完整标志，保留 initialized、offset、prefetch 等其他 load-window 字段；未初始化时原样返回。没有为 metadata patch 改写分页或加载策略。

所需最终受影响检查仍是 coordinator 对 ConversationProjectionAtomicityTest、ChatFinalizedProjectionRaceTest、AiAuxiliaryMetadataRaceTest、原 rename/store/submission/ChatTurnAgent 相关类的 GREEN，以及后续统一编译门槛。此处关闭的是独立源码 review 阻塞，不替代执行、设备或真实 provider 验收。
