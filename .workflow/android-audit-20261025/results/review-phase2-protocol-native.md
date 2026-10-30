# Independent Phase 2 review: Responses protocol and native Markdown

Reviewer audit_tools; no authorship of these production changes, no production/test edits or Gradle/cargo run during this review. Reviewed current implementation and root/worker verification evidence.

## Review regression and disposition

### PR2-1 / P2: standalone suffix replay has no prefix in its reconciler

`ResponseEventTransport.streamStored` now requests `starting_after=cursor.sequence` but creates a fresh `ResponseStreamReconciler`. The response.completed event contains the complete response output, while this reconciler has seen only suffix deltas. Its `reconcileText` prefix-diff logic assumes accumulated text starts at the beginning of the full output.

Concrete coherent event sequence: caller already has prefix `ha`; suffix SSE sends delta `ha`, then completed output `haha`. Fresh reconciler records `ha`, regards it as a prefix of `haha`, and emits another `ha` from completed. `RunRecoveryService.fetchMissingEvents` seeds the local partial and appends these chunks, producing `hahaha` instead of `haha`.

Production route: cold-start stored response recovery -> completed status with finalMessage=null (explicit supported SSE fallback) -> fetchMissingEvents uses session.cursor -> StoredResponseApi.streamStored -> suffix query + fresh reconciler. Immediate reconnect within an existing transport reuses the prior reconciler and is safe; the issue is the standalone cursor API.

The initial suffixReplayUsesServerCursorAndCheckpointsWithoutACreatedEvent fixture supplied completed output `tail`, although a coherent terminal response includes prefix+tail. The implementation owner replaced it with a seeded-prefix/full-terminal fixture and added an actual ResponseAPI + Room recovery route. Root/coordinator ran RED in logs/phase2-response-review-red.log: both tests failed with expected `haha`, actual `hahaha` (JUnit XML verified by this reviewer). This confirms both protocol behavior and production consumer reachability.

**After-implementation re-review: source blocker closed, coordinated GREEN pending.** The new standaloneSuffixReplay branch is enabled only for direct streamStored(cursor != null). Actual deltas continue through the normal reconciler; item-level full message snapshots are checkpointed without inventing a prefix diff or replacing an entire multi-item response. The existing terminal event flag forwards the authoritative full terminal message. MessageStreamAccumulator.append explicitly replaces the active message when delta is null and message exists (:31-37), so both repeated-prefix text and final-only content converge on the server's complete response. No new accumulator, retry path or recovery framework was introduced.

The direct fixture now checks intermediate content after output_text.done and before terminal, as well as final content/cursor. The real Room fixture retains full item.done + full completed, durable result, COMPLETED and cleared cursor assertions. Existing full-rebuild path still passes cursor=null (no starting_after / no standalone mode); automatic reconnect keeps the original full-history reconciler and does not enable standalone mode. Source review found no further blocker in this bounded correction. Root owns full-AI and app recovery GREEN and must append that evidence before claiming verification complete.

## Responses contracts verified in current code

- ResponseAPI's official endpoint/API-key/useResponseApi/user toggle gates determine resume before encoder invocation. The normal and custom/off streaming path does not gain background mode. Codex branch does not pass resume to the encoder. Nonstream creation sets store for opted-in storage but stays synchronous.
- Gated streaming creation emits background=true and store=true. No new opt-in/permission/provider was introduced.
- Replay GET uses HttpUrl query construction with stream=true. Explicit full rebuild passes cursor=null and omits starting_after; immediate reconnect and suffix replay pass the actual sequence.
- Known response identity is injected into standalone/reconnect replay, so suffix events do not depend on receiving response.created again.
- response.created is queued and drained before later content; store.save is awaited before visible chunk emission. Its nullable chunk emits no phantom empty assistant message. Early disconnection uses the same existing single reconnect.
- Dedup is before reconciling a queued event, preserving accumulated state from replayed old deltas.
- fetchStatus retains its JSON GET. No undocumented retry framework, extra endpoint probe or custom-provider background fallback was added.

## Native Markdown review: no blocker found

- Code/link/image/raw-HTML protected source ranges now come from the already-installed pulldown parser. Tilde, indented, unclosed and multi-backtick code preservation assertions remain intact; no new dependency or parallel Markdown grammar was introduced.
- Link/Image destinations are sanitized at their event owner. Text/Code are left untouched. Raw HTML scanning matches real opening-tag spans and exact quoted href/src attribute names; title/data-href and ordinary raw character data preservation have explicit regression coverage.
- Block HTML is accumulated only between parser-owned Start(HtmlBlock)/End(HtmlBlock), because multiline tag attributes arrive as line events. Final source includes a multiline raw tag regression requiring actual javascript/file destinations to become #.
- Inline HTML follows its own event path. Existing dangerous Markdown link/image and raw href/src tests remain unchanged alongside code/literal preservation cases.
- The existing sanitizer policy is preserved, not represented as a general HTML/XSS sanitizer. No speculative broader attribute/scheme normalization is requested by this review.

Worker evidence phase2-native.md reports current markdown-parser 28/28 and host native workspace 92 passed / 0 failed, with the existing JNI logger doctest ignored. I inspected final source/test ownership but did not rerun those checks. Android cross-compile/packaged JNI and device rendering remain root's separate verification boundary.
