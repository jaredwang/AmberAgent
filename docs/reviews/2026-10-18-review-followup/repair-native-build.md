# Native / Markdown / regex 修复记录

本包范围为审查 N1-N7，外加主 agent 授权的 N1 同根因 `MessageRenderCache` 规则 key。遵循测试先固定可观测行为、主 agent 串行验证、随后修生产的顺序。未读取兄弟产品，未运行 Gradle/Cargo；不提交或推送。

## RED 准备

已补实际行为回归：

- `AssistantRegexReviewRegressionTest`：同 hash find/replacement 编辑；Java `$1bar`、反斜杠及非法 group per-rule skip；plain native 与 lookbehind 正控。
- `MessageRenderRegexCacheTest`：视觉规则同 hash 编辑后，同一已显示正文必须更新。
- `MarkdownContentPreservationTest`：两个 Kotlin 入口的 LaTeX 偏移、波浪号围栏及 TLD 边界；保留正常链接与尾标点。
- `markdown-preprocess`：配对的公式偏移、波浪号代码保护、TLD 结束边界及正常 URL 正控。
- `markdown-parser`：真实 HTML emit 的 code URL 保真和普通文本/代码里的危险 scheme 字面量保真；真实 link/image/raw HTML 的安全正控。
- `regex-transformer`：固定锁定 crate 的真实 `$1bar`、反斜杠展开结果，作为 JVM 分发回归所注入 native 输出的独立证据。

仅为可验证接线加入：原 `replaceRegexes` 主流程的 internal nativeApply 函数参数 overload、原 `generateMarkdownHtml` 的可见性 wrapper；此时保留全部旧问题行为。初次中间态曾短暂使用函数类型 named arguments，已立即改成 positional arguments；那次编译失败不记作产品 RED。

JVM filters：

```text
app.amber.core.ai.transformers.AssistantRegexReviewRegressionTest
app.amber.feature.ui.components.message.MessageRenderRegexCacheTest
app.amber.feature.ui.components.richtext.MarkdownContentPreservationTest
```

保留行为控制：`app.amber.core.model.ReplaceRegexesPreflightTest`、`app.amber.feature.ui.components.richtext.MarkdownLinkifyPreprocessTest`。

Native 命令，在 `native/` 执行：

```sh
cargo test -p markdown-preprocess -p markdown-parser -p regex-transformer --locked --offline
```

## 已决定的最小方向

- N1：两个缓存 key 使用完整不可变规则值，保留容量、LRU、锁和生产调用边界。
- N2：现有 preflight owner 将含 `$` 或反斜杠 replacement 的 batch 交给 cached JVM；普通 replacement 保留现有 native 开关和路径。没有取消 native 功能、删除 dead code、扩展兼容矩阵。
- N3-N6：Kotlin 已有 JetBrains parser 与 Rust 已有 pulldown parser 的代码范围作为代码上下文依据；每次文本变化后针对对应文本取范围。TLD 结束条件阻止链接到较长域名的前缀。
- N7：保持 HTML 安全处理 owner，只处理真实 tag 中 href/src 的值，正文及序列化后的 code 文本保持原样；既有危险 scheme 策略不撤销。

实际 RED / GREEN、独立 review 与构建证据待主 agent 统一执行后更新；当前不宣称修复完成。
