# Independent second verification: data, settings, workspace and sync

Reviewer: provider-audit agent, independent of data/sync discoverers. Current production source reviewed; no code/test edits or Gradle. All eight candidates have a real reachable owner issue, with narrowing and one material repair-plan correction below.

| Candidate | Verdict | Independent check / necessary scope |
| --- | --- | --- |
| D1 binary overwrite | Accept P2 | WorkspaceDocuments.writeBytes selects `w` for overwrite; downloads/render/archive tools reach it. Android explicitly permits nontruncating `w`; neighboring text and copy use `wt`. One-mode change plus provider fixture is sufficient. |
| D2 move exact name | Accept P2 | Cross-directory copyDocument uses createFile then deletes source without checking actual provider-created name; returned requested path can be nonexistent. Same-directory rename also accepts `targetParent.findFile(targetName) ?: source`, so it can acknowledge the wrong final name. Enforce this existing product's exact-name contract before reporting success; preserve source before deleting cross-directory original. |
| D3 malformed migration domains | Accept P2, conditional failure contract | SettingsSecretMigrator defaults present undecodable custom headers/search/MCP/WebDAV/S3 to empty/default and commits them if changed. This is a migration failure-handling defect, not evidence that normal persisted JSON is corrupt. The class and existing provider migration test explicitly preserve unreadable raw input for recovery; a new generic fallback is unwarranted. |
| D4 unreadable secret + unrelated save | Accept P2, narrow failure contract | rehydrateSecret returns blank on null read; aggregator.update(fn) reads that blank; redactSecret returns before recording old ref; writeRefs removes it and orphan sweep deletes ciphertext. SecretStore.read and rehydrateSecret explicitly promise not to delete the reference when unreadable. Preserve only existing unreadable fields whose owner still exists; readable explicit clearing/removing owners must continue deleting secrets. |
| D5 SAF versus terminal mirror | Accept P1; active-only proposed fix is incomplete | Sequential background terminal then file_edit is enough: startJob returns while appScope worker holds mirror lease; edit writes SAF; lease syncBack overwrites from stale mirror. An additional fully sequential idle variant remains after lease release and must be covered (see below). |
| SYNC-1 masked username scope | Accept P1 | STANDARD encode masks username; restoreLocalSecrets calls hasSameSecretScopeAs at every object and includes username as plaintext equality key; identical local WebDAV/SearXNG fails scope match. applyRestoredSettings restores refs, then aggregator.update receives blank username/password; blank redact omits refs and orphan-sweeps, so refs do not rescue it. |
| SYNC-2 parent-first assumption | Accept P2 | WebDavClient.list unconditionally drop(1), and WebDavSyncProvider then filters collections. Valid parent-last multistatus loses first sidecar. RFC order is explicitly insignificant. |
| SYNC-3 displayname identity | Accept P2 | listSnapshots filters DAV displayName; loadSidecar derives snapshotId and GET path from it. A compliant server's displayname can differ from href and COPY/MOVE should preserve it, making the temporary-upload naming protocol a concrete trigger. Use href filename within the configured collection, retaining displayName only for presentation. |

## D3: not merely a proposed corruption guard

Existing `SettingsSecretMigrationTest.unreadable provider settings fail migration without replacing the raw value` asserts this exact preserve/retry behavior for providers. `SettingsSecretMigrator` documents abort before DataStore replacement and no completed migration marker on failure. Applying the same rule to other migrated secret domains closes an inconsistent existing owner contract; it does not introduce defensive recovery machinery.

Correction to audit-data's uncertainty: malformed SEARCH_SERVICES is also affected. `removeLegacySearchServicesIfPresent` does `raw.decodeSearchServicesDroppingLegacy() ?: return@edit` and then returns true. It does not abort migration for decode failure. The main migration subsequently chooses emptyList and replaces raw search JSON. Retired service deletion, by contrast, is expressly authorized migration behavior and should remain unchanged.

Decode all present domains before any SecretStore mutation if possible. Preferences transaction rollback alone cannot undo SecretStore writes done earlier in the same block. Missing keys may still use existing defaults.

## D4: preserve intent without expanding the secret design

The real contract is preservation of an existing unreadable reference/ciphertext on unrelated settings saves. There is no need to make a missing credential look usable, retry decryption, or add a second storage layer. The existing `SecretPrefsChainRoundTripTest` explicit readable-key clear and owner-removal assertions are mandatory regression guards. The test's toggled decrypt failure demonstrates the destructive second write; it should not be presented as proof that the user's device currently has a transient Keystore fault.

## D5: include stale idle mirror replay

An active-lease rejection and mirrorMutex serialization stop the initial concurrent scenario, but are insufficient for the full defect:

1. Acquire a terminal mirror from SAF `note.txt=old` and release it successfully. Mirror remains `old`.
2. With no active terminal, file_edit/write changes SAF to `new` and returns success.
3. setWorkspace or clearWorkspace unconditionally calls mirrorCopy.syncFromMirror on the previous workspace before clearing its mirror. SAF becomes `old` again. An idle explicit terminal flush or a later mirror-only terminal with flush=true can do the same.

Delete/move additionally leave old mirror paths that are resurrected by a later flush. Existing stage1 policy that mirror deletions do not delete SAF does not justify replaying stale mirrored copies after this app acknowledged a direct SAF mutation.

Required repair outcome: use the existing mutex/active-owner gate and keep or invalidate the affected mirrored paths after acknowledged SAF mutations so later flush cannot overwrite/recreate them. Add tests for both active mutation refusal and idle mutate-then-flush/switch, including move/delete. Do not add an external-editor CAS, a global lock held for full terminal lifetime, or bidirectional delete propagation. This is coordination between this app's existing mutation owners.

## Protocol verification

- [Android ContentResolver](https://developer.android.com/reference/android/content/ContentResolver): `w` may or may not truncate; `wt` is an accepted explicit mode.
- [RFC 4918 section 9.1](https://www.rfc-editor.org/rfc/rfc4918.html#section-9.1): collection multistatus order is insignificant.
- [RFC 4918 section 15.2](https://www.rfc-editor.org/rfc/rfc4918.html#section-15.2): displayname is presentation metadata, href remains identity; COPY/MOVE should preserve displayname.

No actual migration fault, live WebDAV server, terminal process, or device data was exercised in this second source review. Proposed root-coordinated regressions are still needed for RED/GREEN.
