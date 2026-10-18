# Audit B: chat lifecycle, memory, and context

Read-only audit against HEAD `d983c0b` and the pre-existing working tree. No production/test edits and no Gradle runs were performed by this audit. Findings below were traced through current production callers and checked again against the owning state/persistence contract. Suggested reproductions are test specifications, not claims of executed tests.

## Confirmed findings

### B1 / P1: ordinary paused-run resumes consume the previous pause snapshot

- Defect: `app/src/main/java/app/amber/core/service/ChatService.kt:1342-1355` waits for a new activation snapshot only when `expectedPausedRunId != null`; ordinary same-run resumes have `resumeRunId != null` and `expectedPausedRunId == null`.
- Reachable entry: `handleToolApproval` (`ChatService.kt:2224-2240`) -> `applyToolApprovalDecision` (`2353-2364`) -> `continueGenerationInline` -> `runKernelDispatchLoop(resumeFirst=true)` -> `dispatchKernelTurn`. Batch approval and notification approval also use this ordinary resume path.
- State/persistence evidence: `InProcessAgentRunner.kt:139-150` deliberately reuses the existing StateFlow. `launch` starts an asynchronous lazy job (`156`, `362-364`), and resets the snapshot to RUNNING only after the suspending durable gate succeeds (`200-223`). `dispatchKernelTurn` therefore can observe the still-current WAITING_USER snapshot immediately after `runner.launch` and terminate its wait at `1350-1355` before the resume activation starts.
- Impact: the session job ends and `activeKernelRuns` is removed (`1373-1375`) while the agent coroutine can still execute tools/stream output. UI generation state and queue ownership no longer cover that work. A UI Stop after the actual durable state has become RUNNING can find neither an active kernel run nor a WAITING_USER fallback; queued/new input may dispatch while the resumed activation is still running.
- Deterministic regression: seed a WAITING_USER snapshot and durable row for one run; hold the runner resume gate with a `CompletableDeferred`; accept the final pending tool; assert the session dispatch stays active and the active run remains registered before gate release, and assert it waits for this activation's next terminal/pause after release. Current code returns on the pre-launch WAITING_USER snapshot.
- Minimal repair: apply the already-existing pre-launch snapshot barrier to every same-run resume, not only settings-driven `expectedPausedRunId` resumes. Keep the strict automatic-resume ownership checks and requested-run-id behavior.
- Second review: not a by-design pause release. Pause release is correct after the new activation pauses; this path releases before that activation obtains ownership. The runner's delayed RUNNING publication is itself intentional and must be retained.

### B3 / P1: disabling all memory scopes still injects the dream user profile

- Defect: `app/src/main/java/app/amber/core/memory/recall/MemoryRecallStore.kt:49-67` loads and injects a stored profile independently of the enabled-memory scopes.
- Reachable entry: settings page changes `enableCoreMemory`, `enableShortTermMemory`, and `enableLongTermMemory` (`SettingAgentMemoryPage.kt:1101-1122`). `ChatGenerationRoundEngine.kt:161-175` calls `buildPrompt` and includes its result in the system prompt on every round.
- State/persistence evidence: `recallSelections` correctly returns no records when every scope is disabled (`MemoryRecallStore.kt:80-85`), but `buildPrompt` continues to read `MemoryProfileStore` and all records, then passes `userProfile.content` to `MemoryPromptBuilder`. The builder intentionally produces nonempty `<memory_context><user_profile>...` even with an empty record list.
- Impact: remembered personal facts continue to be sent to the provider after the user turns off all memory scopes.
- Regression: persist a fresh profile, keep its source IDs active, set all three memory flags false, and call `buildPrompt` for a normal user message. Assert an empty prompt and no repository/profile reads. Current code returns a prompt containing the profile.
- Minimal repair: return an empty prompt at the public build boundary when no scope is enabled; reuse a single enabled-scope computation if needed. Do not add profile migrations or new settings.
- Second review: profile freshness is a separate condition and cannot substitute for the user's recall enablement. This is confirmed even without deciding finer behavior when only some scopes are enabled.

### B2 / P2: accepting an invalidation whose target was deleted creates a new active memory

- Defect: `app/src/main/java/app/amber/core/memory/store/MemoryRepository.kt:664-679` falls through `intentTarget?.toRecord() ?: addMemoryInternal(...)` for an `invalidates memory #N` candidate when N no longer exists.
- Reachable entry: extractor intentionally creates reviewable invalidation candidates (`MemoryExtractor.kt:323-346`, `426`); settings VM accepts them via `SettingAgentMemoryVM.kt:271-287` -> `MemoryRepository.acceptCandidate`.
- State/persistence evidence: the extractor documents INVALIDATE as archive-without-replacement. The repository branch itself says a gone/already-archived target satisfies invalidation without a new record (`668-669`), but the missing-target case inserts `candidate.content` as an active record, marks the candidate ACCEPTED, and logs that new memory as its result.
- Impact: deleting a stale memory between extraction and review can cause the later acceptance to reintroduce that fact (or its retraction text) as active recall content.
- Regression: create PENDING candidate with reason `invalidates memory #11: ...`, delete record 11, accept candidate; assert memory table stays empty and candidate completion has the intended explicit outcome. Also retain the existing-target archive and already-archived-target cases.
- Minimal repair: handle absent-target invalidation as a no-insert outcome. `acceptCandidate` currently returns non-null `MemoryRecord`, but its only production caller discards the return value; choose an honest nullable/outcome contract or a surfaced stale-target failure. Do not fabricate a record solely to preserve the signature. Keep the production transaction around candidate state and memory mutation.
- Second review: not the intentional CONFIRM fallback, which explicitly re-adds a reaffirmed fact. INVALIDATE has the opposite contract. The already-archived target branch is already correct and must remain unchanged.

### B4 / P2: the hard token budget floor can exceed the real model window

- Defect: `app/src/main/java/app/amber/core/context/TokenBudgetFitter.kt:164-168` computes `contextWindow - outputReserve`, then raises the result to at least 4000, overriding the actual available input capacity.
- Reachable entry: model editor accepts positive custom context windows (`SettingProviderModelSettingsForm.kt:92-113`, `378-384`); provider import also accepts any positive context window. Fitter uses this budget to let a request through at `TokenBudgetFitter.kt:74-93`.
- Deterministic numeric case: model window 4096, max output 2048 -> budget returns 4000 instead of 2048. An estimated 3000-token input is accepted although input plus requested output is 5048. Model window 1 also reaches `.coerceIn(1, 0)` and throws an unrelated IllegalArgumentException before a normal fit result.
- Impact: the request boundary advertised as the final hard fit permits requests over the configured model capacity for small windows or large output reserves; these proceed to provider context errors rather than being trimmed/rejected locally.
- Regression: assert budgets for `(window=4096, reserve=2048)` and a larger window with nearly all tokens reserved. Exercise fit with an input between the actual remainder and 4000. Add the supported lower boundary (positive window 1) so the replacement does not retain an empty coerceIn range.
- Minimal repair: honor the actual positive remainder. A large lower bound belongs only to an unknown-window fallback, not to a known model window. Handle the positive minimum explicitly without new heuristics.
- Second review: this is arithmetic independent of token-estimator uncertainty. The documented 4000 floor does not make exceeding an explicitly configured model capacity intentional; the fitter's own contract reserves output inside that capacity.

## Candidates rejected after re-check

- `ChatEventProjector.replayUnfinished` skipping pause states is intentional: paused runs must survive process restart under the same run ID. `RunRecoveryService` mirrors recoverable terminal-owner state into event-store rows before the projector runs, so a superficial “every live row should be INTERRUPTED” fix would break recovery.
- Dream upserts are not blind replacements: `MemoryRepository.upsertRecordInternal` uses a revision CAS, returns the new revision, and the dream applier updates its local record map after every write. The existing merge-then-promote test covers the sequential revision path. No additional general retry/fallback is warranted.
- A historical tool result being cleared by `PreparedContextEditor` is a request-copy transformation with explicit allowlisted read tools, recent-message protection, multimodal/error protection, and a recoverable placeholder. Durable conversation data is unchanged. No confirmed data-loss defect from this path.
- Memory reinforcement cooldown is deliberate to prevent one tool loop from repeatedly farming useCount; no defect reported merely because each loop round does not increment it.
- Invalidating an already-archived memory correctly keeps the archived record and completes the candidate. B2 is specifically the absent-target insertion branch.

## Suggested repair order and verification

1. Chat ownership phase: repair B1 and test deferred same-run resume plus Stop/session ownership; run the affected JVM chat/runtime tests and compile the app.
2. Memory phase: repair B2 and B3; test profile opt-out plus missing/live/archived invalidation acceptance; run targeted memory JVM suites.
3. Context phase: repair B4; test tight-window budgets and final fit, then run relevant context suites.
4. Each phase should receive independent subagent review focused on its diff and reachable behavior. Final integration should run the repository's relevant full JVM and app build gates. Physical-device and live-provider acceptance remain separate gates and were not performed by this audit.
