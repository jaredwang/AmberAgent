# Audit F — 备份、文件、后台调度、WebMount、Terminal

审查基线：`d983c0b782c6eedce7c826f62003aa26973cc17c`。只读生产与测试代码，没有运行 Gradle，没有修改生产或测试文件。范围覆盖 `app/core/sync/export/files/storage`、备份 UI/VM、Cron/Reminder、Board/DeepRead Worker、WebMount 权限与租约/OAuth、`feature/terminal` owner。以下行号以审查时文件为准。

## 已二次复核的真实问题

### F-01 — Cron 把失败或等待人工批准的生成报告为成功（P1）

- 位置：`app/src/main/java/app/amber/feature/cron/AgentCronWorker.kt:57-73`。
- 入口：启用 Cron 后触发任务，或任务管理页手动运行；生成失败（无效凭据、模型错误等），或工具进入 `WAITING_USER`。
- 事实链：Worker 只等待 `generationDoneFlow` 中的 conversationId，收到后无条件 `markRunCompleted`、发成功通知并返回 WorkManager success。该事件并非成功终态：`ChatService.dispatchKernelTurn` 在 `ChatService.kt:1350-1361` 等待的是 `isTerminal || isPause`，失败、取消和人工批准暂停均会返回；`runKernelDispatchLoop` 在 `ChatService.kt:1184` 对该返回无条件 emit。ChatService 已另存 `lastRunOutcomes`，也说明该事件与成功状态有意分开。
- 影响：任务状态/通知谎报成功；需要人工批准的任务还没有执行完就被当成结束，并开始排下一次任务。
- 二次复核：不是“任务已派发即成功”的约定，因为 Worker 本身有等待完成、timeout、`markRunFailed` 和失败通知，明确需要记录执行结果；其当前等待信号选错了。
- 失败测试：注入可控的 ChatService/run facade，分别令本次运行变为 FAILED、WAITING_USER、COMPLETED；仅 COMPLETED 可写成功和发完成通知。暂停不能被当成完成，失败须保留实际错误。
- 最小修法：绑定本次 runId 的结果，读取真正的终态；复用 AgentRunner/现有状态流，不新增第二套任务状态机。不要仅靠 conversationId 的无结果事件判断成功。

### F-02 — Cron 删除、重排或超时不会停止实际模型/工具执行（P1）

- 位置：`AgentCronWorker.kt:63-87`；关联 `AgentCronManager.kt:146-179`（delete、manual REPLACE）与 `ChatService.kt:1096-1107`。
- 入口：任务正在进行模型或工具调用时，用户删除 Cron、修改/禁用 Cron，或再次“立即运行”；另有 Worker 的生成超时。
- 事实链：Manager 的取消/REPLACE 只停止 WorkManager 的 Worker。`ChatService.sendMessage` 是非挂起异步入口，`launchViaKernel` 用独立 `appScope.launch` 持有 dispatch job。Worker 的 CancellationException 只是重新抛出，TimeoutCancellationException 只是写失败，均未调用 `stopGeneration` 或取消本次 AgentRunner run。
- 影响：用户已删除/停止的自动化仍在执行付费模型调用、文件与外部工具副作用。再次运行可能把新 prompt 排在旧任务之后，旧 run 完成事件还可能错误结束新 Worker。
- 二次复核：不像 Terminal 的明确 detached job 契约，Cron 本身把 WorkManager 作为该运行生命周期并使用超时和取消控制；ChatService 的独立 appScope 表明取消无法结构化传播。DeepReadWorker 相同场景已显式 `runner.cancel(handle.runId)`，有本仓一致的可复用做法。
- 失败测试：阻塞一次 Cron run；取消 Worker 或触发超时；断言本次 run 收到 cancel 并停止产生工具调用。再启动新 run，断言旧 Worker 不能取消新 run。
- 最小修法：与 F-01 一起使用本次 runId 的 owner handle，在停止/超时路径取消该 handle；取消清理必须在可执行的取消上下文内完成，并保持 run-scoped ownership，避免 conversation-scoped 取消误伤后继任务。

### F-03 — Reminder 的旧快照排程可覆盖新提醒，最终永远不触发（P1）

- 位置：`app/src/main/java/app/amber/feature/reminder/ReminderScheduler.kt:94-121`。
- 入口：同一提醒的并发修改，或 Receiver 正在 fire 并重排时用户修改提醒。
- 事实链：`ReminderTools.kt:97-116` 在 store 锁之外调用 `scheduler.schedule(updated)`；Receiver 在 `ReminderReceiver.kt:48-64` 也先 commit 后 schedule。Scheduler 的独立 mutex 仅串行接收传入快照，并未读取 store 当前记录。A 得到旧快照挂起，B 写入新时间并完成 schedule，A 随后 schedule 旧快照。Android adapter 的 PendingIntent 按提醒 ID 保持稳定，因此 A 覆盖 B 的新闹钟。旧闹钟到达时 Receiver 又会用持久记录的 fireKey 正确拒绝它；新的 fireKey 没有闹钟，提醒静默漏发。
- 二次复核：store CAS 与 scheduler mutex 各自正确，但二者没有覆盖同一 owner 转换；Receiver 的 stale fire 拒绝机制无法自行修复被覆盖的闹钟。不是假阳性或仅重复通知问题。
- 确定性失败测试：`store.upsert(A)`；`store.upsert(B)`；`scheduler.schedule(B)`；`scheduler.schedule(A)`。用按 reminderId 保存最终请求的 RecordingAlarmOperations 验证最终闹钟必须仍对应 B。再覆盖旧 disabled 快照不能取消当前 enabled B，以及删除后旧 schedule 不得复活闹钟。
- 最小修法：scheduler 持锁时按 ID 读取 store 最新记录，再排程或取消；复用/合并已有 `scheduleLocked`，不新增状态版本框架。该锁内的 store read 是非挂起读，平台 schedule 也是同步操作，可避免过期快照覆盖已完成的新排程。

### F-04 — 备份恢复中断后的文件 journal 只在再次恢复时处理，普通冷启动直接读坏数据集（P1）

- 位置：`app/src/main/java/app/amber/core/sync/core/SyncArchiveManager.kt:469`、`:911-926`、`:939-965`。
- 入口：恢复 EVERYTHING 且包含文件根替换时，进程在文件根已搬走/替换、DB 事务提交前被杀死或崩溃。
- 事实链：`prepareFileTreeRestore` 先把当前 upload/chat_images 等搬到 `cacheDir/sync-restore-file-journal/backup`，再拷入恢复源；之后才在 `restorePayloadFromStage:656` 调用 `restoreTables`。进程中断会留下旧 DB 与新/不完整文件。`recoverInterruptedFileRestore` 本身能够按 DB marker 回滚，但它是 private，且全仓唯一调用点是下次 `restorePayload:469`；App 冷启动与普通 DB/文件消费者没有 recovery 入口。
- 影响：重启后旧聊天仍在，但附件与生成图消失或被替换；用户必须再次发起备份恢复才能触发回滚。后续写入还可能改变中断现场。
- 二次复核：现有 IntegrationTest 的 interrupted 场景全部通过再次 restore 才恢复，证实测试没有覆盖普通启动。文件恢复 journal 的存在和注释表明需要处理进程中断；“只在下一次 restore 恢复”无法保护重启后的日常使用。
- 失败测试：按已有 interrupted journal fixture 构造 `files_replaced` 状态、保留原文件于 backup 与旧 DB；执行真实冷启动初始化/仓库打开路径，不发起新 restore；断言原文件已回滚且 journal 清理。另对有 DB commit marker 的 fixture 验证保留导入文件并修复衍生索引。
- 最小修法：复用既有 recovery 方法，放到会话/图库/文件与后台写入可开始之前的初始化屏障；同时通过现有 restore gate 通知会缓存文件状态的 owner。不要再建第二套恢复事务或重试状态机。必须验证 bootstrap 时序，不能只在 BackupVM init 延迟调用。

### F-05 — Cron 终态通知使用前台 ID，Worker 退出清理会把结果通知一同删除（P2）

- 独立 Phase 2 Spec review 新增并由 root 接受；与 Novel D3 的系统清理机制相同。
- 旧位置：`AgentCronWorker.sendCronNotification` 与 `createForegroundInfo` 都使用 `task.id.hashCode()`。
- 入口：Cron 运行成功或失败后先发布终态通知，随后 Worker 结束，WorkManager 按 ForegroundInfo 的 ID 移除前台通知。相同 ID 已被结果通知覆盖，因此结果通知也消失。
- 二次复核/RED：提取并真实接线最窄的 foreground/result ID helper，先保留相同 hash；`AgentCronNotificationTest` 使用生产 ID 和真实 Android NotificationManager 发布运行/结果，再执行相同 cancel(foregroundId)。root 实际取得 success/failure 清理及 ID distinct 三项失败，日志为 `/tmp/amber-review-20261018-green-phase2-ui-red-cron-notification.log`。
- 最小修法：仅把结果 ID 改成 `task.id.hashCode() xor Int.MIN_VALUE`，保持稳定且与同任务的前台 ID 必然不同。不抽取通知模块，不改变通知正文、点击路径或审批行为。

## 已排除的假阳性 / by design

- 旧 `BackupArchiveUtils.replaceDatabaseFilesFromTemp` 的备份/回滚失败处理不完整，但生产只有旧 `WebDavSync`/`S3Sync` 类关联；全仓没有这两类构造、DI 或其他调用。归 dead code 候选，不作为真实生产问题，更不擅自删除。
- `ReminderRecurrence.DAILY/WEEKLY` 使用固定 epoch 24h/7d，已有显式注释与 DST 测试，属于约定，不改为当地日历时间。
- Reminder receiver 的持久 fireKey CAS 正确拒绝 stale/duplicate fire，不是重复通知缺陷；实际问题在 F-03 的排程 owner。
- Terminal 的长任务返回 detached job 是 API 契约；Termux/SSH 无法证明外部进程已结束时记录 INTERRUPTED 且告知可能仍运行，是明确设计，不能改成伪装 CANCELLED。
- Terminal 的 WorkspaceMirrorLease.release 实际包在 NonCancellable + mirrorMutex 内，取消时不会因普通挂起取消而泄漏 activeTerminalMirrors。
- DeepReadWorker 等待 AgentRunner 时遇取消会显式 cancel 本次 run；Board/DeepRead durable writes 有 SyncRestoreWriteEpoch 链，未找到可证实的 stale restore write。
- WebMount 的签名 fetch 有当前 origin、外发 origin/permission、shim call permission 与 lease gate；交互工具有审批。没有将已有权限约束误报成“任意网页可直接调用”问题。
- Sync DB 导出表处于同一 SQLite 读事务；文件与 DB 无原子共同快照是代码明确记录的当前边界，缺乏可重复具体故障前不扩大成新全局锁设计。

## 建议修复阶段

1. Cron owner 修复：F-01/F-02 合并处理，按 runId 取得真实结果和传递取消，覆盖失败、暂停、超时、删除和后继 run 不被误伤。
2. Reminder owner 修复：F-03，限定在 scheduler/current-store 状态接线，重放 stale snapshot 测试。
3. 恢复启动一致性：F-04，先定位最早共享初始化屏障再接入现成 journal recovery；验证未提交回滚与已提交保留两类场景。

每阶段后由独立 subagent 复核本阶段 diff 与可达入口；本审查包未替代阶段 review，也未宣称已执行测试。
