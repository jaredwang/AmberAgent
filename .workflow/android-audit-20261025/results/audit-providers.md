# Provider and message audit

Baseline: 0971aa41; read-only production audit. Root coordinates Gradle. A separate scoped sidecar audited Responses API in `audit-response-api.md`.

## Confirmed candidates

### AI-1 / P1: cancellation stops owning non-stream requests at headers

- Owners: `ClaudeProvider.kt:109-117`, `GoogleProvider.kt:266-272`, `openai/ChatCompletionsAPI.kt:135-148`, OpenAI image create/edit endpoints (`OpenAIProvider.kt:457-462,485-489`), Google image predict, and similar provider metadata/OAuth body reads.
- Mechanism: `common/http/Request.kt:20-23` resumes the cancellable continuation with Response at headers. Its invokeOnCancellation is tied to that suspended await, not subsequent blocking `body.string()`. Every listed provider then reads the body synchronously, with no active Call cancellation owner. `withContext(Dispatchers.IO)` does not interrupt a socket read on coroutine cancellation.
- Production entry: `ChatGenerationRoundEngine` non-stream branch -> `ProviderAttemptExecutor.complete` -> gateway.complete; OCR also calls complete independent of chat streaming. Image-generation tools reach image endpoints. Stop cancels the job but can wait until body timeout / server completion, delaying run settlement and task replacement.
- Narrow fix: reuse existing AI `withCancellableCall` around call issuance plus body consumption, including Grok 401 replacement call; use Response.use. Do not change common await's header-return semantics or add retry/fallback. Include reachable OAuth/metadata calls if changing their HTTP owner for the same defect; no need to rewrite all networking.
- Regression: local ServerSocket sends HTTP 200 headers and one body byte then blocks. Wait for body read, cancelAndJoin the provider.complete job under 1 second, assert Call canceled. Existing `openai/CancellableCallScopeTest` already captures the required timing; add at least actual Claude/Google/ChatCompletions entry coverage to prevent a wrapper-only green result. Image endpoint equivalent if touched.
- Second pass: cancellation before headers already works and is excluded; SSE EventSource awaitClose already cancels; remote image download already has an end-to-end cancellation continuation; Codex image streaming uses withCancellableCall. Those paths need no speculative changes.

### AI-2 / P1: Mistral content arrays lose their final answer

- Owner: `openai/ChatCompletionsAPI.kt:814-824,864`.
- Both complete and SSE call parseMessage. `content` accepts JsonPrimitive only; array handling reads only the first item's first thinking text. No Text chunk from an array ever enters UIMessagePart.Text. A supported Magistral/Mistral response with content `[thinking(...), text(answer)]` produces only reasoning; streamed `content:[{type:text,text:answer}]` produces no parts.
- Production entry: configured OpenAI-compatible Mistral endpoint -> OpenAIProvider -> ChatCompletionsAPI -> generation accumulator/timeline. Request code explicitly recognizes api.mistral.ai (stream options and reasoning), so this is a supported transport, not a new provider proposal.
- Impact: successful paid request renders no visible answer or only reasoning; repair paths can issue an unnecessary second request.
- Narrow fix: parse all supported array chunks in order, preserving all nested thinking text and visible Text parts; keep primitive content and existing top-level reasoning/tool/image handling. Do not add permissive unknown-object serialization.
- Raw fixture: `{"role":"assistant","content":[{"type":"thinking","thinking":[{"type":"text","text":"check"}]},{"type":"text","text":"42"}]}`. Assert reasoning `check` and text `42` for complete, plus SSE text-array round-trip.
- Second pass: Mistral docs explicitly support string OR chunk-list response content; this is not hypothetical multimodal support. Verified primary sources: [Chat completions](https://docs.mistral.ai/studio/conversations/chat-completion), [Reasoning](https://docs.mistral.ai/studio/conversations/reasoning), [official Magistral cookbook](https://docs.mistral.ai/resources/cookbooks/mistral-agents-non_framework-hubspot_dynamic_multi_agent-hubspot_dynamic_multi_agent_system).

### AI-3 / P1: Gemini signed Text parts disappear before history replay

- Owners: `GeminiGenerateContentAdapter.kt:156-166,340-345`; streaming owners `MessageStreamAccumulator.kt:124-136,319-330` and legacy `Message.kt` text append branch.
- decodePart retains thoughtSignature on Reasoning/Tool/Image but drops it on ordinary Text. encodePart drops Text metadata too. A final streaming empty Text carrying only thoughtSignature is skipped outright by both accumulators; adjacent text merging also needs to preserve signed-part boundaries.
- Production entry: GoogleProvider.complete/stream -> adapter -> chat round message accumulation -> durable conversation -> next Google request. Chat models with IMAGE output are explicitly supported by generationConfig.responseModalities. A generated-image response's first signed normal Text plus signed inlineData is legitimate output; the next user asks to edit it.
- Impact: Gemini 3 image conversational edits can fail HTTP 400 because all signed normal/image parts must be returned unchanged. Ordinary text chat loses recommended reasoning continuity; do not overclaim that all text chats fail.
- Narrow fix: preserve ordinary Text signature metadata on decode/encode; preserve empty signed Text and signed-part boundaries through both message accumulation paths and coalescing. Add only the existing signature contract, no generic transport event abstraction.
- Regression fixtures: (a) completion with Text(signature D) then Image(signature E), append history + new user and encode: both signatures and order retained; (b) stream Text `answer`, then empty signed Text, accumulate and re-encode: signature remains; (c) distinct signed Text parts must not collapse into one replacing the earlier signature.
- Second pass: functionCall and image signatures already survive and are excluded. Primary docs explicitly state signatures occur on Text, can arrive on empty Text, and image editing validates all signed model parts: [Gemini 3 guide](https://ai.google.dev/gemini-api/docs/generate-content/gemini-3), [Thought signatures](https://ai.google.dev/gemini-api/docs/generate-content/thought-signatures), [image generation](https://ai.google.dev/gemini-api/docs/generate-content/image-generation). Text-part non-merging contract: [thinking](https://ai.google.dev/gemini-api/docs/generate-content/thinking).

### AI-4 / P2: key-rotation cache writes decrypted provider keys to plaintext

- Owner: `util/KeyRoulette.kt:49-50,65-81,96-98`.
- Production: ClaudeProvider/GoogleProvider/OpenAIProvider with Context select KeyRoulette.lru; each provider request passes the rehydrated API key. LRU serializes a provider -> raw API key -> timestamp map to cacheDir/lru_key_roulette.json. Even one key is copied on first request; old keys from other providers persist until their entry expires and another request cleans it.
- Violated current owner contract: `core/settings/.../secret/SecretStore.kt:21-24` protects credentials in Keystore AES/GCM and says real values never enter key names. Settings migration does not address this secondary plaintext copy.
- Impact is narrowly plaintext-at-rest credential retention in app-private cache, NOT proven external disclosure. Cache is not included by current backup rules, and no claim of exported file access is made. Root should weigh this narrower scope when accepting severity.
- Narrow fix: persist SHA-256 fingerprints rather than API keys, map selected fingerprint back to the in-memory key. Scrub existing plaintext cache via in-place rewrite/migration rather than retaining a new file next to it. No extra encryption framework or permissions.
- Regression: Context-backed LRU with two known keys chooses in expected rotation order; cache file contains neither key. Seed legacy raw-key format, call next, assert all persisted entries no longer contain raw keys, including other providers.

## Rejected / deferred

- Tool Image outputs are filtered to Text by ChatCompletions/Gemini/Responses. MCP ImageContent reaches this path and Anthropic supports images, but current WebMountCaptureTools:58 explicitly documents the narrower tool-image boundary and uses a text workaround. Without a broader product requirement, do not call all tool-image omission a new regression or expand all provider protocols here.
- Gemini streaming function-call indices are globally monotonic in GeminiStreamDecoder; this previously fixed issue is not present now.
- Anthropic signature deltas append via mergeReasoningMetadata, redacted empty reasoning persists; do not repeat historical findings.
- SSE malformed/error payload and premature EOF handling already propagate failure through StreamTerminationGuard/EventSource cancellation.
- ChatCompletions null finish-only delta is intentionally accepted and usage-only chunks are retained.
- Antigravity onboarding can theoretically overwrite a concurrently refreshed credential snapshot, unlike Gemini's owner-scoped onboarding update. Current production entry pre-refreshes and no concrete tested timing was established in this packet, so defer rather than inflate the confirmed list.

## Validation boundary

Source/caller and official wire-contract review completed. No production/test edits, Gradle, paid provider request, or device execution in Phase 0. Regression cases above need root-coordinated RED/GREEN. Responses-specific confirmed findings and exclusions are in the separate sidecar report.
