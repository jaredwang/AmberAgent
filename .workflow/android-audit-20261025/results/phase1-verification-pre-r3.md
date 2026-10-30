# Phase 1 verification before R3 follow-on repair

Log: logs/phase1-green-with-r3-red.log; BUILD FAILED in 1m 6s solely on the expected newly added R3 test. No OAuth fixture properties used; no JNI tasks excluded.

## app

{'tests': 171, 'failures': 1, 'errors': 0, 'skipped': 0}

- app.amber.core.service.ChatApprovalActionsTest :: a decision already applied cannot resume again or be overwritten by a stale approval

## settings

{'tests': 116, 'failures': 0, 'errors': 0, 'skipped': 0}


## tools-api

{'tests': 15, 'failures': 0, 'errors': 0, 'skipped': 0}


## workspace

{'tests': 2, 'failures': 0, 'errors': 0, 'skipped': 0}


## terminal

{'tests': 19, 'failures': 0, 'errors': 0, 'skipped': 8}


## R3 affected rerun after repair

Log logs/phase1-r3-green.log; BUILD SUCCESSFUL in 1m 30s. App {'tests': 45, 'failures': 0, 'errors': 0, 'skipped': 0}. Selectors: ChatApprovalActionsTest, NotificationApprovalDispatchTest, SubAgentThreadGraphIntegrationTest.

## Independent-review follow-up RED

Log logs/phase1-review-followup-red.log; BUILD FAILED in 1m 48s, 14 cases, 3 expected failures:

- WorkspaceDocumentsWriteTest.sameDirectoryMoveRejectsNormalizedRenameAndRestoresTheSource
- WorkspaceManagerMirrorIsolationTest.movingSafDirectoryRelocatesPrivateTerminalOutputWithoutRecreatingItsOldPath
- WorkspaceManagerMirrorIsolationTest.movingSafDirectoryRejectsAnExistingPrivateMirrorDestinationBeforeChangingSaf

Eight Android cargo Exec tasks excluded for this focused JVM invocation only, as root scheduled. Host native tests/final assemble remain separate normal runs. Authors repaired only these confirmed owner paths after RED.

## Independent-review follow-up GREEN

Log logs/phase1-review-followup-green.log; BUILD SUCCESSFUL in 2m 42s. App {'tests': 14, 'failures': 0, 'errors': 0, 'skipped': 0}. Kotlin incremental missing class/backups error recovered automatically with Gradle compiler fallback; no cache deletion/config change. Eight Android cargo Exec tasks excluded for focused JVM only. Both owner classes pass.
