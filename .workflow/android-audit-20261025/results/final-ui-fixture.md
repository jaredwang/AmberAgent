# Final suite citation fixture isolation

Owner: audit_ui; test-only repair. Production source unchanged.

Full app XML (`TEST-app.amber.feature.ui.components.message.SubAgentCitationInteractionTest.xml`) failed in @Before at GlobalContext.register before any source-click assertion. A global Koin application was already registered by the suite. The XML does not identify its originating fixture; existing suite tests include global startKoin fixtures and an explicit prior comment in ModelMenuInteractionTest documenting this shared-state risk. No claim is made that a particular earlier class leaked it.

The new citation test incorrectly assumed a vacant global context and also called unconditional stopKoin in @After, which could stop the pre-existing context if @Before registration failed. That was a test isolation bug, not a source-click production failure.

Repair: create a local koinApplication instance and provide it through the installed Koin 4.2.1 KoinIsolatedContext to the actual card/sheet composition. Teardown closes only that owned instance. Capture the previous GlobalContext instance and assert identity remains unchanged after teardown. No existing global context is stopped or replaced.

The actual card open, legacy citation click, and exact launched source URL assertions remain unchanged. Local installed AAR/class metadata verified that KoinIsolatedContext and local koinApplication are available. git diff --check passed. No Gradle invocation; coordinator owns the focused rerun and final suite validation.
