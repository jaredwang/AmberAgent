# Independent Phase 3 implementation review: Novel domain and storage

Reviewer authored none of these Phase 3 changes. Reviewed current owner diffs, verify-novel-ui, verify-memory-storage, phase3-novel-domain, phase3-storage and the actual phase3-4-red report. No production/test edits or Gradle were performed. No blocking correctness/regression finding in this bounded review.

## ND1: targeted rewrites use the immediately preceding private candidate

`NovelWorkspaceChapterGenerator` builds the rewrite prompt from the boundJob returned by beginCandidateTurn. That existing owner checks running/execution/chapter/plan binding and retains the current pendingCandidate. The new prompt includes its complete title/body before the current confirmed repairs, rather than reading a public chapter or the initial candidate from an older request.

Candidate recording still uses the previous identity and createdAt, increments its attempt, and delegates validity/CAS to recordCandidate. The two-rewrite limit and read-only candidate turn remain. The actual batch/fake-kernel test captures the second and third writer prompts under Rewrite/Rewrite/Pass, checks each preceding body/title plus repairs, and requires one final receipt; existing identity/plan/attempt controls remain.

## ND2: numbered heading recognition preserves ordinary opening prose

The prior arbitrary startsWith-第/contains-章 test is replaced by a start-anchored numbered chapter prefix. The regression's ordinary first paragraph beginning 第一次 and mentioning 徽章 no longer matches and remains unchanged in the body for both private-candidate and host-write modes. Actual Chinese/Arabic ordinal headings still split, and the existing Markdown heading branch is unchanged, including marked English titles. This stays within the stated leading 第N章/Markdown title contract rather than introducing title inference.

## NS1: unsupported files fail before accepted or durable writes

The shared validateFile at NovelWorkspacePaths exactly matches the existing Store collector's `.md`/`.yaml` suffix policy. Store.write checks before history capture/temp-file writes; the tool checks before either direct free write or buffered canon/auto-approved acceptance; ProposalStore.add checks before adding durable pending approval. Thus a path cannot receive a save/proposal success while being absent from list/fileTree/ledger/export.

The change deliberately does not tighten read/delete or ProposalStore.load. Historical unsupported files can still be read/deleted, and unsupported pending proposals remain readable and rejectable; their approval cannot bypass Store.write. Proposal editing cannot alter target paths. New tests cover free/protected/proposal rejection, supported md/yaml visibility and tracked tree, plus a legacy unsupported queue item that remains rejectable. No broad file format or migration framework was added.

## NS2: category gates apply before graph matching and traversal

ContextAssembler filters collected nodes by the existing per-kind flags before constructing always/contextual sets or calling neighborhood. Disabled smart decisions/foreshadowing cannot be direct plan roots or relation-hop targets, and their edges cannot pull unrelated nodes into the prompt. Existing flag behavior for pinned materials is preserved: ordinary nodes follow neighborhood, special kinds follow their corresponding flags. Injection off/always, plot, budget, mandatory freshness/unresolved warnings and downstream rendering are unchanged.

The new effective-materials regression checks directly named disabled special kinds, their relation-reached characters, an enabled normal character, and re-enabled category behavior through the actual assembler. Existing pinned category tests remain intact.

## Precision and validation boundary

The changes stay with the current prompt, path and context owners, retain domain version/approval rules, and introduce no retries, fallback or parallel state. Source review is clean and targeted diff whitespace checks pass. Actual RED is recorded in phase3-4-red; fresh focused/module GREEN is the coordinator's responsibility. This report does not claim real-provider prose quality, physical-device UI, installation or user behavior acceptance.
