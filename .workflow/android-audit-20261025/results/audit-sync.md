# Phase 0: sync, backup and iCloud audit

Read-only review of current HEAD 0971aa41; no production/test edits and no Gradle runs.

## Confirmed candidates

### SYNC-1 (P1): STANDARD restore clears unchanged WebDAV / SearXNG credentials

- Owner: `app/src/main/java/app/amber/core/sync/core/SyncRedactor.kt:177-185`, scope key `username` at line 243.
- Production path: backup UI -> SyncArchiveManager.createArchiveFile -> SyncPayloadExporter.writePayload -> SecretRedactor.redactForExport -> SyncRedactor.encodeSettings(STANDARD); restore UI -> verify/apply -> SyncRedactor.decodeSettingsForRestore -> applyRestoredSettings -> SettingsAggregator.update.
- Trigger: export STANDARD backup with WebDAV username/password, then restore EVERYTHING on the same device with identical local WebDAV endpoint/account. encodeSettings masks username with `__MASKED_BY_AMBERAGENT_SYNC__`, but hasSameSecretScopeAs compares that masked username to local plaintext username. The object no longer matches itself, so trustLocalSecret becomes false for both username/password and restores them as empty strings. SearXNG username/password are affected too: its array object fails matching before descent. Current ref persistence does not repair empty strings: redactSecret returns empty before consulting refs.
- Impact: otherwise unchanged working backup/search credentials are removed, requiring reentry; STANDARD explicitly intends to preserve local secrets. No external provider assumptions needed.
- Narrow fix: do not use exported masked sensitive values as a plaintext identity discriminator. Keep the nonsecret identity checks (provider id/type/URL/path etc.) so changed endpoints still cannot receive another endpoint's credentials. Avoid fallback or retry.
- Regression: extend existing `SecretBackupExportTest` helpers: export STANDARD `runtimeSettings()`, decode against same local settings, assert WebDAV username/password survive. Add username/password SearXNG same-id same-endpoint round trip and changed-endpoint negative case. End-to-end settings assertion can use SyncArchiveManagerIntegrationTest.
- Second pass: FULL mode is not this path; CONFIG_ONLY adopts only providers, so WebDAV loss specifically applies to EVERYTHING. This is not the old unused BackupSettingsRedactor positional merge helper.

### SYNC-2 (P2): WebDAV listing discards the first child when collection is not first

- Owner: `app/src/main/java/app/amber/core/sync/webdav/WebDavClient.kt:371-379`.
- Production path: BackupVM / upload plan -> WebDavSyncProvider.listSnapshots -> WebDavClient.list -> propfind(depth=1) -> unconditional `drop(1)`.
- Trigger: valid PROPFIND multistatus returns snapshot sidecar first, collection self-entry last. `drop(1)` removes the real sidecar, and the remaining collection is filtered by provider. A one-snapshot folder appears empty; upload OVERWRITE planning cannot find the old snapshot either.
- Evidence: RFC 4918 section 9.1 explicitly defines collection PROPFIND order as insignificant; sections 13 and 14.24 also permit any response order. [Primary specification](https://www.rfc-editor.org/rfc/rfc4918.html#section-9.1).
- Narrow fix: exclude the requested collection by normalized href identity rather than position. Handle absolute and path-form href and trailing slash. Do not drop subcollections generically at client layer.
- Regression: existing WebDavSyncProviderTest fake server always emits parent first; add parent-last fixture and assert the one sidecar remains discoverable. Include absolute/path-form self href.
- Second pass: this is live production WebDAV SyncProvider, not a dead legacy adapter. Arbitrary server order is a supported protocol contract, not speculative compatibility.

### SYNC-3 (P2): WebDAV uses DAV:displayname as resource identity

- Owner: `app/src/main/java/app/amber/core/sync/provider/WebDavSyncProvider.kt:67-70,182-188`; parser preserves displayname at WebDavClient.kt:434-440.
- Production path: uploadSnapshot uploads temporary `upload-*.tmp` files then MOVE publishes final names; listSnapshots filters `resource.displayName` for `.snapshot.json`, then loadSidecar reconstructs snapshot ID and GET path from displayName instead of href.
- Trigger: a compliant server retains a DAV:displayname distinct from the URI filename, including retaining the temporary upload name across MOVE. The newly published backup becomes invisible. If displayname still has `.snapshot.json` but differs from href, GET uses the wrong URL and the sidecar is silently skipped.
- Evidence: RFC 4918 section 15.2 says displayname is presentation metadata, resource identity remains URL, and COPY/MOVE SHOULD preserve displayname. [Primary specification](https://www.rfc-editor.org/rfc/rfc4918.html#section-15.2).
- Narrow fix: derive the sidecar filename and request identity from the resource href within the requested collection; displayName stays display-only. Keep existing snapshot format and upload protocol.
- Regression: existing fake server fixture with href `/backups/snap-1.snapshot.json` but displayname `upload-123.tmp` must still list/read `snap-1`; alternate displayname `friendly.snapshot.json` must not trigger GET of the friendly name.
- Second pass: source upload uses ASCII snapshot names, so percent-decoding is not required to prove this defect. This is separate from ordering: parent-first responses still fail when displayname differs.

## Excluded / not promoted

- `BackupSettingsRedactor.mergeMaskedSecrets` merges arrays positionally, but there are no production callers in current main source. Do not fix/delete it in this audit.
- iCloud `overwrite=true` moves old file to trash before upload; `ICloudDriveTools` explicitly advertises this sequence in its parameter description, so the replacement behavior is by design. A failed upload can leave the old item in trash, but changing the protocol requires more than a precise local audit fix; no claim of permanent data loss.
- iCloud node_ref does not bypass configured vault: client resolves current configured path first and only reports hint match/fallback.
- DB/file export is not one atomic snapshot; exporter explicitly documents the absence of a shared owner lock, while Novel snapshot has its own owner boundary. No demonstrated failing production interleaving was established, so no broad transaction rewrite.
- Preserve-conversations omits conversation_draft/thread tables. Draft is explicitly an unsent MiniApp composer item and UI preservation text specifies conversation history; artifact registry has independent ownership. Without a stronger intended scope, do not label wholesale preservation of these independent datasets a confirmed defect.
- Sync restore journal recovery after DB commit intentionally keeps committed DB/files and reports partial commit; it does not pretend secrets/settings were restored atomically. Existing partial-commit tests cover this contract.
- Backup import size caps differ between providers; no concrete user archive fixture exceeding supported caps was established, so no arbitrary limit expansion.

## Suggested phase

One sync/settings phase can implement SYNC-1 in SyncRedactor and SYNC-2/3 together in WebDAV listing identity. Focused app JVM selectors: `app.amber.agent.data.sync.SecretBackupExportTest`, `app.amber.agent.data.sync.SyncArchiveManagerIntegrationTest`, `app.amber.core.sync.provider.WebDavSyncProviderTest`. Root agent owns running Gradle and obtaining RED/GREEN.
