# Provider / document repair A1–A5

范围：当前工作区 `ai/` 与 `document/` 的五个已复核问题。没有提交、推送或发布；没有读取兄弟产品仓库，没有增加依赖。本工作包不运行 Gradle，由 root 串行执行验证。

## 已完成修改

- A1：Gemini 工具 ordinal 归属于一次 stream decoder，不在每个 SSE event 重置；同轮跨 event 的独立 functionCall 保持独立。共享 OpenAI tool merge 的 blank index -> 后补真实 id 规则未改。
- A2：Claude 每条流创建 scope，并把 content block index 作为 reasoning block identity；共享 accumulator/legacy merge 只拼同一 reasoning block，签名 delta 按片段拼接。snapshot 保留 metadata 承载的空 reasoning；可上传性判断承认签名、redacted、encrypted、thoughtSignature 等真实回放字段。Claude encoder 只输出协议里的 signature/data，不把内部 block identity 写入 wire。Responses reasoning text delta 同时保留 item id，final-only 空 summary 的 encrypted reasoning 也能生成与调和，避免 final 事件重复回放。
- A3：没有 image call identity 的完整 data URI/http(s) URL 保持独立图；hosted URL 原样保存，不再被错误包装成 base64 data URI。Responses 稳定 call identity 的合法分片仍按原规则拼接。
- A4：PPTX 用 officeDocument relationships namespace 读取 r:id，不再把 numeric slide id 当 relationship id。缺 presentation metadata 时，从 slide 文件 basename 提取数字排序，不受 ZIP entry 顺序影响。
- A5：识别旧 Claude family 使用 manual `enabled + budget_tokens`，4.6+ 保持 adaptive；manual budget 至少 1024 并小于 max_tokens。用户明确设置无法容纳 thinking 的 max_tokens 时给出直接参数错误，不增加 HTTP 重试或静默关闭 thinking。只有支持 effort 的旧 Opus 4.5 发送 effort，并把该模型不支持的 xhigh/max 映射为 high；4.6 的 xhigh 同样映射为其支持的 high。未知兼容模型维持此前 adaptive 行为。

## 依据

Anthropic 官方 [Extended thinking](https://platform.claude.com/docs/en/build-with-claude/extended-thinking) 明确 Claude 4.5 及更早不支持 adaptive、manual budget 最低 1024 且必须小于 max_tokens，Opus 4.5 是唯一支持 effort 的 manual family。[Effort](https://platform.claude.com/docs/en/build-with-claude/effort) 给出模型支持的 effort 级别；[Thinking](https://platform.claude.com/docs/en/about-claude/models/extended-thinking-models) 要求工具续轮原样回传 thinking/signature 与 redacted blocks。此次 family 策略按这些明确约束实现，未因最新模型默认值改变旧模型契约。

## 失败证据与回归

root 已执行修改前失败验证：`/tmp/amber-review-20261018-red-phase1.log` 中 ProviderStreamRoundTripRegressionTest 的原始 8 项全部失败，OfficeParserFixtureTest 有 3 项失败。失败覆盖跨 event 工具、opaque reasoning、独立图、旧 Claude thinking 和真实双 id PPTX，而非仅靠静态推测。

生产修改后增加了同缺陷组的回归：

- Google raw SSE -> provider -> accumulator -> encode：有/无真实 id 的两个独立工具、两个完整 inline images、空文本但带 thoughtSignature 的块。
- Claude raw SSE -> provider -> accumulator -> encode：单 redacted、signed + redacted、双 redacted、signature 分片、不同流重用 index=0；断言内部 identity 不出现在 wire。
- Chat Completions raw SSE -> accumulator/legacy merge：两个 hosted URL 原样保留。
- Responses parse -> reconciler -> accumulator -> encode：streamed 与 final-only encrypted reasoning 在空 summary 下都可回放且不重复。
- Claude request：3.7/4/4.5 与 4.6/4.7、AUTO/OFF/显式级别、低预算/短输出上限及旧 Opus effort。
- PPTX：真实 numeric id + r:id、两种属性顺序、缺 presentation metadata 时 slide1/slide2/slide10 的数字排序。

已完成本包有限 diff 检查与 `git diff --check`。root 的统一 GREEN 执行记录为 `/tmp/amber-review-20261018-green-phase1.log`；当前 XML 证实 ProviderStreamRoundTripRegressionTest、ResponseAPIStreamReconcileTest、OfficeParserFixtureTest 均通过，前述数据完整性与回放断言成立，ai/document 生产已编译。该次总任务在其他工作包的 RunRecoveryServiceTest.kt:101（String? -> String 类型不匹配）编译处停止，不应据此写成整个 app 测试通过。

测试/编译/组装与真实 provider、安装、运行、用户行为验收需分别报告；本包没有实际收费 provider 调用或设备验收证据。
