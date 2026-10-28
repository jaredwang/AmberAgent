# Novel workspace storage and transaction audit

Baseline: 0971aa41ca7a2526e9ee17eb9497b7edc1db07be. Read-only production review; no Gradle or production/test edits performed.

## Accepted candidates, challenged twice against current code

### NS1 (P2): successful writes can create invisible, uncommitted workspace output

- Location: `app/src/main/java/app/amber/feature/novel/workspace/NovelWorkspaceTools.kt:222-279`; `feature/novel-workspace/src/main/kotlin/app/amber/feature/novelworkspace/NovelWorkspaceStore.kt:68-84,134-142`.
- Reachability: a normal interactive Novel turn invokes `novel_workspace_write` with `{path: "drafts/scene.txt", content: "new draft"}` (or a setting path without an extension). The tool schema permits any string. `isFreeWritePath` checks only the prefix/segment count, and `Store.write` checks traversal/hidden segments, so the file is atomically written and the tool returns `已保存`.
- The contradiction is downstream: `Store.collect` includes only lowercase `.md` and `.yaml`. `fileTree`, turn-end `commitTree`, draft queries, checkout materialization, and ZIP export all use `Store.list`. The turn succeeds with a ledger commit that omits the purportedly saved draft; the draft disappears from the author's visible workspace and exchange export. Protected `.txt` paths can likewise be approved, written, and omitted from the resulting manuscript tree.
- Second check: this is not a request to support arbitrary attachments. `Store` explicitly documents the book as `.md`/`.yaml`; `NovelWorkspaceStoreTest` intentionally excludes externally created notes.txt. The defect is the write boundary promising success for content that does not join that book. No current tool or storage gate rejects unsupported extensions. The Novel domain reviewer independently agreed; no arbitrary-file contract was found.
- Narrow fix: reject unsupported book-file extensions before accepting/buffering an agent write, using the existing supported tree contract; ensure durable proposal validation cannot accept unsupported targets. A simple shared predicate is sufficient if needed by both; do not add attachment machinery or change export to include arbitrary files.
- Regression: invoke the actual `NovelWorkspaceToolSession` write tool for free and protected unsupported paths, assert refusal and no disk file/no proposal. Keep md/yaml success cases. Existing `NovelWorkspaceRuntimeTest` fake tool/kernel fixtures are appropriate; add a store-level gate check only if that owner is changed.

### NS2 (P2): disabled foreshadowing/decision injection leaks through neighborhood retrieval

- Location: `feature/novel-workspace/src/main/kotlin/app/amber/feature/novelworkspace/NovelWorkspaceContextAssembler.kt:51-59`.
- Reachability: project flags persist `foreshadowing=false` and/or `decisions=false`, with `neighborhood=true`. A normal batch generation reads the flags and calls `Runtime.runTurn -> ContextAssembler.assemble`. If the confirmed chapter plan names a smart foreshadowing/decision node, `Nodes.neighborhood(nodes, planText)` receives every node kind. It checks injection off but does not know the per-kind flags; the assembler then filters only always nodes. The disabled node is included under `Chapter-related nodes`.
- Observable impact: the author disables a constraint source, but its content remains in the model prompt whenever it is named or reached by a relation. The dedicated sections are correctly absent, making the UI setting appear applied while the payload contradicts it.
- Second check: the always-node path at lines 32-37 already applies the same flags per node kind. `NovelWorkspaceEffectiveMaterialsTest:111-132` pins that contract. The smart path is the missed equivalent, not a new interpretation of injection off or a request to remove an eligible relation. The Novel domain reviewer found no contrary toggle contract.
- Narrow fix: filter nodes eligible under the current per-kind flags before neighborhood matching/traversal, sharing the existing kind decision if useful. Filter before traversal so a disabled node cannot become the matching root of an otherwise unrelated neighbor.
- Regression: extend `NovelWorkspaceEffectiveMaterialsTest` with smart decision/foreshadow nodes directly named in the chapter plan and relation-linked cases. With neighborhood enabled and the corresponding kind flags disabled, their content must be absent while an enabled character remains present. Also pin re-enabling flags restores those nodes.

## Rejected / by-design candidates

1. Forking a plot-stale or unresolved source yields a fresh branch: explicitly by design. `NovelWorkspaceBranchesTest` says a fork resets the new branch's staleness; the unresolved contract explicitly lists author fork as a resolution path. Do not propagate gates speculatively.
2. Undo is book-local single-level and invisible on another branch: by design, documented in `NovelWorkspaceUndoRecord` and guarded by the record slug and branch head.
3. Native project import does not carry host ledger/history/settings: by design, ZIP exchange is the public book only; native backup is a separate contract.
4. Source branches copied via `Files.copy` rather than `Store.write` do not newly capture chapter history: not a current history loss; all normal source chapter writes already capture content in the project-wide content-addressed history, which the fork can reuse.
5. Raw symlink traversal through `Store.resolve` uses lexical rather than canonical boundaries: no production creator/import path for such a symlink was established in this scope; not elevated into a security finding.
6. Malformed/duplicate manually constructed install lists and unusual nested metadata paths: no ordinary producer shown; ZIP duplicate entries already reject, normal exporters produce unique metadata. No speculative format framework proposed.

## Coverage and follow-up validation

Read both workspace package trees, Store/Ledger/Installer/ProjectRepository/Exchange/BookExport/Branches/Undo/ChapterHistory/RestoreBoundary/ProposalStore/ProposalOwner/AuthorEdits/DiscardedChapters/ReviewedCommits/Jobs and relevant runtime/tool/ViewModel/page callers. Existing tests were inspected for contracts, not executed. Root agent owns Gradle coordination.

Suggested focused tasks: `:feature:novel-workspace:testDebugUnitTest --tests '*NovelWorkspaceEffectiveMaterialsTest'` and app `testFullDebugUnitTest --tests '*NovelWorkspaceRuntimeTest'` (or a new focused tool test using its fixtures), then touched module suites and app compile as root schedules.
