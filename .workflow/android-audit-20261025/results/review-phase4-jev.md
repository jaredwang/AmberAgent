# Independent Phase 4 Jev review

Reviewer: audit_background/council, not a Jev author. Read-only; no source/test changes or Gradle invocation.

Inputs: verify-web.md narrowed contracts, phase4-jev.md, phase3-4-red.md, current JevWebGoalRunner/JevScreenGoalRunner diffs, runtime/coordinator dispatch gates, actual bridge driver, and regression tests.

## One remaining blocker — P2: screen goal can send a frame under a changed configuration after suspended capture

`app/src/main/java/app/amber/core/jev/JevScreenGoalRunner.kt:106-131` awaits screenshotProvider and then the newly added main-dispatcher AX observation, but does not compare the current SCREEN_AUTOMATION config with the run's captured `config` before calling runtime.decide.

The first existing equality gate is before those suspensions (line 73), and the next is after runtime.decide has returned (149). JevRuntime.decide:59 reads a NEW config; it does not receive or validate the runner's original one. The coordinator correctly validates that new config's mode/scopes and captures its current revision, so it does not reject a valid A->B model/endpoint switch that happened during screenshot capture. It can therefore transmit the previously captured frame under B, then the runner returns configuration_changed only after that request.

Second-pass challenge: OFF and screenshot-scope revocation are already safe because runtime/coordinator reject those before transport. This finding is narrower: both configs remain enabled and authorized for images, but endpoint/model/config identity changed during the suspension. The runner's established contract is to stop this in-flight goal on any configuration change, not to perform one request under the replacement configuration and reject afterward.

Minimal correction: immediately after the capture/foreground observation path and before runtime.decide, recheck `runtime.configFor(SCREEN_AUTOMATION) != config` and return existing handback/configuration_changed. No new transport protocol, permission, retry, snapshot policy, or atomic-capture claim is needed.

Regression: use existing screenshotProvider seam; suspend it, change Settings.jev to a different valid model/baseUrl while keeping ACTIVE and all scopes, return an attached image with the same foreground package. Assert handback/configuration_changed and transport.calls == 0. Prefer actual callback mutation over a read-count-sensitive settings fake. This review did not run the new test.

## Accepted implementation details

- JEV-1: decideStep carries the already observed visible_text_sample in state.page, capped at 1,200 chars, consistent with bridge semantic sample. No second network scrape was introduced. done_check asks for current visible page/element evidence; actions alone do not prove completion.
- JEV-2: only positive verified DONE performs a fresh driver.pageState; actual requires_human takes priority, changed URL gives handback, current state is returned. Cancellation propagates, observation errors do not become completed, and configuration is rechecked after this added suspension. Same-URL semantic/ref/fingerprint changes alone remain allowed, as independently narrowed.
- JEV-3: attached image now requires a same-target foreground package observation before the network decision. Mismatch returns needs_user_action with skipped image and no transport; unavailable observation gives handback. Same-package AX-ID changes remain allowed; the code does not pretend to prove atomic screenshot/AX identity.
- Existing screen DONE snapshot identity verification, guarded action dispatch, dry-run/shadow behavior, cancellation and scope enforcement remain in place.

## Evidence and limits

Initial source RED is recorded as four intended failures in phase3-4-red.md: visible body dropped, navigation-stale DONE, late human handoff, cross-package attached capture. New tests also preserve harmless fingerprint changes and same-package changed AX ID. Full Phase 4 GREEN is root/coordinator-owned and was not claimed in this read-only review. No real WebView navigation, MediaProjection, outbound service, OEM or physical-device acceptance was performed here.
