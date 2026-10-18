# Novel / DeepRead 追加独立审查

范围：`feature/novel`、`feature/novel-workspace`、app Novel workspace 与关联 UI，以及 DeepRead 生产链。基线为当前既有 WIP；没有修改生产代码、没有运行 Gradle，没有读取兄弟产品仓库。DeepRead 另由只读子审查独立复核后汇总。下述新项均区分源码确定行为和实际验证边界，不借既有测试报告声称本轮重新执行。

## 已修 D1–D4 的复核

| 原项 | 当前接线与复核结论 |
|---|---|
| D1：planned 伏笔漏注入 | `NovelWorkspaceNodes.kt:78-81` 当前只排除 resolved，与 Catalog 的未回收定义一致；off 仍由 brief/neighborhood 注入筛选排除。没有发现修复回退。 |
| D2：导出混合版本 | `NovelWorkspaceExchange.exportZip` 当前在 `NovelWorkspaceRestoreBoundary.write` 内采集全部路径、正文、派生 syncStatus，压缩在锁外；`NovelWorkspaceBookExport.read` 也在同一 owner monitor 读取书名和章节。与写正文/plot/plan/ledger 的既有 monitor 一致，没有引入另一把锁。 |
| D3：终态通知被 WorkManager 删除 | `DeepReadNotifier.kt:73-115` 完成、失败使用 `resultNotificationId`，运行进度仍用原 ID；结果 ID 的 xor 位与原 ID 范围不重叠。现有新增回归覆盖完成和失败通知被清理进度后的保留。没有发现修复回退。 |
| D4：伏笔批准后状态不生效 | `NovelWorkspaceRuntime.kt:1246-1283` 仅 foreshadowing 路径进入专属合并；宿主维持 ID、生成 kind，允许 open/resolved、保存既有 planned 状态以及省略状态时的原状态。没有通用 front matter merge。 |

Novel 继续沿 UI gate → owner/CAS → durable persistence 检查了：模型工具写入/批准/拒绝、作者资料编辑与删除、章节历史恢复、废稿移出与恢复、分叉/切换、代笔启动/暂停/重试/取消、reviewed commit 崩溃窗口恢复、ZIP 与旧格式导入、备份 restore epoch。当前没有另接受新的 Novel 缺陷。

## 追加确认的问题

### N1 / P2：DeepRead 失败回写会将其他任务的同标题旧文章绑定给新 URL

- **入口链**：聊天 `/deepread 标题 https://new-source` → `createDeepReadOpenEvent` → UI/调度器入队 → `DeepReadWorker.runGeneration` → AgentRunner / `DeepReadAgentRunManager.createRunContext`。
- **可达触发**：已有其他 topicId 的同标题、未过期且全段 READY 文章；新 URL 对应的 topicId 尚无文章；当前没有可用模型或模型不支持工具。`DeepReadAgentRunManager.kt:428-434` 会在为新任务写入正文前返回正常 admission 失败。这不需要损坏文件或并发修改。
- **根因**：`DeepReadWorker.kt:63` 和 `:154` 两个错误分支都调用 `materializeFreshDeepRead(topicId, title)`。`HotListRepository.kt:196-228` 会从其他 topicId 按同标题取文章、克隆到新 topicId；Worker 没有传模板约束，也没有禁止有 sourceUrl 的标题回退。
- **影响**：失败回写保留旧文章的 READY 段落，甚至旧 source/structured/template。之后恢复模型再运行，新任务按自己的 ID 命中这份旧缓存，`DeepReadAgentRunManager.kt:164-177` 将其标为完成，不读取新 URL。用户可能把旧文章误认为新来源阅读结果。
- **二次复核**：正常 `fresh` 的 `DeepReadAgentRunManager.kt:1525-1540` 明确只有无 URL 的请求才允许标题回退；有 URL 仅按 topicId 读取。无 URL 的回退还必须匹配 requestedTemplateId。因此 Worker 失败回写绕过的是已有生产约束，不能用“同标题缓存复用本来允许”解释。
- **最小修复**：失败/重试回写只读本 topicId 的 `getFreshDeepRead(topicId)`，避免在失败路径创建其他文章的别名；继续保留当前任务已有 partial/READY 结果。
- **必要回归**：真实 Room repository 中预置其他 ID 的同标题 READY 文章；新 URL admission 失败后，新 ID 不得出现旧段落；再运行应读取新 URL。另验证本 ID 的 partial/READY 内容仍被保存。两个错误出口都需覆盖或复用同一个被测试的读取入口。
- **验证边界**：本轮确认生产调用链和确定状态结果，没有执行新 JVM/设备/provider 测试。

### N2 / P2：聊天 DeepRead 将大小写敏感的不同 URL 合并成一个任务 ID

- **入口链**：`parseDeepReadSlashCommand` / `createDeepReadOpenEvent` → `deepReadChatTopicId` → DeepRead 页面、WorkManager 唯一任务和 Room topicId。
- **根因**：`app/src/main/java/app/amber/core/ai/tools/DeepReadOpenRequest.kt:94-103` 对整条 sourceUrl 调用 `lowercase(Locale.ROOT)` 后计算 ID；原 sourceUrl 本身则在 `:68-74` 保持原样。
- **可达触发**：分别打开 `https://example.com/Foo`、`https://example.com/foo`，或同一路径下区分大小写的 query token。不同来源得到相同 topicId。
- **影响**：第二次请求直接命中第一次的 source/文章缓存，或强制重建时覆盖同一历史条目；topic mutex 和排程去重也错误地把两个资源视为一个。
- **二次复核**：仅 scheme/host 不区分大小写；path/query 可标识不同资源。正常带 URL 的 `fresh` 已禁用标题回退，但仍按发生碰撞的 topicId 取值，所以这个问题不会被现有源隔离逻辑挡住。这里是输入规范化造成的确定相同 hash，不是猜测性 SHA 碰撞。
- **最小修复**：URL key 保留 path/query 大小写。可直接保留 trim 后的完整 URL，或只规范化 scheme/host；标题模式的 lowercase 行为保留。
- **必要回归**：`DeepReadOpenRequestTest` 对 path/query 大小写差异断言不同 ID；同一个 URL 重复打开仍稳定。若实现规范化 scheme/host，再加入对应大小写等价断言。
- **验证边界**：本轮确认确定性 ID 算法与缓存生产接线，未执行新测试。

### N3 / P2：文档解析失败和无可读内容被 DeepRead 导入当作成功正文

- **入口链**：`DeepReadCreateSheet.kt:123-136` 选择文档 → `DeepReadSourceImporter.extract` → shared document parser → `Success.text` 写入创建表单内容 → `BoardViewModel.kt:162-180` 作为自定义来源持久化。
- **根因**：`DeepReadSourceImporter.kt:62-65` 只把抛异常或 blank 当失败；`PdfParser.kt:8-11`、`DocxParser.kt:27-39,69-70`、`PptxParser.kt:48-53`、`EpubParser.kt:23-50` 在解析异常或无正文时直接返回非空诊断字符串，例如 `No readable slides in PPTX file` / `Error parsing ...`。
- **可达触发**：用户选择没有可读 slide 的合法 PPTX，或内容损坏但扩展名/类型受支持的文档；现有 `OfficeParserFixtureTest.kt:78-89` 已固定前者返回诊断字符串，Importer 当前必定把该返回当作 Success。
- **影响**：UI 显示已导入，表单实际只有诊断文字，原文没有导入，也没有导入失败提示。短诊断可能在 source prefetch 的 60 字门槛被排除；足够长的异常诊断还可能进入来源证据。因此首要确认影响是“文档未读入却报告成功”，不是声称每个短诊断都会生成错误文章。
- **二次复核**：这是 shared parser 的既有字符串 API 结果；Importer 自己声明 typed Result 且已有 READ_ERROR/NO_TEXT，应在边界映射诊断。`ChatInput.kt:212` 已存在相同 parser 诊断识别可供复用，不必更改所有 parser 的契约。
- **最小修复**：在文档解析边界把已知 error/no-readable 返回映射为对应 Failure，保留普通文本中合法内容。若要共享识别，限现有 parser 诊断契约，不泛化所有 `Error` 文本。
- **必要回归**：以现有无正文 PPTX fixture 和损坏 DOCX/PDF 触发真实 parser；导入应返回 NO_TEXT/READ_ERROR，UI 不得填入诊断正文；正常文档与普通文本照常成功。
- **验证边界**：已有 fixture 的确定返回和生产消费路径已核对；没有在本轮重跑 fixture/JVM/UI。

## 未接受的疑点与设计行为

- **unknown SIZE 的 64 MB 上限绕过：待可观察复现。** `DeepReadSourceImporter.kt:48-56` 只依赖 provider 的 SIZE，未知/负数会跳过，`copyTo` 无累计计数。源码显示超大流会被完整拷贝，但本轮未构造 ContentProvider 复现，先交 root 决定补失败证据再接受。不要直接把它写成已经执行验证的问题。
- **分叉后 plot stale 归零：by design。** `NovelWorkspaceBranchesTest.kt:399-400` 明确固定该行为；不据此补继承或隐式同步。
- **分支 unresolved 不共享：by design。** `NovelWorkspaceUnresolved` 按分支保存，说明中 fork 本就是现有解法之一；不把另分支无 gate 当漏洞。
- **历史恢复后旧编辑器覆盖：误报。** Reader 的 `refreshVersion` 关闭旧编辑器并重读；polish committed progress 变化也会驱动刷新。
- **仅调用 runtime 的跨分支路径、任意损坏/外部手改状态、未知 front matter 上的潜在丢失：未见当前正常入口/契约证据，不列真实缺陷。**

建议追加一个 DeepRead 精确修复 phase：N1 缓存归属、N2 URL identity、N3 导入结果边界；先补对应失败回归，修后定点 JVM，交独立 subagent review，再由 root 串行统一 app compile/assemble。unknown SIZE 若复现成立可并入 N3，避免预先增加新的 parser/调度状态机。
