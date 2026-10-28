# Background / Board / DeepRead audit

Read-only audit at baseline `0971aa41`. No production/test edits and no Gradle invocations. Scope: Cron, Reminder, task projection/recovery, Live Companion, Mac Gateway, Board collection/generation/scheduling, DeepRead orchestration/notifications/publication. Model Council is independently audited in `audit-council.md`.

## Confirmed source-level findings (require regression before repair)

### BG-1 / P1: Disabling a Board source does not stop notification capture or queued model consumption

- UI contract: `TodayBoardSettingsReview.kt:68-75,225` toggles `TodayBoardSetting.enabledSources`; `BoardSettings.kt:209-210` explicitly describes absence as source default-off.
- Production path: `AppBackgroundSchedulers.startBoardNotificationCollector` (`:64-76`) observes only `todayBoard.enabled`. With Board enabled and NOTIFICATION source disabled, the listener stays registered. `NotificationSignalCollector.onNotificationPosted` (`:49-58`) converts content and calls `SignalAggregator.ingest`; `SignalAggregator.kt:81-108` persists without checking `enabledSources` or Board enabled. `getFilteredSignalBatch` (`:145-153`) also forwards any previously persisted disabled-source signals to `BoardAgent`.
- Trigger: enable Board, grant notification listener access, turn notification source OFF, receive a supported messaging notification. Its content is still persisted and included in a later model request. The queued-source half also applies to other sources disabled after collection.
- Impact: user-selected data-source boundary is bypassed, including third-party message content sent to the configured model.
- Minimal owner repair: enforce current enabled-source gate in aggregator ingestion and surfaced-batch selection; subscribe notification registration to both Board enabled and notification source enabled. Keep muted/disabled rows out of model input without prematurely treating disabled rows as reviewed.
- Regression: in-memory Room and mutable setting provider; disabled notification `ingest` must return false and create no row; ingest while enabled then disable before batch read must omit the signal; re-enable should allow intended processing. Also verify enabled source continues to surface. No permission changes needed.
- Second pass: `collectAll` does filter enabled collectors, but push collection bypasses that loop and persisted batch selection has no equivalent gate. This is not fixed by the pull filter and is not a deliberate fallback.

### BG-2 / P2: Every evening Board update appends another complete evening review

- Production path: `BoardScheduler` has separate unique anchor/incremental/manual workers. `BoardWorker.runCycle` calls `maybeRunDailyReview` after Empty, no surfaced signal, and successful runs (`:100-139`); `maybeRunDailyReview` selects EVENING for every run in 18:00-23:59 (`:159-169`).
- Owner: `DailyReviewAgent.kt:81-109` reads a single row, always calls the model, and appends `existing.content + rawMarkdown` whenever requested phase is evening, even if `existing.phase` is already evening. There is no completed-phase check or serialization.
- Trigger: two ordinary incremental/manual/anchor cycles on the same evening, sequentially or concurrently.
- Impact: repeated model charges and duplicate/ever-growing afternoon sections; concurrent cycles can also overwrite each other's composed review. The class contract describes two phases, noon then one evening supplement.
- Minimal owner repair: serialize DailyReview read/model/write and return the already completed phase without another model call; evening completed must also suppress a delayed noon request. Preserve one noon->evening append and evening-first standalone behavior.
- Regression: repeat evening against an evening row and assert zero additional model calls / unchanged body; noon then evening appends once; concurrent same-phase requests converge to one persisted supplement. Reuse existing fake ProviderCatalog/Room fixtures.
- Second pass: no manual regeneration API currently calls this agent. Its only production caller is automatic BoardWorker; same-phase repetition is not an explicitly requested regeneration feature.

### BG-3 / P2: Disabled Cron plans become queued tasks after process restart

- Production path: Cron create/update maps `!enabled` to `AgentTaskStatus.CANCELLED` in `AgentCronManager.toAgentTaskSnapshot` (`:447-464`) and durably upserts that projection. `AgentTaskStore.loadSnapshots` (`:280-289`) calls `AgentTaskRecoveryManager.recoverOnStartup` for every disk snapshot.
- Owner: `AgentTaskRecoveryManager.kt:21-29` unconditionally maps every `type == "cron"` snapshot to QUEUED/SCHEDULED, including the disabled CANCELLED snapshot. `AppBackgroundSchedulers.rescheduleCronTasks -> AgentCronManager.rescheduleAll` cancels disabled WorkManager jobs but does not repair this task projection.
- Trigger: create or disable a Cron task, then restart process and inspect agent task list/status.
- Impact: task dashboard/tools report cancelled plans as queued/scheduled indefinitely, although no worker is scheduled. This does not actually execute the disabled plan.
- Minimal owner repair: at minimum preserve disabled/CANCELLED Cron snapshots as terminal during recovery; keep enabled recurring-plan recovery behavior. If reconciling from authoritative Cron records instead, avoid overwriting active manual worker projections.
- Regression: persist a disabled Cron task projection, recreate AgentTaskStore, and assert CANCELLED/TERMINAL is preserved; existing running-enabled Cron recovery test must remain scheduled.
- Second pass: enabled Cron plans deliberately recover as scheduled even after a prior completed/failed occurrence. That design does not apply to disabled CANCELLED plans.

### BG-4 / P2: Completed DeepRead synthesis workspace artifacts lose the article body

- Production path: BoardModule supplies the real ArtifactRepository to DeepReadAgentRunManager (`BoardModule.kt:103`). `DeepReadSynthesisGenerator.kt:139-157` builds a marker `DeepReadOutput` containing summary plus READY section states; the actual article body, sources, and references are written separately to `structured_json` via `article.encoded()`.
- Publication owner: `DeepReadArtifactPublisher.kt:13-20` serializes only the marker DeepReadOutput. `ArtifactRepository.saveDeepRead` (`:161-216`) writes that exact JSON to the workspace artifact content file and marks it PARSED. The file is available via normal workspace mirror/file preview/open/share and `ArtifactRepository.readContent` (`:402-430`); it does not resolve Room `structured_json` when read.
- Trigger: generate a non-magazine synthesis template (QA, brief, timeline, debate, review), then read/share the automatically published workspace artifact or back up its content. The artifact has the lede and completion markers, with no synthesized body/references.
- Impact: a supposedly completed, portable workspace artifact is incomplete; losing the source cache leaves no recoverable article body in that artifact.
- Minimal owner repair: include the persisted structured article in the publication content while keeping the existing magazine JSON contract. Load the authoritative stored entry in publisher (or explicitly pass the article) and ensure cached completion republishing also carries it. No second generation path or additional persistence layer is needed.
- Regression: complete a synthesis article fixture with unique body/reference text, publish to real ArtifactRepository fixture, read content and assert body and references are present; magazine publication remains readable and cached synthesis completion republishes full data.
- Second pass: ordinary DeepRead share is already correct: `DeepReadScreen.kt:138-141` uses `DeepReadSynthesisWriter.markdown(templateArticle)` before the magazine fallback. Do not modify that path as a supposed export defect.

## Candidate not promoted without further proof

- Changed content sharing one Board sourceRef is ignored by `SignalAggregator.kt:85-87` (chat conversation IDs, updated notification keys), until old processed rows are pruned. This is a concrete freshness limitation but sourceRef/lifecycle/dedup policy may intentionally suppress repeated source items; changing it would require a revision-aware processing contract. Do not casually replace dedup with upsert/reset, because a stale worker could mark a new version processed. Not included in confirmed repair scope without contract/reproduction review.
- Live streaming callback lacks explicit generation/run identity, but providers use coroutine Flow and cancellation may prevent late callback reachability. Final cards already have generation, screen signature and TTL checks. Do not add a speculative stream owner framework without a controlled reachable reproduction.
- MacGateway in-flight old refresh vs unpair/re-pair may publish stale status; pairing UI cancels the polling effect on connection change. A stale Unauthorized response deleting a successor pairing requires cancellation timing proof before repair. No hardening accepted from static suspicion alone.

## Rejected / by design / already correct

- Reminder DAILY/WEEKLY use fixed epoch duration, explicitly documented in ReminderStore; DST/local-time recurrence is not its contract.
- ReminderScheduler re-reads durable current snapshot under its own owner mutex, so delayed caller snapshots cannot replace a newer alarm.
- Cron result and foreground notification IDs are already separate; DeepRead result and foreground IDs are also separate. WorkManager foreground cleanup does not delete terminal notices on these current paths.
- Cron REPLACE execution is serialized per task; ChatTaskRun handles runner settlement. No duplicate-owner defect asserted merely from WorkManager replacement.
- DeepRead source importer office-document truncation flag initially looked suspect, but DOCX/EPUB/PPTX/PDF parsers append explicit truncation suffix beyond maxChars, so importer detects it. No fix needed.
- DeepRead ordinary sharing/export includes synthesis Markdown as described above.
- DeepRead WorkManager cancellation calls synchronous AgentRunner.cancel; it is not an ineffective suspend call in an already cancelled context.
- BoardItemDAO generated-field upsert preserves user completed/dismissed state, so repeated model output does not reactivate completed items.
- MacGateway custom self-signed certificate trust uses a pinned SPKI key; permissive hostname verification here is deliberate and not an unpinned TLS bypass.

## Verification boundary

Findings above are production source/control-flow evidence, not device/provider acceptance. No tests run in this read-only packet. Suggested focused commands after regression preparation: `:feature:task:testDebugUnitTest` and `:app:testFullDebugUnitTest --tests '*Board*' --tests '*DailyReview*' --tests '*AgentTaskRecoveryManagerTest' --tests '*DeepRead*'`; root must coordinate Gradle.
