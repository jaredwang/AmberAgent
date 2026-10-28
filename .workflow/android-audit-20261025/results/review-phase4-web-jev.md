# Independent Phase 4 Web / Jev implementation review

Reviewer: background agent, independent of the Web and Jev implementers. Jev was additionally inspected by the Council sidecar, also independent of those authors. Read-only review: no production/test edits and no Gradle runs.

## Blocking finding

### P2 / JEV-3 follow-up: valid Jev configuration can change during screenshot capture before the outbound request

`JevScreenGoalRunner` takes the original config at run start and checks it at the start of an iteration. It then suspends in `screenshotProvider` and, for attached images, in the newly added foreground AX recapture. At the current `stateWithImage` / `runtime.decide` boundary there is no comparison against the original config.

`JevRuntime.decide` reads the current configuration again. If the user changes between two valid configurations A and B during capture (for example endpoint/model), this frame is sent with B before the runner's existing post-request `configuration_changed` check can stop the goal. OFF and revoked-scope cases are already denied by runtime; the reachable missing case is A -> B where both remain enabled and authorized in isolation.

Narrow correction: compare `runtime.configFor(SCREEN_AUTOMATION)` with the run's original config after the capture/foreground suspension and before `runtime.decide`. Return the existing `handback/configuration_changed` outcome without an outbound request. Add a controlled screenshotProvider callback that changes a valid config, returns an attached asset, and asserts zero transport calls. Do not add expected-config plumbing, locks across model/network work, a retry, or a new state machine.

## Reviewed paths without blockers

- **WEB-1**: `JsCellRuntime.runCell` resets `outputCharsSoFar` alongside the output list/truncation flag under the existing cell lock, after rejecting a RUNNING/TERMINATED cell. The existing persistent session/global state is retained. Repeated below-cap and truncated-then-fresh executions exercise the actual tool/runtime path.
- **WEB-2**: MiniApp redirect handling now preserves the existing repeatable Request method/body only for 307/308. The same per-hop destination validation and redirect budget remain. The tests inspect method/body/content type on the destination and retain 303-to-GET plus private-address rejection controls. No credentials or headers were broadened.
- **WEB-3**: GitHub contents requests now use Ktor raw path-segment construction instead of interpolating a full URL string. Tests drive the actual client through MockEngine and Robolectric Base64 decode, checking literal `#`, `?`, `%`, percent-like names and spaces with nested paths; `ref` is still a separate query value. No path normalization or extra fallback was introduced.
- **WEB-4**: Reddit post/comment timestamps share numeric epoch-seconds conversion, so integer and `.0` representations retain the same real milliseconds. Tests assert both actual response shapes; absent values retain the old zero behavior. No speculative malformed-service hardening is needed.
- **JEV-1**: Web decisions include only the already observed body sample, bounded to 1200 characters in state.page. WEB_CONTENT/TASK_TEXT scope requirements stay unchanged. The done question uses actual page/element evidence instead of historical intention alone.
- **JEV-2**: Positive web DONE reacquires current page/window state, checks configuration after the new suspension, prioritizes actual human handoff, and rejects changed navigation URL. Observation failure cannot report completion. The existing independent Noul threshold still applies. Fingerprint/network/ref changes alone remain allowed, as required by second verification.
- **JEV-3 package scope**: Attached capture returns are followed by current package observation before bytes are sent; wrong target package or unavailable observation returns without network. Same-package AX ID changes remain allowed, which avoids adding an unsupported identity-equality policy. This check is appropriately not represented as proof of atomic screenshot/AX association.

## Evidence and limits

Actual pre-fix regressions are listed in `phase3-4-red.md`, with implementation scope and positive controls in `phase4-web.md` / `phase4-jev.md`. I checked the current production/test diff and relevant runtime request path. Scoped `git diff --check` produced no whitespace problems. Root/sole coordinator owns the final GREEN results.

This review makes no claim about live WebView pages, real MediaProjection frame contents, or credentialed GitHub/Reddit/provider/device acceptance. Those were not exercised in this read-only packet.

## Closed blocker: bounded follow-up review

The sole coordinator first observed the valid-config A->B regression fail with one outbound request instead of zero (`logs/phase4-residual-red.log`). Root then authorized the four-line correction and reports the full JevScreen suite GREEN, 17/17, including the existing target-package and unchanged-config/same-package-ID controls.

I independently re-read only that final guard and its controlled callback regression. At current `JevScreenGoalRunner.kt:131-134`, the original-config comparison is after both screenshot and fresh-foreground suspension points and immediately before `runtime.decide`. A mismatch returns the existing handback/configuration_changed outcome and marks an attached image skipped. It adds no extra suspend point, permissions, retries, schema, or same-package snapshot equality requirement. The regression keeps ACTIVE/scopes/package valid, changes the model during attached capture, and asserts zero transport calls.

The sole P2 blocker is closed. No outstanding Web/Jev blockers remain from this independent Phase 4 implementation review. This follow-up was read-only and ran no Gradle tasks.
