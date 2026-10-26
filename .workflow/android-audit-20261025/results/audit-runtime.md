# Runtime / chat lifecycle / subagent audit

Baseline: 0971aa41. Read-only production audit; no Gradle executed by this reviewer.

## Accepted candidates pending executable reproduction

### R1 (P1): a subagent cancelled during start can still launch model/tools

- Owner: `feature/subagent/src/main/kotlin/app/amber/feature/subagent/SubAgentManager.kt:260-313`; cancellation at `:371-384`.
- Real route: parent `subagent_start` -> `SubAgentManager.start` -> runs admission and `AgentTaskStore.register` (publishes UI cancel callback) -> suspending Room-backed `ThreadGraphManager.startNode` -> unconditional `launchTurn`.
- During the write-ahead suspension the task dock can invoke its already-published cancel callback. `cancel` holds the per-thread lifecycle lock, but `start` does not. It sees `turnRunId == null`, publishes/persists CANCELLED, and cannot cancel the not-yet-launched kernel run. The suspended start then writes its RUNNING node, resets the run state flow to the original RUNNING value, and launches generation anyway. Its eventual finish cannot claim the terminal snapshot because RuntimeRun.snapshot is already CANCELLED. Result: paid generation/tools can run after a successful cancel, with terminal UI and durable ownership disagreeing.
- Second-pass challenge: not a hypothetical cross-process race. The production task store is published before the first suspending Room write, and its callback directly calls the same public cancel method. Existing per-thread locks protect followup/cancel races but do not cover initial start.
- Narrow repair: make initial startup registration/write-ahead/launch share its thread's lifecycle ownership with cancel, or ensure an already-cancelled startup cannot launch and cannot overwrite its durable terminal. Keep existing thread identity and admission mechanism.
- Reproduction: extend `SubAgentThreadGraphIntegrationTest` with an injectable/delegating ThreadGraphStore that blocks first startNode write after task registration; obtain task/thread id from task store; cancel concurrently; release write; assert no generation survives cancellation, final node and task agree CANCELLED, and no replacement RUNNING projection is published. Test both ordering outcomes.

### R2 (P2): caller cancellation/failure during initial write-ahead strands a RUNNING subagent admission

- Same owner `SubAgentManager.kt:260-297`.
- The parent run can be cancelled (Stop) while `startNode` is suspending. `start` has no post-admission catch/finally. Its caller exits before `launchTurn` creates an observation job, leaving `runs[runId].snapshot.status == RUNNING` and a task-store RUNNING record indefinitely. Future starts count this slot in `admissionErrorLocked`; a missing durable node cannot be found by parent cascade cancellation.
- This differs from R1: no user task cancellation callback is required, and the startup exits rather than continuing after a cancel.
- Second-pass challenge: start is intentionally detached once the child is launched, but this path never launches a child. The pre-existing followup try/catch releases pending admission; initial start has no equivalent ownership release or terminal settlement.
- Narrow repair: after admission, settle failed/cancelled startup through the existing finish path, with non-cancellable cleanup where required, without launching a model. Treat stale restore-epoch rejection separately so stale durable state is not recreated.
- Reproduction: block a fake/delegating startNode, cancel the coroutine calling `start`, release, then assert task no longer RUNNING and a new start can use the concurrency slot. Repeat with an actual store exception and verify error propagation is preserved.

### R3 (P1/P2, needs deterministic integration reproduction): approval continuation races preceding dispatch cleanup

- `app/src/main/java/app/amber/core/service/ChatRunCoordinator.kt:921-937` cancels the existing session dispatch and immediately saves the decision and resumes; no join of the preceding dispatch. `NotificationApprovalDispatch.kt:21-35` similarly replaces the session job immediately.
- Real route: pending tool chunk/approval notification -> user approve/answer -> old session dispatch cancel -> old dispatch catch at coordinator `:665-668` calls runner.cancel(same runId), finally `:670-676` waits handler cleanup -> new decision path reads/saves conversation and can resume same durable paused run concurrently.
- The old activation can still persist cancellation/final conversation while the new approval path saves Approved and opens the same run. Generic runner launch gating can reject a new activation while the old row is RUNNING, or old cancellation can move the pause terminal before resume. This can lose the continuation or leave the approved tool cancelled/pending. The regression is concentrated in approval entry points: ordinary launch (`:387-400`) and regenerate (`:824-832`) already await prior dispatch.
- Second-pass challenge: the pending card is emitted before all handler cleanup completes; a visible pending tool is not proof that the session Job has completed. UI can also approve from notification during the pause handoff. `ConversationSession.setJob` only cancels; it does not await completion.
- Narrow repair: capture preceding session job before replacing it; let the new session-owned approval job wait for its cleanup before reading/saving/resuming. Avoid a new global lock or cancelling a successor by run id. Confirm whether cancelling a still-pausing activation should instead allow that approval pause to settle normally.
- Focused regression seam: `NotificationApprovalDispatchTest` old job finally suspended behind NonCancellable barrier; assert process/persist/continuation does not start until barrier released and new owner remains cancellable. Add current in-app approval equivalent or full coordinator case with held handler-finalization and same-run WAITING_USER.

## Rejected / by-design / not elevated

- `PersistingEventWriter` logs/drops serialization/store errors: explicit observer fail-safe contract, not silently used as tool-effect durability authority.
- Runner observe after unknown/evicted id returns INTERRUPTED: process-local observation contract; durable readers use event store. No proven current consumer defect.
- `SubAgentTurnLauncher` timeout does not await actual handler finally: late callbacks and flows are generation-scoped; no concrete conflicting write reproduced here, so not independently elevated.
- Per-run runner maps and retained completed subagent snapshots can grow: no bounded-history product contract or concrete failure established; do not add generalized caching.
- `ConversationEditor` deleting an unselected variant may change selected index unexpectedly: current UI deletes only the displayed currentMessage, so no reachable defect confirmed.
- StateFlow update side-effect flags in pending queue could be wrong if CAS retries under concurrent full-queue enqueue; current ordinary send callers are mostly Main-owned and no supported reproducer established. Do not add locking speculatively.
- Cancellation lock contention in generic runner warrants a dedicated concurrency test but no distinct reachable owner failure established beyond approval startup paths; not counted separately.

## Verification commands for accepted repairs

Root should coordinate Gradle serially, e.g. `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew --offline :app:testFullDebugUnitTest --tests 'app.amber.feature.subagent.SubAgentThreadGraphIntegrationTest' --tests 'app.amber.core.service.NotificationApprovalDispatchTest'`, followed by impacted runtime tests and app compile.

Memory/context sidecar report: `audit-memory-context.md` (separate review ownership).
