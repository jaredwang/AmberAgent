# Baseline JVM verification

Source: logs/baseline-online.log and app/build/test-results/testFullDebugUnitTest XML.

Root baseline finished: BUILD FAILED in 7m 6s; no active wrapper/test process at delegated inspection.

App counts: {'tests': 3124, 'failures': 22, 'errors': 0, 'skipped': 14}

## Failed cases

- `app.amber.agent.data.agent.workspace.WorkspaceManagerMirrorIsolationTest :: activeTerminalMirrorRejectsSafMutationsBeforeTheyCanBeAcknowledged`: java.lang.AssertionError: SAF mutation 0 must reject the active terminal owner; actual=null
- `app.amber.agent.data.agent.workspace.WorkspaceManagerMirrorIsolationTest :: idleSafMutationsSurviveTheStaleMirrorFlushWhenClearingWorkspace`: org.junit.ComparisonFailure: expected:<[new]> but was:<[old]>
- `app.amber.agent.data.sync.SecretBackupExportTest :: standard restore keeps local SearXNG credentials for the same service`: org.junit.ComparisonFailure: expected:<[search-user]> but was:<[]>
- `app.amber.agent.data.sync.SecretBackupExportTest :: standard restore keeps local WebDAV username and password at the same endpoint`: org.junit.ComparisonFailure: expected:<[dav-user]> but was:<[]>
- `app.amber.ai.provider.providers.google.AntigravityOAuthSessionTest :: late invalid grant cannot clear a session created after logout`: java.lang.AssertionError
- `app.amber.ai.provider.providers.google.AntigravityOAuthSessionTest :: late successful refresh cannot replace a session created after logout`: java.lang.AssertionError
- `app.amber.core.context.ConversationContextPlannerTest :: softFitKeepsCurrentUserAndToolContinuationEvenWhenTheyExceedTarget`: java.lang.AssertionError: expected:<[d80fa148-b10c-41a9-9d47-a5f575f07d01, 3a788ff2-297e-4c94-ad08-83eb4c4ad242, e59a1721-3459-4306-b261-edaeb39f7b70]> but was:<[d80fa148-b10c-41a9-9d47-a5f575f07d01, e59a1721-3459-4306-b261-edaeb39f7b70]>
- `app.amber.core.context.ConversationContextPlannerTest :: zeroSoftBudgetStillKeepsCurrentUserAndItsContinuation`: java.lang.AssertionError: expected:<[ab87b6d5-c3de-4825-a452-7190d819be15, 0c666e7e-d4ff-413a-b78b-365f14719a88]> but was:<[0c666e7e-d4ff-413a-b78b-365f14719a88]>
- `app.amber.core.memory.MemoryCandidateFilterTest :: longSharedPrefixDoesNotHideDistinctFactsOrWeakenExactDedupe`: java.lang.AssertionError: expected:<[distinct]> but was:<[]>
- `app.amber.core.memory.MemoryDreamPlannerTest :: deterministicMaintenanceDoesNotMergeLongPrefixMatchesOrIgnoreTheirCandidates`: java.lang.AssertionError: expected:<[1, 3]> but was:<[1, 2, 3]>
- `app.amber.core.memory.MemoryDreamPlannerVisibleInputsTest :: modelPlanCanOnlyTargetRecordsAndCandidatesActuallyIncludedInItsPrompt`: java.lang.AssertionError: expected:<[1]> but was:<[1, 81]>
- `app.amber.core.memory.MemoryImportExportTest :: importRemapsSupersessionAfterAllRowsExistAndDropsMissingForeignReferences`: java.lang.AssertionError: expected:<[43]> but was:<[41, 999]>
- `app.amber.core.service.NotificationApprovalDispatchTest :: approval waits for predecessor pause handoff without cancelling the run`: java.lang.AssertionError: approval must not cancel the still-parking run
- `app.amber.core.sync.provider.WebDavSyncProviderTest :: listSnapshots uses href identity when displayname differs from the filename`: java.lang.AssertionError: expected:<[snap-1]> but was:<[]>
- `app.amber.core.sync.provider.WebDavSyncProviderTest :: listSnapshots keeps a child returned before the collection itself`: java.lang.AssertionError: expected:<[snap-1]> but was:<[]>
- `app.amber.feature.runtime.ToolInvocationRecoveryRegressionTest :: interruptedAppendHttpMutationAndMcpCallsRequireAnOutcomeDecision`: java.lang.AssertionError
- `app.amber.feature.subagent.SubAgentThreadGraphIntegrationTest :: failedInitialWriteAheadReleasesTheAdmissionAndPreservesTheFailure`: java.lang.AssertionError
- `app.amber.feature.subagent.SubAgentThreadGraphIntegrationTest :: cancellationDuringInitialWriteAheadCannotLeaveAStartedThreadRunning`: org.junit.ComparisonFailure: expected:<[CANCELLED]> but was:<[RUNNING]>
- `app.amber.feature.subagent.SubAgentThreadGraphIntegrationTest :: cancelledInitialWriteAheadReleasesTheAdmissionAndSettlesPublishedTask`: java.lang.AssertionError
- `app.amber.feature.tools.WorkspaceArtifactHttpRequestTest :: defaultGetWithBodyIsRejectedBeforeItCanBecomePost`: java.lang.AssertionError: Body-bearing reads must fail before transport: null
- `app.amber.feature.tools.WorkspaceArtifactHttpRequestTest :: explicitGetWithEmptyBodyIsRejectedBeforeItCanBecomePost`: java.lang.AssertionError: Body-bearing reads must fail before transport: null
- `app.amber.feature.tools.WorkspaceArtifactHttpRequestTest :: headWithBodyIsRejectedBeforeConnecting`: java.lang.AssertionError: Body-bearing reads must fail before transport: null

This run includes newly written regression tests. These failures are RED evidence for current candidates, not all previously existing suite failures. App test failure stopped the original non-continue run; independently scheduled module checks follow in phase1-red.log.
