# Phase 3 background fixes

## Test preparation (production unchanged)

BG-1: `app.amber.feature.board.BoardSignalSourceGateTest` added. Exercises actual SignalAggregator + in-memory Room: disabled notification push is rejected with no row, Board-off queued callback is rejected, disabled-source filtering occurs before SQL LIMIT, disabled rows are not marked reviewed and become available on re-enable.

BG-2: `app.amber.feature.board.agent.DailyReviewIdempotenceTest` added. Exercises actual DailyReviewAgent + Room + recording gateway. Covers completed evening suppressing repeated evening/delayed noon requests, noon->evening append exactly once, and two concurrent evening cycles held at the provider barrier.

BG-3: `app.amber.feature.task.DisabledCronRecoveryTest` added in feature/task. Exercises durable AgentTaskStore write/restart and persisted recovered JSON; disabled Cron remains CANCELLED/TERMINAL, enabled RUNNING recurring Cron retains scheduled recovery.

BG-4: `app.amber.feature.board.hotlist.deepread.DeepReadGenerationFallbackTest` extended with synthesis article publication through real manager, provider fixture, Room, ArtifactRepository and workspace mirror. Asserts body/source reference retention and cached republishing without another generation call.

Expected RED against baseline: BG-1 three behavior failures, BG-2 three behavior failures, BG-3 disabled recovery failure (enabled control passes), BG-4 body missing from published artifact. Actual outcomes pending single coordinator; no Gradle run by this agent.

`git diff --check` passed after test preparation. Root/coordinator notified of exact classes. Await Phase3 authorization before production edits.

## Actual RED and authorized repair

Root authorized Phase 3 production after coordinator recorded intended failures in `phase3-4-red.md`: Board gate three failures, daily-review three failures, synthesis artifact body missing, disabled Cron resurrected QUEUED; the enabled Cron control passed. All relevant app/module sources and tests compiled during that run.

BG-1 repaired `AppBackgroundSchedulers`, `SignalAggregator`, `BoardRepository`, and `BoardSignalDAO`: registration now follows notification-source choice, ingress rejects disabled Board/sources, and the durable batch query filters enabled sources before LIMIT. Disabled-source queue rows remain unconsumed for later re-enable.

BG-2 repaired `DailyReviewAgent`: one owner mutex covers read/generation/persist, and an existing same-phase or final evening review returns without another generation or append. The existing noon-to-evening append format is retained.

BG-3 repaired `AgentTaskRecoveryManager`: CANCELLED Cron projections bypass recurring-plan requeue and retain terminal cleanup-only recovery. Scheduling/execution behavior is untouched.

BG-4 repaired `DeepReadArtifactPublisher` and its one constructor wiring in `DeepReadAgentRunManager`: publisher reads the authoritative persisted entry and publishes existing structured article JSON for synthesis; magazine continues to use existing DeepReadOutput JSON. All generation and cached completion calls share this publisher, with no new schema or fallback path.

Production diff passes `git diff --check`. No Gradle was run by this worker. Root and sole coordinator notified GREEN-ready; final status remains pending focused GREEN and independent phase review. External search `DeepReadSourcePrefetcher` edits were observed and preserved.

## Independent phase-review follow-ups

Reviewer `audit_data` identified two reachable residual paths within BG-1/BG-3.

- DailyReview's direct raw-chat collection bypassed the signal-source toggle. The first regression fixture falsely passed because placeholder conversation/assistant IDs were not UUIDs; repository decoding failed and the existing catch returned no chats. Corrected both IDs, added an enabled-source positive control proving the unique title reaches the real gateway, then disabled CHAT_HISTORY for the evening request. Coordinator recorded the intended privacy assertion RED in `logs/phase3-bg-startup-review-red.log`; only then the raw-chat collection call was gated by the existing enabledSources setting. App usage has no source toggle and remains deliberately unchanged. Coordinator subsequently recorded all four DailyReview cases GREEN in `logs/phase3-cron-startup-red-chat-green.log`.
- A disabled plan can still be run manually. Its worker's COMPLETED task projection was indistinguishable from an enabled plan during generic task recovery, so the earlier CANCELLED-only fix was insufficient. New `AgentCronManagerConcurrencyTest` drives a real manual WorkSpec through RUNNING/SUCCEEDED and production prepare/start/complete, reloads the durable task store, then calls the actual startup rescheduler. Initial direct WorkSpec writes hit Room's main-thread test restriction; moved those writes to IO without relaxing Room checks. Coordinator then recorded the exact final-status RED, expected CANCELLED / actual QUEUED, with the lifecycle preconditions passing. The disabled/no-active-manual branch of `AgentCronManager.rescheduleAll` now republishes from its authoritative Cron record. Active manual work retains its existing guard and projection.

Both narrow follow-ups were conditionally authorized by root after meaningful RED. No new fields, schemas, settings, permissions or retry paths were introduced. Final Cron GREEN and independent source re-review remain pending; this worker has not run Gradle.
