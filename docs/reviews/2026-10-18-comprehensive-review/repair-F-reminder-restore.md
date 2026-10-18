# Phase F：提醒排程与中断恢复

## 二次复核

- 提醒旧快照覆盖：确认为真实竞态。工具先持久化快照再调用 scheduler；receiver 先 `commitFire` 再排程。A 持久化旧状态后延迟、B 持久化新状态并排程、A 最后排程时，scheduler 使用 A 参数；AlarmManager 的 PendingIntent 身份固定为 reminderId，A 覆盖 B。receiver 依据 durable fireKey 拒绝 A 后不再重排 B，因此漏提醒。
- 恢复 journal 启动缺口：确认为真实问题。文件根先备份/替换，随后 SQLite 才提交。journal 已有完整 rollback 与 committed marker，但唯一恢复入口位于下一次 `restorePayload`。普通重启会使旧 DB 与新/不完整文件共同暴露给消费者。

## 最小修复路径

1. Scheduler 仅把调用者快照用于取得 ID；持有 scheduler mutex 后读取 store 当前状态，再复用一份排程实现；记录已删除则取消残存 alarm。不引入重试、第二份状态或新协调框架。
2. 复用 SyncArchiveManager 的现成 journal 恢复，接在 Koin `modules(...)` 后、`workManagerFactory()` 前，同步完成后才创建 eager consumers。无 journal 的普通启动不解析完整恢复依赖链。中断恢复沿用既有 marker 判断，未提交回滚、已提交保留并重建派生索引，已恢复 Novel 原生快照须保留 adoption 语义。

## 启动时序证据

- AndroidManifest 已移除 AndroidX 默认 WorkManager initializer。
- 本机 Koin WorkManager 4.2.1 AAR 的 `workManagerFactory()` 直接调用 `WorkManager.initialize`，因此不能在其后另起异步恢复。
- 本机 Koin core 4.2.1 jar 的 `GlobalContext.startKoin(lambda)` 先运行 lambda，再 `createEagerInstances()`。在 lambda 中注册 modules 后同步恢复能够位于 eager singleton 之前。
- 恢复 owner 的 WorkspaceManager 只在构造时读取 SharedPreferences；FilesManager/MessageFtsManager 无构造副作用。SettingsAggregator 的 collector 不读取待替换文件根。生产恢复方法不得等待 Main dispatcher，因为 Application 在主线程等待恢复。

## 失败回归与修复

- `ReminderOwnerTest` 新增旧 enabled、旧 disabled、删除后三种确定性交错；RecordingAlarmOperations 按 ID 保留最终 alarm，断言接收端仍可接受当前 fireKey。
- `SyncRestoreStartupWiringTest` 固定真实生产入口的时序：依赖注册后完成恢复，先于 WorkManager 与 eager consumers，且必须同步等待。
- root 已实跑 Phase 2 RED：三个 Reminder 旧快照测试与启动接线测试均失败，日志 `/tmp/amber-review-20261018-red-phase2-ui.log`。
- 已完成生产修复：所有提醒入口复用 `scheduleLocked(id)`，在 scheduler mutex 内读取 store 当前状态；恢复入口 `recoverInterruptedRestore()` 复用原 journal/DB marker 恢复实现与现有 gate。
- AmberAgentApp 仅在有 journal 时显式解析既有 `NovelWorkspaceRestoreBridge`，同步恢复结束后才初始化 WorkManager。NovelModule 的既有取消 closure 仅在 WorkManager 已初始化时取消任务，冷启动尚无可运行 worker；没有增加 adoption 并行路径。

## 真实 journal / Room 验证覆盖

- `startupRecoveryRollsBackUncommittedFilesWithoutAnotherArchiveRestore`：直接执行启动恢复入口，回滚 upload 与原生 Novel 根、保留旧 Room 文件记录、不误写 native adoption，确认既有 bridge 收到生命周期。
- `startupRecoveryKeepsCommittedFilesAndDatabaseAndRebuildsDerivedFts`：journal 仍为 files_replaced、DB marker 已提交时，保留导入文件/Room 数据，清除旧 FTS 派生行及提交 marker；不重新提供 archive。
- `startupRecoveryAdoptsCommittedNativeNovelSnapshotThroughExistingBridge`：已提交原生 Novel snapshot 仍由现有 bridge 标记，阻止遗留自动迁移覆盖已采用状态。
- source-order 测试另外锁定 bridge 在恢复前注册。它只补充生产接线证据，不代替上述文件/Room 行为断言。

## 维护检查与当前验证边界

- 按 code-simplifier 对本工作包做过一次有限检查：去掉 scheduler 两份排程实现；恢复继续使用既有 journal、gate、bridge，不引入新 owner、重试、版本或兜底。启动依赖无 cycle，不等待 Main dispatcher 的 collector。
- 本工作包 `git diff --check` 通过；Gradle 由 root 统一运行，agent 未运行 Gradle、提交或推送。
- 当前生产和测试已稳定，等待 root Phase 2 GREEN 与独立 subagent review。未进行物理设备闹钟/系统杀进程验收，不以 JVM/Robolectric 代替这些验收。
