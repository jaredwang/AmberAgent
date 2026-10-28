# Independent second pass: background and Council candidates

Verifier: audit_ui, independent of the initial background/Council auditors. Current source checked at baseline 0971aa41; no production/test edits and no Gradle run.

## Accepted for regression and owner-level repair

| Candidate | Verdict | Independent evidence / limits |
| --- | --- | --- |
| BG-1 Board source off bypass | Real, P1 | Pull collectors honor enabledSources, but AppBackgroundSchedulers observes only Board.enabled and push ingest persists unconditionally. Batch selection has no source filter. A disabled notification can therefore reach BoardAgent. This is a data-source selection contract, not optional collection fallback. |
| BG-2 repeated evening review | Real, P2 | BoardWorker calls maybeRunDailyReview after every successful/empty run in the evening window; DailyReviewAgent always generates and appends whenever any existing row exists. A second automatic cycle appends a second evening section. No manual regenerate consumer was found. |
| BG-3 disabled Cron projection recovers queued | Real, P2 | AgentCronManager maps disabled plans to CANCELLED. AgentTaskRecoveryManager's first branch unconditionally maps every cron snapshot to QUEUED/SCHEDULED. Work scheduling still respects disabled, so report impact is status projection only, not unintended execution. |
| BG-4 synthesis artifact lacks body | Real, P2 | SynthesisGenerator writes real article to structuredJson, passes a marker-only DeepReadOutput to publisher, which serializes only that marker into ArtifactRepository's portable JSON content. Normal DeepRead share correctly uses the structured article and is excluded. |
| C1 mode override lost across classifier | Real, P2 | User switch records override then mutates room; classification checks override only before suspension and unconditionally applies detected mode afterward. The live dropdown is enabled in EXPLORING/DEBATING. |
| C2 bottom answer does not resume waiter | Real, P2 | userMessage appends USER but leaves INTERRUPTED and pendingAskUser unchanged; existing autoRun claim suppresses restart. Timeline hides ask card for any later USER message, so its visible answered state contradicts owner state. |
| C2b early answer misses waiter | Real, same question-owner group | Both research and review publish ASK_USER/INTERRUPTED before awaitUserAnswer registers its deferred. Manager.mutate publishes store state then awaits task reporter while holding room lock. An early resume captures null before lock acquisition; later registration cannot complete that captured-null path. |
| C3 pre-setup steering skipped | Real, P2 | AutoRun initializes watermark from max user timestamp after research/roster setup; later interjection processing only admits strictly newer timestamps. Host opening includes objective/context/roster, and guests prefer the newly appended host opening as latest steer. The accepted intervening user instruction can be omitted from every prompt. |

## Repair constraints from independent review

- BG-1: source filtering must happen before the DB query's LIMIT. Current `BoardSignalDAO.getUnprocessed` orders all unprocessed rows then limits to 200. Filtering afterward while retaining disabled rows would starve newer enabled sources. Keep disabled rows out of `consideredSignalIds` so the worker does not mark them reviewed without a model request. No permission additions.
- BG-2: preserve one noon-to-evening append and evening-first generation. A completed evening should also suppress delayed noon overwrites. Owner serialization must cover read/model/write, not merely the final upsert.
- BG-3: keep enabled recurring-plan recovery scheduled after completed/failed occurrences. CANCELLED is the current disabled-plan projection; don't reinterpret all terminal Cron states as permanently disabled.
- BG-4: retain existing magazine output shape and publish persisted synthesis content for both fresh and cached completions; do not add another generation pipeline.
- C1: override revalidation must be atomic with automatic mode application under the actual mutation boundary. A second pre-network check is ineffective.
- C2/C2b: one question identity and one waiter owner should cover question publication, ordinary composer acceptance, and answer-card acceptance. Register before publication, complete only accepted answers. Do not solve with another background restart/waiter.
- C3: don't replay the original topic or already accepted ask_user answers as new steering. A message-position/id cursor avoids the current same-millisecond collision as well as setup-time loss.

## Exclusions retained

Confirmed exclusions from initial reports remain appropriate: epoch-based Reminder recurrence is explicit; notification foreground/result IDs already differ; normal synthesis sharing already includes its body; manual Council methods with no UI caller are not counted; best-effort optional classifier fallback is deliberate. Freshness-by-sourceRef, speculative live stream callback identity, and MacGateway late refresh remain unaccepted without proof.

## Required validation

These are second-pass source/control-flow verdicts. Root still needs failing behavioral regressions before repair, then focused GREEN and an independent phase review. No physical-device, OEM-background, live-provider, or complete-product acceptance is asserted here.
