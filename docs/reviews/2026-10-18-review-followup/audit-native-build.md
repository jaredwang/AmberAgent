# Native/JNI 与构建补充审查

范围：本 Android 构建根的 Rust crate、Kotlin JNI adapter、native 开关、Gradle 打包配置及 Manifest 入口。基于当前工作树，只写本报告；未修改生产或测试，未执行 Gradle/Cargo 构建或测试，未读取兄弟产品。已排除原 comprehensive-review 中的 26 组修复。下列结论是入口、输入及源码契约的二次复核，回归执行由主 agent 统一安排。

## 确认问题

### N1 / P2：规则缓存只比较 32 位摘要，编辑碰撞字符串后继续执行旧规则

- 位置：`app/src/main/java/app/amber/core/ai/transformers/AssistantRegexProcessor.kt:93-116`；缓存命中直接返回旧 program（54-60 行）。
- 生产入口：`UserInputPreprocessor.kt:34` 在用户消息入库前执行；`RegexOutputTransformer.kt:50,56` 处理 assistant 正文与 reasoning；视觉消息也调用同一 extension。
- 明确触发：一个固定 id 的启用规则，find 为 `Aa`、replacement 为 `X`，先处理一次后，在设置中把同一规则 find 编辑为 `BB`，其余字段不变。Java/Kotlin `"Aa".hashCode() == "BB".hashCode() == 2112`，key 的 scope、visual、signature 全相等，下一条 `BB` 不会变成 `X`，旧 `Aa` 规则仍然生效。replacement 的同 hash 编辑同样有问题。
- 二次复核：`AssistantRegex` 是不可变 data class，但缓存 key 丢弃其完整值，因此换新对象也不能失效。此问题不依赖 native library 是否加载，JVM 路径同样受影响。不是正常 LRU 淘汰或允许过期：命中会持续把旧条目留在前端。
- 最小修法：key 保存影响 program 的完整不可变规则值/快照与 scope、visual，使 hash 只承担桶定位，值相等承担命中判断。保留现有容量、锁及 compiled program，无需新的失效协议。
- 回归：固定同 id 的 `Aa -> BB` find 编辑和 replacement `Aa -> BB` 编辑，通过真实 `replaceRegexes` 断言新行为立即生效；不只测试 key/hash helper。
- 同根因生产层：`app/src/main/java/app/amber/feature/ui/components/message/MessageRenderCache.kt:42-49,203-208` 的 `visualRegexText` 将完整规则丢成同类 `renderSignature()`。例如同一 assistant 视觉规则 `Aa -> X`，先显示 `Aa BB` 后将 find 改为 `BB`，外层 visual cache 仍直接返回 `X BB`，即使内层 program key 修好也不会重算。主 agent 已授权本轮同时修该 key，保留原 LRU、锁和调用边界。对应真实 `visualRegexText` 的同 hash find/replacement 行为回归已准备。

### N2 / P2：默认开启的 Rust regex 路径仍接收 Java/Rust 语义不同的 replacement

- 位置：`AssistantRegexProcessor.kt:24-30,175-186`；`native/regex-transformer/src/lib.rs:126-128` 直接使用 `regex::Regex::replace_all`。
- 生产入口同 N1；`core/settings/.../NativePathPrefs.kt:58-60,127-129` 默认启用 regex，采样默认 0。`NativePathBootstrap.kt:275` 将真实开关接入。
- 明确触发：find `(foo)`、replacement `$1bar`、input `foo`。Java replacement 为 capture 1 后接 `bar`，得到 `foobar`；锁定的 Rust regex 1.12.3 使用最长名称，把 `1bar` 当完整 group 名，未找到时展开为空，得到空字符串。当前 preflight 仅检查 replacement `\\$` 和 `$<`，该规则会进入 native，返回合法字符串，不触发 fallback。Rust 本地依赖主源码 `regex-1.12.3/src/regex/string.rs:650-669` 明确记录该展开规则。
- 同一根因的另一形态：Java replacement 中的反斜杠用于转义下一个字符，Rust replacement 不作相同处理；当前仅检查反斜杠加 `$`，漏掉普通反斜杠转义。Java/Rust 默认字符类也存在语义不同，不能把“双方都编译成功”视为语义相同。
- 二次复核：这不是允许的“高亮风格不同”。现有注释与 prefs 契约明确承诺 USER/ASSISTANT 内容转换保持 JVM 语义；N2 可在消息持久化前改写用户正文。只开启 diff 采样不能修复，因为采样后仍返回 nativeOutput。
- 最小修法决定：主 agent 比较后采用现有 preflight owner 的充分条件：replacement 含 `$` 或反斜杠时整批走 cached JVM，普通 replacement 保留 native 开关和性能路径。统一 cached JVM 的方案会扩大到撤销现有 native 功能，本轮不采用；也不增加兼容矩阵、引擎或死代码删除。此结论只覆盖本轮已固定的 replacement 缺陷，不将黑名单宣称为任意 Java/Rust 语义等价证明。
- 回归：真实 extension 分发下覆盖 `$1bar`、反斜杠 replacement、非法 group 引用以及原先 lookbehind case，证明输出和原 JVM per-rule skip 契约相同；不要复制 detector 正则来“测试” detector。

### N3 / P2：JVM LaTeX 第二遍替换继续使用第一遍之前的代码偏移

- 位置：`app/src/main/java/app/amber/feature/ui/components/richtext/Markdown.kt:171-195`；HTML 分支的 `MarkdownNew.kt:107-125` 重复了同一问题。
- 生产入口：`MarkdownBlock -> parse/preProcess`；native 被用户关闭、kill switch 关闭或 library 不可用时走该 JVM 路径。`MarkdownNew` 的预处理总在 HTML 生成前执行。
- 明确触发：输入前缀 `\\(a\\) `，后接单反引号包围的 `\\[x\\]`。第一遍把前缀从 `\\(a\\)` 缩短为 `$a$`，原代码区间从 6..12 移到 4..10；第二遍找到 block LaTeX 起点 5，用旧区间判为“代码外”，把代码字面量 `\\[x\\]` 改成 `$$x$$`。
- 二次复核：Rust `native/markdown-preprocess/src/lib.rs:104-107` 已明确重算中间文本的 code ranges，证实预期是保留代码原文，而非有意兼容错误。不能因默认 native 开而视为死路径：JVM fallback 是实际产品支持路径，HTML 分支也独立执行该逻辑。
- 最小修法：第二次替换基于第一遍的 result 重算代码区间；两个 Kotlin preprocess owner 都要对齐。避免全局状态或额外解析缓存。
- 回归：使用现有 `preProcessForTest`/HTML 回归设施，断言前缀被转换而反引号内 `\\[x\\]` 保持原文；覆盖两个 Kotlin 入口。

### N4 / P2：预处理没有保护合法的波浪号代码围栏，代码内容被自动链接/公式转换

- 位置：`Markdown.kt:148,173,206`；`native/markdown-preprocess/src/lib.rs:39-42,113-117`。
- 生产入口：同 N3；native 默认分支及 JVM fallback 均受影响。
- 明确触发：`~~~text\nexample.com\n~~~` 是合法 fenced code，但保护正则只认闭合三反引号或单反引号。裸域名被改成 `[example.com](https://example.com)`，后续正常解析为代码时显示并复制的已是被改写内容；代码中的 `\\(x\\)` 同理会改写。
- 二次复核：当前 `NativeMdTree.kt` 的代码围栏处理明确支持反引号与 `~`（174-208 行），故不是项目不支持该语法。保护代码内容是本来就声明的规则，增加准确范围识别是在原职责内修复。
- 最小修法：在现有 code span/fence 范围 owner 中支持有效波浪号围栏（含未闭合到末尾的 streaming 场景），native/JVM 行为对齐；无需新增依赖或通用解析框架。若使用现有 parser offset 获取代码范围，保持当前转换次序。
- 回归：两路 preprocess 对 `~~~` 内域名、inline/block LaTeX 保留原文，代码外同类内容仍转换；包含 fence 内反引号、不同 fence 长度及末尾尚未闭合的真实 streaming 用例。

### N5 / P2：裸域名 TLD 没有结束边界，会把较长域名链接到错误主机

- 位置：`Markdown.kt:150-151`；`native/markdown-preprocess/src/lib.rs:45-53`。
- 生产入口：所有 Markdown 预处理，native/JVM 都会执行自动链接。
- 明确触发：输入 `example.company`，正则先匹配 `example.com`，得到 `[example.com](https://example.com)pany`。`.info` 等白名单 TLD 的更长后缀也有同类截断。
- 二次复核：TLD 白名单可能是产品范围设计，但对白名单之外的完整域名，应保留原文，不能制造指向另一真实域名的链接。现有 `trimEnd` 只处理尾标点，不能恢复已经截断的 TLD。
- 最小修法：在域名后加准确的结束条件，Kotlin 可用现有语法边界；Rust 在 match 后按紧接字符判断，避免为此换 regex 引擎或依赖。不要扩大 TLD 范围来掩盖问题。
- 回归：`example.company`、`example.infox` 不被部分链接；`example.com/path`、`example.com.` 的原有链接行为保持一致；两路输出对齐。

### N6 / P2：Native HTML 自动链接在代码块内插入尖括号

- 位置：`native/markdown-parser/src/lib.rs:158-164,174-211,219-265`。
- 生产入口：`Markdown.kt:1632-1638` 在消息含 block HTML 时调用 `MarkdownNew`；其 138-158 行在实际默认 `markdownHtml=true` 时调用 Rust HTML。该入口不依赖尚未开放的 `markdownAst`。
- 明确触发：消息 ` <div>hello</div>`（去掉开头空格亦可）后空行，再接 `~~~text\nhttps://example.com\n~~~`。`collect_skip_ranges` 没有波浪号围栏，自动链接先把 code source 改成 `<https://example.com>`；pulldown 随后将它当代码正常转义，用户看到并复制的多出尖括号。缩进代码及含不同反引号 run 的代码区间也不能由当前“相邻 run 配对”可靠保护。
- 二次复核：N4 是另一层 preprocess 的裸域名/公式改写；即使 N4 修复，这里仍会独立改写带 scheme 的 URL。不是只发生在 standalone Rust helper，因为实际 HTML 分支默认已接线。
- 最小修法：在现有 pulldown Parser 中利用其代码/链接事件范围精确排除代码后再自动链接，或复用已有准确范围扫描；不要扩大成新的 Markdown 框架。保留裸 URL 正常生成 anchor 的契约。
- 回归：`render_to_html` 对波浪号、缩进、未闭合 fence 和多反引号 inline code 中的 URL 保留代码原文，正文裸 URL 继续成 anchor；带 raw HTML 的输入用来固定生产分支可达性。

### N7 / P2：Native HTML 的安全链接 regex 会修改代码/普通文本中的属性示例

- 位置：`native/markdown-parser/src/lib.rs:164,288-301`。
- 生产入口同 N6。
- 明确触发：带 block HTML 的消息，再接代码块内容 `href="javascript:alert(1)"`。pulldown HTML code 文本保留双引号；全段 HTML 的 regex 未限定在真实元素属性上，将 `<code>` 中的源代码改成 `href="#"`。普通文本里的相同属性示例也会被替换。
- 二次复核：保留禁用危险链接是既定契约，但 code/text 中的字面量并非可点击 href/src，因此不是安全策略有意变更。当前 helper 既不能区分语义，也会把代码范例改坏；不建议简单取消 sanitization。
- 最小修法：在现有 HTML emit 职责中只处理真实 link/image destination 与真实 raw HTML 属性，保留正文/Code 事件文本；可用已有 parser 事件范围与当前 raw HTML 局部处理，保持 `javascript/data/vbscript/file` 的原安全行为，不引入新解析框架。
- 回归：代码和普通文本的 `href="javascript:..."` 原样保留，实际 Markdown link/image 与 raw HTML href/src 仍按既有策略变为 `#`；保留现有 safe-https 和 dangerous-scheme 断言。

## 已排除 / 未作为缺陷

- highlight 对未打包语言返回 Plain：当前代码与注释明确把它作为可接受的降级，不属于内容丢失。
- Native AST 默认关闭、保留 JVM 树：明确 rollout gate，不据此列为未完工缺陷。
- HTML diff normalizer 的 cosmetic divergence：只走采样诊断，不用于实际显示，不列用户行为问题。
- Reader extractor JNI 的返回签名/默认接线/ProGuard ctor keep：未找到满足当前生产入口和明确影响的新问题。
- `.so` 缺失时 JVM fallback、构建工具是否安装：现有 release/graphite/baseline 打包校验有独立策略；不把本机工具状态当产品缺陷。
- Sync crypto：当前 AES-256-GCM 的 key/IV/tag 布局和 native-null 回退未见新的高置信契约问题；不增加假想参数兜底。
- Manifest 的权限绑定 service 与 shortcut 入口：未以“exported=true”本身推断漏洞；未找到本范围内可证明的新信息泄露链。

建议分阶段收口：先 N1/N2（regex program owner），再 N3-N7（Markdown 内容保持与 URL 边界）；每阶段先固定失败回归，再修复并由独立 reviewer 核对实际接线和未受损的安全语义。构建与设备验收由主 agent 单独报告。
