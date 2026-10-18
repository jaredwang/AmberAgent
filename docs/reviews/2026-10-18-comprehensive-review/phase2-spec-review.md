# Phase 2 独立行为复核

基准：`d983c0b` 与当前 Phase 2 diff。范围 B1、F1–F4、D1–D4；Phase 1 不重复审查。只读核对生产入口、owner 状态及回归；未运行 Gradle，执行结果由 root 统一记录。

## 初次结论：需要修复

### P2 / Cron 终态通知仍被 WorkManager 前台清理删除

- 位置：`app/src/main/java/app/amber/feature/cron/AgentCronWorker.kt:119-120,134-140`。
- 触发：Cron 正常完成、生成失败或超时后，`sendCronNotification` 使用 `task.id.hashCode()` 发布终态通知；`createForegroundInfo` 使用同一个 ID。`doWork` 返回后，WorkManager 删除对应前台 ID，同时删除刚发的终态通知。
- 影响：F1 已正确区分本次 run 的成败，但用户离开页面后仍无法在通知栏保留成功结果或失败原因。
- 二次复核：与本阶段 D3 已确认的 AndroidX `SystemForegroundDispatcher.onExecuted` 行为完全相同；不是有意只显示运行进度，因为 Worker 有独立的 completed/failed 文案、状态类别与会话入口。
- 精确修法：沿 DeepRead 的现有处理分离稳定结果 ID 和运行 ID；用实际 NotificationManager 发布终态，再模拟前台清理，覆盖成功和失败。无需增加通知框架。

## 其余行为检查通过

- B1：全部同 run resume 都将 preLaunchSnapshot 交给 activation barrier；等待异步 gate 后的新 activation，再接受 pause/terminal。正常暂停释放 session 的契约保留。
- F1/F2：Worker 经 Main admission 检查空闲 owner，持有首个 AgentRunHandle，按该 run 的真正终态判成功，WAITING_USER 继续等待。取消覆盖 dispatch 未启动和 Main 返回交接；取得 handle 后只按 runId stop，既有 stop owner 校验可拒绝后继 run。旧 conversationId 无结果 completion signal 已移出 Worker。相关回归直接验证新的生产 lifecycle owner；未把 fake runner 当作真实 Worker 设备验收。
- F3：scheduler 持自身 mutex 按 ID 重读当前 store；旧 enabled/disabled、删除后的延迟 schedule 均不再恢复旧 alarm，既有 fireKey CAS 不变。
- F4：已有文件 journal 在 WorkManager 初始化与 eager consumer 前同步恢复；无 journal 不解析完整恢复链。DB marker 决定回滚或保留；通过现有 gate 与提前注册的 Novel bridge 保留 native adoption。依赖构造未见等待 Main 的恢复调用；Room/文件集成回归覆盖两类恢复及 bridge。
- D1/D4：planned 继续注入未回收约束；仅显式 resolved 排除。伏笔批准写入投影 kind 与 open/resolved，宿主 ID 保留；原 planned 重写及普通章节身份规则不变。
- D2：ZIP 与成书均在已有 owner monitor 内读取不可变 snapshot，ZIP 压缩/输出与成书渲染在锁外；真实 owner 事务交错回归覆盖等待与版本一致性。
- D3：DeepRead 的结果 ID 与前台 ID 已分离，真实通知清理回归保留历史路由。

除 Cron 通知外未确认其他问题；真实 provider、系统杀进程、闹钟与通知设备验收仍未执行。

## F5 补修复核：关闭

Worker 的 createForegroundInfo 使用 cronForegroundNotificationId，成功/失败通知使用 cronResultNotificationId，稳定结果 ID 与本任务前台 ID 分离。实际 NotificationManager 清理回归覆盖成功、失败和 ID 区分；当前测试 XML 为 3 项、0 failure/error。原始发现保留，F5 已关闭。

## F2 manual REPLACE 补修复核：仍需要修复

新增 taskId execution Mutex 的范围正确：覆盖 prepare 前直到 Worker finally 之后，独立于短 mutationMutex；未见重入链，等待者可取消，不同 task 不互锁。已核实受控 cleanup/等待者两项 RED 日志。

但实际旧 run 的收尾没有纳入该范围：ChatRunLifecycle.kt:56-61 在 handle 已交付时只等待 stopRun 返回；ChatService.stopGeneration 对显式 runId 仅请求 AgentRunner.cancel。InProcessAgentRunner.kt:380-412 的 cancel 立即发布 CANCELLED snapshot，handler 与 durable terminal CAS 在独立 runner coroutine 中异步收尾。因此旧 Worker 可释放 task Mutex，而同 task replacement 的 startTaskMessage 在 ChatService.kt:1019-1027 仍读到旧 active durable owner，按 busy 拒绝并丢失本次手动执行。只等 snapshot terminal 也无法消除此窗口。

二次复核：可在真实 runner 的取消 terminal transition 挂起时稳定触发；当前新增 mutex 测试使用人工 cleanup barrier，未覆盖 stopRun 已返回而 durable owner 仍存在的状态。需等待这个旧 run 的实际 owner 收尾后才交接任务锁，保持 explicit runId，不 join 可能已处理后继用户消息的 dispatch job，也不接管其他用户 run。本次未运行 Gradle，等待补修后再复核；Phase 2 尚不能最终 pass。

## F2 最终补修：代码复核通过，GREEN 待 root 执行

原始发现保留。新增 AgentRunner.awaitRunSettled 是必须实现的精确 run 屏障；真实 InProcessAgentRunner 在 LAZY activation 启动前登记 Job，Job 完成才移除。它不依赖提前 CANCELLED snapshot，也不改变 gate-approved owner map 或快取消信号。生产 ChatTurnAgent 的 onRunFinished、handler finally 与 runner 的 NonCancellable terminal CAS 均在该 Job 生命周期内，因此之前缺失的 durable owner 收尾现在包含在等待中。

ChatTaskRun 正常等待和取消都消费该屏障；正常等待在 settle 后重读 snapshot，若快 CANCELLED 被持久状态校正为 WAITING_EXTERNAL，会继续等后续真实终态。相同 runId 的审批 activation 使用当前登记集合；无关 run 不参与等待。

ChatService 的 active map 清理移到 dispatch Job 完成回调，并同时核对 runId 与真实 session job。相同 ID 的较新审批 dispatch 不被旧 completion 清掉。awaitChatRunDispatchHandoff 只等本 run 的 session 释放或 map 转移；队列开始后继用户 run 时立即交接，不 join 后继模型。任务锁仍按 taskId，未改用户 run 的 admission 或 cancellation 权限。

已读取真实 InProcessAgentRunner 新回归源码：launch gate 前取消、handler finally 与 durable cancellation 双屏障、同 ID 审批 resume、无关 run 正控；另外检查 ChatTaskRun 的 WAITING_EXTERNAL 重对齐与 ChatDispatchHandoff 的真实 session job 场景。它们不只是手工声明 Worker cleanup 完成。当前未确认剩余行为阻塞；测试尚由 root 执行，本文只关闭代码复核问题，不提前声明最终 GREEN。

最后窄复核：dispatchKernelTurn 的两个生产 caller 均传入 runKernelDispatchLoop 已持有的同一 ConversationSession；finally 复用该引用，不再 getOrCreateSession。清理 sessions 后迟到回调不会因此重建 session，run/job 条件清理与后继 handoff 语义保持。此收口源码审查通过；最终受影响测试仍待 root 的统一 GREEN 记录。

## 最终验证关闭：通过

已读取 root 的 final-verification.json 与 final-jvm-tests.log：最终 JVM gate exit code 0，22 个测试模块共 3700 项，0 failure/error、24 skipped，实际通过 3676 项；app 2920 项、0 failure/error、14 skipped；core/agent-runtime-impl 的真实 runner 24 项全部通过。F2 settlement/dispatch handoff、taskId Mutex、F5 通知与最后 session 引用收口均已进入此轮编译测试。此前 RED 和发现保留，最新结论为 Phase 2 独立行为审查与回归验证通过，无未关闭的确认问题。Native 独立 gate 为 81 passed、0 failed、1 ignored。普通配置 APK 构建进行中；设备 WorkManager、真实 provider、系统杀进程和闹钟验收不在这次 JVM 证明范围内。
