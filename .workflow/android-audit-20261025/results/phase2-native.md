# Phase 2 native Markdown repair

Owner: native/markdown-parser/src/lib.rs only. No new dependency, no other native crate changes, no EPUB/Gradle edits. Independent phase review is assigned by root to audit_tools.

## Changes

- Bare-URL protection uses the existing pulldown-cmark parser source ranges for code blocks, code spans, links/images, raw/inline HTML. This replaces the incomplete manual fence/backtick recognizer.
- Safe-link transformation now runs on Link/Image destinations and raw/inline HTML attributes. Ordinary Text/Code events never enter sanitation.
- Raw HTML attribute sanitation preserves ordinary raw text, nested literal attributes such as title, and non-URL attributes such as data-href. Existing quoted href/src dangerous-scheme policy remains in place.
- Pulldown emits block HTML as line events. A standalone event probe and a failing regression demonstrated that per-line sanitation would miss multiline raw tags, so the render owner collects only the parser-defined HtmlBlock before sanitizing it. No general HTML parser/framework was introduced.

## RED evidence

- Before production edits: focused html_ baseline 11 passed / 6 failed. Exact six code/text preservation assertions retained unchanged.
- Added raw-HTML text/non-URL-attribute regression before production edits: 0 passed / 1 failed, showing title/data-href/raw text all rewritten by the old sanitizer.
- Added multiline raw tag regression after event-level conversion: 0 passed / 1 failed. This specifically exposed a regression in the first implementation, then was repaired at HtmlBlock ownership.

## Final GREEN evidence (actual current run)

- `cargo test -p markdown-parser --locked --offline`: **28 passed, 0 failed**, dump_corpus 0 tests, markdown doctests 0 tests.
- `cargo test --workspace --locked --offline`: **92 unit tests passed, 0 failed** across 9 crates; one `jni-common::init_logger_once` documentation example remains ignored. No other failed or ignored tests.
- Workspace breakdown: highlight 6; HTML normalizer 7; JNI common 6; Markdown parser 28; Markdown preprocessor 20; Office parsers 7; reader extractor 2; regex transformer 9; sync crypto 7.
- `git diff --check -- native/markdown-parser/src/lib.rs`: passed.

Host tests validate the Rust renderer and preserve actual unsafe-link/image/raw-tag destination assertions. Android cross-compilation/packaged JNI and device rendering remain root's separate integration/acceptance evidence.
