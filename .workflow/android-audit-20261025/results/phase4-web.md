# Phase 4 Web regression preparation

Status: root authorized Phase 4 production after all intended RED assertions were observed. Four owner fixes implemented and ready for GREEN; Gradle execution belongs to `audit_novel_storage`.

## Regression classes ready for RED

- `app.amber.feature.jscell.JsCellRuntimeTest`: repeated executions each get the per-run console allowance; truncation in one execution does not consume the next execution's allowance. Uses existing FakeJsCellEngine and real runtime/store/tool path.
- `app.amber.feature.miniapp.MiniAppHttpClientTest`: 307 and 308 preserve POST and JSON body; 303 changes POST to GET; redirected POST still rejects a private destination before dispatch. Uses injected OkHttp application interceptor and deterministic address resolver, with no network access.
- `app.amber.feature.webmount.adapters.github.GithubClientTest`: literal `#`, `?`, `%`, spaces, nested directories and percent-like file names remain encoded path data, while `ref` remains its own query parameter. Uses Ktor MockEngine plus real Robolectric Base64 decode, verifies returned content and requested URL.
- `app.amber.feature.webmount.adapters.reddit.RedditClientTest`: integer and `.0` epoch seconds produce identical nonzero millisecond dates for both post and comment JSON shapes. Uses existing Ktor MockEngine test facility.

Expected current-source failures: JSCell's second execution loses output; MiniApp's 307 POST arrives as GET; GitHub literal file delimiters change URL semantics; Reddit decimal timestamps become zero. The 303 and private-destination controls should already pass.

During test-only preparation, no production sources were changed. No Gradle task, provider call, WebView/device action, search edit, commit or publication was performed by this packet.

## Observed RED and production repair

Sole coordinator recorded the actual failures in `phase3-4-red.md`: both JSCell budget assertions, POST-to-GET corruption, truncated GitHub URL and zero Reddit timestamp all failed at the intended behavior assertions. Fixture compilation and the 303/private-destination controls passed.

On root's production GO:

- `JsCellRuntime`: resets the output character count under the existing run-state lock alongside clearing the output list.
- `MiniAppNetwork`: retains the current method/body on 307/308; other redirect behavior, per-hop URL checks and redirect limit remain unchanged.
- `GithubClient`: builds the contents endpoint with Ktor `appendPathSegments`, which encodes raw file delimiters and literal percent characters while preserving directory separators; `ref` remains the existing separate parameter.
- `RedditClient`: both post/comment dates share numeric epoch-second conversion accepting standard decimal representations, retaining zero for an absent field.

Only these four production files were changed by this packet. Targeted `git diff --check` passes. The four test classes above are ready for coordinator GREEN. No Gradle commands were run by the implementer; no search production edits were made.
