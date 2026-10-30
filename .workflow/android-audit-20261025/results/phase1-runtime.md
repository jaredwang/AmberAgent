# Phase 1 runtime repair evidence

Ownership: SubAgentManager and its integration tests; approval dispatch/coordinator only if the new handoff regression fails. Other agents own other modules; existing work preserved.

## RED regressions prepared

SubAgentThreadGraphIntegrationTest (production manager/runner plus Room, test-local delegating ThreadGraphStore barriers):

- cancellationDuringInitialWriteAheadCannotLeaveAStartedThreadRunning: cancel while node write is suspended; after releasing startup, no model launch and both snapshots/node stay CANCELLED.
- cancelledInitialWriteAheadReleasesTheAdmissionAndSettlesPublishedTask: cancel the caller at node write; no RUNNING task/admission survives, replacement starts under maxConcurrentRuns=1.
- failedInitialWriteAheadReleasesTheAdmissionAndPreservesTheFailure: write exception propagates unchanged, published task becomes FAILED, replacement can start.

NotificationApprovalDispatchTest:

- approval waits for predecessor pause handoff without cancelling the run: pending approval is visible before predecessor finishes parking; decision waits until normal handoff and does not cancel that run.

Root confirmed all four exact new RED methods failed against the baseline (see baseline-verification.md). Root authorized production changes afterwards.

## Implemented

- SubAgentManager initial startup now holds the existing per-thread lifecycle mutex through task publication, write-ahead and turn registration. Cancel/interrupt/followup therefore cannot terminally settle an unregistered startup and then let that startup overwrite them. Missing-agent settlement uses finishLocked while already owning this lock.
- An exception/cancellation after admission follows the existing terminal path under NonCancellable, retaining the captured restore epoch. The original exception still propagates; cleanup errors are attached rather than replacing it. No new startup state machine or retry was introduced.
- The R1 regression was narrowed to the actual completion contract: after cancellation completes, durable/thread/task cancellation must agree and an entered fake child must have received cancellation. A pending local write can hold the mutex, so immediate cancel responsiveness and zero prior model admission are not asserted. Its barrier allows both guarded and unguarded implementations, keeping the original write-ahead race observable without a new UI responsiveness requirement.
- Notification and in-app approvals now share dispatchToolApproval. It waits for the predecessor's normal pause handoff instead of cancelling the run, rejects a cancelled or superseded predecessor, then installs the existing lazy session-owned decision/continuation job. Admission uses the supplied AppScope dispatcher. The in-app path captures its restore epoch at the click before scheduling.
- Approval tests preserve persisted-decision acknowledgement and active continuation ownership, and add Stop/successor rejection while awaiting the predecessor. The previous never-ending-job fixture was changed to a completed predecessor because cancellation of that predecessor is precisely the behavior now proved defective.

GREEN-ready classes: SubAgentThreadGraphIntegrationTest; NotificationApprovalDispatchTest. Root's sole Gradle runner owns execution; no Gradle invocation by this worker. git diff --check passed.

## Follow-on stale approval regression

The normal predecessor wait makes the owner recheck essential: a duplicate request can outlive the first applied decision and otherwise resume again or overwrite it with a stale deny. ChatApprovalActionsTest added `a decision already applied cannot resume again or be overwritten by a stale approval`; root's phase1-green-with-r3-red.log confirmed its second-resume assertion failed against the unguarded owner. All prior runtime regression classes passed in that run.

After root authorization, ChatApprovalActions now accepts only the selected history's matching still-pending tool. A stale action returns applied=false/shouldResume=false; coordinator does not acknowledge a nonexistent persistence or resume. Explicit answer handling, recipe resume input and existing approval audit logic are retained. The new regression also verifies the applied flag; affected class rerun is requested from root's sole Gradle coordinator. audit_ui was notified for independent phase review.
