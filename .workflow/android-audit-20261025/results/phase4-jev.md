# Phase 4 Jev packet

Ownership: JevWebGoalRunner, JevScreenGoalRunner and their existing test classes. Android-only; concurrent owners' files are preserved.

## Independent contract accepted

- JEV-1: include already observed bounded visible_text_sample in state.page for decisions and completion checks; no second scrape.
- JEV-2: positive web DONE reacquires live page/window state, honors actual human handoff and navigation changes. Do not require whole semantic_fingerprint equality, which includes unrelated network/scroll signals.
- JEV-3: after an attached asynchronous screenshot, check current foreground package before any outbound request. Do not require same-package AX snapshot identity or claim the check proves atomic image/AX association.

## TEST-ONLY prep

Modified existing `JevWebGoalRunnerTest` using a transport callback seam:

- decisionIncludesTheAlreadyObservedBodyEvidence (expected RED before fix)
- doneDoesNotCompleteAfterNavigationDuringDecision (expected RED)
- doneHonorsHumanHandoffThatAppearedDuringDecision (expected RED)
- unrelatedFingerprintChangeDoesNotByItselfRejectDone (preserve intended boundary)

Modified existing `JevScreenGoalRunnerTest` using its screenshotProvider/controller seams:

- switchingForegroundPackageDuringAttachedCaptureDoesNotSendTheFrame (expected RED)
- attachedCaptureStillAllowsSamePackageDecisionWhenAxIdChanges (preserve the narrowed package policy)

The root/sole build coordinator observed the three web regressions and one screenshot regression RED before authorizing production fixes. Evidence: `logs/phase3-4-red-queued.log:756-766` names exactly these four failed cases. This worker did not run Gradle.

## Production repair

- Web page evidence includes visible_text_sample capped at the existing bridge's 1200-character sample budget. DONE classification now asks for current page/element evidence rather than treating historical intentions as proof.
- A positive web DONE reacquires driver state. A new requires_human condition returns needs_user_action; a changed observed URL returns handback/completion_page_changed. The returned lastState is the current observation. Configuration is checked again after this new suspend point; cancellation propagates. Observation failure returns handback rather than a completion claim.
- No whole fingerprint or ref snapshot equality check was added. These can change due unrelated network traffic or extracting fresh refs. This narrow repair does not claim detection of every possible same-URL content transition.
- Attached screenshot attempts now recapture only to check the target foreground package before transmitting bytes. A mismatched package returns needs_user_action with image marked skipped; unavailable observation returns handback. Same-package AX-ID differences remain allowed. This does not prove atomic image/AX association.

## Shared search follow-up

Added one case-sensitive URL regression to the existing new `SearchProviderProtocolRegressionTest` as requested: distinct path case and query-value case remain distinct; host case remains equivalent. No search production changes; independent `.workflow/search-repair-20261025` workflow owns that code.

The delayed-socket SearchBodyCancellationTest now uses a test-local supervisorScope. This prevents an intentionally incomplete-body cleanup exception in a child from replacing the primary cancellation assertion; the bounded settlement and Call.isCanceled assertions are unchanged.

## Status

Root authorized Phase 4 production after RED; repairs are GREEN-ready and the sole Gradle coordinator audit_novel_storage was notified. Scoped git diff --check passes. Awaiting focused JVM/independent phase review results; no live WebView/MediaProjection or provider/device acceptance claim.

## Independent review follow-up: configuration identity

The initial coordinated Phase 4 GREEN finished successfully (75 app cases across packets, per sole coordinator; `logs/phase4-green.log`). Independent reviewers identified a JEV-3 scope within the new screenshot suspend: changing to another valid model/endpoint while capture is pending may send the captured frame under the new runtime configuration before the outer runner notices the mismatch.

Added controlled regression: `JevScreenGoalRunnerTest.changingModelDuringAttachedCaptureDoesNotSendTheFrameUnderNewConfiguration`. It keeps ACTIVE/scopes/package allowed, changes model in the capture callback, and asserts handback/configuration_changed plus zero outbound calls. Existing unchanged-configuration screenshot positive remains.

The sole coordinator captured actual RED in `logs/phase4-residual-red.log`: outbound count was 1 instead of 0; the other 16 screen cases passed. Root then authorized the narrow repair. The runner now compares its original runtime configuration immediately before outbound, after capture/fresh-foreground suspension points, and returns configuration_changed with the image marked skipped. No new API/framework/permission or snapshot-equality requirement was introduced. Awaiting coordinated GREEN and independent follow-up review.
