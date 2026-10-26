# Phase 3 Novel storage and context repairs

Owner: audit_novel_storage. Root authorized NS1/NS2 after actual test-only RED in phase3-4-red-queued.log (three unsupported-file failures and one disabled-smart-kind failure). Other workers own generator and background/Council sources; their edits are preserved.

## Changes

- NS1: `NovelWorkspacePaths.validateFile` expresses the existing visible `.md`/`.yaml` book contract. Store.write uses it before filesystem mutation, workspace tools use it before direct/buffered acceptance, and ProposalStore.add uses it before durable registration. Query/read/delete validation remains unchanged. Existing unsupported proposals remain readable/rejectable; loading is not newly made fatal. A focused legacy-queue test protects that real pre-fix state without adding a compatibility path.
- NS2: ContextAssembler filters disabled node kinds once before section assembly and neighborhood matching/traversal. Existing per-kind flags, injection off/always semantics, budget, and warning rules are retained. A disabled decision/foreshadowing node can no longer become a graph root or appear through a direct plan mention.

## Owned production files

- app/src/main/java/app/amber/feature/novel/workspace/NovelWorkspaceTools.kt
- feature/novel-workspace/src/main/kotlin/app/amber/feature/novelworkspace/NovelWorkspacePaths.kt
- feature/novel-workspace/src/main/kotlin/app/amber/feature/novelworkspace/NovelWorkspaceStore.kt
- feature/novel-workspace/src/main/kotlin/app/amber/feature/novelworkspace/NovelWorkspaceContextAssembler.kt
- feature/novel-workspace/src/main/kotlin/app/amber/feature/novel/workspace/NovelWorkspaceProposalStore.kt

## Validation

- Prior actual RED: NovelWorkspaceFileTypeTest three intended assertion failures, supported-type control passed; EffectiveMaterialsTest smart-kind traversal regression failed while five existing tests passed.
- GREEN-ready tests: app NovelWorkspaceFileTypeTest (five cases including existing unsupported proposal rejection), full feature:novel-workspace module suite (store/type/context changes justify the module).
- git diff --check passed for owned files.
- One bounded code-simplifier pass completed on this owned diff, using Kotlin guidance; state and error ownership are direct, shared format gate is named at the path owner, and no further abstraction/retry/registry was introduced.
- Main coordinator will run combined Phase3 GREEN once all four implementation owners are ready. No Phase4 production changes are included.

## Actual GREEN

phase3-green-with-bg-review-red.log verified full novel-workspace155 cases and app NovelWorkspaceRuntimeTest/FileTypeTest without failures. NS1/NS2 controls, including old unsupported proposal readability/rejection, pass. Root reported independent Novel review clean. Other Phase3 packets still resolving unrelated UI fixture and new BG follow-up evidence; do not treat whole Phase3 closed from this owner result.
