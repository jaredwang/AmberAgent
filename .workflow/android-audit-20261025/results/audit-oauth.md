# Antigravity OAuth baseline failures: independent classification

Scope: two existing app-suite failures named `late invalid grant cannot clear a session created after logout` and `late successful refresh cannot replace a session created after logout`. No production/test edits or Gradle commands performed by this reviewer.

## Conclusion

These two failures are **test configuration failures, not evidence of a reachable logout-generation race**. Both abort before the mock HTTP request starts. Do not weaken production configuration checks or generation ownership to satisfy them.

## Current evidence

- Current XML: `app/build/test-results/testFullDebugUnitTest/TEST-app.amber.ai.provider.providers.google.AntigravityOAuthSessionTest.xml`, timestamp `2026-10-25T14:42:03.885Z`, two failures.
- Invalid-grant failure stack points to `AntigravityOAuthSessionTest.kt:77`; successful-refresh failure points to `:46`. Both lines assert `entered.await(2, TimeUnit.SECONDS)`.
- The test has not reached `client.logout`, saving the new session, releasing the response, or checking retained tokens when it fails. Its name describes an intended later scenario, not the actual observed failure.
- `ai/build/generated/source/buildConfig/debug/app/amber/ai/BuildConfig.java:11-13` currently defines both Antigravity client ID and secret as empty strings. No secret values were read or printed.
- `ai/src/main/java/app/amber/ai/provider/providers/google/AntigravityOAuth.kt:191-197`: `refresh` invokes `requireClientConfiguration()` before constructing/calling HTTP.
- `requireClientConfiguration` at `:159-162` rejects blank ID/secret. The async coroutine wraps the exception in `runCatching`; therefore the test's mock `entered` latch never counts down and the foreground test waits two seconds before failing.

## Logout race second-pass check

- `logout` at `:121-125` holds the provider generation lock, increments the generation and clears the old token.
- Refresh captures generation before HTTP (`:193`) and uses `saveIfCurrent` on success (`:206`) or `invalidateIfCurrent` for invalid grant (`:201`). Both helpers recheck the expected generation under the same lock (`:138-151`).
- In each test's intended sequence, logout increments the generation before the late response arrives. The old success cannot save, and old invalid grant cannot clear the new token. The current implementation is consistent with those test assertions.
- This review is scoped to the supplied logout/new-session sequence. It does not certify all login/refresh/onboarding races.

## Minimal repair / verification recommendation

Use the existing build configuration seam in `ai/build.gradle.kts:15-20,32-33` to run these mock-network tests with harmless, explicit fixture values:

```text
./gradlew --offline -PantigravityClientId=oauth-unit-test-client -PantigravityClientSecret=oauth-unit-test-secret :app:testFullDebugUnitTest --tests 'app.amber.ai.provider.providers.google.AntigravityOAuthSessionTest'
```

Root/sole Gradle coordinator should execute and record the result. These values are mock test fixtures, not working credentials; the HTTP interceptor controls token responses. Then regenerate/build normal configuration without those properties before producing APKs. Verify normal BuildConfig/APK does not retain the fixture markers.

No production code change is justified by these failures. If the repository requires a default unconfigured suite to be green, the test owner should explicitly express the fixture prerequisite or use a task-specific fixture configuration; do not silently skip the behavioral verification or add production fallback credentials. Root decides the narrow test-wiring policy after the fixture run proves the intended path.
