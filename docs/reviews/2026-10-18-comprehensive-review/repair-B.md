# Repair B: memory and final context budget

## Scope and authorization

Owned changes: `MemoryRepository`, `MemoryRecallStore`, `TokenBudgetFitter`, their targeted tests, and audit/repair notes. No `ChatService` edits (B1 belongs to the chat/runtime worker), no commits/pushes, no Gradle invocation by this worker. Existing unrelated WIP was preserved.

## Repairs

- B2: `acceptCandidate` now returns `MemoryRecord?`. An invalidation whose target has already been deleted completes the candidate without inserting a new memory and records `memoryId=null` in its acceptance event. Existing live/archived invalidation outcomes and the production Room transaction stay in place. The sole production caller already ignores this return value; positive tests now explicitly require a non-null record.
- B3: `buildPrompt` stops immediately when all memory scopes are disabled. User profiles now follow the existing `ChatStartSuggestionGenerator` contract: every source must remain active, unexpired, and inside an enabled scope; legacy source-less profiles require all scopes. Freshness checking remains in place. Enabled-scope selection is owned by a small private helper used by public prompt construction and recall selection.
- B4: the final hard input budget is the actual configured model window minus its output reserve. Removed the 4000-token floor that could exceed this remainder. A minimum positive window fully reserved for output produces budget zero and a normal `ContextTooLargeException` instead of an invalid `coerceIn` range.

## Regression evidence

Root ran the new tests before production edits; `/tmp/amber-review-20261018-red-phase1.log` demonstrated:

- `MemoryLifecycleTest`: missing-target invalidation incorrectly recreated a memory.
- `MemoryRecallProfileGateTest`: five negative profile/scope/source cases failed against the previous implementation; the enabled-profile positive control passed.
- `TokenBudgetFitterTest`: actual-remainder budget, final request rejection, and minimum-window checks failed against the previous implementation.

Targeted regression files are:

- `app/src/test/java/app/amber/core/memory/store/MemoryLifecycleTest.kt`
- `app/src/test/java/app/amber/core/memory/recall/MemoryRecallProfileGateTest.kt`
- `app/src/test/java/app/amber/agent/data/context/TokenBudgetFitterTest.kt`

After edits, `git diff --check` passed. Green JVM execution, app compile/build, and phase-independent review remain root-owned pending validation. Device installation, app execution, and live-provider acceptance were not performed by this worker.

## Bounded maintainability pass

Read and applied `code-simplifier/SKILL.md` and its Kotlin guidance once to the owned diff. Nullability now describes an actual domain outcome instead of fabricating a record; profile gating follows the existing same-product behavior without new settings or persistence; context fitting keeps its current estimator and request boundary. No additional refactoring was needed.
