# Independent second verification: Web / MiniApp / JSCell / Jev

Reviewer: background audit agent, independent of `audit-web-jev.md` and `audit-jev.md` authors. Read-only; no code/test edits or Gradle runs.

## Decisions

| Candidate | Decision | Independent evidence / precision constraint |
|---|---|---|
| WEB-1 JSCell lifetime output budget | Accept P2 | `runCell:94-96` clears output/truncation but not `outputCharsSoFar`; `appendOutput:326-335` uses that persistent counter. `JsCellModels` calls the limit per-run. Deterministic reuse defect. Reset exactly that counter under the existing cell lock. |
| WEB-2 POST 307/308 loses method/body | Accept P2 | `buildRequest:152-174` genuinely supports POST with body; `executeChecked:190` rewrites every redirect to GET. Bridge consumers can use normal API redirects. RFC 9110 independently verified; preserve method/body only for 307/308, keep existing 301/302/303 policy and per-hop destination gate. |
| WEB-3 GitHub raw path interpolation | Accept P2 | `GithubTools:151-176` accepts a raw repository-relative file path. `GithubClient.readFile:101` passes it as a whole URL string, so `#` and `?` become fragment/query syntax before request dispatch. Encode path segments once; do not normalize or reinterpret user file names. MockEngine URL assertion remains needed. |
| WEB-4 Reddit decimal epoch parsing | Accept P2 | Both post/comment `created_utc` use `JsonPrimitive.longOrNull` then zero. Reddit's own JSON documentation explicitly represents epoch seconds with `.0`. Preserve integer and zero-decimal representations; no malformed-input recovery framework needed. |
| JEV-1 web goal body evidence discarded | Accept P2 | `BridgeGoalDriver.pageState:143-155` returns bridge fields unchanged. `bridge.js semanticState:149-154` contains visible_text_sample. `decideStep:275-279` drops it; `done_check:322` is explicitly restricted to page/actions. Titles/URLs cannot distinguish same-page body success vs loading/error. Include existing bounded sample in state.page, rather than adding another scrape. |
| JEV-2 stale web DONE | Accept **narrowed P2** for changed live window/navigation; do not blindly require equality of the current whole semantic fingerprint | Existing check is only the model's `done_check` over the earlier immutable state. `run:151-159` returns completed immediately; there is no current page/window recheck. JS can open a dialog/popup or navigate while the external Jev request waits even though an AGENT lease blocks competing native tool dispatch. `WebMountPrimitiveShared.webMountWindowState:99-145` explicitly defines every pending popup/dialog as human handoff. Tool response subsequently merges live window state (`WebMountGoalTool:119`), allowing contradictory `status=completed` and `requires_human=true`. Reacquire current state on positive DONE and honor actual human handoff / changed navigation before claiming completion. The original full-fingerprint equality proposal is broader: bridge fingerprint includes network signal sequence, scroll offset, readyState, and all bounded semantic text. An unrelated request or clock can change it without invalidating the goal. Do not present that equality as necessary or a complete proof of completion. A reproduction should mutate real driver window/URL during transport suspension and show the old path returns completed. |
| JEV-3 screenshot crosses package scope | Accept **narrowed P1** for post-capture foreground package mismatch | `ScreenAppScopeTools:246,269` scopes the call to an explicitly allowed target package. Runner `:81-84` blocks a wrong package before capture, but `:106` awaits MediaProjection capture and `:118-133` sends its bytes before any new package observation. `ScreenCaptureManager` result has no package identity and capture really suspends on a service frame. A screenshot callback switching controller foreground to another app is a valid controlled reproduction. Add a target-package check after a successfully attached capture and before network transmission. Same-package full snapshot-ID equality is a separate broader accuracy policy; do not quietly add it as mandatory privacy validation or claim that before/after package checks prove atomic screenshot/AX identity. |

## Existing completion contract checked

Screen DONE already recaptures and validates `fresh.id == snapshot.id` plus target package at `JevScreenGoalRunner:156-163`; do not duplicate or replace this as a newly found defect. Web DONE has model Noul/confidence/config checks, but no recapture. Both are distinct from the pre-transmission screenshot scope gap.

No current defect was found in existing WebMount lease validation, snapshot-guarded action dispatch, or mandatory approval wiring. These findings do not authorize new fallback capture, permissions, retries, or a generalized snapshot framework.

## Primary protocol verification

- 307/308 automatic redirects preserve method: [RFC 9110 sections 15.4.8-15.4.9](https://www.rfc-editor.org/rfc/rfc9110.html#name-307-temporary-redirect), opened live during this second pass.
- Reddit `created_utc` is epoch seconds with zero fraction; own wiki includes decimal examples: [Reddit JSON data structures](https://github.com/reddit-archive/reddit/wiki/json), opened live during this second pass. This is historical API documentation; the fixture establishes the accepted valid representation, not a live credentialed Reddit acceptance claim.

## Verification handoff

Source/control-flow confirmation is complete. Root still needs deterministic failing regressions followed by owner-level fixes and GREEN checks. No real WebView, MediaProjection frame, GitHub/Reddit credentialed request, or device acceptance was exercised in this packet.
