# Phase 4 first GREEN

Log logs/phase4-green.log; BUILD SUCCESSFUL in 1m 26s. App {'tests': 75, 'failures': 0, 'errors': 0, 'skipped': 0}

- app.amber.core.jev.JevScreenGoalRunnerTest: 16 cases
- app.amber.core.jev.JevWebGoalRunnerTest: 19 cases
- app.amber.core.service.ChatTitleRenameRaceTest: 1 cases
- app.amber.core.service.ConversationStoreTest: 9 cases
- app.amber.feature.jscell.JsCellRuntimeTest: 10 cases
- app.amber.feature.miniapp.MiniAppHttpClientTest: 3 cases
- app.amber.feature.ui.components.ai.ChatInputSubmissionOwnerTest: 14 cases
- app.amber.feature.webmount.adapters.github.GithubClientTest: 1 cases
- app.amber.feature.webmount.adapters.reddit.RedditClientTest: 2 cases

Eight Android cargo Exec tasks skipped for focused JVM only. No OAuth fixture properties. Independent phase review remains a separate gate.

## Independent residual RED

Log logs/phase4-residual-red.log; BUILD FAILED in 1m 1s. App19 cases,3failures,0skips. All three intended assertions, no fixture/compile errors:

- app.amber.core.jev.JevScreenGoalRunnerTest :: changingModelDuringAttachedCaptureDoesNotSendTheFrameUnderNewConfiguration :: java.lang.AssertionError: A captured frame must not be sent using a changed model configuration expected:<0> but was:<1>
- app.amber.core.service.AiAuxiliaryMetadataRaceTest :: titleResultWaitingForAWriterPreservesTheSuccessorTailAndSuggestions :: live and durable successor-node tails were replaced by the earlier captured nodes; exact values retained in XML/log.
- app.amber.core.service.AiAuxiliaryMetadataRaceTest :: suggestionResultWaitingForAWriterPreservesTheSuccessorTailAndTitle :: live and durable successor-node tails were replaced by the earlier captured nodes; exact values retained in XML/log.

Auxiliary tests use actual generator/store/Room and held writer; JEV capture changes a valid model while other gates remain allowed. Production residuals unchanged during this run.

## Atomic and finalized projection follow-up RED

Log logs/phase4-projection-red-residual-green.log; BUILD FAILED in 1m 29s. App22 cases,3 intended assertion failures,0skips; AiAuxiliaryMetadataRaceTest2 and JevScreen17 now GREEN. No fixture/compile failure.

- app.amber.core.service.ChatFinalizedProjectionRaceTest :: finalizedEventWaitingForRenameCannotRestoreThePreviousTitle :: java.lang.AssertionError: expected:<[User chosen title, User chosen title]> but was:<[Original title, User chosen title]>
- app.amber.core.service.ConversationProjectionAtomicityTest :: metadataTransformCannotReplaceANewerStreamedTail :: Assertion compares live and durable latest-node projections; expected successor tail is replaced by the captured earlier nodes. Exact values retained in XML/log.
- app.amber.core.service.ConversationProjectionAtomicityTest :: actualStreamingProjectionCannotUndoARenameCommittedAfterItsStateRead :: org.junit.ComparisonFailure: expected:<[Committed renam]e> but was:<[Original titl]e>

## Consistent atomic owner final affected GREEN

Log logs/phase4-atomic-final-green.log; BUILD SUCCESSFUL in 1m 1s. App {'tests': 46, 'failures': 0, 'errors': 0, 'skipped': 0}.

- app.amber.core.service.AiAuxiliaryMetadataRaceTest: 2 cases
- app.amber.core.service.ChatFinalizedProjectionRaceTest: 1 cases
- app.amber.core.service.ChatRunCoordinatorTest: 7 cases
- app.amber.core.service.ChatTitleRenameRaceTest: 1 cases
- app.amber.core.service.ConversationProjectionAtomicityTest: 2 cases
- app.amber.core.service.ConversationStoreTest: 9 cases
- app.amber.feature.chat.impl.ChatTurnAgentTest: 10 cases
- app.amber.feature.ui.components.ai.ChatInputSubmissionOwnerTest: 14 cases

Actual CAS metadata/message transforms, finalized projection, auxiliary results, rename/checkpoint, input admission and existing runtime coordinator/agent checks pass. JEV17 unchanged fresh passing result reused from prior batch. Independent root phase review and final integration checks remain separate.
