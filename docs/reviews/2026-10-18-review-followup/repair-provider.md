# Provider / document follow-up repair

Owner 范围：`GeminiGenerateContentAdapter.thinkingConfig` 与新增 `GoogleThinkingRequestTest`；document 子 owner 只负责 DOCX/EPUB 与现有 `OfficeParserFixtureTest` 的追加回归。前轮 Gemini tool / Claude / PPTX WIP 保留。

## AP1：Gemini thinking request 契约

root 已实际运行 RED：`GoogleThinkingRequestTest` 8 项中 6 项失败，合法邻居与 AUTO/Code Assist OFF 两项通过。记录为 `red-provider.json`，总日志 `/tmp/amber-followup-red-provider-document-history.log`。六项失败分别验证 complete/stream 对 Pro OFF、original 3 Pro MEDIUM、2.5 Flash MAX 的实际 HTTP 请求，不只测试私有分支。

生产修复仅在既有 adapter owner 内复用现有 model family matcher：

- Gemini 3/3.1 Pro 的原生 OFF 使用最低合法 level `low`。
- original 3 Pro 的 MEDIUM 映射 `high`，保留 3.1 Pro 已支持的 `medium`。
- Gemini 2.5 Flash 显式 thinking budget 上限 24576，保留 2.5 Pro 的 32000 和 Flash 的合法较低预算。
- AUTO 与 Code Assist OFF 保持动态配置，不增加 provider 请求重试、模型切换或支持矩阵框架。

当前已完成一次限定本包 diff 的维护检查；用注释说明 Pro 的两个协议差异，预算计算留在单一 owner；`git diff --check` 通过。新增测试使用 OkHttp 注入受控 HTTP400/200 合约响应，不访问网络。测试过滤器：`app.amber.ai.provider.providers.GoogleThinkingRequestTest`；建议同跑 `ProviderStreamRoundTripRegressionTest`、`ProviderWireAdapterFixtureTest`、`GoogleProviderMessageTest`。

GREEN、独立 review 与最终统一检查由 root 串行运行和记录；本子任务没有运行 Gradle，也没有真实 provider/设备验收。

## AP2 / AP3：DOCX / EPUB 文本保真

root 已实际运行 RED：`OfficeParserFixtureTest.docxReadsContentControlParagraphsAndTablesInOrderWithoutDuplicates`、`epubPreservesWhitespaceBetweenInlineElements`、`epubPreservesXhtmlNamedAndXmlEntities` 三项均失败。记录为 `red-document.json`。

RED 后已完成最窄生产修复：DOCX 只把 body 和块级内容控件的 sdtContent 识别为块容器，继续由现有段落/表格 owner 完整消费子树；EPUB 改用 nextToken 保留 entity 事件，使用 Android 自带 Html 本地解析命名实体，预定义/数字实体沿用 parser 结果。保留内联空白与 NBSP，块边界的源缩进空白仍省略，新增一项输出不变回归。没有加载外部 DTD、增加依赖或网络访问。

document 子 owner 已完成一次有限维护检查；`git diff --check` 通过。GREEN filter 为 `app.amber.document.OfficeParserFixtureTest` 整类。root 的统一 GREEN 与独立阶段 review 结果仍需单独记录；没有将探针或静态检查写成完整模块测试通过。
