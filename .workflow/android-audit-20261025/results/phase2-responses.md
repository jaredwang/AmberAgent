# Phase 2 Responses protocol repairs

## Test-only preparation

Owned test file: `ai/src/test/java/app/amber/ai/provider/providers/openai/ResponseAPIStoredProtocolRegressionTest.kt`.

Added six focused wire/ownership fixtures using the existing ResponseAPI transport injection and in-memory cursor store:

1. Only opted-in official streaming creation enables background mode; off/custom gates remain ordinary.
2. Direct nonstream encoder cannot return an immediately queued background response.
3. Full rebuild uses GET stream=true without starting_after (durable prefix may lag cursor).
4. Suffix replay requests starting_after and persists the known response identity without a response.created event.
5. response.created checkpoints identity before output without emitting an empty assistant message.
6. Disconnect immediately after response.created can use the existing single reconnect and checkpoint suffix output.

Preparation left production files untouched. Root coordinated Gradle RED:

```sh
./gradlew --offline :ai:testDebugUnitTest --tests 'app.amber.ai.provider.providers.openai.ResponseAPIStoredProtocolRegressionTest'
```

Expected current failures: missing background flag, stream query, creation cursor and early reconnect. Nonstream compatibility control should already pass. Existing `ResponseAPIResumeStreamTest` has bare-URL expectations and a checkpoint-null expectation that need updating only when the production phase is authorized; do not weaken content/cancellation assertions.

## Authorized implementation (GREEN-ready)

Root reported the actual RED result: 6 cases, 5 meaningful failures; nonstream compatibility control passed. Phase 2 implementation authorization then received.

- `ResponseRequestEncoder`: gated resume + streaming sets background=true alongside existing store=true; nonstream encoding stays synchronous.
- `ResponseEventTransport.storedRequest`: replay GET always sets stream=true; suffix replay/reconnect additionally sets starting_after, while explicit full rebuild intentionally has no suffix cursor.
- Replay transport receives its known response ID, so a server suffix without response.created still persists the cursor.
- response.created enters the existing ordered queue/drain as an identity-only checkpoint; no empty UI chunk is emitted. An immediate disconnect can use the same existing single reconnect.
- Sequence filtering now happens before the reconciler mutates state, preventing duplicate history from polluting final-only text reconciliation.
- `StoredResponseApi` documentation now reflects the wire query.

Existing test adjustments are contract-specific: bare replay URL expectations now assert stream and cursor queries; suspended content checkpoint retains the earlier creation cursor instead of requiring null. The old duplicate-replay fixture previously changed already-delivered sequence payloads from abcde to fghij and asserted a truncated abcdeklmno. It now replays coherent history and additionally requires the final-only suffix to survive, retaining rather than weakening content correctness.

GREEN scope: `ResponseAPIStoredProtocolRegressionTest`, `ResponseAPIResumeStreamTest`, `ResponseAPIStreamReconcileTest`, `ResponseAPIMessageTest` (all under app.amber.ai.provider.providers.openai) plus root's broader provider checks. `git diff --check` passed. No Gradle run was started by this packet; root/build coordinator runs verification.

## Independent review follow-up PR2-1 (test-only)

Independent reviewer audit_tools identified fresh suffix replay's missing prefix state: prefix `ha`, suffix delta `ha`, complete server snapshot `haha` can become `hahaha` when the fresh reconciler compares the full snapshot against only its suffix. The earlier suffix protocol test incorrectly supplied a suffix-only terminal snapshot and missed it.

The AI fixture now seeds the actual accumulator with `ha` and requires the coherent full snapshot `haha`. Added `RunRecoveryServiceResumeTest.completedStatusWithoutOutputUsesRealSuffixStreamAndFullTerminalSnapshot` to exercise the production fallback: real ResponseAPI GET returns completed without output, Room holds partial/cursor, real transport receives suffix plus full item.done and completed snapshots. It checks durable final text, terminal state and cursor clearing. These follow-up edits are tests only pending root's RED/GO.

Suggested owner repair: a fresh suffix replay does not know the prior reconciliation prefix, so preserve its authoritative complete terminal message as a snapshot for the caller's existing accumulator replacement. Automatic reconnect shares the original complete reconciler and keeps its delta behavior. Existing terminal queue metadata can distinguish these without new retries or a parallel recovery framework.

Root/coordinator confirmed both direct AI and actual Room recovery RED: expected `haha`, actual `hahaha`, in `phase2-response-review-red.log`. Root authorized the owner repair.

Implemented `standaloneSuffixReplay` only on the direct `streamStored(cursor != null)` path. It forwards real deltas, checkpoints intermediate item snapshots without inventing a diff against the unknown prefix, and returns the complete terminal message unchanged so the existing caller accumulator replaces its partial. Item-level snapshots are not treated as whole-response snapshots, preserving correctness when a response has several message items. Automatic reconnect keeps the original shared reconciler and full incremental history. The direct fixture now also asserts the partial is still `haha` immediately after `output_text.done`, before the terminal event, preventing transient invented text from being retained if delivery stops there. No new retry/restore mechanism. GREEN-ready; Gradle remains with coordinator.
