# Responses API read-only audit

Baseline: current source at 0971aa41. No production/test edits or Gradle run in this packet.

## Confirmed candidates

### RESP-1 / P1: stored-response creation omits required background mode

- Owner: `ai/src/main/java/app/amber/ai/provider/providers/openai/ResponseRequestEncoder.kt:51-55`.
- Entry: provider settings enable resume -> `ChatRunHooksFactory.responsesResumeFor` (397-425), capability flag and official endpoint gate -> `DefaultRunKernel` -> `ChatGenerationRoundEngine` -> `ResponseAPI.streamText` -> encoder.
- The resume branch only sets `store=true`; this encoder never emits `background=true`. These are synchronous responses. OpenAI's background contract says a new stream can only be started from a background response originally created with `stream=true`; synchronous cancellation terminates the connection rather than calling the background cancel API.
- Impact: enabled resume cannot provide the promised running-response continuation; `StoredResponseStopCancel.cancelStored` calls `/cancel`, whose background-only semantics reject this synchronous response, producing WAITING_EXTERNAL/unconfirmed stop despite a usable network. Fixing just the GET query below will not fix this prerequisite.
- Narrow fix: for the existing gated resumable **streaming** path, encode background mode consistently with store/stream. Do not enable it on unsupported custom endpoints/Codex/non-resume requests. Verify any nonstream caller contract separately rather than blindly backgrounding nonstream calls.
- Regression: existing `ResponseAPIResumeStreamTest` fake records the POST request. Assert JSON `background=true`, `store=true`, `stream=true` for eligible resumable streaming; all off/custom/Codex gates preserve ordinary behavior. Add a local protocol fixture which rejects cancel/replay of a non-background creation, avoiding paid provider calls.
- Second pass: not a preference for background processing; it is required by the already-wired server-side cancel/replay semantics. Existing tests fake all SSE regardless of request body and therefore cannot catch it.
- Official source checked live: [Background mode](https://developers.openai.com/api/docs/guides/background), especially Streaming a background response and Limits.

### RESP-2 / P1: stored replay requests JSON endpoint without `stream=true`

- Owner: `ResponseEventTransport.kt:410-421` (`storedRequest`).
- Entry: network disconnect -> `storedEventStream` 368-380; explicit same-run continuation -> `ResponseAPI.streamText` 88-105 -> `streamStored`; recovery fallback -> `RunRecoveryService.fetchMissingEvents` -> `streamStored`.
- Request is GET `/responses/{id}` plus Accept `text/event-stream`. The documented request enables SSE with query `stream=true`; absence means normal JSON retrieval. `starting_after` is the documented server cursor query. Current request has neither, while local cursor filtering only happens after the SSE reconciler.
- Impact: real recovery/reconnect receives JSON instead of SSE and fails/no events. The fake transport tests explicitly assert a bare URL and script SSE for it, hiding the wire mismatch.
- Narrow fix: construct the replay HttpUrl with `stream=true`; pass `starting_after` only when doing true suffix replay, while the explicit full-rebuild path deliberately omits it. Maintain known response ID on replay so suffix streams without a response.created event still checkpoint. Move local dedup before reconciliation if retaining duplicate replay acceptance.
- Regression: assert actual GET query parameters in fake transport tests (full-rebuild vs cursor suffix). Prefer one local HTTP protocol fixture returning JSON for missing stream=true and SSE for the correct query. A suffix replay without a response.created event should still advance the saved cursor. Replayed old deltas must not contaminate final reconciliation.
- Second pass: HTTP Accept does not replace this query parameter in the documented API. `fetchStatus` must retain its normal JSON GET; only `storedRequest` changes.
- Official sources checked live: [Retrieve response](https://developers.openai.com/api/reference/resources/responses/methods/retrieve), [Background mode](https://developers.openai.com/api/docs/guides/background), and [official SDK streaming usage](https://github.com/openai/openai-node/blob/main/docs/streaming.md).

### RESP-3 / P2: creation event is discarded before the durable response cursor exists

- Owner: `ResponseEventTransport.kt:285-288` and `persistDrain` 401-405.
- Trigger: eligible resumable stream emits response.created (has response ID and sequence), then disconnect/Stop/process death before the first event decoded to a visible MessageChunk. The code sets the response ID but immediately continues, never persisting it. Only parsed visible output events enter persistDrain.
- Impact: reconnect knows the ID in memory but line 370 loads no cursor and throws; Stop's gateway has no cursor and treats the run as nothing stored; cold start has no response identity. With RESP-1 corrected, an actual background generation can continue server-side while the app loses its handle. This is an existing gap in the promised write-ahead ownership invariant, not a speculative retry requirement.
- Narrow fix: checkpoint response.created through the same ordered drain without generating an empty assistant message. Preserve callback/drain ordering; no new retry framework. A known replay ID should likewise not depend on replaying response.created.
- Regression: emit only response.created, yield drain, then disconnect: store must contain responseId/sequence and reconnect must use it. A Stop/gateway lookup after created should resolve the response even without any text.
- Second pass: no earlier consumer checkpoint exists in this path; `ResponseResumeStore` is the only runtime mapping used by `OpenAIStoredResponseGateway`. Existing tests always send content before failure.

## Rejected / conditional candidates

- **Streaming fc_ versus call_ identifiers:** decoder stores item ID as local toolCallId and metadata retains call_id; encoder reconstructs both function_call and function_call_output with the same local ID. Because the request replays the complete call and does not use previous_response_id, this alone does not prove a server pairing failure. Do not claim a functional bug without a concrete rejected request.
- **Final-only images disappear in ResponseStreamReconciler:** buildReconciledDelta currently forwards text/reasoning/tools only, while parseResponseOutput supports generated images. This is a codec gap, but normal official streams emit `response.output_item.done` containing image result and that path survives. Without a valid endpoint fixture that sends final-only image output, classify as conditional rather than inventing provider omissions. Non-stream image parsing already has coverage.
- **Final-only reasoning suffix after streamed ID:** similar conditional codec gap; normal official item.done updates opaque reasoning metadata. Do not add generic final replacement logic based only on imagined event loss.
- **Tool image output filtered to Text:** real serialization limitation across Responses/Chat/Gemini, but existing `WebMountCaptureTools` explicitly documents the workaround; parent packet handles broader product support/policy classification. Anthropic does support multimodal tool output.

## Validation boundary

Protocol requirements were verified against primary official documentation and source traces. No real OpenAI request, paid key, device, or Gradle invocation was made. Suggested regressions still need to be run by the root build coordinator.
