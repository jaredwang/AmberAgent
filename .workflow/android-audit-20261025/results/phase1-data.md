# Phase 1 data repair packet

Ownership: WorkspaceDocuments.kt (D1/D2), SettingsSecretMigrator.kt and SecretRedactor.kt (D3/D4); only touch aggregator if required. Terminal owner edits WorkspaceManager.kt separately; sync owner edits app core/sync separately. Preserve concurrent changes. Root coordinates Gradle.

## RED prepared

- `app.amber.agent.data.agent.workspace.WorkspaceDocumentsWriteTest`: actual WorkspaceManager -> SAF fixture; nontruncating `w`, MIME-extension-on-create, rename refusal. Three checks cover replacement/append, exact cross-directory target, original source retention on refused target.
- `app.amber.core.settings.secret.SettingsSecretMigrationTest`: five present malformed domains must fail while preserving raw payload, references, secret and version marker. Extends existing strict-provider migration contract.
- `app.amber.core.settings.prefs.SecretPrefsChainRoundTripTest`: actual canonical settings save with toggleable decrypt failure; unrelated update must retain provider/custom-header/MCP header/OAuth/URL ciphertext and references. Existing readable explicit-clear test retained; owner deletion still reclaims credential.

Root confirmed six expected core/settings RED failures. WorkspaceDocuments fresh RED was initially blocked by Kotlin fixture generic inference; explicit `toTypedArray<Any?>()` corrected it. Sole Gradle coordinator audit_novel_storage then confirmed all three SAF tests failed for intended behavior (binary tail retained, changed target name, lost original source on refused exact name) in `logs/phase1-docs-terminal-red.log`. D1..D4 production fixes are now applied; unified GREEN pending with sole coordinator.

## Applied narrow fixes

- D1: `wt` for non-append binary replacement.
- D2: reuse exact-name document creation for copied files before source deletion; reject provider-refused name.
- D3: existing raw migration inputs decode strictly before redaction/commit, only absent keys use defaults; no retries or general settings-loader behavior change.
- D4: existing unreadable refs retain their persisted mask/ref while the owning field/config remains; readable explicit clears still remove secrets, owner removal still sweeps. Reuse existing redaction boundary, no recovery layer.

## D3/D4 implemented

- Migrator validates all present domains before candidate secret redaction. Missing values retain defaults; malformed values return existing MIGRATION_FAILED, leave payload/ref/ciphertext/version intact. Ordinary read fallback remains unchanged.
- Redaction recognizes an existing actual ciphertext whose read fails, retaining its reference/mask on an empty runtime projection. Header and MCP URL wrappers now reach this same existing field owner. Readable empty values still clear, and omitted owners produce no references for orphan sweep.
- No changes to SettingsAggregator or its concurrent search persistence edits.

## D1/D2 implemented

- Binary replacement uses explicit `wt`; append keeps `wa`.
- Moved the existing exact-name file creation/rename policy to one private helper used both by normal writes and cross-directory copies. A refused exact name fails before deleting the original source. No new compatibility or transaction framework.
- Production diff check clean. No self-run Gradle, device installation, provider invocation, commit or push.

## Independent review follow-up

Reviewer found D2 same-directory rename still accepted a provider-normalized name while reporting the requested path. Added a distinct rename-normalizing SAF control and regression requiring explicit failure plus original-name restoration, with supported exact rename and directory-content controls. Root confirmed the new failure in `logs/phase1-review-followup-red.log` and authorized correction. Native rename now verifies the actual name, restores the original name once on mismatch, and fails explicitly; if restoration itself fails, the error reports the retained actual location. Directory moves keep native rename semantics rather than being converted to copy/delete. Sole coordinator and independent reviewer notified for GREEN and re-review.

Coordinator reported follow-up GREEN: WorkspaceDocumentsWriteTest plus WorkspaceManagerMirrorIsolationTest, 14/14 passed in `logs/phase1-review-followup-green.log`. This is JVM SAF-owner evidence; no physical device claim.
