# Audit A: provider, search, document

审查基线：`d983c0b7` 加当前工作区。只读检查 `ai/`、`search/`、`document/` 和 app 的生产 provider/generation 接线；未读取兄弟产品，未运行 Gradle，未改生产/测试代码。下列“确认”指当前源码、可达调用链与协议契约复核成立；回归测试设计已给出，尚未把设计描述成已执行测试。

## 确认问题

### A1 / P1: Gemini 跨 SSE event 的独立工具调用会串成一个工具

- 位置：`ai/.../GeminiGenerateContentAdapter.kt:496-500`，`ai/.../ui/Message.kt:596-616`，`Message.kt:482-515`。
- 入口/owner：`GoogleProvider.stream -> GeminiStreamDecoder.decode -> MessageStreamAccumulator.append -> appendTool -> findToolMergeTarget`；app `ChatGenerationRoundEngine.kt:351-404` 用该 accumulator 的 snapshot 执行工具和更新消息。
- 触发：第一 event 的一个完整 functionCall 为 `{"id":"call-a","name":"search","args":{"q":"a"}}`；第二 event 的另一个完整 functionCall 为 `{"id":"call-b","name":"lookup","args":{"q":"b"}}`。每个 event 的 `withIndexedTools()` 都赋 index=0；按新真实 id 查不到时仍以 index=0 命中 call-a。
- 结果：只剩 call-a，工具名变为 `searchlookup`，参数变成 `{"q":"a"}{"q":"b"}`，工具执行失败或漏执行。无 provider id 的老 Gemini 同样受影响，因为本地 fallback id 每次不同但 index 重用。
- 二次复核：不是同一调用参数片段的合法合并，因为输入有两个不同真实 id；当前 `findToolMergeTarget` 明确仍按 index 回退。已执行工具不会成为目标的保护不适用，因为同一轮工具在流结束前尚未执行。
- 最小修法：Gemini decoder 为流内独立调用分配稳定且不碰撞的工具 index；真实 provider id 优先决定归属。不要删除 OpenAI 首个 blank id 后补真实 id 所需的 index 语义。
- 回归：两段 raw SSE 经 `GoogleProvider -> decoder -> accumulator`，断言两个工具的 id/name/args 和 encodeContents 的两条 functionCall/functionResponse 对应关系；覆盖有真实 id、无 id、同 event 并行三个场景。现有 `ProviderWireAdapterFixtureTest` 只直接测试 `Tool.merge` 后补 id，不覆盖 decoder 跨 event。

### A2 / P1: opaque thinking 块被 snapshot 丢弃，且相邻 thinking 块边界被破坏

- 位置：`ai/.../ui/MessageStreamAccumulator.kt:165-176,330-338`；`ai/.../AnthropicMessagesAdapter.kt:164-179,288-303,354-382`；OpenAI `ResponseAPI.kt:1003-1026,1060-1087`。
- 入口/owner：Claude/Responses/Gemini decoder 产出 `UIMessagePart.Reasoning`，app 经 accumulator snapshot -> `GenerationUpdate.full/streamingTail` 持久化/下一轮重新 encode。
- 触发 1：Claude 的 `content_block_start` 返回 `{"type":"redacted_thinking","data":"opaque"}`；该 part 文本为空但 metadata 有 `redacted_thinking_data`。snapshot 的 coalesce 只保留 `reasoning_content_present`，其他空 Reasoning 全部消失。
- 触发 2：相邻的两个 Claude content blocks 是 `thinking` 和 `redacted_thinking`，或者两个 redacted blocks。decoder 只为 Tool 记录 block index；appendReasoning 把相邻 Reasoning 无条件拼在一起，以后一个 metadata 覆盖前一个，回放只剩最后一个块。
- 结果：工具续轮缺失或改写必须原样回放的 thinking，Claude API 可拒绝该轮；Gemini 空 thoughtSignature 和 OpenAI 无可见 summary 的 encrypted reasoning 也会在 snapshot 中消失。
- 二次复核：不是隐藏 UI 的产品设计：adapter 专门实现了 redacted_thinking encode/decode，snapshot 同时是下一轮协议输入。Anthropic 官方明确要求工具续轮原样传回 thinking/redacted blocks：[Thinking](https://platform.claude.com/docs/en/about-claude/models/extended-thinking-models)。
- 最小修法：为需要回放的 provider reasoning 保存块 identity，使相邻不同块不能合并；同块增量仍可拼接。保留携带 opaque replay metadata 的空块，仍清除真正没有内容/元数据的空 reasoning。检查 legacy `UIMessage.appendChunk` 同类 merge 语义，避免两个公开入口行为分歧。
- 回归：raw Claude SSE 包含独立 redacted block + tool use，经 accumulator 后 encodeConversation 必须原样保留；再覆盖 thinking(signature) + redacted、两个 redacted、只有 signature 的空 thinking、Responses empty encrypted reasoning。不能只直接对 adapter 做 round-trip 而绕开 snapshot。

### A3 / P2: 两个 event 各返回完整图片时，第二张被追加到第一张 base64/URL

- 位置：`ai/.../ui/MessageStreamAccumulator.kt:139-155`；`ai/.../ui/Message.kt:77-97,521-538`；`GeminiGenerateContentAdapter.kt:386-407`、`ChatCompletionsAPI.kt:873-888`。
- 入口/owner：Google inlineData 或 OpenAI-compatible images 数组解码出没有 openai_image_call_id 的完整 `UIMessagePart.Image`，ChatGenerationRoundEngine 合并后经 `Base64ImageToLocalFileTransformer` 落盘。
- 触发：event 1 产生 `data:image/png;base64,<完整图 A>`，event 2 产生 `data:image/png;base64,<完整图 B>`；或两个独立 hosted image URL。`appendImage` 将第二张视作上一张分片，直接 append `streamImageData()`。
- 结果：两张图变成一张，数据是 `<A><B>`；第一张可能还能被容错解码，但第二张丢失。URL 则变成 `https://a...https://b...`，无法加载。
- 二次复核：同 event 的多图已有专门保护与测试，但它只禁止合并当前 event 刚创建的图；下一个 event 又会命中。Google adapter 的 inlineData 和 Chat Completions image_url 均明确是完整图片，没有标记为 base64 fragment。
- 最小修法：provider 明确标识独立完整图，或把 full data URI/http URL 视作新图；只有显式同 image identity 的片段才合并。保留 Responses image call id 的分片处理。无需新增下载重试或文件兜底。
- 回归：Google raw SSE 的两个 inlineData event，及 OpenAI-compatible 两个 hosted image event，经 accumulator/legacy merge 后断言有两张独立原始图片；继续保留同 call id 的合法分片回归。

### A4 / P2: 真实 PPTX 的双 id 属性导致导入幻灯片顺序失真

- 位置：`document/.../PptxParser.kt:78-84,390-393`，回退 `:71-75`。
- 入口：chat `DocumentAsPromptTransformer.kt:59`，DeepRead `DeepReadSourceImporter.kt:81`，workspace office reader `WorkspaceArtifactTools.kt:288`。
- 触发：真实 OOXML `<p:sldId id="256" r:id="rId1"/>`，其中 `id` 是数字 slide id，`r:id` 才是关系键。attribute("id") 按 local name 取首个属性，通常得到 `256`，找不到 targets[256]，回退按文件名排序。
- 结果：PowerPoint 中重排的 presentation 在聊天上下文/DeepRead 中恢复成旧文件名次序，段落含义错误。现有回退排序还对所有正常路径 `ppt/slides/slideN.xml` 的 `substringAfter("slide")` 取到 `s/slideN.xml`，数字转换失败后全为 0，实际退化成 ZIP entry 顺序。
- 二次复核：现有 `OfficeParserFixtureTest.pptxUsesPresentationSlideOrderAndReadsTable` 的 `<p:sldId>` 只有 r:id，遗漏了真实文件的 numeric id，因此其成功不能证明真实文件正确；正常创建/重排的 PPTX 会同时包含两者。
- 最小修法：按 officeDocument relationships namespace 精确取 r:id；fallback 数字排序用 basename 的 slideN 提取。无需改变 parser 结构或引入 Office 依赖。
- 回归：fixture 包含 numeric id 在 r:id 前与后的两种属性顺序，关系故意把 slide2 排在 slide1 前；再将无 presentation metadata 的 ZIP entry 顺序设为 slide10、slide2、slide1，断言 fallback 数字排序。

### A5 / P1: 所有 Claude reasoning 模型被一律发送 adaptive，旧模型固定 400

- 位置：`ai/.../AnthropicMessagesAdapter.kt:67-73,227-235`；`ai/.../registry/ModelRegistry.kt:210-229`。
- 入口：provider 设置页可配置/拉取 Claude models，ModelRegistry 给 3.7/4/4.5 标记 reasoning；ChatGenerationRoundEngine 从 sessionDefaults.reasoningLevel 构建 TextGenerationParams，ClaudeProvider.stream/complete 都经 encodeRequest。
- 触发：选择 claude-sonnet-4-5 / claude-opus-4-5 / claude-haiku-4-5 等仍支持 extended thinking 的旧模型，reasoning 非 OFF（包括 AUTO）。encodeThinking 只检查 REASONING ability，必发 `{"type":"adaptive","display":"summarized"}`。
- 结果：这些模型不支持 adaptive，正常聊天请求 HTTP 400。显式非 AUTO 时还一律发 output_config.effort，而旧模型并非都支持 effort。
- 二次复核：不是项目只支持新模型：registry 明确保留旧系列，公开 catalog 也不筛掉；官方明确 Claude 4.5 及更早只支持 manual extended thinking，adaptive 返回 400：[Extended thinking](https://platform.claude.com/docs/en/build-with-claude/extended-thinking)。
- 最小修法：按已支持 model family 区分 adaptive 与 manual enabled+budget_tokens，effort 仅在支持模型发送；OFF 保持 disabled。预算必须小于实际 max_tokens，复用既有 ReasoningLevel.budgetTokens，不加重试/静默降级。
- 回归：相同 TextGenerationParams 分别给 4.5 和 4.6/4.7，断言 wire 请求的 thinking/effort；覆盖 AUTO、OFF、显式级别、小 max_tokens。用 mock HTTP server 拒绝旧模型 adaptive，验证 production complete/stream 发正确 body，而非只测私有 helper。

## 排除/暂不作为缺陷

- Chat Completions SSE 背压：已用 trySendBlocking，buffer 满静默丢 token 的旧问题不成立。
- Chat/Claude/Responses 非 2xx 空/非 JSON body：失败回调已合成 HTTP 异常，不会正常完成零 chunk；不重复报旧问题。
- SSE EOF 无终态：StreamTerminationGuard 已覆盖 Chat [DONE]、Claude message_stop、Responses completed/incomplete、Google finishReason；当前路径不是裸 EOF 成功。
- SSE 多行 data：normalizeOpenAIStreamDataLines 先尝试整体 JSON，再兼容按行；标准跨行 JSON 不会直接被拆碎。
- Responses function arguments.done 重复参数：已携带 replace marker，Tool.merge 实现替换，不能再报 append 重复的旧问题。
- Responses failed：当前 parseResponseDelta 明确抛错，不把 failed 当正常完成。
- Gemini 远程图：GoogleProvider 已先 resolveRemoteImages，并对 MIME/bytes/cancellation 作有界处理；不能用“Gemini 不接受 URL”再报一个已经修复的问题。
- 本地图编码失败：encodeBase64 返回 Result 并在图 request path getOrThrow；本次没有把明确失败错误地判断为静默丢图。
- document ZIP 路径：DOCX/PPTX/EPUB 只用 ZipFile.getEntry 读取，不落地解压；不报告不存在的 Zip Slip 写越界。
- XML entry 上限：BoundedInputStream 限制实际解压字节，不只信任 ZIP 元数据；没有仅凭压缩比作 bomb 推测。
- 搜索 runCatching 捕获取消、Bing Jsoup 阻塞、大小限制等：虽有潜在改进点，本轮未证明可观察取消后副作用/持久化错误，不列为确认缺陷。
- EPUB 内联元素间纯空白丢失、特定非 UTF-8 文档解析：实现存在可疑点，但本轮不扩大到缺乏实际文档 fixture 的格式兼容工程。
- 跨 provider 历史 reasoning 回放：当前 adapter 可疑，但本轮主列表按五个已完成二次复核的问题收敛；不借此追加持久化/provider 隔离设计。
- Claude listModels 同步 execute 取消时机：当前只确认是设置页有界网络等待，不以风格或协程惯例单独列 defect。

## 建议实施顺序

1. Provider stream 数据完整性：A1、A2、A3；同一 phase 结束由独立 reviewer 复核 raw SSE -> accumulator -> tool replay/图结果，避免只修 helper。
2. Claude request 契约：A5；完成 family 配置的精确修复，不扩到 OAuth/模型列表重构。
3. Document 顺序：A4；精确 namespace + basename numeric sort 和真实结构 fixture。

每一 phase 由 root 统一运行必要 JVM 测试、独立 subagent review；最后 app compile/assemble。上述证据均不能替代真实收费 provider、物理设备或用户行为验收。
