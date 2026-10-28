# Jev read-only audit

Baseline inspected: `0971aa41ca7a2526e9ee17eb9497b7edc1db07be`. No production/test edits or Gradle invocations. Scope: core/jev, app core/jev, Jev production wiring and existing regression fixtures.

## Confirmed candidates after second-pass challenge

### JEV-1 / P2: Web goal decision discards the observed page content

- Owner: `app/src/main/java/app/amber/core/jev/JevWebGoalRunner.kt:275-279`, `:320-323`.
- Production chain: approved `wm_run_goal` (`feature/webmount/tools/WebMountGoalTool.kt:93-102`) -> `BridgeGoalDriver.pageState()` (`:143-160`) -> JS `semanticState` (`app/src/main/assets/webmount/bridge.js:128-156`) already returns `visible_text_sample` and `semantic_fingerprint` -> `decideStep` rebuilds `state.page` using **only title, URL, snapshot ID**. The visible content is discarded.
- Trigger/impact: goal whose completion depends on body text (e.g. wait until a search/no-results or success message is displayed) while title/URL stay unchanged. Noninteractive text never reaches the decision model; the `done_check` question explicitly permits only `state.page` and action history, even excluding the separately sent element labels. It must guess whether the goal is achieved and can falsely report completion or repeatedly navigate/observe.
- Narrow fix: include the bounded visible text already obtained from `semantic_state` in the decision's page evidence and ask done-check to judge current evidence. No extra network/API/abstraction needed.
- Regression proposal: extend `JevWebGoalRunnerTest` with a transport that inspects `request.body` for a unique `visible_text_sample` supplied by FakeDriver. Assert that body-content success versus loading/error evidence is transmitted and available to the completion question.
- Second-pass result: **real**, not simply model accuracy. Host deterministically removes available evidence. Existing fake driver tests provide only title/URL and hardcoded answers, so they do not cover this loss. No intentional privacy boundary explains dropping body text: WEB_CONTENT is already required and the same sample is returned in final_state.

### JEV-2 / P2: Web goal can report completion after its observed page has changed

- Owner: `JevWebGoalRunner.kt:151-159`.
- Production chain: `pageState()` + interactive extraction -> suspend outbound Jev call -> accepted `action=done`, `done_check>=threshold` -> `completed` immediately. Config is checked, page/window state is not.
- Trigger/impact: SPA body update, navigation, or JS dialog/popup during the Jev request. A decision about the old page is returned as current verified completion, including when the live page now requires human action. This differs from click/type, which run through `runVerifiedAction`, and screen DONE, which recaptures its snapshot before reporting success.
- Narrow fix: on a positive DONE, reacquire semantic/window state; stop for requires_human and compare the **semantic fingerprint + URL** used for the decision before returning completed. Do not compare snapshot_id alone: interactive extraction starts a new ref snapshot even with unchanged content. A changed observation can hand back rather than creating a retry loop.
- Regression proposal: FakeDriver's initial state has fingerprint A; transport changes state to fingerprint B (same URL/title) or requires_human before returning high-confidence DONE. Current code returns completed; expected handback or needs_user_action. Unchanged fingerprint remains completed, preserving normal behavior.
- Second-pass result: **real** current-observation contract violation; no intended stale completion behavior documented. Existing tests cover config changes and action receipts but not page change while DONE is evaluated. Distinct from JEV-1: even with complete evidence sent, a stale positive remains incorrect.

### JEV-3 / P1: Screenshot-enabled screen goal can send a different app's screen

- Owner: `app/src/main/java/app/amber/core/jev/JevScreenGoalRunner.kt:81-84`, `:102-118`; producer wiring `app/src/main/java/app/amber/core/di/JevModule.kt:81-101`.
- Production chain: approved, package-scoped `screen_run_goal` -> AX snapshot checked for target package -> suspend `screenshotProvider` -> `ScreenCaptureManager.capture()` dispatches to MediaProjection service and awaits capture -> result bytes attached to Jev request without another package/observation check. The captured result has dimensions/time/file but no package or matching AX snapshot identity (`core/automation/ScreenCaptureManager.kt:14-20`).
- Trigger/impact: screenshots are explicitly enabled, an active capture session exists, supported image dimensions are used, and another app/permission dialog becomes foreground while the asynchronous frame is captured. The frame from that app is sent to the external decision provider under the target package's old snapshot label. The next package validation happens only after transmission. This violates the explicit no-other-app-content rule at runner:81 and package-scoped tool description (`feature/tools/ScreenAppScopeTools.kt:243`).
- Narrow fix: after an actual screenshot attempt and before building/sending the request, recapture AX state on main; require target package and matching observation before using the image. If foreground/observation changed, return needs_user_action/handback without sending the captured bytes. Avoid expanding permissions or inventing fallback capture behavior. The capture/source identity limitations should remain explicit; a label based only on old `snapshot.id` is not proof of image identity.
- Regression proposal: a Fake screenshotProvider changes FakeAccessibilityController.current to a different package and returns an attached asset; assert zero outbound Jev calls and no actions. Another same-package changed snapshot case should prevent attaching mismatched image. Existing JevScreenGoalRunnerTest injection points make this deterministic; no physical device is needed for the owner gate regression.
- Second-pass result: **real** asynchronous scope gap. Mandatory call approval and SCREEN_SCREENSHOTS consent do not authorize a different app: this tool explicitly promises to stay within one named/allowed package. Fresh action validation happens after request and cannot prevent the data leaving. Real capture/device acceptance remains separate.

## Explicitly rejected / by design

- `JevDecisionCoordinator.runLocks` removal can admit transient same-run overlap: explicitly documented best-effort serialization, still globally bounded and no per-run state corruption identified. Do not redesign for this audit.
- `JevToolResultRetention` keeps previously fixed retention decisions in SHADOW: explicit prompt-prefix stability policy. No new external transmission without scopes was found.
- Completion-check shell parser intentionally rejects compound commands and uncertain execution. It is conservative by contract; lack of recognition for some valid commands is not elevated absent a demonstrated product failure.
- Auto-approval judge withholds argument bodies: intentional data-scope boundary, covered by existing tests; do not send commands/path values merely to improve classification.
- Jev failures/unsupported image dialects return fallback or handback and propagate caller cancellation. No retry/hardening changes justified.
- Vercel confidence/range and negative Retry-After edge cases were not elevated: malformed-service inputs without demonstrated production failure are insufficient for this task.

## Suggested validation owner

Root should coordinate focused app JVM `app.amber.core.jev.JevWebGoalRunnerTest`, `JevScreenGoalRunnerTest` and related Jev tests after reproductions are added; no Gradle run was performed by this read-only audit.
