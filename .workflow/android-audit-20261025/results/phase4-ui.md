# Phase 4 UI regressions

Ownership: U1 shared composer submission owner and U2 actual chat rename metadata path. Camera/crop U3 remains conditional and is not a repair. Search production belongs to a separately active workflow and is untouched.

## Test-only preparation

- U1: `ChatInputSubmissionOwnerTest.editConfirmationSharesTheSubmissionAlreadyWaitingForValidation` starts the real `submit` with deferred validation, attempts `confirmEdit(false)`, and requires that no second action is admitted. It then releases validation and checks exactly one accepted submission and correct edit target/content.
- U2: `ChatTitleRenameRaceTest.actualChatRenamePreservesLiveAndFinalDurableTailAcrossSuspendedPersistence` constructs the real `ChatVM` on paused Main, uses its actual rename through ChatService/ConversationStore and in-memory Room, suspends a DAO boundary, publishes a newer live assistant tail, and queues an actual final checkpoint behind the rename writer. The checkpoint must preserve the new durable title immediately (failed/cancelled generation skips success-only finalWindow), then actual `persistFinalWindow` is run and both live/durable tail/title checked. This single controlled schedule also catches a metadata-only rename whose older queued generation snapshot would roll the title back.
- The DAO barrier pauses the baseline full-save exists read or the future metadata-only updateTitle command, so the same test remains meaningful after a narrow owner-level fix. It does not copy rename logic into a test helper.
- Unrelated VM startup collectors/hydration/prewarming are canceled before exercising rename; unrelated generation/provider components are isolated using the repository's existing ReflectionFixture. Fixture failures must be fixed before recording product RED.

## RED and authorized implementation

The sole Gradle coordinator confirmed both intended RED assertions (see `phase3-4-red.md`): duplicate edit admission and queued checkpoint old-title vector. No fixture/compile failures. Root authorized Phase 4 production after clean Phase 3 reviews.

- U1: confirmEdit checks the same inFlight admission as submit, then owns it through its synchronous send callback with finally release. Widget actions remain independent, and no second submission state was added.
- U2: ChatVM rename calls a dedicated ChatService -> ConversationStore title command. The store reuses the existing restore writer and repository metadata/FTS transaction; after Room returns it patches the current live projection, preserving any tail published during the write.
- Existing initialized window checkpoints rebase only title and latest update timestamp from their current session before persisting message content. This prevents a snapshot queued before rename from undoing the title on failed/cancelled generation, which does not reach success-only finalWindow. Generic full-save/branch edit behavior was not rewritten.

`git diff --check` passed for owned production files. GREEN-ready: `ChatInputSubmissionOwnerTest`, `ChatTitleRenameRaceTest`, existing `ConversationStoreTest`; also compile FullDebug after these owner/API changes. No Gradle invocations by this worker; root/coordinator own execution. Independent phase review remains required.

## Independent review follow-up: same U2 auxiliary projection path

Original targeted checks passed 75/75 according to coordinator. Independent review then traced the same stale snapshot pattern through real AiAuxiliaryGenerator title/suggestion work: each builds a whole Conversation before waiting for the ordinary writer gate, then can replay it over a successor turn. Root froze final phase acceptance and requested actual-generator regression before production changes.

Prepared `AiAuxiliaryMetadataRaceTest` with two field-specific cases, real generator/store/Room, a paused TextModelGateway response, and an ordinary held writer. After the response returns and its owner waits at the gate, a successor projects new tail/other metadata. Releasing the writer must preserve that tail and unrelated field in both live and final durable state. Auxiliary production is unchanged pending RED. Coordinator has exact class names.

Both auxiliary cases then failed at the intended node assertions (coordinator `phase4-residual-red.log`), and root authorized their repair. AiAuxiliaryGenerator now calls existing ConversationStore metadata ownership directly for title and suggestions, with its pre-network restore epoch attached. Required DI/fixture constructor wiring was updated. The initial suggestion clear remains volatile-only under its existing gate; no failed-request persistence behavior was changed. Queued window checkpoints preserve both current title and suggestions.

New test-only `ChatFinalizedProjectionRaceTest.finalizedEventWaitingForRenameCannotRestoreThePreviousTitle` uses an actual paused Store.updateTitle DAO transaction, then actual projectFinalized waiting behind it. The finalized event must preserve the live/durable renamed title. Projector production remains unchanged pending this RED. Atomic projection tests are owned independently by runtime reviewer in a separate file.

## Final owner convergence after actual RED

Coordinator confirmed the projector case and both independent `ConversationProjectionAtomicityTest` cases fail at intended state assertions, while auxiliary fixes passed. Root authorized one consistent owner correction.

- Existing Store.updateConversationState now uses MutableStateFlow.update; ConversationAccess exposes that existing transform contract as an abstract command. No silent non-atomic fallback was added.
- Actual ChatTurnAgent merges messages inside the current-state transform, so CAS retries preserve concurrent metadata commands. Final reasoning projection in ChatRunHooksFactory uses the same current-state path.
- Projector finalized membership read occurs only after its writer gate; its mutation changes only updateAt through the atomic transform, preserving all other current fields.
- Timeline counts are refreshed through the session's atomic load-state update after projection; existing initialized/full/window rules and other load-state fields are retained.
- All existing production transform callers were inspected: immutable copy/map computations; repository writes, launches, notifications and other external side effects remain outside retryable lambdas. The two existing test-only ConversationAccess implementations were adapted to the required command without weakening assertions.

Owned source `git diff --check` passed. Coordinator and independent runtime reviewer have the exact affected classes/files. Await combined GREEN and final independent phase verdict; no Gradle invocation by this worker.
