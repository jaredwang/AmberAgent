# WebMount, MiniApp and JSCell read-only audit

Baseline: `0971aa41ca7a2526e9ee17eb9497b7edc1db07be`. No production/test edits and no Gradle invocations. Jev-specific independent sidecar findings are in `audit-jev.md` (three further candidates); this report covers separate WebMount/MiniApp/JSCell owners.

## Confirmed candidates after second-pass challenge

### WEB-1 / P2: JSCell output budget leaks across executions

- Owner: `app/src/main/java/app/amber/feature/jscell/JsCellRuntime.kt:94-95`, `:326-335`.
- Production path: capability-enabled Chat tool catalog -> `createJsCellTools` -> `js_cell_run` -> `runCell` -> persistent engine session -> `appendOutput`.
- Trigger: run the same persistent cell twice, each printing output below its 16,000-character per-run cap, but their sum exceeding the cap. `runCell` clears the output list and truncated flag, but never resets `outputCharsSoFar`. The second execution is truncated using the first execution's spent budget. Once the aggregate reaches the cap, later executions lose all console output.
- Observable impact: persistent cells are explicitly reusable, but subsequent computations can no longer return their console result. `JsCellLimits.outputChars` explicitly documents the cap as **per run** (`JsCellModels.kt:62`); this is not a deliberate lifetime quota.
- Narrow fix: reset `outputCharsSoFar` beside `output.clear()` under the existing cell lock.
- Regression: extend `JsCellRuntimeTest` fake-session repeated-execution test; assert two full below-cap outputs, and assert a truncated first run does not consume the next run's allowance. Existing tests verify one-run capping only.
- Second-pass result: real, deterministic owner-state omission; no new abstraction or additional quota behavior needed.

### WEB-2 / P2: MiniApp POST redirects discard method and body for 307/308

- Owner: `app/src/main/java/app/amber/feature/miniapp/MiniAppNetwork.kt:181-190`.
- Production path: MiniApp bridge `fetch` -> sandbox Network gate -> `MiniAppHttpClient.fetch` -> `buildRequest` (supports GET and POST) -> `executeChecked`.
- Trigger: a permitted HTTPS POST endpoint responds `307 Temporary Redirect` or `308 Permanent Redirect`. The redirect loop unconditionally builds `.get()`, so the target receives GET and no original body. FastAPI-style trailing-slash redirects and permanent API URL redirects are normal examples of this response contract.
- Impact: a valid MiniApp API write/query request becomes a different operation and can fail or return an unrelated GET response, while bridge success merely reports that final response.
- Narrow fix: retain method/body for 307/308; keep the existing deliberate POST-to-GET behavior for 301/302/303. Preserve URL guard checks for each hop, redirect cap and header restrictions.
- Regression: use an injected OkHttp interceptor and a public-address resolver, returning a redirect then final response; assert redirected 307/308 retain POST/body, 303 becomes GET, and blocked redirect destinations still fail. No real network or new dependency needed.
- Second-pass result: real protocol violation, not a new feature. RFC 9110 sections 15.4.8 and 15.4.9 require method preservation for automatic 307/308 redirects: [HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html#name-307-temporary-redirect).

### WEB-3 / P2: GitHub file paths are parsed as URI syntax

- Owner: `app/src/main/java/app/amber/feature/webmount/adapters/github/GithubClient.kt:101`.
- Production path: enabled GitHub station -> `GithubTools.github_file_read` (`GithubTools.kt:150-187`, raw file path input) -> `GithubClient.readFile` -> `http.get(".../contents/$path")`.
- Trigger: a valid repository file name contains `#`, `?`, or literal `%` (e.g. `docs/release#1?.md`). The raw path is interpolated into the entire URL string. `#` starts a fragment (not sent to GitHub); `?` starts query parameters; literal percent escapes can be misinterpreted. The requested Git object differs from the supplied path.
- Impact: file reads fail or read a different path even though the file exists. Space/non-ASCII handling by Ktor does not address reserved URI delimiters.
- Narrow fix: construct the contents URL using encoded path segments, preserving directory separators and keeping `ref` as its own query parameter.
- Regression: MockEngine captures URL for paths with `#`, `?`, `%`, spaces and nested directories; assert encodedPath identifies the entire supplied file, fragment is empty, and `ref` remains distinct. Decode a small base64 fixture through the real client to preserve the end-to-end return path.
- Second-pass result: real raw-input contract failure. The tool describes `path` as a repository file path; no pre-encoded-URL requirement exists. Do not add general path normalization, host fallbacks or permission changes.

### WEB-4 / P2: Reddit decimal epoch seconds become 1970 timestamps

- Owner: `app/src/main/java/app/amber/feature/webmount/adapters/reddit/RedditClient.kt:128`, `:145`, `:154`.
- Production path: Reddit station top/search/post_read -> `parseChild` -> `(data.l("created_utc") ?: 0L) * 1000L` -> `RedditTools.childToJson` emits `created_at_ms`.
- Trigger: standard JSON epoch seconds with a zero decimal fraction, e.g. `"created_utc": 1315269998.0`. `JsonPrimitive.longOrNull` is strict integer parsing and returns null for this representation, producing `created_at_ms=0`.
- Impact: real posts and comments are dated 1970 in model-facing tool data; chronology and recency answers become wrong. Existing RedditClientTest fabricates integer timestamps only and does not assert creation dates.
- Narrow fix: parse epoch seconds as a numeric decimal and convert to milliseconds for both post/comment shapes; keep the existing absent-field behavior.
- Regression: add raw MockEngine JSON fixtures with integer and decimal `created_utc`, for both post and comment, asserting identical nonzero milliseconds. No live Reddit credentials required.
- Second-pass result: real valid protocol representation, not malformed input compatibility. Reddit's own JSON API wiki shows `created_utc: 1315269998.0` and documents zero-fraction epoch seconds: [Reddit JSON data structures](https://github.com/reddit-archive/reddit/wiki/json).

## Explicitly rejected or not elevated

- `wm_signed_fetch` factory lacks local approval flags: false positive. Invocation-specific policy in `feature/tools/api/ToolRegistry.kt:512` classifies GET/HEAD versus writes; dispatcher applies that contract. Do not add duplicate local policy.
- JSCell `storeBytes` compares string length: by design. `JsCellLimits` explicitly defines the limit in characters despite the legacy property name. No byte-limit migration is justified.
- JSCell QuickJS cannot preempt a currently blocking native evaluation: documented wrapper limitation in `JsCellEngine.kt`. Turning this into an interrupt/process-isolation redesign exceeds a precise current-owner repair. Keep this as runtime acceptance limitation rather than pretending a state transition proves CPU interruption.
- MiniAppStorage treats empty string as a new key at full quota: not reachable through the bridge path, which JSON-encodes values (`""` is two characters). Do not fix based on direct helper inputs alone.
- MiniApp revision/version restore stores only HTML and retains current metadata/permissions: existing version schema is HTML-only; no verified manifest rollback contract was found. Not elevated as a defect.
- OAuth orphan callback/provider-registration timing: only registered production provider is Feishu, which uses loopback and deliberately cannot survive process death. Deep-link resume concern was not elevated without a production consumer.
- GitHub large-content (`encoding=none`) path: official API restricts 1-100MB files to raw/object media types, while current client asks generic JSON. A synthetic empty-content fixture alone does not establish the actual production response on that path. Not elevated in this audit.
- GitHub PR summary's optional `is_pull_request` marker is absent on `/pulls` responses: wrapper already identifies the result as `pulls` from `github_pr_list`; no consumer depends on that marker. Not counted without observable impact.
- WebMount session leases, action dispatch guards, transient-dialog preservation, cache fingerprint/ref restoration, and MiniApp external-image URL/DNS checks have existing owner-level safeguards. No speculative replacement was proposed.

## Validation handoff

Root owns Gradle. Suggested focused app classes after regressions are added: `JsCellRuntimeTest`, new `MiniAppHttpClientTest` and `GithubClientTest`, and `RedditClientTest`. Jev sidecar proposes separate `JevWebGoalRunnerTest` and `JevScreenGoalRunnerTest`. A compile/test pass does not prove real WebView, MediaProjection, physical-device or remote-provider acceptance.
