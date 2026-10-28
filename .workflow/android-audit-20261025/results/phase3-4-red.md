# Future Phase 3 and 4 test-only RED

Log logs/phase3-4-red-queued.log; BUILD FAILED in 1m 5s. Guard confirmed zero external Java Gradle wrappers before dispatch. Production for these phases unchanged during this RED. Eight Android cargo Exec tasks excluded for JVM only.

## app

{'tests': 135, 'failures': 23, 'errors': 0, 'skipped': 0}

- `app.amber.core.jev.JevScreenGoalRunnerTest :: switchingForegroundPackageDuringAttachedCaptureDoesNotSendTheFrame`: org.junit.ComparisonFailure: expected:<[needs_user_action]> but was:<[handback]>
- `app.amber.core.jev.JevWebGoalRunnerTest :: decisionIncludesTheAlreadyObservedBodyEvidence`: java.lang.AssertionError: expected:<Search results are ready: the requested article is displayed.> but was:<null>
- `app.amber.core.jev.JevWebGoalRunnerTest :: doneDoesNotCompleteAfterNavigationDuringDecision`: org.junit.ComparisonFailure: expected:<[handback]> but was:<[completed]>
- `app.amber.core.jev.JevWebGoalRunnerTest :: doneHonorsHumanHandoffThatAppearedDuringDecision`: org.junit.ComparisonFailure: expected:<[needs_user_action]> but was:<[completed]>
- `app.amber.core.service.ChatTitleRenameRaceTest :: actualChatRenamePreservesLiveAndFinalDurableTailAcrossSuspendedPersistence`: java.lang.AssertionError: expected:<[User chosen title, User chosen title, User chosen title]> but was:<[Original title, User chosen title, User chosen title]>
- `app.amber.feature.board.BoardSignalSourceGateTest :: turning Board off rejects already queued notification callbacks`: java.lang.AssertionError
- `app.amber.feature.board.BoardSignalSourceGateTest :: disabled notification source rejects push ingress without persistence`: java.lang.AssertionError
- `app.amber.feature.board.BoardSignalSourceGateTest :: source filter precedes SQL limit and leaves disabled queue unconsumed`: java.lang.AssertionError: expected:<[enabled-later]> but was:<[disabled-oldest]>
- `app.amber.feature.board.agent.DailyReviewIdempotenceTest :: completed evening suppresses repeated automatic evening and delayed noon calls`: java.lang.AssertionError: expected:<0> but was:<2>
- `app.amber.feature.board.agent.DailyReviewIdempotenceTest :: noon and evening are generated once and evening appends exactly once`: java.lang.AssertionError: expected:<2> but was:<4>
- `app.amber.feature.board.agent.DailyReviewIdempotenceTest :: concurrent evening cycles share one generation and one durable supplement`: java.lang.AssertionError: expected:<1> but was:<2>
- `app.amber.feature.board.hotlist.deepread.DeepReadGenerationFallbackTest :: synthesis workspace artifact retains full body and references on generation and cached republish`: java.lang.AssertionError: The portable artifact must retain the article body
- `app.amber.feature.jscell.JsCellRuntimeTest :: a truncated execution does not consume the next executions output budget`: org.junit.ComparisonFailure: expected:<[[LOG] hello]> but was:<[]>
- `app.amber.feature.jscell.JsCellRuntimeTest :: each execution receives its own console output budget`: org.junit.ComparisonFailure: expected:<[LOG] hel[lo]> but was:<[LOG] hel[]>
- `app.amber.feature.miniapp.MiniAppHttpClientTest :: temporaryAndPermanentRedirectsPreservePostBody`: java.lang.AssertionError: expected:<[POST, POST]> but was:<[POST, GET]>
- `app.amber.feature.novel.workspace.NovelWorkspaceFileTypeTest :: unsupportedFreeWritesNeverCreateInvisibleBookFiles`: java.lang.AssertionError: Unsupported write created an invisible file: drafts/scene.txt
- `app.amber.feature.novel.workspace.NovelWorkspaceFileTypeTest :: unsupportedTargetsCannotBePersistedAsApprovalProposals`: java.lang.AssertionError
- `app.amber.feature.novel.workspace.NovelWorkspaceFileTypeTest :: unsupportedCanonWritesNeverEnterTheApprovalBatch`: java.lang.AssertionError: An unsupported canon target must not become an approvable write
- `app.amber.feature.novel.workspace.NovelWorkspaceRuntimeTest :: ordinary prose beginning with di and mentioning a badge stays in chapter body`: org.junit.ComparisonFailure: expected:<第[ 2 章]> but was:<第[一次看见那枚徽章时，他并不知道它的来历。]>
- `app.amber.feature.novel.workspace.NovelWorkspaceRuntimeTest :: targeted rewrites receive the immediately preceding private candidate title and prose`: java.lang.AssertionError
- `app.amber.feature.ui.components.ai.ChatInputSubmissionOwnerTest :: editConfirmationSharesTheSubmissionAlreadyWaitingForValidation`: java.lang.AssertionError
- `app.amber.feature.webmount.adapters.github.GithubClientTest :: filePathsKeepUriDelimitersInsideThePath`: org.junit.ComparisonFailure: expected:<...ontents/docs/release[%231%3F.md]> but was:<...ontents/docs/release[]>
- `app.amber.feature.webmount.adapters.reddit.RedditClientTest :: postAndCommentCreationTimesAcceptDecimalEpochSeconds`: java.lang.AssertionError: expected:<1315269998000> but was:<0>

## novel-workspace

{'tests': 6, 'failures': 1, 'errors': 0, 'skipped': 0}

- `app.amber.feature.novelworkspace.NovelWorkspaceEffectiveMaterialsTest :: disabled smart node kinds cannot enter through plan matches or relation traversal`: java.lang.AssertionError

## task

{'tests': 2, 'failures': 1, 'errors': 0, 'skipped': 0}

- `app.amber.feature.task.DisabledCronRecoveryTest :: disabled cron remains cancelled in memory and disk after restart`: java.lang.AssertionError: expected:<CANCELLED> but was:<QUEUED>

## council

{'tests': 19, 'failures': 4, 'errors': 0, 'skipped': 0}

- `app.amber.feature.modelcouncil.CouncilRoomManagerTest :: late classifier preserves a mode explicitly selected while classification is running`: java.lang.AssertionError: expected:<DEBATE> but was:<EXPLORE>
- `app.amber.feature.modelcouncil.CouncilRoomManagerTest :: steering received during roster planning is processed once even with matching timestamps`: java.lang.AssertionError: expected:<2> but was:<0>
- `app.amber.feature.modelcouncil.CouncilRoomManagerTest :: composer answer resumes the pending question once and is not replayed as an interjection`: java.lang.AssertionError: expected:<FINALIZED> but was:<INTERRUPTED>
- `app.amber.feature.modelcouncil.CouncilRoomManagerTest :: answer accepted while question publication is finishing wakes the original waiter`: java.lang.AssertionError: expected:<FINALIZED> but was:<EXPLORING>
