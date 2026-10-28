# Independent verifier: native Markdown HTML and EPUB

Reviewed live source at 0971aa41; no production/test edits, no Gradle invocation. Root owns coordinated JVM tests.

## Native focused reproduction (current run)

Ran from native/: `cargo test -p markdown-parser --locked --offline html_ -- --nocapture`.
Result: 17 matched tests, 11 passed, 6 failed, exit 101. These are current failures, not historical artifacts.

The six failures reduce to **two owner-level defects**, not six separate bugs:

1. **P2: Markdown source regex rewrites URL literals inside code** (`native/markdown-parser/src/lib.rs:174-279`).
   - Four failures: tilde fenced code, indented code, unclosed fenced code, multi-backtick inline code with an inner single backtick.
   - Actual output adds visible `<` and `>` to code literals; e.g. tilde fence URL emits `&lt;https://example.com/path&gt;` rather than original code text. Multi-backtick fixture also adds `>` outside the inline code.
   - Cause: `collect_skip_ranges` manually recognizes only paired triple-backtick blocks and greedily pairs all backtick runs without delimiter-length semantics. It never recognizes tilde/indented code and never closes an unclosed fenced skip range to EOF.
   - Minimal repair: derive protected source ranges from the already-present pulldown-cmark parser (`Parser::new_ext(text, markdown_options()).into_offset_iter()`). Protect `Event::Code`, code-block start ranges, links/images, and HTML events before applying the existing bare-URL rewrite. Existing parser handles tilde/indented/unclosed fences, exact backtick lengths, nested/container syntax, and UTF-8 byte boundaries. No new parser dependency or hand-built Markdown state machine is needed.
   - The locally installed pulldown-cmark 0.12.2 source documents `into_offset_iter` as source ranges in `src/parse.rs:1306-1312`; parser tests establish that Start ranges span entire constructs (`offset_iter`, `reference_link_offsets`).
   - A read-only standalone rustc probe linked to the existing pulldown rlib confirmed all four protected spans: tilde fence 0..36, indented code 4..29, unclosed fence 0..33, multi-backtick inline Code 0..35. The probe source is saved under logs/parser-offset-probe.rs; its temporary binary was removed. No production file was edited for the probe.

2. **P2: whole-output URL sanitizer modifies ordinary code/text literals** (`native/markdown-parser/src/lib.rs:288-301`).
   - Two failures: literal `href="javascript:alert(1)"` inside a fenced code block and in a plain paragraph is rendered as `href="#"`.
   - Cause: the regex runs across all emitted HTML and does not distinguish an actual `href`/`src` attribute in a tag from character data between tags. Preserving code samples and plain text is the expected behavior; it is not permission to retain unsafe clickable URLs.
   - Minimal repair: scope sanitation to actual tag attributes (or corresponding parsed link/image/HTML events). Preserve text/code events verbatim. Keep existing actual Markdown-link/image and raw-HTML unsafe-destination tests green. If scanning rendered HTML tags, use quote-aware tag boundaries so `>` within a quoted attribute does not end the tag early; avoid broad regex replacement over character data. No new dependency is needed.

## Production reachability / false-positive challenge

- These failures affect the **HTML renderer**, not the packed-AST renderer. Do not label every Markdown message affected.
- `MarkdownDocument.kt:242-248` routes a message containing a block-level HTML element into `MarkdownNew` with the entire message; all current failure fixtures deliberately prefix `<div>raw block</div>` to reach this production route.
- `MarkdownNew.kt:34-45` -> `HtmlMarkdownParse.kt:22-44` -> `MarkdownNativeSwitch.renderHtmlOrNull` -> `MarkdownParserNative.renderHtml` -> Rust `render_to_html`.
- Native HTML is **enabled by default**, despite stale spike/disabled comments: `core/settings/.../NativePathPrefs.kt:61` initializes `markdownHtml=true`, persisted missing preference defaults true at line 116; `NativePathBootstrap.kt:262-264` enables it unless the remote kill switch is on or the user disables it. Availability is checked and missing native libraries fall back to JVM.
- Therefore these are reachable production text-preservation defects when native .so is available, not experimental-only test failures. They are **not established security exploits**; the demonstrated impact is displayed/copied code or text corruption. No extra security-hardening framework is justified by these six assertions.

## EPUB historical entity/indentation reports

- Inspected current `document/src/main/java/app/amber/document/EpubParser.kt` and current `OfficeParserFixtureTest`.
- Entity handling now uses `nextToken()`, explicitly handles `ENTITY_REF`, and falls back to Android `Html.fromHtml` for missing **or empty** parser entity text (`EpubParser.kt:151-159`). This is materially different from the earlier entity failure: commit `5375e1c8` changed `parser.text ?: ...` to `parser.text?.takeIf { it.isNotEmpty() } ?: ...`.
- Source indentation handling only appends whitespace-only TEXT when the current output is nonempty and its last character is not whitespace (`EpubParser.kt:145-149`), so block-separating source indentation after `\n\n` is discarded while inline spaces survive.
- Existing focused tests include `epubPreservesWhitespaceBetweenInlineElements`, `epubPreservesXhtmlNamedAndXmlEntities`, and `epubIgnoresSourceIndentationBetweenBlocks`, plus strict/legacy diagnostics contracts.
- Existing on-disk JUnit XML shows all 17 OfficeParserFixtureTest tests passed **on 2026-10-18T06:48:42Z**. This file is historical and is not current verification. Root has been asked to run `:document:testDebugUnitTest --tests app.amber.document.OfficeParserFixtureTest` in its coordinated run.
- **Classification until current JVM run: no newly confirmed EPUB entity/indentation defect.** Current source contains the prior repair, and no further edit is justified from old memory alone. Update this paragraph with root's live result before final delivery.

## Required checks after repair

- Native: `cargo test -p markdown-parser --locked --offline`, then root's already-required workspace run once.
- Keep all six existing failure assertions unchanged; actual unsafe-link/image/raw-HTML destination sanitation must still pass.
- JVM: current OfficeParserFixtureTest entity/indentation/inline-space and strict/legacy tests; no EPUB patch unless the live run proves a current defect.
