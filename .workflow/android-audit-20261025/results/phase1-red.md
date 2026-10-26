# Phase 1 focused RED

Command: exact root-scheduled offline --continue --max-workers=3 app/settings/tools-api filters, see logs/phase1-red.log.

Result: BUILD FAILED in 1m 14s.

App did not execute: test compilation failed in WorkspaceDocumentsWriteTest.kt:129 on a reified intersection type. Existing baseline-online app RED evidence is preserved separately.

## feature/tools/api/build/test-results/testDebugUnitTest

{'tests': 6, 'failures': 3, 'errors': 0, 'skipped': 0}

- app.amber.feature.tools.ExternalActionEffectPolicyTest :: fileWriteRecoveryDistinguishesOverwriteFromAppend
- app.amber.feature.tools.ExternalActionEffectPolicyTest :: httpRecoveryUsesTheActualRequestMethod
- app.amber.feature.tools.ExternalActionEffectPolicyTest :: mcpExternalCallsDoNotDeriveReplaySafetyFromTheirNames

## core/settings/build/test-results/testDebugUnitTest

{'tests': 40, 'failures': 6, 'errors': 0, 'skipped': 0}

- app.amber.core.settings.prefs.SecretPrefsChainRoundTripTest :: unrelated save preserves an unreadable credential until it can be read again
- app.amber.core.settings.secret.SettingsSecretMigrationTest :: malformed custom headers preserve migration input and secret references
- app.amber.core.settings.secret.SettingsSecretMigrationTest :: malformed WebDAV settings preserve migration input and secret references
- app.amber.core.settings.secret.SettingsSecretMigrationTest :: malformed S3 settings preserve migration input and secret references
- app.amber.core.settings.secret.SettingsSecretMigrationTest :: malformed MCP settings preserve migration input and secret references
- app.amber.core.settings.secret.SettingsSecretMigrationTest :: malformed search settings preserve migration input and secret references

No new Gradle invocation will start while the separately owned search-repair compile is active. Its exact process was identified; no external job was terminated.

## Fresh docs and terminal RED

After explicit fixture generic correction, phase1-docs-terminal-red.log completed BUILD FAILED in 2m 8s; 8 cases, 4 expected failures:

- WorkspaceDocumentsWriteTest.replacingBinaryContentTruncatesThePreviousPayloadAndAppendStillAppends
- WorkspaceDocumentsWriteTest.crossDirectoryMoveUsesTheExactRequestedNameWhenProviderAddsAnExtension
- WorkspaceDocumentsWriteTest.crossDirectoryMoveRetainsSourceWhenProviderRefusesTheRequestedName
- TerminalAdmissionTest.late Termux running projection cannot resurrect the completed durable task

D1/D2 and terminal workers were authorized by root post-RED instruction to perform their precise repairs after this run.
