# Novel domain/runtime audit, baseline 0971aa41

Read-only scope: feature/novel; app Novel runtime, Controller/Worker, chapter generation/review/polish, continuity audit, turn launcher/capture, project migration, and Novel ViewModel/UI entry wiring. No Gradle invocation or production/test edits.

## Confirmed candidates (second pass completed)

### ND1 / P2: targeted rewrite cannot see the prose it must preserve

- Owner: `app/src/main/java/app/amber/feature/novel/workspace/NovelWorkspaceChapterGenerator.kt:95-102`; caller `NovelWorkspaceWriteChapterWorkflow.kt:163-179`.
- Trigger: a normal version-bound batch generates a private candidate, joint review returns `rewriteRequired=true` with repair instructions.
- Chain: WriteChapterWorkflow calls generateCandidate(repairInstructions) -> generator loads boundJob and retains pendingCandidate -> writingPrompt contains chapter plan and repair instruction text, but never pendingCandidate.title/body -> TurnRequest has empty history -> readOnly tools can only inspect the visible workspace, while candidate lives privately under .amber/jobs -> model returns a new chapter -> recordCandidate replaces the private candidate -> later reviewed host commit writes it to canon.
- Observable impact: the request explicitly says to preserve unaffected prose, but provides none of that prose. A small targeted repair becomes a fresh regeneration and can discard all unaffected passages.
- Second-pass exclusion: original candidate is not available via injected context, request history, or workspace tool reads. It is correctly included in the independent review prompt; that does not carry into the next independent writing turn. This is a missing input, not a request for more AI validation.
- Narrow fix: on rewrite, include the current pendingCandidate title and complete body with the repair instructions; preserve existing plan/identity/attempt/CAS behavior.
- Regression: extend the existing candidate rewrite/runtime fixture to capture the rewrite request and assert that it includes the exact prior private candidate title/body and repair instructions. Test second rewrite uses the immediately preceding candidate rather than the first version. Existing `repairable review rewrites the same candidate then commits once` covers the normal outer workflow but does not assert inputs.

### ND2 / P2: ordinary opening prose is stripped as a chapter title

- Owner: `app/src/main/java/app/amber/feature/novel/workspace/NovelWorkspaceChapterGenerator.kt:254-265`, especially 262-263.
- Trigger: a normal generated chapter has no heading and begins with an ordinary paragraph such as `第一次看见那枚徽章时，他并不知道它的来历。` followed by the rest of the chapter. The opening starts with 第 and contains 章 in 徽章.
- Chain: ghostwriteOneChapter obtains finalText -> splitChapterTitle tests only startsWith(第) and contains(章) -> removes the entire first paragraph from body and uses it as title -> version-bound recordCandidate or legacy host-write fallback persists that split -> manuscript commit has missing opening prose (and a prose paragraph as title).
- Observable impact: generated manuscript content is altered by the host parser before review/commit. The documented parser contract is `第N章` heading detection, not arbitrary occurrence of the character 章.
- Second-pass exclusion: this is valid prose, not malformed model JSON or a user-crafted invalid workspace. Both current version-bound batch and retained host-write fallback call this parser. Candidate review cannot recover text omitted before candidate creation.
- Narrow fix: recognize an actual numbered chapter heading; leave non-heading prose intact. Preserve current Markdown heading handling and supported Chinese/Arabic chapter labels.
- Regression: run generator with 500+ characters of multi-paragraph prose whose opening includes 第一次 / 徽章, assert candidate body retains the exact opening and gets fallback chapter title. Keep valid 第2章 / 第二章 heading coverage.

## Rejected / by design / already protected

- Pending review requesting rewrite with empty repair instructions: rejected as a bug; GhostwriteJobs.validReviewDecision rejects it before workflow execution.
- Progress using the original job object after a reviewed commit: false positive; progress reloads the bound durable job by matching execution identity before counting receipts.
- Single-chapter polish uses author proposal while batch polish uses independent factual review: intentional manual approval versus unattended commit boundary, no change proposed.
- Per-project invalid model override falls through to current global chat model: explicitly documented current behavior in worker and ViewModel; no product contract authorizes changing it.
- Terminal event versus runtime rollback race: launcher onCompletion waits payload.completion and cancellation claims handler ownership, so the inspected caller path already waits cleanup.
- Worker unexpected failures leave RUNNING indefinitely: false positive for ordinary exceptions; doWork catches exceptions and persists a failed CAS, and controller reconciliation recovers unfinished WorkManager identity.
- Stale continuity report after manuscript changes: already checked with final head plus treeDigest equality, reported as incomplete.
- Ordinary plan text written without a ledger commit: intentional control-file workflow; a new job captures exact current plan and tree digest. No generic commit rewrite proposed.
- Legacy monofile repository is still wired for migration and rollback source deletion; no dead-code deletion proposed.

## Suggested focused validation

`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew --offline :app:testFullDebugUnitTest --tests app.amber.feature.novel.workspace.NovelWorkspaceRuntimeTest`

Root coordinates actual tests. No provider/device acceptance was performed by this packet.
