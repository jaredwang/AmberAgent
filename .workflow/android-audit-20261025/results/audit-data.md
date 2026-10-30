# Data / settings / workspace audit

Read-only audit against 0971aa41. No production or test edits; no Gradle execution. Scope: core/settings, app core/files/repository/storage, feature/workspace. Sync/icloud findings are in audit-sync.md.

## Confirmed candidates

### D1 — P2: Binary workspace overwrite can leave old trailing bytes

- Owner: `feature/workspace/src/main/kotlin/app/amber/feature/workspace/WorkspaceDocuments.kt:112`.
- Trigger: existing workspace file contains a longer payload; `writeBytes(path, shorterBytes, append=false)` opens with `w`.
- Production reachability: `WorkspaceArtifactTools.downloadFileTool` (line 113), image convert/render and archive extraction all call `WorkspaceManager.writeBytes` -> `WorkspaceDocuments.writeBytes` -> SAF ContentResolver.
- Impact: providers that implement plain write without truncation retain the old tail, corrupting downloads/images/archives despite a success response and reported new byte count.
- Second check: this is not an invented provider requirement. Android ContentResolver explicitly documents that `w` may or may not truncate; neighboring `writeText` and `copyDocument` already use `wt`. Source: https://developer.android.com/reference/android/content/ContentResolver#openOutputStream(android.net.Uri,%20java.lang.String).
- Narrow fix: use `wt` for append=false; retain `wa` for append=true.
- Regression: extend existing app `WorkspaceManagerMirrorIsolationTest` SAF test provider so `w` is a permitted nontruncating mode, then overwrite `123456` with `xy` and assert exact bytes `xy`; separately assert append.

### D2 — P2: Moving a document across directories can publish an inaccessible requested path

- Owner: `WorkspaceDocuments.kt:287`, followed by source deletion at line 161 and returning requested path at line 165.
- Trigger: a SAF provider changes the created document display name (e.g. appends the MIME extension); move `a.txt` to `sub/b` creates `sub/b.txt` but reports `sub/b` and deletes `a.txt`.
- Production reachability: `feature/tools/impl/.../WorkspaceTools.kt:187` workspace move tool -> WorkspaceManager.move -> WorkspaceDocuments.move/copyDocument.
- Impact: subsequent reads at returned destination fail; directory moves may silently rename nested files. The actual destination exists at a different path, so this is path correctness / original-source loss, not complete content destruction.
- Second check: existing `requireOrCreateFile` at lines 256-263 already recognizes this provider behavior and attempts an exact rename, failing if exact name cannot be achieved; the move copy path omitted the same contract. No production caller repairs its returned path.
- Narrow fix: enforce exact created names before copying/deleting source, reusing the existing exact-name creation behavior where possible; fail while retaining source if provider cannot supply target name. Verify same-directory rename too because it currently falls back to returning source on a missing expected name.
- Regression: SAF provider fixture that appends a MIME extension on create; assert exact destination and preserved contents, and source retained on refused exact rename. Existing test provider needs parent-aware children and rename/delete handling rather than a mock-only helper test.

### D3 — P2: Secret migration overwrites malformed non-provider domains with defaults

- Owner: `core/settings/.../secret/SettingsSecretMigrator.kt:86-124`, commit at lines 143-151 and orphan sweep at 172.
- Trigger: first secret migration (version < 1) with a present but undecodable `AMBER_CUSTOM_HEADERS`, `MCP_SERVERS`, `WEBDAV_CONFIG` or `S3_CONFIG`. Their decode returns null, which is replaced with an empty/default object, marked changed, persisted and marked migrated. Search decode has the same pattern, though its earlier legacy cleanup may already reject malformed search data and should be checked separately.
- Production reachability: `AmberAgentApp.kt:277` startup migrateIfNeeded -> dataStore.edit -> subsequent orphan sweep.
- Impact: original persisted configuration and associated retry references are irreversibly lost, whereas malformed provider settings correctly abort migration and remain retryable.
- Second check: this violates the class's explicit failure-preserves-old-values migration contract and mirrors the already-covered provider/legacy-profile failure behavior. It is limited to migration input that is actually undecodable; ordinary Settings loading fallback is a separate intentional contract. Do not add fallback/retries.
- Narrow fix: a missing key may default; a present key failing decode must abort the migration before any Preferences replacement or orphan cleanup. Decode all domains before redacting if practical, to avoid modifying SecretStore for an ultimately aborted candidate.
- Regression: extend `SettingsSecretMigrationTest` analogous to existing unreadable provider settings test, assert each present malformed payload, references and secrets stay intact, version remains unmarked, result MIGRATION_FAILED.

### D4 — P2: An unrelated settings save deletes an unreadable secret and its reference

- Owner: `SecretRedactor.kt:157` plus rehydrate failure lines 537-540; `SettingsAggregator.kt:235-236` orphan sweep.
- Trigger: an existing secret reference/ciphertext becomes temporarily unreadable. `rehydrateSecret` publishes empty string. The next unrelated `aggregator.update { copy(displaySetting=...) }` re-reads that empty value; `redactSecret` returns empty before retaining existing reference. The new refs omit it and the orphan sweep deletes the stored ciphertext.
- Production reachability: every normal settings lambda update -> SettingsAggregator readCurrentSettings/writeSettings -> SecretRedactor -> deleteOrphans.
- Impact: a transient read/decrypt failure becomes permanent credential loss after an unrelated settings change. Current comments promise unreadable refs are preserved, but preservation only lasts until the next save.
- Second check: explicit clearing a readable key must continue removing its reference/ciphertext (existing `SecretPrefsChainRoundTripTest` assertion). Deleting the entire provider/config must also clean it. The defect is only preserving existing unreadable credentials on a non-secret update, not retaining every empty key.
- Narrow fix: at the owning save/redaction boundary distinguish an existing unreadable reference from a readable explicit clear; retain its mask/ref (or reject that specific secret rewrite) until replaced/owner removed. Avoid a second secret framework or swallowing errors.
- Regression: toggleable SecretCipher in actual SettingsAggregator round-trip: persist key, make decrypt fail, update unrelated display flag, make decrypt work, assert credential/ref still present; retain readable-clear and owner-removal assertions.

### D5 — P1: Approved SAF edits can be overwritten when an already running terminal job flushes its stale mirror

- Owner: `feature/workspace/.../WorkspaceManager.kt:86-99` and `deleteFile` at line 179; active lease flush at lines 139-145.
- Trigger: start a background built-in terminal job with workspace sync enabled; it acquires a mirror containing `note.txt=old`. Before it finishes, a subsequent approved `file_edit` writes `note.txt=new` directly to SAF. Job completion releases its lease with syncBack=true, which overwrites the newer SAF content from stale mirror `old`.
- Production reachability: `TerminalRuntime.startJob` launches appScope worker then returns snapshot at lines 204-223; runBuiltinAlpineJob acquires lease at 485 and flushes at 511. A sequential next tool call `WorkspaceTools.file_edit` at 126 reaches unguarded WorkspaceManager.editText. No parallel-tool setting or simultaneous dispatcher is needed. Terminal audit independently verified this chain.
- Impact: a successful explicit file write/edit/move can disappear after a background terminal job finishes. Existing mirror policy that local results overwrite SAF is valid; the missing part is coordinating this app's own mutation owners.
- Second check: `activeTerminalMirrors` already guards workspace switching, refresh and explicit flush. Documents mutations bypass both this lease check and mirrorMutex entirely. This is not the unused syncToMirror/withMirrorSync helper candidate, and it does not ask for external-file CAS or bidirectional deletion propagation.
- Narrow fix: route existing SAF mutation entry points through the same mirrorMutex and reject while a terminal mirror lease is active, consistent with current refresh/flush contract. Serialize mutation versus workspace switching as well. Do not hold a global lock for the entire terminal job or introduce retries.
- Regression: existing parent-aware SAF fixture: acquire terminal mirror from old content, attempt file edit/write/move/delete while active, assert explicit rejection and unchanged SAF; release then assert mutation succeeds. Include an end-to-end stale mirror sequence showing no acknowledged edit is later lost.

## Rejected / by design / not enough evidence

- `ProviderPrefs.update` / other domain write helpers can persist plaintext, but repository-wide caller search found no production callers. Canonical production writes go through SettingsAggregator. Not a reachable finding; no dead-code deletion authorized.
- `WorkspaceManager.syncToMirror/withMirrorSync` lack active lease checks, but repository-wide caller search found only definitions/comments. Do not patch unreachable helpers for hypothetical concurrency.
- `SyncRestoreWriteGate` short durable writes are serialized; network work is intentionally outside the lock. Do not add a broad global lock.
- Forked conversation attachment sharing was a false positive: `ConversationEditor.forkConversationAtMessage` copies attachments and existing regression proves separate durable physical ownership.
- `WorkspaceMirrorCopy.syncFromMirror` intentionally does not propagate mirror deletions to SAF (documented stage1 policy). Do not turn this into delete synchronization.
- SecretRedactor orphan sweep already restricts SETTINGS_SECRET_SCOPES and preserves SSH credentials. No global-orphan deletion finding.
- Plaintext preservation on legacy SecretStore write failure is an explicit compatibility contract; no unsolicited fail-closed rewrite proposed.
- Pin-vs-session-cleanup race remains unproven pending owner review; not accepted in this report.

## Suggested verification

Root-coordinated Gradle only: targeted `:core:settings:testDebugUnitTest` SecretPrefsChainRoundTripTest / SettingsSecretMigrationTest; targeted `:app:testFullDebugUnitTest --tests '*WorkspaceManagerMirrorIsolationTest'`; then affected module suites and app compilation. No device/provider acceptance claimed.
