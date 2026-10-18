# Phase 2 MiniApp repairs: U5 and U6

Ownership is limited to `MiniAppRunnerPage.kt`, `assets/miniapp/miniapp_bridge.js`, their tests, and this repair record. Existing theme-library and other workers' changes are retained. No Gradle task is run by this worker.

## RED preparation

- **U6, current host theme:** the existing provider closure was extracted into the production `rememberMiniAppThemeProvider` Compose seam and passed into the actual `MiniAppBridge` factory. The seam deliberately retains the old system-mode selection and first-composition capture for the RED gate. `MiniAppThemeProviderTest` uses real Compose `MaterialTheme` and `LocalDarkMode`, captures the first provider as the real WebView bridge does, and asserts host mode overrides system mode and the retained provider observes a later theme recomposition.
- **U5, confirmation timeout:** `scripts/miniapp-bridge-regression.cjs` executes the real JS asset with a controllable clock and native message endpoint. It covers delayed success and denial, retention of the sensor subscription receipt and its working unsubscribe, and unchanged deadlines for noninteractive fetch/storage calls. The confirmation cases include the legacy 5-second calls and system/AI calls that wait in the same native confirmation flow with longer fixed JS deadlines. No real sleep, provider request or duplicated bridge implementation is used.
- Before test creation, an independently executed Node VM reproduction observed `host.sendToConversation` schedule a 5000ms timer, reject with `MiniApp bridge timeout`, and ignore a later `{ok:true,data:{status:"sent"}}` receipt. Its command/output are captured in `/tmp/amber-miniapp-bridge-timeout-repro.json`.

Coordinator RED commands:

```sh
node --test scripts/miniapp-bridge-regression.cjs
./gradlew :app:testNotionDebugUnitTest --tests 'app.amber.feature.ui.pages.miniapp.MiniAppThemeProviderTest'
```

The coordinator owns the actual Gradle variant choice and serialized RED execution. Neither test pass nor a device result is claimed at this point.
