# Independent Phase 3 Council implementation review

Reviewer: audit_data/audit_sync; authored none of the Council code or tests. Read current Council owner/UI diffs after the implementation author declared readiness, verify-background.md, phase3-council.md, test changes and actual call sites. No production/test edits or Gradle runs.

## Verdict

No remaining blocking correctness finding in this bounded after-implementation review. Focused GREEN and app compile are still owned by the root/sole Gradle coordinator; this source review does not assert those checks passed.

## Contracts checked

- C1: CouncilRoomManager.applyDetectedMode rechecks the explicit-mode override while holding the actual per-room mutation lock. switchMode records the override before its own serialized room mutation. A late classifier cannot overwrite a completed explicit choice; lock ordering does not nest jobsLock while waiting for the room lock.
- C2/C2b: askUser registers a PendingQuestion carrying the exact message id before store publication. Both research and review question paths use this command. Ordinary composer and answer-card submissions share acceptUserMessage, which rejects stale/unlinked card identities, links the accepted answer under the room lock, writes/publishes state before completing only the matching waiter, and rejects duplicate answers using the persisted message link. Completion/cleanup retain waiter identity checks; close still cancels the owned generation and waiter.
- UI wiring: CouncilTimelineTab captures the rendered question id, CouncilRoomVM forwards it to manager.resumeAfterUserAnswer, and answered/pending projection requires replyToMessageId equality rather than any later user timestamp. CouncilTimelineInteractionTest was updated to assert the id and keep an unlinked later message from hiding the editor.
- C3: the cursor begins at the actual topic before setup, advances by message identity and list order, and skips linked ask_user answers as additional steering turns. Setup inputs with equal timestamps remain ordered and are processed once. Accepted clarification text is explicitly carried to role planning, host opening, guest turns and final synthesis even after newer host messages.
- Replacement of host streaming completion with question publication resets the author to IDLE. No new retry, alternate model loop, permission or unowned background waiter was introduced.

## Review concern resolved before final review

The first version of the new setup-time steering drain did not pause the total deadline, unlike the existing per-guest/final drains. Reviewer flagged the reachable budget regression; the author added the same elapsed-duration deadline extension at CouncilRoomAutoRun.kt:112-114. Final source now preserves the documented user-segment budget behavior.

## Validation evidence and limits

- phase3-council.md / phase3-4-red.md record four intended failing behavioral regressions before repair: classifier override, composer answer, early answer during publication, and equal-timestamp setup steering.
- Existing and new tests retain original pipeline call-count assertions; the stale-question negative case checks that rejected input does not mutate messages. Clarification guest prompt assertion checks accepted answer propagation.
- Scoped git diff --check passed for modelcouncil, Council UI and Council interaction tests.
- Reviewer did not repeat Gradle, call a live model/provider, or claim device/visual acceptance. No evidence-backed reason was found to expand this phase into a durable orchestration redesign or unrelated manual host APIs.
