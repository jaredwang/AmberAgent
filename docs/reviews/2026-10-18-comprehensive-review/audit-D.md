# 工作包 D：Novel 与关联后台任务

审查基线：`d983c0b` 加既有 WIP。只读取 Android 本仓；未读取兄弟产品。初审期间未运行 Gradle、未修改生产代码。以下候选已经按入口、领域契约和反例二次复核，随后按主 agent 授权补失败回归并精确修复。

修复前失败证据：root 已运行 `:feature:novel-workspace:testDebugUnitTest` 与 `:app:testFullDebugUnitTest` 的定点集合。本工作包 7 个回归全部按预期失败：D1 一项、D2 两出口两项、D3 终态两项、D4 创建/回收两项。日志 `/tmp/amber-review-20261018-red-phase2-ui.log`（Novel 模块 167–174；app 1091–1101）。不是编译失败或测试配置失败。

## D1 / P2：planned 伏笔在目录仍未回收，却从约束简报中消失

- 根因：`NovelWorkspaceNodes.kt:78-81` 声明只有显式 resolved 才回收，实际筛选只收 null/open。`NovelWorkspaceCatalog.kt:64-67` 与现有 `NovelWorkspaceCatalogTest.kt:30-39` 明确把 planned 作为未回收伏笔。
- 可达入口：工作区 ZIP 导入保留节点状态；生成讨论、代笔、审核都经 ContextAssembler 注入未回收伏笔。没有本章计划命中该节点时，planned 节点完全缺席，生成丢失已存在的故事约束。
- 二次复核：off 节点应继续排除；resolved 大小写不敏感；空状态已在解析时归 null，无需另加兼容分支。
- 失败回归：`:feature:novel-workspace` 的 `NovelWorkspaceReviewRegressionTest`，对目录未回收与实际 brief 注入作联动断言。
- 修复：沿当前契约仅排除 resolved，保留既有 injection/off 筛选。

## D2 / P2：工作区 ZIP 和成书导出可以读到 owner 事务的混合版本

- 根因：`NovelWorkspaceExchange.kt:22-32` 和 `NovelWorkspaceBookExport.kt:65-88` 对多文件读取不持已有 owner monitor；后台审核收录则在同一 monitor 内逐个写正文、plot、plan、ledger。
- 可达入口：`NovelProjectsPage.kt:456-457,765-773` 允许从项目列表导出；`NovelProjectsViewModel.kt:254,279` 直接调用导出，后台代笔可同时继续。
- 影响：ZIP 可混入旧第一章、新第二章与新剧情；成书可在正文多文件提交未完成时读出一半旧稿、一半新稿。ZIP 的 syncStatus 还可能由旧账本搭配新工作树推导。
- 二次复核：并非要求禁止后台导出；现有 owner 锁足够。只需锁内采集不可变 snapshot，压缩与输出 IO 留在锁外。
- 失败回归：`:feature:novel-workspace` 的 `NovelWorkspaceExportSnapshotTest`。ZIP 输出到第一章 entry 时让另线程实际 owner 写正文/plot/ledger；成书出口在 owner 写第一章后暂停事务，确认导出等待完成。

## D3 / P2：DeepRead 完成和失败通知随 WorkManager 前台通知一起消失

- 根因：`DeepReadNotifier.kt:75-80,104-106` 的终态通知与 `DeepReadWorker.kt:createForegroundInfo` 共用 `notificationId(topicId)`。
- 可达入口：任意后台 DeepRead 成功/失败后先通知，再返回 WorkManager 结果。AndroidX `SystemForegroundDispatcher.onExecuted` 会删除 ForegroundInfo 的通知 ID，把刚发的终态通知也删掉。
- 官方证据：[AndroidX SystemForegroundDispatcher](https://raw.githubusercontent.com/androidx/androidx/androidx-main/work/work-runtime/src/main/java/androidx/work/impl/foreground/SystemForegroundDispatcher.java)，onExecuted 的 removedInfo cancellation。当前 Novel Worker 已用独立 failure ID 处理同一窗口。
- 影响：用户离开页面等生成，任务结束后通知入口不能保留；失败原因也不留在通知栏。
- 失败回归：`:app` 的 `DeepReadNotifierTest`，通过实际 NotificationManager 发布运行/终态通知，再模拟 WorkManager 删除前台 ID，断言终态仍可见、路由仍指向历史。
- 修复：运行进度使用原 ID，终态使用另一个稳定 ID；路由不变。

## D4 / P2：模型创建和回收伏笔经批准后未接入领域状态

- 根因：`NovelWorkspaceRuntime.kt:1245-1271` 对所有 protected 文件丢弃模型 front matter。新 `plot/foreshadowing` 文件被宿主写成 kind=plot，Nodes 明确忽略 plot；已有伏笔的 status=resolved 又被 withBody 还原成旧 open。
- 可达入口：`NovelWorkspacePrompts.kt:21,42` 明确要求模型在 plot/foreshadowing 新建 open、回收时改 resolved；write tool 正常登记审批，用户正常批准。界面批准成功，却新伏笔不出现、旧伏笔不能回收。
- 二次复核：保留 host identity 是合理设计，但 kind 可由路径宿主推导，status 是 prompt 明确授权修改的业务字段；不能用身份保护解释全部丢弃。普通 plot 与章节的身份规则应保持。
- 失败回归：`:app` 的 `NovelWorkspaceForeshadowingWritesTest`，实际 fake kernel 调 write tool → Completed proposal → approve → Nodes/Catalog/brief，并检查宿主 ID 不被模型替换。
- 修复：只在 foreshadowing 路径宿主生成 kind=foreshadowing，并处理受支持的 open/resolved 状态；保留宿主 ID，避免通用 front matter merge。

## 排除的问题

- 分叉时 plot stale 归零：`NovelWorkspaceBranchesTest.kt:400` 明确固定该领域行为，是 by design，未擅改为继承源分支落后。
- 历史恢复后编辑器可能覆盖恢复：`NovelMarkdownWorkspacePage` 成功恢复增加 readerRefreshVersion，Reader 的 LaunchedEffect 关闭旧编辑器并重读，初步怀疑不成立。
- 不把只在存储层任意调用才触发的跨分支路径、任意外部手改或损坏文件等边界算成已经确认的生产缺陷。
