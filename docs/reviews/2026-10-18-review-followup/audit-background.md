# Background follow-up audit

审查基线：当前 `d983c0b782c6eedce7c826f62003aa26973cc17c` 工作树（包括上一轮尚未提交的完整修复及用户 WIP）。只读生产与测试源码，未运行 Gradle，未修改生产/测试代码，未访问兄弟产品。先阅读了上一轮 `audit-F.md`、两个 `repair-F-*.md`、`findings.md` 和两份 Phase 2 独立 review，再沿实际生产入口检查。

## 已确认的新问题

### BG-01 / P1 — 本地文件夹上传把 tree URI 当 parent document URI，真实 SAF provider 拒绝创建

- 生产位置：`app/src/main/java/app/amber/core/sync/provider/LocalFolderSyncProvider.kt:141-152`；目录来源为 `BackupPage.kt:390` 的 `ActivityResultContracts.OpenDocumentTree()`，`BackupVM.kt:996-998` 原样持久化返回 URI。
- 入口链：备份页选择本地文件夹 → 保存 `content://.../tree/<id>` → 本地文件夹上传 → `uploadSnapshot` → 两次 `DocumentsContract.createDocument(resolver, treeUri, ...)`。
- 触发与影响：对标准 Android `DocumentsProvider` 选择任何合法目录后上传；第一个临时归档文档即无法创建，整个上传失败。目录列举使用 `buildChildDocumentsUriUsingTree`，因此“能够选择和列举”不能证明上传可用。
- 二次复核：AOSP 的 `DocumentsProvider.callUnchecked` 在 CREATE 分派前对 `EXTRA_URI` 调用 `DocumentsContract.getDocumentId`；后者仅接受 `/document/<id>` 或 `/tree/<treeId>/document/<id>`，对单独 `/tree/<id>` 抛 `IllegalArgumentException`。这属于确定 API 形状错误。已读取官方 [DocumentsProvider 源码](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/DocumentsProvider.java) 的对应调用，及官方 [DocumentsContract 源码](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/DocumentsContract.java) 的 `getDocumentId` 实现；没有凭设备菜单或历史记忆推断。
- 当前回归为何漏掉：`LocalFolderSyncProviderUploadTest.kt:274-282` 的 fake 直接继承通用 `ContentProvider`，CREATE 分支只取 MIME/name，完全未读取和验证 `EXTRA_URI` 中的 parent document URI，绕过真实 `DocumentsProvider` 校验。
- 最小修复：上传开始时复用已有 `treeDocumentUri(treeUri, DocumentsContract.getTreeDocumentId(treeUri))` 构造 root document URI，仅把两次 CREATE 的 parent 改用它。保留 tree URI 用于查询子项与生成其他文档 URI，不引入新的 SAF 抽象。
- 回归：让既有 fake 在 CREATE 时调用真实 `DocumentsContract.getDocumentId(parentUri)`，并断言 parent ID 为 root；先取得现实现 RED，再修复后运行已有成功发布/sidecar 发布失败回滚。可补一个直接 URI 断言，但不能用它替代上传生产入口测试。

### BG-02 / P2 — Terminal admission 在持久注册前取消/失败会遗留无 worker 的运行槽

- 生产位置：`feature/terminal/src/main/kotlin/app/amber/feature/terminal/TerminalRuntime.kt:176-218`；挂起点为 `feature/task/src/main/kotlin/app/amber/feature/task/AgentTaskStore.kt:48-76`。
- 入口链：已批准的 `terminal_job_start`（`TerminalTools.kt:121-129`）或 `terminal_install_packages`（`:203-208`）→ `TerminalRuntime.startJob` → admission → task store register → backend launch。
- 触发：cold-start task load 尚未完成或 task store mutex 被其他写入占用时，在 register 处停止当前 Agent run；另一确定异常分支是 snapshot 持久化失败。`register` 明确先 `awaitReady()` 再 `mutex.withLock` 并持久化，因此不是“suspend 但永不挂起”的假窗口。
- 事实链与影响：admission 已在 `jobs` 放入 QUEUED job；安装任务还已把 `installRunning` 从 false 置 true。只有 register 返回后才启动 appScope backend。交接之间没有 catch/finally，取消/异常直接离开 startJob，job 一直为 running 的 QUEUED，计入 `runningJobs` 并占并发槽；若为 install，后续 install 还会一直收到 Another terminal_install_packages job is already running。异常发生在 register 完成前时用户没有取得 job ID，任务页也没有可取消记录。
- 二次复核：这是“任务尚未启动”时丢失 owner，不是 API 明确允许的已启动 detached job。`finishJob` 与 process finally 原本会释放安装门，但这些分支只由已启动 backend 或主动 stop 调用，此处根本没有 backend。终端 detached、Termux/SSH INTERRUPTED 契约无需改变。
- 最小修复：在现有 startJob owner 中收拢 admission 到 backend handoff，交接前取消/异常时释放该 job 与其 install 标志，若已有任务投影则使其终态可观察；异常继续向上抛。不要修改已经成功启动的 detached job 生存期，也不要建立第二套终端状态机。清理需仅处理本次 admission，不能覆盖其他安装 owner。
- 回归：用受控 `AgentTaskStore` 启动读取/锁 gate 卡住真实 register，启动一个 `isInstall=true` admission 后取消，在没有 backend 启动的情况下确认后续 job 与 install 可被接纳；再覆盖注册持久化抛错以及已交接 backend 的正控。可在 owner 交接提取最窄 seam，但不能只测一个人工计数器，必须验证真实 `jobs` admission 与安装门。

### BG-03 / P2 — WebDAV 部分删除报成功，留下无法从 App 再删除的 ghost snapshot

- 生产位置：`app/src/main/java/app/amber/core/sync/provider/WebDavSyncProvider.kt:166-170`；`WebDavClient.kt:198-214`；UI 成功投影 `BackupVM.kt:936-949`。
- 入口链：备份页确认删除 WebDAV 快照 → `deleteWebDavSnapshot` → provider 删除 `.amberbackup` → 删除 `.snapshot.json`。
- 触发与影响：归档 DELETE 成功，随后 sidecar DELETE 返回 500/网络错误。`client.delete` 返回 `Result.failure`，外面的 `runCatching { client.delete(...) }.getOrNull()` 既没有 `getOrThrow`，又丢弃了结果。provider 正常返回，UI 从本地列表去掉快照并显示已删除。刷新时 listSnapshots 仅依 sidecar，又列出该快照，但下载归档 404；再次删除会在第一条归档 DELETE 的 404 抛错，永远执行不到残留 sidecar，因此 App 无法清理它。
- 二次复核：不是“保留历史元数据”的设计，provider 协议与已有 `deleteSnapshot removes archive and sidecar` 回归都约定删除两件；上传也专门处理 orphan archive。显式 best-effort 写法能解释代码意图，但不能满足当前列表/删除的可重试契约。
- 最小修复：两件删除均消费真实 Result；仅将目标已不存在（HTTP 404）作为删除已达成处理，让部分删除后的重试能够继续清除 sidecar，其余错误传回现有 UI failure 分支。保持当前删除顺序，不加重试循环或新日志状态。
- 回归：在既有 `WebDavSyncProviderTest` fake server 注入仅 sidecar DELETE 500：首次删除必须失败；将其恢复后第二次删除允许 archive 已为 404，最终两件均不存在且列表为空。再覆盖 archive DELETE 500 必须保留 sidecar并报告失败，避免伪成功。

## 上一轮 F 修复复核

- F1/F2：CronWorker 已持有 `startTaskMessage` 返回的本次 run handle 并等待真正 terminal；真实 run settlement 与 session dispatch handoff 均在 task mutex 释放之前。手动 REPLACE 由 taskId Mutex 串行准备与清理；未发现此前取消清理窗口重新打开。
- F3：ReminderScheduler 持锁时按 ID 重读当前 store；旧 enabled/disabled 快照和已删除记录不会再覆盖最新 schedule。
- F4：journal recovery 已在 Koin 模块注册后、WorkManager 初始化与 eager consumers 之前同步执行；复用原 DB commit marker、restore gate、Novel bridge。未发现冷启动仍仅等下一次 restore 的旧缺陷。
- F5：Cron 结果通知与 foreground ID 分开；当前两个真实调用已正确消费 helper。
- 上述为源码复核；上一轮全测记录可作历史证据，本轮未执行 Gradle，不能据此宣称最新全部改动 GREEN。

## 已排除或证据不足

- FilesManager 先写 upload 文件再持 restore gate 登记的交错：源码存在可疑窗口，但尚未证明实际 UI/Agent 调用在导航取消与 restore epoch 的现行接线下能持续完成并插入无文件行。**本轮未接受为真实缺陷**，不得据此扩成全局长文件写锁或 staging 框架。
- `deleteChatImageFiles` 看似无独立 gate，但唯一生产 caller `ChatService.deleteMessage` 把它包在 `withConversationWrite` 内，并在外层捕获 restore epoch，排除“直接跨恢复删图”误报。
- SessionCleanupManager 的重新 dry-run 虽然在 Room transaction 外，但整个执行被现有共享 restore/write gate 包住，ConversationRepository 正常持久写也消费同一 gate。未取得可达绕过者证据，排除直接报 stale-selection 数据丢失。
- Reminder DAILY/WEEKLY 固定 epoch 时长、Terminal 已启动 detached job、Termux/SSH 无法证明停止时使用 INTERRUPTED，均保留上一轮明确设计边界。
- 旧 WebDavSync/S3Sync helper 仍未找到生产 owner，不把 dead-code 候选当生产缺陷，也未删除。
- WebMount 子域由独立只读 subagent 复核，未得到新增确证项：signed fetch 写请求已有 `ToolRegistry.kt:512-529` 的动态审批；Settings 清会话链先删除 cookie 再清页面存储，不能只看 controller 单方法误报；断开期间 refresh 迟到 token 已由 `putTokenIfCurrent` 拒绝并有回归。WebViewPool 的 Main 返回取消、ProfileBridge 自主导航 origin 窗口、SessionOwner reservation rollback 三个候选缺少实际交错与可观测影响证据，全部未计入问题列表、未扩修。

## 建议修复分组

1. 本地文件夹 parent URI 与真实校验回归，独立验证上传和 publish rollback。
2. Terminal admission 交接清理，独立验证取消、持久化失败、成功 detached 正控。
3. WebDAV 两件删除与部分成功后的重试契约，独立验证错误不被吞、404 仅代表已删除。

三项职责独立，可各自 narrow ownership 实施；每项后由另一 subagent 只复核相应 diff 与实际失败回归，root 统一整合与 Gradle gate。设备 SAF/Termux/SSH、真实 WebDAV 网络行为仍需分别报告为未验收。
