# Phase 2 — Chat resume 与 Cron run owner

范围：B1、F-01、F-02，以及独立 Phase 2 review 追加接受的 F-05。除 ChatService、ChatRunLifecycle、CronWorker/CronManager 与对应回归，F2 真实清理屏障按 root 授权扩展至 AgentRunner 的明确 `awaitRunSettled` 契约及唯一真实实现；root 实施两处 core 生产代码，本 worker 补 core/app 回归与三个测试 fake。没有修改 Reminder 或 Sync startup；保留其他 agent 与用户原有改动。

## 修复前证据

- B1 已先把现有等待逻辑做等价提取，保留 `expectedPausedRunId != null` 的错误条件。root 在 `/tmp/amber-review-20261018-red-phase2-ui.log` 实际取得 `ChatDispatchActivationTest.ordinary approval resume waits for its activation before accepting another pause` 失败；自动恢复、取消和 fresh dispatch 基线保持通过。
- F-01/F-02 的旧代码证据是生产接线：CronWorker 等 conversation-level `generationDoneFlow` 后无条件成功；ChatService 在失败/暂停时也会 emit，且实际生成由独立 appScope launch。旧 Worker 没有本次 runId 的取消入口。没有把新 owner 的绿色单测冒充旧 Cron 的失败复现。
- F-05 先提取并真实接线 foreground/result ID helper，保留两者同 hash；root 在 `/tmp/amber-review-20261018-green-phase2-ui-red-cron-notification.log` 实际取得 success/failure 清理及 ID distinct 三个回归失败。修复只把结果 ID 的最高位翻转，与运行前台 ID 分离。
- 独立 review 再次证明，WorkManager 任务锁必须等待真实 run 的清理，不能只等待快速 `stopRun` 或 `CANCELLED` snapshot。root 在 `/tmp/amber-review-20261018-red-cron-actual-cleanup.log` 实际取得新增 `ChatTaskRunTest` 的真实 cleanup 阻塞回归失败；该测试的 stop 已返回、CANCELLED 已可见，但异步清理 Job 仍未完成。此后补齐下述明确的 run settlement 和 dispatch handoff 契约。

## 最终行为

1. 所有 same-run resume 都使用已有 immutable snapshot barrier；普通审批恢复也会等到这次 activation 获得 owner 后的新快照。真正的 terminal cancellation 仍能解除 gate 等待。
2. Cron 使用 `ChatService.startTaskMessage` 接收首消息的 `AgentRunHandle`，只观察该 runId 的真正 terminal。FAILED/CANCELLED 进入失败分支；WAITING_USER 等待用户处理，不能提前发成功通知。
3. Task admission 在 Main 上完成，先等既有冷启动恢复 gate。正在运行、已有队列或待处理/可恢复 durable owner 的会话明确拒绝本次 Cron，避免自动任务接管其他工作。普通 `sendMessage` 入口与既有消息队列语义不变。
4. Launch handoff 只属于首条消息。后续排队用户消息仍由原 drain loop 正常处理；旧 task 的取消只调用 `stopGeneration(conversationId, explicitRunId)`，不能取消正在处理后继消息的 dispatch job。
5. 未交付 runId 时取消启动 dispatch；`invokeOnCompletion` 保证 LAZY/尚未进入 body 的取消也能完成 handoff，避免 NonCancellable cleanup 永久等待。
6. Main 到 Worker 返回 handle 的取消窗口由 `admitChatTaskRun` 回收已创建但未交付的 owner。超时/取消清理由现有 run-scoped stop 执行，沿用已有 server-cancel 与 durable terminal 策略，没有新增任务状态框架、持久队列或通用重试。
7. Cron 的成功/失败通知使用独立的稳定结果 ID；WorkManager 退出时移除前台通知不会一并清掉结果。通知内容与点击 PendingIntent 保持原实现。
8. `AgentRunner.awaitRunSettled(runId)` 是无默认空实现的明确接口。真实 runner 在 launch 返回/Job 启动前登记该 run 的 activation Jobs，在 `invokeOnCompletion` 移除，短同步只保护集合；等待发生在同步区外。原先 gate 批准后的 cancellation-owner `jobs` map 与快取消行为保留。Task 的 await/cancel 都等待这一真实屏障，随后重读 snapshot，不能把真实收尾后改回 WAITING_EXTERNAL 的乐观 CANCELLED 当成完成。
9. `activeKernelRuns` 的删除延至真实 session dispatch Job 完成；同时核对 runId 与 generationJob 身份，旧 pause Job 的完成不能删掉同 runId 的新审批 resume。Task 真实 run settle 后，使用现有 ConversationSession job 与 activeRun 归属等待当前 dispatch 完成或转给后继 run，不等待后继用户模型，也不新增 Task 的重复 owner map。
10. 最后一次生命周期收口把调用者已有的 ConversationSession 引用直接传给 dispatchKernelTurn 并在 finally 复用；App cleanup 清空 sessions 后，迟到的 finally 不会为绑定完成回调重新创建 session。只涉及两个既有私有调用点与方法参数，未增加恢复分支或更改发送行为。

## 同任务 manual REPLACE 回归补充

新 admission 对其他工作 busy 的明确拒绝仍然必要，但不能让同 task 的 manual REPLACE 丢失原先可执行的后继 prompt。当前 WorkManager 2.11.2 的本机 aar 字节码证实：Processor 先从私有 map 移除旧 wrapper，再只调用 workerJob.cancel，不等待旧 NonCancellable cleanup。public WorkInfo/Operation 不提供这段清理的完成 future，因此不能靠 WorkInfo.CANCELLED 等待。

root 已接受最窄修法：在既有 CronManager 以 taskId 分离执行 Mutex，Worker 从 prepare 前直到旧 owner 的完整清理结束才释放，不同 task 继续并发；不复用短持久化 mutationMutex，不等待无关用户 run，不新增 retry/持久队列。先落等价无锁 `withTaskExecution` seam 后，root 在 `/tmp/amber-review-20261018-red-cron-replace.log` 实际取得同 task cleanup/新 prepare 与等待者取消两个失败、不同 task 正控通过；随后启用了实际 taskId Mutex。字节码证据在 `/tmp/amber-review-work-processor.txt` 和 `/tmp/amber-review-work-wrapper.txt`。

## 回归与验证边界

- `ChatDispatchActivationTest`：普通 resume 的旧 pause、激活后立即再次 pause、RUNNING 到完成、gate 期间取消、fresh dispatch。
- `ChatTaskRunTest`：失败终态和原始错误、审批暂停后继续、timeout 精确停止、启动 gate 期间取消、dispatch body 从未进入时取消、admission 返回窗口取消、旧 task 不取消后继 dispatch。
- `ChatTaskRunTest` 补充 stop 早返回而实际 cleanup 阻塞、正常观测 CANCELLED 也等真实收尾、最终 snapshot 回到 WAITING_EXTERNAL 后继续等待。
- `ChatDispatchHandoffTest` 直接使用生产 ConversationSession 与生产 handoff helper，验证 Stop 收尾、同 drain job 转后继不等后继模型、同 ID 审批 resume 的旧 Job 不得删新 owner，以及 pause Job 完成后的归属释放。
- `InProcessAgentRunnerLaunchGateTest` 新增真实 runner 层回归：launch gate 执行前取消、handler finally 与 durable cancellation CAS 双 gate、同 ID resume 清理、目标 settlement 不等待无关 run。
- `AgentCronNotificationTest` 使用真实 Android NotificationManager 与生产 ID seam 验证成功/失败结果在 foreground cleanup 后保留；没有把该测试描述成完整 Worker 调度测试。
- `AgentCronManagerConcurrencyTest` 追加同 task cleanup/新 prepare 串行、不同 task 并行、等待中的 replacement 取消三个场景；原有 WorkManager REPLACE/disabled 手动运行断言保留。
- 本 worker 的定点 `git diff --check` 通过，按授权没有运行 Gradle。生产与测试文件已稳定，统一 GREEN、编译和独立 phase review 由 root 执行，不能提前宣称通过。
- root 的补修定点测试已确认 `AgentCronNotificationTest` 三项通过；同 task replacement 的两个测试先取得 RED，启用 Mutex 后仍需 root 统一 GREEN。新 owner 测试属于本次 owner 的行为回归，仍与旧 Cron 源码接线证据区分。
- 本次没有真实 provider、WorkManager 物理设备任务、安装或用户审批体验验收；owner 测试使用受控 AgentRunner，Worker 到 ChatService 的真实接线仍需独立 review 检查。

## 一次有限可维护性检查

按 `code-simplifier` 只检查本次自有 diff：启动归属、handoff、terminal 观察、取消及错误归属均集中在 `ChatRunLifecycle` 与 `startTaskMessage`；普通聊天没有新增并行状态。保留原 drain loop 和 explicit run-scoped stop，未扩展到其他生命周期重构。无额外行为重构建议纳入本阶段。
