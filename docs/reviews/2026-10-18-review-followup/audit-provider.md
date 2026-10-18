# Provider / search / document follow-up audit

范围：当前 `ai/`、`search/`、`document/`（包括基线已有 WIP）及其 app 生产入口。先读取上一轮 `audit-A.md`、`repair-A.md` 与 `findings.md`，仅追加能够指明触发、影响与 owner 的问题。没有改生产/测试代码，没有运行 Gradle，没有读取兄弟产品，没有调用收费 provider。官方资料于本次审查访问。

## 已确认追加问题

### AP1 / P2：普通 Gemini reasoning 选项会编码成该模型不支持的请求值

- 位置：`ai/src/main/java/app/amber/ai/provider/providers/GeminiGenerateContentAdapter.kt:220,232,237`；`ai/src/main/java/app/amber/ai/core/Reasoning.kt:24`。
- 生产入口：`ReasoningPicker.kt:48` 公开全部 `ReasoningLevel.entries`；chat `ChatPage.kt:1492` 保存等级，`ChatGenerationRoundEngine.kt:271-277` 从 session defaults 原值构建 params；`GoogleProvider.kt:230,286` 的 complete/stream 均使用本 adapter。相关模型由 `ModelRegistry.kt:115-126,142-167` 明确保留，不是外部自行构造的不可达模型。
- 已确认触发：① Gemini 3.1 Pro（包括 customtools）选择 OFF，编码 `thinkingLevel=minimal`，但该模型只支持 low/medium/high；② Gemini 3 Pro 选择 MEDIUM，编码 medium，但该模型只支持 low/high；③ Gemini 2.5 Flash 选择 MAX，`budgetTokens=32000` 直接成为 thinkingBudget，但其上限是 24576。API-key/Vertex 原生路径可达；Code Assist 的 OFF 分支已有独立省略语义，不扩大为 OAuth 问题。
- 实际影响：用户在正常设置界面选择的等级生成不符合 provider 合约的请求，模型请求被拒绝；这不是模型无法完全关闭思考这一已知限制本身，而是我们仍发送明确不受支持的值。
- 二次复核：当前 Google 官方 [Gemini 3 developer guide](https://ai.google.dev/gemini-api/docs/gemini-3?hl=en) 的 thinking 表明确 3.1 Pro 的 minimal 为 Not supported；当前 [Thinking](https://ai.google.dev/gemini-api/docs/thinking/) 的完整模型表列 `gemini-3-pro-preview` 为 low/high、`gemini-3.1-pro-preview` 为 low/medium/high；[GenerateContent thinking budgets](https://ai.google.dev/gemini-api/docs/generate-content/thinking?hl=en) 列 2.5 Flash 为 0–24576，2.5 Pro 为 128–32768。旧 3.0 Pro 与 3.1 Pro 的 MEDIUM 支持不同，不能把所有 Pro 都映射成同一种规则。
- 最小修复：复用已有 family match，在 adapter 内精确映射 Pro OFF 到其最低合法级别 low、3.0 Pro MEDIUM 到合法级别 high，并把 2.5 Flash 的显式预算限制在 24576；保留 3.1 Pro medium、Flash minimal、2.5 Pro 的 32000。无需新增通用配置矩阵、失败后重试或静默更换模型。
- 最小回归：参数化覆盖这三个拒绝情形和对应合法邻居；至少经 `GoogleProvider.complete/stream -> mock HTTP` 校验最终 wire body，mock 根据已引述公开合约拒绝非法值。AUTO 和 Code Assist OFF 的现有语义作为不变性断言。此处是源码与当前协议文档验证，尚无真实收费 API 验收。

### AP2 / P2：DOCX 内容控件中的普通正文被完整跳过

- 位置：`document/src/main/java/app/amber/document/DocxParser.kt:56-60`。
- 生产入口：chat `DocumentAsPromptTransformer.kt:57,73`、DeepRead `DeepReadSourceImporter.kt:80`、workspace office reader `WorkspaceArtifactTools.kt:287` 均调用 `DocxParser.parse`。
- 触发：合法 Word 文档把普通段落包在 block content control 中，即 `w:body/w:sdt/w:sdtContent/w:p/w:r/w:t`。当前只接受 body 的直接子节点 p/tbl，因此 p 的深度更大时完全不读取。
- 实际影响：Word 模板、表单或内容控件内填写的正文从聊天上下文、DeepRead 导入、workspace 读取结果消失，其余段落正常返回，错误不显眼。若正文全在控件内，结果可以为空。
- 二次复核：这不是隐藏修订、字段指令或未知附件。Microsoft 官方 [SdtBlock](https://learn.microsoft.com/en-us/dotnet/api/documentformat.openxml.wordprocessing.sdtblock?view=openxml-3.0.1) 定义 block content control 并提供 body/sdt/sdtContent/p 的正文结构。parser 本身已经读取相同 `w:p/w:r/w:t` 正文，缺失仅来自 body 的直接深度 gate。局部修复可保持现有文本、列表、表格处理。
- 可观测复现：子审查使用现成 document 编译产物及原 Gradle worker classpath，以手工 `java org.junit.runner.JUnitCore` + Robolectric SDK 34 执行 `/tmp/document_blindspots_probe/ProbeTest.java`，没有运行 Gradle。fixture 的 `Before / CONTROL_CONTENT / After` 输出实际为 `Before\n\nAfter`。探针只负责显示输出，不把一次 JUnit runner 成功等同于回归断言通过。
- 最小修复：使正文 block 遍历接受 sdt/sdtContent 这个已确认的 block wrapper；遇到 p/tbl 仍交给现有 owner 消费，避免简单放开任意深度把表格单元格内容重复读为顶层正文。无需引入 Office 库或通用 DOM。
- 最小回归：ZIP fixture 包含普通正文、控件内 p 和 tbl、控件后普通正文，断言文本和顺序全部保留且表格不重复；已有直接 body 段落/表格 fixture 继续通过。

### AP3 / P2：EPUB 合法行内空白及 XHTML 命名实体被丢弃

- 位置：`document/src/main/java/app/amber/document/EpubParser.kt:127-129,149,182`。
- 生产入口：chat `DocumentAsPromptTransformer.kt:61,75` 和 DeepRead `DeepReadSourceImporter.kt:82` 调用 `EpubParser.parse`，再将结果作为模型正文。
- 触发一：合法 XHTML `<p>Hello<span> </span>world</p>`，空白是独立 TEXT 节点。`isNotBlank()` 过滤掉它，输出变成 `Helloworld`。这不是排版缩进：此空格承担两个词之间的分隔。
- 触发二：带标准 XHTML 1.1 DOCTYPE 的 EPUB 正文 `caf&eacute; &amp; tea&nbsp;time&hellip;`。Android pull parser 禁用 DTD 后，`next()` 不保留未识别的命名实体；实际输出为 `caf & teatime`，重音字符和省略号消失，单词也连接。
- 实际影响：普通英文/拉丁文正文被改变，搜索词、摘要及引用依据使用了源文件不存在的单词或缺失字符。
- 二次复核：上一轮因缺少合法 fixture 暂不接受 EPUB 空白；本次已补合法 XHTML ZIP fixture 和 Android SDK 34 行为证据。`epub-nbsp` 包含 `<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">` 和 XHTML namespace，不能将其误归为无声明的非法 XML。最初“整章抛错”猜测已排除：Android 实际是静默丢实体，`&amp;` 仍保留。
- 可观测复现：与 AP2 共用 `/tmp/document_blindspots_probe/ProbeTest.java` 和 ZIP fixtures，手工 Robolectric runner 两轮输出一致；未运行 Gradle。
- 最小修复：保留正文里的空白 TEXT，再在 block 边界做现有输出归一；为 XHTML 命名实体使用本地确定性解码，继续禁用外部 DTD/实体访问。无需增加下载 DTD、宽泛 HTML 容错或新导入框架。
- 最小回归：fixture 覆盖 span 内纯空白、相邻 inline 元素间空白、普通段落缩进、预定义实体以及 XHTML 1.1 的 eacute/nbsp/hellip，断言文字与词边界保真。没有必要改变本次未验证的 CSS 隐藏、布局或复杂格式支持。

## 已修 A1–A5 的当前源码复核

- A1：Gemini tool index 属于 stream decoder 实例，跨 SSE event 不再每次从 0 开始；共享 blank-id/index 的 OpenAI 合并规则仍在。
- A2：Claude 流 scope + content block index 决定 reasoning identity；opaque replay metadata 使空 Reasoning 保留；Responses reasoning item id 与 encrypted_content 经过 parse/reconcile/accumulator。没有把内部 identity 写进 Claude wire。
- A3：没有 image identity 的完整 data URI/http(s) URL 创建独立图，Responses 同 call identity 仍按片段合并。
- A4：PPTX 用关系 namespace 读 r:id，fallback 从 slide basename 取数字。没有发现原双 id/数字排序问题回退。
- A5：Claude 旧系列使用 manual enabled/budget，adaptive 与 effort 按 family 划分，预算小于实际 max_tokens。未重复列入追加问题。

上述是当前源码复核；本子任务没有重跑前轮 Gradle 测试，不把 `repair-A.md` 的历史通过声明作成本次运行。

## 明确排除与范围边界

- Responses resumed complete 使用 `.last()` 看似会只剩尾 chunk，但 `streamText(resumeFrom)` 已先用 accumulator 转成每步全量 recovered snapshot；不接受这个假阳性。
- Responses `incomplete` 映射到 stored COMPLETED 是“服务端已终止”状态，同时保留 finishReason 供上层截断处理；不按枚举名称误报。
- SSE 背压、非 JSON HTTP 错误、缺终态 EOF、多行 data、Responses 参数 done replace、failed、Gemini remote image 等上一轮明确排除项仍不重复报告。
- 搜索 Result/runCatching 捕获取消、阻塞 execute、URL 去重大小写及 HTML 解析规则未得到新的生产可观察失败证据，不按风格或潜在风险列缺陷。
- 跨 provider 历史 reasoning 的过滤仍可疑，但本次收束前没有建立独立失败回归与完整切换 owner 证据；继续保留待查，不追加历史隔离框架。
- DeepRead importer 的 parser 错误哨兵/unknown SIZE 两项由 root 协调归属，本文件不把尚未二次判定项列入确认。
- PDF native 文本语义与真实扫描件 OCR 未在本子任务中作设备/原生库验收，不以源码风格替代结果。

## 建议阶段

1. Provider request 契约：AP1 的失败回归、adapter 精确分支与 mock wire 验证，独立 subagent review。
2. Document 文本保真：AP2/AP3 的合法 ZIP fixture 先证明当前错误，复用现有 parser 修复，定点 `OfficeParserFixtureTest` 与独立 review。
3. root 按整体改动统一运行相关模块检查与构建；报告源码、受控 JVM/mock、真实 provider/设备为不同证据层级。
