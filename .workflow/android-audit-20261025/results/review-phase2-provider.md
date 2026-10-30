# Independent Phase 2 provider implementation review

Reviewer authored no ai production or tests. Read current ai diff, phase2-provider.md, prior verify-providers.md, new fixtures and relevant production callers. No production/test edits or Gradle/API/device execution. Stored-response stream/replay changes are outside this packet; only ResponseEventTransport.generateText's AI-1 body owner was reviewed here.

## Result

No blocking production defect found in AI-1..4 repairs. The implementation uses existing owners and stays within the confirmed protocol/storage problems. Coordinated GREEN belongs to the root's Gradle runner; this source review does not claim it.

## Behavior verification

- **AI-1 HTTP body cancellation:** Claude/Google/ChatCompletions complete, OpenAI image create/edit, Google image predict and Responses nonstream now keep the existing CancellableCallScope registered until Response.use finishes body consumption. Error bodies are covered as well. Grok and Responses 401 retries replace the tracked active Call within the same owner; the old response is closed before replacement. Cancellation races before enqueue remain handled by ensureActive plus Call.await's existing cancellation registration. SSE and remote-image owners were left unchanged. Actual loopback tests wait for responseBodyStart then assert the active Call's cancellation before join, so they verify the original after-headers failure rather than a wrapper-only mock.
- **AI-2 Mistral content:** the common complete/SSE parser walks all array chunks in order, gathers every nested thinking TextChunk and retains each visible TextChunk. Primitive visible content, top-level reasoning_content metadata, tool calls and images retain existing mappings. Mixed reasoning/text in a single delta reaches both accumulators in the same order. Existing final chat owner finishes reasoning on durable generation completion, so the accumulators' existing mixed-chunk timing condition is not a permanently unfinished-reasoning bug.
- **AI-3 signed Gemini Text:** decode and encode preserve thoughtSignature on ordinary Text. Mutable append, snapshot coalescing and legacy append all retain empty signed parts and prevent signed/unsigned boundaries from merging. isValidToUpload accepts a signed empty Text, avoiding a later upload filter undoing preservation. The fixtures use raw Google payloads, both actual accumulation paths, and serialize/deserialize UIMessage before replay. Existing image/function-call signature handling is preserved.
- **AI-4 LRU:** the on-disk envelope has explicit version=2; only that envelope is interpreted as fingerprints. A legacy raw 64-hex key is therefore hashed, not mistaken for a fingerprint. Legacy load migrates every provider's map before the next successful write, including other still-unexpired providers, so plaintext is not merely moved to a second file or left in unused provider entries. Selection maps the fingerprint back to the original in-memory credential and keeps existing LRU semantics. The tests cover rotation, all-provider legacy scrub and raw64hex. Cache remains app-private; no exfiltration or backup-disclosure claim is supported or made. Existing best-effort cache write behavior is unchanged; successful persistence is the migration completion boundary.

## Fixture precision note (nonblocking)

The original Mistral SSE fixture sent thinking-array and text-array events separately. Implementer refined that existing case to a real mixed closing-thinking plus first-text transition, followed by a primitive answer suffix. Reviewed the calibration: both accumulation paths still assert exact visible Text `42` and Reasoning `checked`, with an equivalent primitive reasoning_content control confirming the existing `toText()` newline convention. No production change or weakened content assertion was made. No additional test framework or provider feature was introduced.

## Boundaries and exclusions

- Metadata/model-list/OAuth HTTP calls outside the chosen chat/image failure paths were not rewritten or claimed fixed.
- No speculative tool-image expansion, final-only output replacement or new retry protocol was introduced.
- Read/write cache failures are not external key disclosure, and arbitrary attacker-edited versioned cache contents are not a demonstrated production input.
- No real paid provider request, device behavior, OEM/background acceptance or credential exposure test was run by this reviewer.
