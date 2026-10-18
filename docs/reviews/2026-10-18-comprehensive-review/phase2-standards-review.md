# Phase 2 Standards 独立复核

结论：**无阻塞项，未发现本阶段 diff 对仓库标准的硬违反。**

范围：`d983c0b` 后 B1、F1–F4、D1–D4 的生产修改和对应回归；结合 audit/repair B、D、F 复核真实接线。Phase 1 不重审。本轮只写报告，不改生产或测试，不运行 Gradle，不读取兄弟产品。root 的 GREEN 与 Novel 全测结果独立记录，本报告不冒充执行证据。

- **状态与职责（用户规则 4、5；根 AGENTS owner 原则）**：Cron 绑定首次 dispatch 的 run handle，等待 AgentRunner 真正 terminal，未复制 run 状态；后续普通消息继续由原 drain loop 处理。B1 把既有 immutable activation barrier 应用于普通同 run resume，没有增加时间戳/并行状态机。首条消息 handoff 的所有出口集中在 ChatService/ChatRunLifecycle，失败保持可观察。
- **取消契约（用户规则 3、5、7）**：Worker timeout/cancel 经 task owner 传到明确 runId 的既有 stop；旧 task 已交付 handle 后不取消共享 dispatch job，避免误伤后继消息。NonCancellable 只承载 handoff 清理和既有 stop；未新增循环、重试或永久 deferred。未启动 body 的 job 由 completion callback 结束 handoff；Main dispatcher 返回窗口由 admission cleanup 覆盖。停止耗时仍受既有 stop/server API 与本地持久化路径约束，本轮没有真实网络取消时延证据。
- **持久化与初始化（用户规则 4、5）**：Reminder 在 scheduler mutex 内按 ID 重读 store，以一份排程逻辑消费当前 durable 状态；未再保存业务版本。恢复入口沿已有 journal/DB marker/gate/Novel bridge，在依赖注册之后、WorkManager 与 eager consumers 之前同步完成。正常无 journal 启动仅做存在性检查；冷启动尚未初始化 WorkManager 的分支有实际时序原因。
- **Novel 与通知（用户规则 4、5、7）**：ZIP/成书只在已有 owner monitor 内采集 snapshot，压缩/输出在锁外；状态合并只处理伏笔路径的 kind/open/resolved，宿主 identity 与原 planned 状态保护保留。DeepRead 使用分离结果 ID，不扩展通知框架。回归实际覆盖 owner 交错、journal/Room、审批写入与 NotificationManager 清理。

Fowler smells 仅作 heuristic：ChatTaskRun 包含真正的 admission/cancellation 生命周期职责，不是无意义转发；snapshot、mutex、journal 均复用现有 owner，不构成推测性抽象。启动 source-order 测试较依赖文本形状，但有真实 journal/Room 行为测试补充，不能单独作为运行时初始化证明；未据此要求额外框架或重构。

## 最后补修追加复核：Cron F5 与 REPLACE 接续

F5 的两处生产 ID helper 分别接到 ForegroundInfo 与结果通知，清理不会删除同一任务的结果；没有额外状态或通知框架。新增 taskId mutex 位于 singleton AgentCronManager，Worker 从 prepare 到 finally 持有；当前唯一调用不重入，外部 mutation 路径不反向取得 execution lock，不同 task 不共用锁，等待者可取消。锁解决真实 WorkManager REPLACE 不等待旧 Worker 的问题，符合用户规则 4、5，不建议增加重试、持久队列或锁回收框架。

**仍有一项行为阻塞，需继续复核并修复后关闭：task lock 释放尚未等到旧 run 的领域清理完成。**

- `ChatRunLifecycle.kt:61` 对已交付 handle 只等待 `stopRun` 返回。`ChatService.stopGeneration(explicitRunId)` 给 runner/ownership job 发 cancel，不 join handler 的清理。
- `InProcessAgentRunner.kt:398–413` 在 job 仍可收尾时立即发布 CANCELLED snapshot；因此追加等待 runner terminal 也不能证明 durable owner 已释放。
- 真实 `ChatTurnAgent` 的 finally 执行 `hooks.onRunFinished`；`ChatService.kt:1600–1660` 先做 updateConversation/checkpoint 等挂起操作，随后才 `runTerminalStore.finish`。这些步骤尚未完成而旧 Worker 退出时，新 Worker 虽然拿到 task mutex，仍会被 `startTaskMessage:1019–1027` 的 durableOwner 检查判 busy，立即运行仍可能失败。

这违反本次 F2 的接续目标及用户规则 3、7：必须按真实 durable owner 验证，不能用仅锁住模拟 cleanup block 的 GREEN 替代生产收尾。建议以可控门阻塞实际 run-finalization，验证后继准备仍等待；等待应绑定本次 run 的领域清理，不能无条件 join 已可能处理后继用户消息的共享 dispatch。

本追加只读，未运行测试；已将证据发 root。原阶段记录保留，上述阻塞关闭后再追加最终结论。

## F2 最终 owner 补修复核

**上一条源码阻塞已关闭；当前未发现新的 Standards 或行为阻塞。最终 GREEN 待 root。**

- `AgentRunner.awaitRunSettled` 明确区分快速 terminal 信号与实际 activation 完成。InProcess runner 在 LAZY job start 前登记，completion 时移除；短 synchronized 只维护目标 run 的 Job 集合，`joinAll` 在锁外，没有持锁挂起或回取 task mutex 的路径。该集合记录现有工作的生命周期，不复制 durable 业务状态；原 gate-approved jobs、cancel intent 和快速 Stop 信号保留。
- ChatTaskRun 的正常完成与取消都等待该 run 的 handler、hook 与 durable finalization；随后重读最终 snapshot，不能再用乐观 CANCELLED 掩盖最终 WAITING_EXTERNAL。审批 pause 仍等待用户，取消仍只作用于捕获 runId。
- activeKernelRuns 的释放跟随 session dispatch Job completion，并以 runId/当前 job 保护后继 owner。handoff 只等目标 dispatch 完成或归属转移，不无条件 join 已接续下一条用户消息的共享 drain job。该等待链不反向进入 task execution mutex，未发现重入、死锁或新增脱离实际 owner 的永久 deferred。
- 已阅读新增 ChatTaskRun/ChatDispatchHandoff 门控回归，以及 InProcess 实际 Job 对 gate、handler finally、durable terminal、同 ID resume 和不同 run 的测试。它们针对此前遗漏的真实完成边界，不再仅证明 mutex 能锁住模拟 cleanup。

本追加只检查最后补修，无生产/测试修改，无 Gradle 执行。源码责任边界已满足，测试与最终集成结果仍须使用 root 实跑证据；没有据本报告宣称 GREEN、物理设备停止或网络取消时延验收。

### 最终全量前：session 归属窄复核

无新增阻塞。`runKernelDispatchLoop` 的 resume/new-message 两个入口均把现有 ConversationSession 传入 `dispatchKernelTurn`；finally 直接使用该引用注册 completion 清理，不再调用 `getOrCreateSession`。该变更保持当前 owner 身份，避免删除/移除会话后的收尾副作用重新创建 session，符合用户规则 5。没有新增业务状态、fallback 或等待路径。最终全量 GREEN 待 root，本复核未运行 Gradle。

## 最终验证关闭

本阶段 Standards review 与此前 F2 阻塞均已关闭，无剩余阻塞。已核对 root 的 `final-verification.json`：最终 JVM 22 模块退出码 0，3700 用例中 3676 实际通过、24 跳过、0 failure/error；app 为 2920 用例、14 跳过、0 failure/error，实际 InProcess runner 的 24 项均通过。F2 的 activation settlement、dispatch handoff 与最后 session 参数收口均已进入此轮编译测试，证据为 `final-jvm-tests.log`。Native 为 81 通过、0 失败、1 ignored，见 `final-native-tests.log`。先前阻塞、RED 证据和补修过程不删改。APK 构建由 root 继续完成；系统杀进程、闹钟、真实网络取消和物理设备不由本轮 JVM/Native 结果代替。本 reviewer 未自行运行 Gradle。
