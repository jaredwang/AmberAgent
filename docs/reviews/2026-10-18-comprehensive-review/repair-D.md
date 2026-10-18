# Phase 2 D1–D4 修复交付

范围限本工作包四项确认缺陷。root 已取得修复前 7 个真实失败；日志 `/tmp/amber-review-20261018-red-phase2-ui.log`。本 worker 未执行 Gradle、未提交或推送。

## 已实施

- **D1**：`NovelWorkspaceNodes.openForeshadowing` 沿现有契约只排除显式 resolved；planned 仍归未回收，并保留 injection=off 排除。
- **D2**：ZIP 在现有 `NovelWorkspaceRestoreBoundary` 内采集路径、正文与派生 syncStatus 为不可变文件列表；压缩、编码输出均在锁外。TXT/MD/EPUB 共用的 `BookExport.read` 在同一边界内读取章节与书名；渲染仍在锁外。
- **D3**：DeepRead 完成/失败结果使用与前台进度不重叠的稳定通知 ID；WorkManager 清理进度不会删除结果。通知权限、路由和内容保持原约定。
- **D4**：只有 `plot/foreshadowing` 分支进入专属合并函数。宿主生成/保持节点 kind 与 ID，作者批准的 open/resolved 更新可落盘；原有 planned 重写和省略状态的正文编辑保持旧状态。普通 plot 与章节仍沿旧身份保护路径。

## 回归与实际验证

- `:feature:novel-workspace`：`NovelWorkspaceReviewRegressionTest`、`NovelWorkspaceExportSnapshotTest`。两个导出回归均使用真实 `withNoActiveBranch` owner 事务；ZIP 在输出首章 entry 时允许另线程提交正文/plot/ledger，同时核对导出仍为旧 snapshot；成书出口在半写事务期间必须等待。
- `:app`：`DeepReadNotifierTest`（Robolectric 实际 NotificationManager 清理前台 ID 后结果仍在）；`NovelWorkspaceForeshadowingWritesTest`（实际 kernel tool write → proposal → approve → Nodes/Catalog/brief，补 planned 原状态与身份不变的回归）。
- 本工作包限定文件 `git diff --check` 通过。
- 修复后 Gradle GREEN、每 phase 独立 subagent review、应用构建由 root 串行统一执行；本 worker 不把代码级修复声明为真机行为验收。

## 一次有限可维护性检查

已按 `code-simplifier` 与 Kotlin 指引只检查本工作包 diff。导出复用现有事务边界和现有 `NovelWorkspaceFile`，没有新锁或缓存；业务状态合并集中在命名明确的伏笔专属函数，没有通用 front matter merge；通知 ID 的分离原因就地解释。没有为附带疑点扩展抽象、权限、重试或兜底。
