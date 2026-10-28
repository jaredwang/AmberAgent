# Phase 3 Novel domain repairs

Owner: ND1/ND2. Source acceptance: audit-novel-domain.md.

## Test-only RED preparation

Class: `app.amber.feature.novel.workspace.NovelWorkspaceRuntimeTest`.

- ND1: actual batch/coordinator/AgentRunner path generates three candidate versions under Rewrite, Rewrite, Pass. Fake kernel records actual writer prompts. Each rewrite must receive the immediately preceding private candidate title, full body and confirmed repair instructions; final batch still commits once.
- ND2: actual ghostwrite generator runs both version-bound private-candidate and host-write fallback modes with ordinary opening prose `第一次看见那枚徽章时...`. The complete opening remains in the body with a fallback chapter title.
- Preservation: actual generator continues to split `第二章`, `第2章`, and existing Markdown English `# Chapter 2: ...` headings. No new unmarked English heading behavior is requested.

Only tests changed during preparation. `git diff --check` passed. Root/audit_novel_storage own Gradle execution.

## RED and implementation

The combined future-phase run (`phase3-4-red-queued.log`, `phase3-4-red.md`) confirmed both ND1/ND2 fail at the intended assertions; numbered-heading controls passed. Root then authorized production edits.

Generator-only fix now ready for GREEN:

- Rewrite prompt includes the latest bound private candidate title and full prose immediately before the existing targeted repair instructions. Candidate identity/attempt/CAS, review flow and commit path remain unchanged.
- Numbered chapter heading recognition requires an actual Chinese/Arabic ordinal between 第 and 章. Ordinary prose such as 第一次...徽章 remains intact. Existing Markdown title parsing, including marked English headings, remains unchanged.

`git diff --check` passed for the generator. No Gradle invocation by this owner; GREEN and independent phase review pending root coordination.
