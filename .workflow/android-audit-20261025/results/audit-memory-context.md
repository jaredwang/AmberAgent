# Phase 0: memory and context audit

Baseline reviewed: 0971aa41. Read-only audit; no production/test edits, no Gradle run. Existing contracts/tests and actual caller wiring inspected. Findings below are candidates for root's independent second pass.

## MC1 - P1: pre-provider soft fitting discards the current user request

- Owner: `app/src/main/java/app/amber/core/context/ConversationContextPlanner.kt:178-194`; caller `ConversationContextEngine.kt:275-283`; production entry `ChatGenerationRoundEngine.kt:139-147` -> provider final gate `ProviderAttemptExecutor.kt:113-126`.
- Trigger: a tool-loop round has the current USER message followed by executed ASSISTANT tool messages; retained recent messages still exceed the soft context budget after compaction (or notify-only mode skips compaction). `fitMessagesToTokenBudget` retains a suffix until the first message crosses the budget, then breaks without ensuring that the most recent USER survives. For example, last USER costs about 1500 tokens, subsequent executed tool ASSISTANT costs 1500, soft budget 2000: result contains only tool ASSISTANT. With a model hard input budget above 3000, this could have been sent intact, but soft fit already erased the task.
- Observable effect: continuation model sees tool outputs without the current task/user constraints and can answer or act on incomplete instructions. The final `TokenBudgetFitter` explicitly preserves CURRENT_USER and TOOL_RESULT (lines 213-223), but it cannot restore a USER removed by the earlier context planner.
- False-positive challenge: this is a request-copy mutation, not durable data deletion, but it is still real behavior loss. History trimming is intended; dropping the live user task before the declared hard-gate protection is not. The production round engine reruns preparation on tool rounds, so the last message need not be USER.
- Narrow repair: align this soft-fit stage with the existing final-gate preservation contract (at least last USER and current tool-result chain); if protected content cannot fit a soft target, preserve it and let the final hard gate reject if needed. Avoid adding another retry or compaction framework.
- Regression: extend `ConversationContextPlannerTest` with USER + executed-tool-ASSISTANT and a budget between the tool-only and combined costs; assert current USER remains. Also exercise prepareContext/real provider request construction if a suitable existing fixture exists.

## MC2 - P1: first-200-character dedupe silently merges distinct memories

- Owners: `app/src/main/java/app/amber/core/memory/extraction/MemoryCandidateFilter.kt:65-66` and `app/src/main/java/app/amber/core/memory/dream/MemoryDreamPlanner.kt:318-319`.
- Actual path A: Chat completion -> `MemoryExtractor.extractAfterConversation` -> `MemoryCandidateFilter.filter` -> valid candidate marked FILTERED as duplicate if normalized first 200 characters match an existing record.
- Actual path B: `MemoryDreamWorker` -> `MemoryDreamRunCoordinator.run` -> `planMaintenance` (`MemoryDreamPlanner.kt:199-221` groupBy normalize) -> auto apply when `autoApplyMaintenance=true` (default, `core/model/.../MemoryModels.kt:163-166`) -> `MemoryDreamApplier` -> `MemoryRepository.applyDreamMerge` (`MemoryRepository.kt:285-294`) persists one content and archives other distinct records. The same prefix key also ignores pending candidates as duplicates in planner lines 241-249.
- Reproduction fixture: two long records `"sharedprefix".repeat(25) + "project Alpha uses port 8080"` and same prefix + `"project Beta uses port 9090"`, different IDs. Their normalized prefixes exceed 200 characters; planner produces a deterministic merge despite different suffix facts. An accepted candidate with second content is likewise filtered against first. No length cap on stored record content prevents this input.
- False-positive challenge: normalization intentionally ignores case/punctuation. Prefix truncation is a separate loss of identity. Planner itself documents exact-normalize dedupe as verbatim-only (`MemoryDreamPlanner.kt:321-324`), and auto-maintenance is documented to merge exact duplicates only. This can archive nonduplicate user data without manual review.
- Narrow repair: compare full normalized content; remove `.take(200)` in both owner functions. Full normalization already occurs before take, so this does not add a new full-string scan or dependency. Keep existing case/punctuation behavior.
- Regressions: existing `MemoryCandidateFilterTest`, `MemoryDreamPlannerTest`: differing suffix after >200 normalized chars must survive, true full duplicates must still merge/filter. Optionally use existing `MemoryDreamApplierTest` to confirm both records stay active when planner returns no merge.

## MC3 - P2: imported memory supersession lineage retains foreign database IDs

- Owner: `app/src/main/java/app/amber/core/memory/export/MemoryImportExportManager.kt:118-137` and `178-191`.
- Actual path: memory settings file import -> `MemoryImportExportManager.importFrom` -> decoded original IDs are replaced with fresh/matched local IDs, but `supersedesIds` is passed through unchanged via `record.mergingOnto(existing)`. Topics correctly remap member IDs in lines 154-156; formal supersession IDs receive no equivalent remapping.
- Trigger: export record old #10 and replacement #11 with `supersedesIds=[10]`; import into nonempty library where old maps to #150 and replacement to #151. Local #151 still claims to supersede #10 (possibly unrelated local data), not imported #150.
- Observable effect: persisted/exported lineage is wrong; `MemoryDreamApplier.kt:119-124` uses these IDs to find an already-existing replacement and can no longer recognize the imported replacement for #150, producing another duplicate replacement. Memory tool DTOs and future exports also expose the incorrect lineage. Not merely cosmetic foreign IDs: these are primary keys used against this local DB.
- False-positive challenge: source conversation/message IDs are cross-export provenance and should remain unchanged; `supersedesIds` names local memory rows and must follow the same ID mapping as topic members. Current code explicitly acknowledges the foreign-ID problem for topic members.
- Narrow repair: build/import mapping for all formal records, then apply supersession links using the completed mapping, with existing CAS semantics. Do not infer links to unrelated existing rows for missing exported references. Preserve existing merged-row metadata rules.
- Regression: extend `MemoryImportExportTest` using nonempty fake DAO + exported archive old/new replacement; assert replacement links imported old local ID, not file ID. Include replacement-before-old file order so mapping is not order-dependent.

## MC4 - P2: dream model plan accepts mutations of records omitted from its prompt

- Owner: `app/src/main/java/app/amber/core/memory/dream/MemoryDreamPlanner.kt:118-150`.
- The model sees only unarchived/unexpired `promptRecords.take(80)` and `candidates.take(50)` but its response is validated with *all* `records` and *all* `candidates` at line 150. `parseModelPlanJson` permits IDs from those complete collections, then resulting merge/archive/supersede/ignore operations pass through existing applier gates.
- Trigger: >80 formal memories or >50 pending candidates; model returns an ID that exists locally but was omitted (the prompt contains generic schema IDs too). A short-term omitted record can be archived/replaced and an omitted pending candidate can be ignored despite no fact/evidence being supplied to the planner.
- Observable effect: dream suggestions act on unrelated unseen rows. They still require user approval (model plan is never auto-maintenance), so severity is P2; review UI only lists counts and up to five ID references and does not show every target's current content (`MemoryDreamReview.kt:133-203`). This approval gate does not make incorrect target resolution intentional.
- Narrow repair: retain `promptCandidates = candidates.take(50)` and pass exactly `promptRecords, promptCandidates` to `parseModelPlanJson`; keep deterministic maintenance's full-library scope untouched.
- Regression: mock planner gateway with 81 memories / 51 candidates and raw response targeting the omitted entries; assert those mutations are discarded while visible IDs remain accepted. Existing parser tests alone need an explicit shown-set contract because the faulty argument is at planner call site.

## Examined and not elevated

- Memory candidate approval intentionally re-reads the target's current revision (`MemoryRepository.kt:735-741`) and executes inside a Room transaction. A claim that every accept path lacks CAS is false. Staleness between *planning* and later dream approval is a separate design choice; it was not elevated without a settled snapshot-approval contract.
- Profile scope gating exists: recall requires every source ID active and enabled; legacy source-less profile requires all scopes. The historical disabled-scope privacy report is already fixed in current code.
- Dream merge/supersede writes and their audit events are Room transactions in production DI; the no-DB path is for test fakes, not evidence of production partial commits.
- Context planning validates compact source IDs before replacing messages; stale/missing source variants are not blindly hidden.
- Extraction cancellation is rethrown in the outer failure path. The inner confirm `runCatching` may briefly convert cancellation to false, but the subsequent suspending write still observes cancellation; no independently demonstrated stale write was established.
- Relative-date extraction has a genuine prompt/validation inconsistency worth a separate decision: prompt says resolve relative dates to absolute, while `validateEvidence` rejects every numeric token absent from original message text. A date computed from prompt `Today` is rejected. Not included in the four repair candidates because a safe date-derivation contract is absent and allowing arbitrary new dates would weaken grounding; no speculative validator bypass proposed.

## Verification suggestions (root owns Gradle)

`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew --offline :app:testFullDebugUnitTest --tests 'app.amber.core.context.ConversationContextPlannerTest' --tests 'app.amber.core.memory.MemoryCandidateFilterTest' --tests 'app.amber.core.memory.MemoryDreamPlannerTest' --tests 'app.amber.core.memory.MemoryImportExportTest'`

Check actual test package/class filters before invoking: source directory and Kotlin package differ in these migrated tests.
