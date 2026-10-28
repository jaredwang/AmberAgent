# Independent second verification: runtime R1/R2/R3

Reviewer: audit_tools (did not originate these findings). Baseline 0971aa41.
Read-only source/caller verification; executable regression still belongs to root's coordinated Gradle phase.

## R1: confirmed reachable, P1

SubAgentManager.start admits runs[runId] at :260-264, registers a durable visible AgentTaskStore snapshot and cancel callback at :279-282, then suspends on ThreadGraphManager.startNode at :290-296. ThreadGraphManager.startNode first suspends on getNode, then unconditionally upserts RUNNING. Task UI/agent_task_cancel -> AgentTaskStore.cancel -> registered SubAgentManager.cancel is a real independently callable route once register publishes.

cancel serializes via threadOperationLock at :372, claims CANCELLED through finishLocked before a turn exists (:375-384), while start holds no such lock. If cancel wins during startNode, start can subsequently upsert RUNNING, reset public runStateFlow to the original RUNNING at :299, and call launchTurn unconditionally. launchTurn (:1109-1181) has no current-running check and launches paid generation even though runtimeRun.snapshot is already CANCELLED. Terminal claim later rejects callbacks because the snapshot is not running (:890-894).

False-positive challenge: the runtime job is intentionally detached only after launch, but this is before any runtime job/runId exists. Per-thread cancel/followup locks do not cover initial start. Store serialization does not close the unlocked gap. Confirm with blocking getNode/upsert fixture and task-store cancel route.

## R2: confirmed reachable, P2

The initial admission region has no catch/finally between runs insertion and launch. Cancellation/store exception in AgentTaskStore.register (:279), transcript append (:283), or thread startNode (:290-296) exits start without terminal settlement. No observation job exists to call finish. admissionErrorLocked (:103-120) continues counting the in-memory RUNNING snapshot, including when no thread node was persisted; cascade cancellation cannot discover the missing node.

False-positive challenge: caller cancellation does not automatically cancel a detached child, but here no child was launched at all. Followup has explicit release in catch (:509-511); initial start lacks its equivalent. Fix should settle only admitted startup, preserve original error and restore-epoch ownership, and not create a synthetic new architecture. Block/cancel initial write, assert released slot and terminal task, and repeat with persistence failure.

## R3: confirmed reachable source route; hold exact window in executable regression

The route is stronger than merely a visible notification arriving during trailing cleanup:

1. DefaultRunKernel.kt:556-559 emits a tool whose approvalState is Pending, before setting terminal WaitingUser at :560-564.
2. ChatTurnAgent.kt:117-129 synchronously projects that list through ConversationAccess.updateConversation, then invokes suspending hooks.onStreamingMessages. ConversationStore.updateConversation:443 assigns session.state immediately.
3. ChatRunHooksFactory.onStreamingMessages updates live notification and suspends at checkpointConversation. UI pending actions are wired through ChatVM.handleToolApproval (:462-476) without a session-job completion gate.
4. ChatRunCoordinator.handleToolApproval:929 cancels the preceding dispatch, launches a replacement and immediately applies/saves the decision (:931-933). NotificationApprovalDispatch also replaces via ConversationSession.setJob, which cancels without joining.
5. Old dispatch cancellation catch (:665-668) calls runner.cancel on the same active run; the independent handler then finalizes cancellation while the replacement can save Approved/read active terminal/resume. InProcessAgentRunner.gateHandler only resumes PAUSE_STATES, so still-RUNNING or now-CANCELLED rows reject the new handler. A newly minted run after terminal removal can instead encounter an approval record bound to the prior run. The decision/continuation can be lost or cancelled despite the user's approval.

A second window remains after the handler commits WAITING_USER but before the remaining finalizer cleanup and runner terminal sync are settled. dispatchKernelTurn itself explicitly treats terminal snapshot as earlier than durable cleanup (:670-674); ordinary launch/regenerate join predecessor while these two approval entries do not.

False-positive challenge: the UI card is not gated until job completion, and producer source order explicitly exposes Pending before durable pause. Thus this does not require guessing phone timing or a forged callback. However helper-only ordering tests should be complemented by a held streaming-checkpoint/finalizer route to prove the intended owner semantics, rather than declaring any non-joined jobs inherently defective.

Repair caution: simply cancelAndJoin can still turn the pre-pause activation into CANCELLED before approval. Allow a pending predecessor to settle normally, then apply/save/resume, keeping the replacement approval operation session-owned and cancellable by Stop. Reuse session lifecycle ownership; do not add a global generation mutex or join unrelated successor work.
