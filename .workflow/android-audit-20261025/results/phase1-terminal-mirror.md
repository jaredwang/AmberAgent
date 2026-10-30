# Phase 1: terminal projection and workspace mutation ownership

Ownership: TerminalRuntime.kt initial Termux RUNNING projection; WorkspaceManager.kt SAF mutation/mirror coherence; related existing app JVM tests. WorkspaceDocuments D1/D2 belong to audit_data and are not edited by this packet.

## Reproduction ready

- WorkspaceManagerMirrorIsolationTest.activeTerminalMirrorRejectsSafMutationsBeforeTheyCanBeAcknowledged: real WorkspaceManager + SAF provider; holds terminal mirror lease, attempts text/edit/bytes/move/delete, requires owner rejection, releases and verifies later accepted mutation.
- WorkspaceManagerMirrorIsolationTest.idleSafMutationsSurviveTheStaleMirrorFlushWhenClearingWorkspace: refreshes an old mirror, changes text/edit/bytes/move/delete through manager, clears workspace (real stale flush path), verifies acknowledged SAF results and unrelated mirror-only output.
- TerminalAdmissionTest.late Termux running projection cannot resurrect the completed durable task: delays the real initial TaskStore projection coroutine using its inherited thread context, dispatches a successful real Termux result into TerminalRuntime, waits for durable completion, resumes/joins initial projection, checks durable and runtime status agree.

Root coordinates RED/GREEN Gradle execution. Root baseline-online.log captured both new workspace tests failing at the intended ownership/content assertions (lines 1227-1231). The fresh phase1-docs-terminal-red.log also captures the new TerminalAdmissionTest failure: actual durable RUNNING instead of expected COMPLETED (XML points to assertion line260). Both owner repairs are now applied after these RED results.

## Intended narrow changes

1. Reuse AgentTaskStore.expectedStatus=QUEUED for the initial Termux RUNNING update.
2. Serialize SAF mutation owners under mirrorMutex, reject active terminal mirror owners, and invalidate affected stale file paths after mutation. Ordinary file move affects source and destination; directory move relocates the mirrored subtree to retain unsynced descendants. Keep unrelated mirror-only output and current stage1 mirror sync behavior.

## Applied workspace repair

SAF mutation owners share mirrorMutex and reject active terminal mirrors before starting the mutation. Successful text/edit/bytes/delete operations invalidate only affected stale mirror paths; a failed/no-op delete leaves mirror output intact. File move invalidates source and destination; directory move relocates the mirrored subtree intact, retaining unsynced terminal descendants. A pre-existing private mirror destination rejects the move before SAF mutation. Unrelated mirror-only output is retained. Files.walk does not follow terminal-created symlinks, and mirror mutation cannot traverse an external parent symlink.

## Applied terminal repair

The independently launched initial Termux RUNNING projection uses the existing AgentTaskStore expectedStatus=QUEUED CAS. A delayed initial write cannot replace any terminal state. Command execution, cancellation, remote unknown-outcome semantics and terminal finish projection stay unchanged.

## Verification status

Workspace RED confirmed in root baseline-online.log. After the separate fixture compile error was corrected, phase1-docs-terminal-red.log reproduced terminal state resurrection.

Root phase1-green-with-r3-red.log: TerminalAdmissionTest 5/5 and WorkspaceManagerMirrorIsolationTest 6/6 passed with no skips (fresh XML timestamps 2026-10-25T14:52Z). feature/terminal and feature/workspace JVM tasks also passed; the combined command failed only on the intentionally included, unrelated ChatApprovalActions R3 RED regression. Do not label that whole command green.

No Gradle invoked by this packet. git diff --check passes for current shared diff. Real Termux command execution, physical Android and SAF providers beyond the behavioral fixture remain untested here. Root owns independent Phase 1 review and final integration checks.

## Independent review follow-up: preserve private descendants on directory move

The reviewer found a regression in the first D5 implementation: recursively invalidating a moved SAF directory also deleted legitimate unsynced terminal files inside that mirrored subtree. A directory move must relocate that subtree; ordinary changed-file invalidation remains correct. A private-only existing destination must also not be silently discarded under the current no-overwrite move contract.

Added WorkspaceManagerMirrorIsolationTest.movingSafDirectoryRelocatesPrivateTerminalOutputWithoutRecreatingItsOldPath and movingSafDirectoryRejectsAnExistingPrivateMirrorDestinationBeforeChangingSaf. The actual SAF fixture is now parent-aware. Both tests exercise production WorkspaceManager and real mirror file operations; the first includes a real terminal mirror lease released with syncBack=false.

Root phase1-review-followup-red.log reproduced both intended failures (missing expected private subtree and accepted conflicting destination). The follow-up now relocates a successful directory mirror subtree with Files.move; any existing private mirror destination rejects before documents.move. Ordinary file invalidation and the existing stage1 flush policy remain.

Coordinator GREEN: phase1-review-followup-green.log completed successfully; WorkspaceDocumentsWriteTest plus WorkspaceManagerMirrorIsolationTest 14/14 passed. Kotlin's automatic fallback recovered an incremental missing-class/backup error without project or environment changes. Independent reviewer re-review remains with audit_providers/root.
