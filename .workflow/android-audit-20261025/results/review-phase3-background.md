# Independent Phase 3 implementation review: background and Council

Reviewer authored no BG1..4 or Council production/tests. Read current diff, verify-background.md and phase3 packet evidence; no edits to production/tests or Gradle/provider/device execution. Council follow-up delegated to independent audit_data/audit_sync (also authored no Council code), completed after the author declared readiness; its report was read and integrated below.

## Resolved follow-up: BG1 raw chat source choice in DailyReviewAgent

- `app/src/main/java/app/amber/feature/board/agent/DailyReviewAgent.kt:82` unconditionally calls collectRecentChatSummaries after reading Settings, even when `BoardSignalSourceType.CHAT_HISTORY` is absent from enabledSources.
- That helper reads recent conversations and sends their titles/counts in the DailyReview provider prompt. UI puts these exact toggles under Daily review -> Review signal sources, with no separate raw-chat consent. The BoardSettings contract says source toggles live here.
- This is a second production consumer of the same disabled raw source, not a request to gate persisted completed Board items or app usage. Source-toggle correctness in SignalAggregator alone does not close it.
- Independently challenged with the background implementer: author confirms this is a real residual, preparing a provider-prompt marker regression before a narrow source check. No configuration, fallback or new collection framework needed.
- Resolution: coordinator established meaningful RED after the fixture used valid conversation/assistant UUIDs and an enabled-source positive control proved the unique title reached the real gateway. DailyReviewAgent now tests the existing CHAT_HISTORY source choice before calling collectRecentChatSummaries; the disabled branch does not read raw conversations. App usage and completed Board items remain unchanged. Reviewed the actual source and prompt-marker fixture; coordinator reports 4/4 DailyReview GREEN. No remaining blocker for this path.

## Reviewed without further blockers

- **BG1 signal paths:** notification registration reflects Board.enabled AND enabledSources; ingress rechecks before durable capture; pull selection checks Board/source before collect. DAO filters unprocessed source_type before SQL LIMIT, preventing disabled backlog starvation. consideredSignalIds contains only enabled rows; disabled rows remain unconsumed and reappear on re-enable. Existing owner tests exercise actual Room with disabled-oldest/enabled-newer limit=1.
- **BG2 daily review:** one existing DI singleton owns the new mutex across read/model/persist. Same-phase repeats and evening-final then delayed-noon return existing phase without model or write. Noon-to-evening appends once and retains generatedAt. Evening-first generation stays supported. Restore epoch is captured before work and repository durable writes reject stale epochs. No extra scheduling/retry/state framework.
- **BG3 Cron projection:** CANCELLED Cron snapshots follow cleanup-only recovery, preserving disabled terminal status/queue. Enabled recurring snapshots, including running/completed/failed occurrences, still enter scheduled recovery. Actual Cron plan projection uses CANCELLED for !enabled; worker cancellation does not introduce an enabled recurring CANCELLED plan. WorkManager schedule/execution contract unchanged.
- **BG4 portable synthesis:** publisher reads persisted structured article JSON, containing real body/references, for both generated and cached completed results. It uses the existing output serializer when structuredJson absent. Magazine generation explicitly clears any prior synthesis structuredJson before its pipeline, so the claimed magazine output shape remains intact; no stale-synthesis artifact blocker. Added fixture covers real manager/provider/Room/ArtifactRepository/workspace and cached republishing without another provider call.

## Council C1..3 review integrated

Detailed independent evidence: `review-phase3-council-sidecar.md`. No remaining Council production blocker found.

- Explicit mode is rechecked atomically under the real per-room mutation lock; lock ordering avoids nested jobs-lock waits.
- Question-id-bound waiter is registered before ASK_USER/INTERRUPTED publication. Composer and answer cards share one accepted-message owner; matching accepted link/state is persisted before only that waiter completes. Stale/duplicate question submissions do not mutate messages or start another pipeline.
- Rendered question identity flows through CouncilTimelineTab -> CouncilRoomVM -> manager; only a matching linked answer changes visible answered/pending status.
- Steering uses topic-initialized message identity/list order and excludes linked question answers from extra steering. Equal-timestamp setup instructions are processed once. Clarifications remain explicit constraints after newer host messages in planning/opening/guest/final prompts.
- Reviewer found initial setup steering drain consumed the total deadline; author fixed it with the existing elapsed-duration deadline extension before final re-review. No new retry or orchestration framework was added.
- Relevant fixtures preserve original pipeline call counts, stale-question rejection, linked clarification propagation and UI callback identity assertions. No reviewer-run Gradle or live provider/device claim.

## Verification boundary

Coordinated JVM GREEN remains root-owned; this report establishes source/control-flow review only. No physical-device notification capture, OEM scheduling/background, live model/provider, or complete-product acceptance is implied.

## Resolved follow-up: BG3 disabled manual-run owner projection

SettingsAgentExtensionsPage allows runTaskNow on a disabled Cron plan; markRunCompleted/markRunFailed writes a COMPLETED/FAILED generic snapshot, which recovery still maps to scheduled. rescheduleAll reads disabled config but does not reproject the row. Background author independently confirmed the production UI/owner path and status-only defect. Snapshot status alone cannot identify whether a recurring plan remains enabled.

The narrow authoritative repair is now in the already-existing disabled-and-no-active-manual-run branch in AgentCronManager.rescheduleAll: after cancelling its scheduled work, one added upsert publishes the actual disabled plan's snapshot. Reviewed the final source and regression: the test executes production prepare/start/complete over a real manual WorkSpec, persists/reloads task state, then invokes the actual rescheduler. Coordinator confirmed expected CANCELLED / actual QUEUED RED before production correction. The existing active-manual control retains both WorkSpec identity and QUEUED task projection; its guard is unchanged. This avoids a new task schema, summary parsing or execution-semantic changes. No remaining source-review blocker; affected GREEN is still root-coordinator-owned and pending at this report update.

## Final bounded re-review verdict

Both confirmed follow-ups were repaired at their actual owners after meaningful RED. Council C1..3 and the other BG paths were already independently reviewed without remaining blockers. Phase 3 implementation review is now clean; this verdict is separate from the coordinator's final affected GREEN/build results and from device/provider acceptance. Reviewer performed no production/test edits or Gradle run.
