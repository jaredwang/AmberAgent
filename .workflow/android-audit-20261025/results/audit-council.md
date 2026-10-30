# Council audit (read-only, baseline 0971aa41)

Scope: feature/modelcouncil, AppCouncilHostToolProvider, actual CouncilRoom UI callers.
No production/tests changed; no Gradle run. Findings below have been challenged against actual UI/owner wiring. Root should add controlled-suspension regressions before accepting implementation.

## C1 — P2: late automatic classification overwrites the user's explicit mode choice

- Owner: `feature/modelcouncil/src/main/kotlin/app/amber/feature/modelcouncil/CouncilRoomAutoRun.kt:101-114`.
- Reachable path: CouncilRoomPage mode dropdown (`CouncilRoomPage.kt:232-267`) -> VM.switchMode -> Manager.switchMode (`CouncilRoomManager.kt:440-476`). The dropdown remains enabled while room status is EXPLORING/DEBATING and the classifier network call runs (20-second timeout in CouncilRoomPlanner).
- Trigger: start deliberation; classifier suspends after `hasModeOverride` returned false; user chooses EXPLORE; switchMode records override and persists EXPLORE; classifier returns DEBATE and unconditionally persists DEBATE.
- Impact: user-selected discussion behavior changes back, and all subsequent turn prompts use the unwanted mode. This contradicts the documented override policy and is not a by-design auto choice.
- Narrow fix: revalidate the override under the manager's per-room mutation lock at the actual automatic apply point. A check only before awaiting the model is insufficient. Do not change general planning/fallback policy.
- Regression: fake classifier held by CompletableDeferred; call userMessage, await classifier start, switchMode(EXPLORE), release DEBATE result; assert mode/round prompts remain EXPLORE.

## C2 — P2: answering a pending host question in the visible bottom composer hides its answer card without resuming the run

- Owner: `CouncilRoomManager.kt:485-556` and `app/.../councilroom/CouncilTimelineTab.kt:438-444,570-579`.
- UI path: CouncilRoomBody always supplies the composer for a nonterminal INTERRUPTED room (`CouncilRoomPage.kt:247-260`). Composer sends any nonempty draft independent of room running status (`CouncilRoomComposer.kt:394-440`) -> VM.sendUserMessage -> Manager.userMessage.
- Trigger: FULL mode pauses at research/review ask_user; user types the reply in the normal bottom composer (still active) and taps Send.
- Actual: userMessage appends a normal USER message but neither completes `pendingAskUser` nor changes INTERRUPTED status. maybeStartAutoRun sees the existing auto-run claim and returns. Meanwhile timeline treats ANY later USER message as answering the question (`createdAtMs > question.createdAtMs`) and hides the only answer-card submission control. The actual suspended run remains waiting until its multi-minute timeout; the empty composer no longer shows Stop because INTERRUPTED.running is false.
- This is a production gate/owner mismatch, not merely an alternate intentional input surface: UI explicitly marks this exact ordinary message as an answer, while the state owner never accepts it as one.
- Narrow fix: route accepted composer replies to the same pending-question owner or keep the question card pending unless a linked accepted answer exists. Prefer a single owner path preserving attachments/drafts; avoid a second waiter or resume state machine. Include cancel affordance for the actual active paused job if fixing projection.
- Regression: existing FULL review-question test, but call userMessage("Keep the cost low") while INTERRUPTED instead of resumeAfterUserAnswer; assert no invisible pending waiter and the answer card's answered policy matches owner state.

### C2b — same question ownership defect: an early answer can arrive before its waiter is registered

- `CouncilRoomHostTurns.kt:288-300` publishes kind ASK_USER + INTERRUPTED through mutate, then calls awaitUserAnswer. Review path similarly publishes in AutoRun.kt:203-222.
- `CouncilRoomManager.kt:844-848` registers pendingAskUser only inside awaitUserAnswer. `resumeAfterUserAnswer:875` removes/captures the deferred BEFORE acquiring the room mutex. Publishing state also awaits taskReporter.upsert after store.upsert has already updated the UI StateFlow (`Manager.mutate:1235-1242`, CouncilRoomRepository.kt:118).
- Deterministic interleaving: suspend taskReporter.upsert for the newly INTERRUPTED room; UI observes question and invokes resumeAfterUserAnswer, which captures null and waits for the room mutex; release reporter; regardless of whether registration happens just before/after answer mutation, that answer has captured null. It is persisted; maybeStartAutoRun no-ops due existing run claim; the original job subsequently waits on an uncompleted deferred.
- Narrow fix: register question/waiter before making the actionable question observable; complete/remove waiter only after the answer has been accepted under the same ownership boundary. Bind it to question identity, not a second recovery job. Test with taskReporter barrier and early answer, checking exact original orchestration resumes once.

## C3 — P2: user steering sent during research/roster assembly is skipped from all model prompts

- Owner: `CouncilRoomAutoRun.kt:91-96` initializes the interjection watermark to the LATEST user timestamp after setup; `handleInterjections:311-316` only processes later messages.
- UI path: composer stays usable during network-heavy pre-topic research/planRoles. userMessage appends subsequent USER messages but retains the first message as objective (`Manager.kt:501-530`). The ongoing assembly/auto-run claim prevents a second pipeline, as intended.
- Trigger: send first topic; while roles/research is waiting (up to 25 seconds or several tool rounds), send a second text instruction such as `@Evidence Reader prioritize the total purchase cost`.
- Actual: setup finishes with both user messages present; watermark becomes the second message timestamp, so it is never processed as an interjection. hostOpeningPrompt only includes objective/context/roster, not intervening USER messages. The first guest's appendSteeringNote sees the newly completed HOST opening as the latest steer, also omitting the second message. Future guests see other host/guest messages, and synthesize filters all USER messages. The accepted instruction can disappear from every generation request.
- Self-check: this is not merely no immediate acknowledgement; inspect captured fake runner prompts for a unique marker in the second message. No call needs to include it with current implementation. Research answer messages are different and should not receive duplicate steering after this fix.
- Narrow fix: initialize consumption from the actual topic identity/position and consume intervening messages once; use message identity/list position where practical so same-ms messages cannot be skipped. Do not replay already-consumed question answers or re-run setup.
- Regression: hold roles/research in fake runner, send second message containing unique marker, release; assert correct guest/host receives that instruction exactly once before synthesis, with no duplicate opening/research/auto-run.

## Exclusions / by design / insufficient evidence

- Manual invite/dismiss/direct synthesize methods have suspicious races (e.g. in-flight turn could resurrect DISMISSED status, repeated synthesis can overlap). Current production UI only defines unused VM forwarding functions; no caller invokes triggerHostAction/requestSynthesize, so do not count these as reachable product defects without another caller.
- Host tool loop intentionally runs a bounded read-only allowlist outside DefaultRunKernel. Its documented lack of run ledger/session sandbox is not itself an actionable regression; no permission framework proposed.
- Role/mode generation uses explicit best-effort timeout fallback. Retain those product choices; a network failure in optional classification is not a defect by itself.
- Synthesis intentionally consumes completed guest evidence rather than every raw user attachment. Lack of image parts on synthesis was investigated but is not independently accepted: guests already contribute image/document analysis, and the prompt explicitly requests synthesis from discussion evidence.
- Host/synthesis failure text is finalized with warnings by existing contract. Do not relabel this alone as a false success without a stated product/status contract.
- ModelCouncilManager (legacy batch path) uses synchronized terminal claims and task expected-status gating for per-turn updates; no confirmed durable terminal overwrite found. Cold read returns INTERRUPTED and transcript path intentionally; no cold batch resume promise was found.

## Suggested focused validation

`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew --offline :feature:modelcouncil:testDebugUnitTest --tests app.amber.feature.modelcouncil.CouncilRoomManagerTest`

After owner/UI changes also compile FullDebug and run any new app-level timeline answer-policy regression. Build and test execution remain root-coordinated.
