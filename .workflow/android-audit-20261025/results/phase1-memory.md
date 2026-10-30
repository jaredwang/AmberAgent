# Phase 1 memory/context repairs

Owner: MC1..4. Independent acceptance: verify-memory-storage.md.

## RED regressions prepared

- ConversationContextPlannerTest: current USER plus executed-tool continuation exceeds soft budget; older history drops but current request/continuation must remain. Nonpositive soft target also retains current turn.
- MemoryCandidateFilterTest: long identical prefix followed by distinct facts survives; exact full duplicates still filter.
- MemoryDreamPlannerTest: deterministic maintenance merges only exact full duplicate, preserves distinct suffix and pending candidate after shared long prefix.
- MemoryImportExportTest: replacement imported before archived source into nonempty DB, supersedesIds follows fresh local source and missing foreign reference drops; repeated import remains idempotent.
- MemoryDreamPlannerVisibleInputsTest: actual planner.plan -> real SettingsAggregator/Room -> fake TextModelGateway. 81 records and 51 candidates, raw response targets visible and omitted rows across all mutation types. Assertions bind accepted targets to exact prompt input.

## RED evidence and implementation

Root baseline-online.log completed with all six original owned regressions failing at the intended assertions:

- MC1: current USER absent after fit; zero budget retained only assistant continuation.
- MC2: long-prefix candidate rejected; deterministic merge included the distinct suffix record.
- MC3: expected supersedesIds [43], actual foreign IDs [41, 999].
- MC4: actual planner returned archive [1, 81] despite omitted record 81; prompt assertions succeeded.

Root authorized implementation after these failures. Changes are now ready for GREEN:

- ConversationContextPlanner retains the last USER and following continuation as a protected tail; soft budget trims preceding history only, preserving existing final provider hard gate.
- Both exact-normalization dedupe owners compare full normalized content.
- Dream planner parses the model response against exactly promptRecords/promptCandidates.
- Import first writes safe local lineage, completes ID mapping, then updates supersession links through existing CAS using the last written snapshots. Unmapped/self references do not become foreign local links. A lineage CAS conflict preserves concurrent data and counts affected source rows as skipped rather than imported.
- Additional import companion regression simulates a concurrent edit during lineage CAS and checks preserved edit plus honest imported/skipped counts.

`git diff --check` passed for owned files. Root delegated sole Gradle execution to audit_novel_storage; no Gradle was launched by this owner. GREEN results pending.
