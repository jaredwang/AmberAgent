# Phase 3 Council repair evidence

Owner: audit_background/council. Accepted findings C1, C2/C2b, C3 from independent verify-background.md.

## Test preparation and confirmed RED

Changed only `feature/modelcouncil/src/test/kotlin/app/amber/feature/modelcouncil/CouncilRoomManagerTest.kt`:

- C1: suspends classifier; explicit DEBATE choice must survive later automatic EXPLORE result and govern guest mode/prompt.
- C2: ordinary composer `userMessage` answering a pending review question must link the accepted answer and resume original pipeline exactly once; no duplicated answer-as-interjection turn.
- C2b: suspends task-report projection after ASK_USER publication; answer-card submission starts before publication finishes and must wake the original waiter (no duplicate research/opening/guest/synthesis).
- C3: suspends role planning, accepts two interjections, forces all USER timestamps equal; both instructions must be handled once in insertion order before synthesis, without replaying the topic or setup.

Fakes gained bounded suspension hooks for model calls and task upsert. Production unchanged. No Gradle run by this worker.

Root RED target: `:feature:modelcouncil:testDebugUnitTest --tests app.amber.feature.modelcouncil.CouncilRoomManagerTest`.

Actual RED: 19 cases, four intended failures, recorded in `phase3-4-red.md`: late classifier guest mode EXPLORE vs DEBATE; composer remained INTERRUPTED; early answer left original job waiting despite EXPLORING snapshot; setup steering call count zero vs two.

## Production fix (root-authorized Phase 3)

- Automatic mode application now rechecks explicit override under the room mutation lock.
- Manager registers a question-id-bound waiter before exposing ASK_USER/INTERRUPTED. Ordinary composer and answer cards share one accepted-message owner; matching waiter completes only after accepted state/task projection is written. Caller cancellation cannot split that local acceptance handoff. Existing model work stays cancellable.
- UI answer callbacks carry the question id; stale question submission is rejected and unlinked later messages no longer hide answer cards.
- Automatic steering uses message identity/list order rather than timestamps, starts from the actual topic before setup, consumes setup interjections once, and excludes accepted question answers from extra steering turns.
- Accepted clarifications remain explicit constraints in role planning, host opening, guest turns and synthesis even after newer host messages.
- Existing question/generation/cancellation owners and lifecycle remain; no retries, alternate waiters, permissions or provider loops added.

Source GREEN-ready. Worker ran only scoped `git diff --check` (clean); root/sole Gradle coordinator owns focused modelcouncil JVM, timeline interaction tests, relevant module suites and app compile. Independent review pending.

## First GREEN and fixture correction

Coordinator reported full Council suite 36/36 passing with the elapsed-deadline fix compiled fresh. Independent peer review found no remaining blocker. App timeline interaction regression exposed a missing test `LocalSettings` when the retained unlinked USER row rendered through production Markdown; added the same `LocalSettings provides Settings()` fixture used by existing renderer/chat tests. No production change or weakened answer/link assertion. Coordinator rerun pending.
