# Android 选定差异补全 · P1 跨对话活动中心执行记录（2026-11-01）

> 本文件是 P1 专用记录，不修改 `2026-10-31-android-selected-parity-handoff.md`。
> 施工提交停在本轮；未获主代理验收不得进入 P2。真机安装/启动/操作仍禁止。

## 1. 基线

- 工作分支：`codex/android-p0-ocr`
- 父提交：`b9682dd55 fix(ocr): P0 OCR 截图新鲜度、块去重、取消与脱敏返工`（主代理代码验收通过，真机仍待统一验证）
- 三个既有未跟踪文档保持不提交。

## 2. 状态映射（显式）

`conversationActivityKindFor(RunTerminalState)`：

| RunTerminalState | ConversationActivityKind | 理由 |
| --- | --- | --- |
| `RUNNING` | `RUNNING` | 现有运行提示 |
| `WAITING_EXTERNAL` | `RUNNING` | 仍在飞行中，沿用运行提示 |
| `RESUMABLE` | `RUNNING` | 冷启动恢复中的 in-flight，沿用运行提示 |
| `WAITING_USER` | `AWAITING_USER` | 工具批准 / 提问 |
| `OUTCOME_UNKNOWN` | `AWAITING_USER` | 用户必须确认重试或放弃；与 `ImageGenerationContinueSource` 一致 |
| `FAILED` | `FAILED` | 失败 |
| `STEP_LIMIT` | `FAILED` | 异常终态，不得映射为 COMPLETED |
| `OUTPUT_LIMIT` | `FAILED` | 同上 |
| `GUARD_STOPPED` | `FAILED` | 同上 |
| `INTERRUPTED` | `FAILED` | 进程死亡后不可恢复的终态 |
| `COMPLETED` | `COMPLETED` | 正常完成 |
| `CANCELLED` | （不投影） | 用户主动停止，不跨对话提醒 |

优先级：**等待用户 > 失败 > 完成 > 运行中**。同一对话只留最重要且最新的一条（kind rank，然后 `versionMs` 新者优先）。

`versionMs = finishedAtMs ?: updatedAtMs`。进程内 `ActiveRun` 的 version 用 `registeredAtMs`。同一 `runId` 上 **registry 覆盖 terminal**，避免 active→terminal 交接出现两条或空窗闪烁。

## 3. 已读水位与首次升级基线

不另建 run 状态库。水位落在同一 `AppDatabase` 的两张新表：

- `conversation_activity_seen`：PK `run_id`，列 `conversation_id` + `version_ms`
- `conversation_activity_baseline`：单行 `id=1`，`established_at_ms`

规则：

- `RUNNING` / `AWAITING_USER`：未被该 run 的 `versionMs` 覆盖则未读（含升级前就在等的 WAITING_USER）。
- `FAILED` / `COMPLETED`：仅当 `versionMs > baseline` 且未见过才提醒，升级时历史终态不会轰炸。
- `markActivitySeen`：先 `UPDATE ... WHERE run_id=? AND version_ms < ?`，再 `INSERT OR IGNORE`，冲突后再 UPDATE 一次，避免新旧版本交叉写入。
- 错配 `runId` 只可能写入另一行，清不掉目标 run 的提醒。
- 同对话新 run 或同 run 更高 version 可再次提醒。
- 进程重启读同一 Room 行，已读仍有效。

collect 启动：`notices()` / `consumeWhileOpen()` 都先 `ensureActivityBaseline(now)` 再 combine。基线 `INSERT OR IGNORE`，后到的 collect 复用原时间戳。baseline 写入之后才出现的终态 `versionMs > baseline`，不会被 seed 吞掉。

导航已读：

- 点击面板：`rememberOpenedNotice`（进程内）+ `markSeen(点击的 runId+version)` + 导航。
- 目标页 `LaunchedEffect` 调 `consumeWhileOpen`：若有 pending，首帧只标点击版本；首帧快照（含更新的 version）记为 initial，**自身 markSeen 引起的 Flow 重放不得把未看到的新 version 标掉**。
- 直达打开（无 pending）：首帧即标已见（用户正在看这条对话）。
- 停留期间 candidate 相对首帧发生变化（新事件）才继续 markSeen。

## 4. 生产路径（UI gate → domain/CAS → 持久化）

1. `AgentInfraModule`：`ConversationActivityCenter(registry, terminals=RunTerminalStore)`，标题仍走 `ConversationRepository`。
2. `ChatPageScaffold`：`notices(excluding = conversation.id)`；点击 `rememberOpenedNotice` + `markSeen` + `navigateToChatPage`；`LaunchedEffect(conversation.id)` 调 `consumeWhileOpen`。
3. `ChatActivityPanelHost` / `ChatActivityPanelContent` 按 kind 换图标和文案（Radio / Clock / CircleAlert / CircleCheck），不再写死「正在运行」。
4. 独立 48dp `ChatActivityButton`、与产物架 / 活动岛 / 回顾互斥、时间线滚动位置均未改。
5. 空列表 / loading fallback 保留；标题查询失败回落 id 前 8 位；`CancellationException` 继续上抛。
6. `RunTerminalDAO.observeActivityCandidates()` 是全局 Flow，CANCELLED 不进投影。

## 5. Schema / migration

- `AppDatabase` 21 → **22**。
- `MIGRATION_21_22` 建 `conversation_activity_seen`（含 conversation_id 索引）和 `conversation_activity_baseline`。
- KSP 导出 `app/schemas/app.amber.agent.data.db.AppDatabase/22.json`。
- `DataSourceModule` 注册该 migration。
- 两表加入 `SyncArchiveManager.SYNC_TABLES`（备份恢复后已读水位仍在，避免恢复后轰炸）。
- `AppDatabaseMigrationTest.migration_21_22_creates_activity_watermark_tables` 已写；**androidTest 真机/模拟器未跑**。

## 6. 测试命令与结果

定点 JVM（Robolectric SDK 34）：

```bash
./gradlew :app:testFullDebugUnitTest --offline \
  --tests "app.amber.feature.runtime.ConversationActivityCenterTest" \
  --tests "app.amber.feature.runtime.ConversationActivityWatermarkStoreTest" \
  --tests "app.amber.feature.runtime.RunTerminalStoreTest" \
  --tests "app.amber.agent.data.sync.SyncArchiveTableCoverageTest"
# 51 tests, 0 failures, 0 errors, 0 skipped
# 日志 /tmp/amber-p1-activity-center-tests.log
```

分类：

- `ConversationActivityCenterTest` **24**：四态/异常终态映射、OUTCOME_UNKNOWN、CANCELLED 隐藏、优先级、去重最新、active→terminal 无重复无空窗、当前对话排除、标题空白/查询失败/取消、历史基线、新终态、打开目标只清对应 run、错配 runId、CAS 旧 version 不盖新、新 run 再提醒、直达已读、导航不标未看到的更新 version、打开后新事件已见。
- `ConversationActivityWatermarkStoreTest` **8**：Room 查询不含 CANCELLED、基线写一次且重启有效、CAS 前进/拒绝回退、错配 runId 分列、持久化水位重启后仍已读且新 run 再提醒、升级不轰炸历史完成、升级前 WAITING_USER 仍提示、registry+Room 合并仍一条 RUNNING。
- `RunTerminalStoreTest` **16**：既有 P1-03 状态机，确认接口扩展未破坏。
- `SyncArchiveTableCoverageTest` **3**：schema v22 表与 SYNC_TABLES 对齐。

编译：

```bash
./gradlew :app:compileFullDebugKotlin --offline
# BUILD SUCCESSFUL
# 日志 /tmp/amber-p1-compile-fulldebug.log
```

本地化：`python3 scripts/check_android_localization.py`。本轮新增的 `chat_activity_kind_{waiting,failed,completed}` 与更新后的 empty/button 文案六套 locale 均在。脚本仍 FAIL 的是既有缺口（ja/ko/ru 缺 `chat_shelf_kind_html` / `live_update_*` 等、zh 缺 grok oauth waiting），不是本轮引入，未顺手修补。

未修 `AntigravityOAuthSessionTest`。

## 7. 剩余真机事项（本轮不做）

统一留到选定差异补全最后一轮：

- 安装/启动/操作真机。
- Room `MIGRATION_21_22` 在真实库文件上的 migration 与 `AppDatabaseMigrationTest`。
- 跨对话：等待 / 失败 / 完成 / 运行按钮与面板、产物架/活动岛/回顾互斥、定位与时间线滚动。
- 冷启动后已读仍有效、升级不轰炸、打开目标只清对应提醒、当前打开对话新事件已见。

## 8. 本轮文件

- `ConversationActivityCenter.kt` / `RunTerminalStore.kt` / `RunTerminalDAO.kt`
- `ConversationActivitySeenEntity.kt` / `ConversationActivityBaselineEntity.kt`
- `AppDatabase.kt` + `MIGRATION_21_22` + schema `22.json`
- `AgentInfraModule.kt` / `DataSourceModule.kt` / `SyncArchiveManager.kt`
- `ChatPageScaffold.kt` / `ChatPagePanels.kt` / `ChatTopBarPanels.kt`
- 六套 `strings.xml`
- `ConversationActivityCenterTest.kt` / `ConversationActivityWatermarkStoreTest.kt`
- `AppDatabaseMigrationTest.kt`（androidTest，真机未跑）
