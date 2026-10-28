# Phase 2 provider repair evidence

Status: AI-1..4 implementation complete after root-coordinated RED; ready for coordinated GREEN and independent review.

Ownership: AI-1 complete/image HTTP body cancellation in ClaudeProvider, GoogleProvider, ChatCompletionsAPI, OpenAIProvider; AI-2 Mistral content arrays in ChatCompletionsAPI; AI-3 Gemini Text signatures in GeminiGenerateContentAdapter and both message accumulation paths; AI-4 KeyRoulette cache. Responses codec/transport belongs to separate sidecar.

New regression classes:

- `app.amber.ai.provider.providers.ProviderBodyCancellationTest`: actual loopback HTTP headers + blocked body through Claude/Google/ChatCompletions public completion and OpenAI/Google image-generation entry points, then cancelAndJoin within one second and assert same Call canceled.
- `app.amber.ai.provider.providers.ProviderContentReplayTest`: raw Mistral JSON completion/all nested reasoning + answer chunks, SSE Text arrays, Gemini signed Text+Image completion replay, empty signed Text after visible output, distinct signed Text boundary through both accumulator and legacy append paths.
- `app.amber.ai.util.KeyRouletteTest`: context-backed real cache, key rotation without raw-key persistence; legacy raw-key entries scrubbed for active and other provider on next write.

Root RED command:

```sh
./gradlew --offline :ai:testDebugUnitTest \
  --tests 'app.amber.ai.provider.providers.ProviderBodyCancellationTest' \
  --tests 'app.amber.ai.provider.providers.ProviderContentReplayTest' \
  --tests 'app.amber.ai.util.KeyRouletteTest'
```

No paid API, credentials, device, or Gradle invoked by this agent.

## RED and fixture correction

Coordinator `phase2-ai-red.log` established all five content-replay and both original LRU plaintext regressions. Initial body-cancellation test cleanup surfaced a secondary EOF instead of the ownership assertion; this was not counted as accepted RED. Its framing now uses supervisorScope and asserts the actual Call.isCanceled after explicit cancellation before join/cleanup. Coordinator `phase2-body-cancellation-red.log` then reproduced all five intended cancellation ownership assertions before AI-1 production GO.

## Applied owner repairs

- AI-1: existing withCancellableCall now owns nonstream generation's Call through body consumption in Claude, Google, ChatCompletions and Responses. Grok/Responses existing 401 retry swaps to the new Call within that same owner. OpenAI image create/edit and Google image predict use the same scope; response.use closes the body on success/error. SSE and remote-image cancellation owners remain unchanged. A sixth cancellation case covers the touched public Responses nonstream route.
- AI-2: ChatCompletions parses all ordered Mistral Text/thinking content array chunks, including every nested thinking text. Primitive text, top-level reasoning, tools and images retain their existing mapping.
- AI-3: Gemini ordinary Text preserves thoughtSignature on decode/replay. Both append implementations and stream snapshot coalescing retain empty signed Text and prevent merging signed parts. Message upload eligibility recognizes a signed empty Text. Replay tests now include serialization/deserialization before wire encoding, covering durable metadata retention too.
- AI-4: LRU persists SHA-256 key fingerprints in an explicit version=2 envelope. The old raw-key map is migrated for all providers in the next write, including unused providers; a raw 64-hex key is hashed because format version, not string shape, determines interpretation. Rotation still returns the in-memory real key. No new crypto/store dependency or permissions.

Independent review packet: audit_data reviews AI-1..4; Responses codec/transport's separate changes are reviewed independently by root's assigned reviewer. This agent changed only ResponseEventTransport.generateText for AI-1; sidecar owns all stored-stream/resume changes.

`git diff --check -- ai` passes. Full module GREEN, build and device/provider boundaries remain root-owned and pending.

## First full AI validation and fixture calibration

Coordinator phase2-ai-green.log: 257 cases, 256 passed, 1 new Mistral SSE assertion failure, 0 skips. The remaining failure was not missing text: UIMessage.toText joins every part with a newline while rendering Reasoning as an empty string, so a reasoning-plus-text message has the existing `\n42` rendering. No production behavior was changed to satisfy this test.

The new SSE fixture now uses a mixed closing-thinking/first-Text array event followed by a primitive Text suffix, compared against the actual existing top-level reasoning_content plus primitive Text stream. Both accumulator paths still assert exact semantic Text=`42`, full Reasoning=`checked`, the control's existing `\n42` rendering, and identical array/control rendering. This retains the missing-answer regression assertion and tests the real transition; it does not trim output or weaken old assertions. Coordinator rerun pending.
