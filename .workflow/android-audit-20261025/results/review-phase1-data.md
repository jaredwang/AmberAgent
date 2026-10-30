# Independent Phase 1 implementation review: data / sync / terminal mirror

Reviewer authored no Phase 1 production code. Reviewed current HEAD diff and relevant tests/reports; no code edits or Gradle executed. Two correctness blockers remain; the other reviewed repairs are appropriately scoped.

## Blocker 1 / P2: D2 same-directory rename still acknowledges a nonexistent requested path

`feature/workspace/src/main/kotlin/app/amber/feature/workspace/WorkspaceDocuments.kt:156-159`

The new exact-name creation helper closes the cross-directory copy case, but same-directory move retains `source.renameTo(targetName)` followed by `targetParent.findFile(targetName) ?: source`. A SAF provider may normalize a requested name (e.g. preserve/append the MIME extension). Renaming `a.txt` to `b` may successfully produce `b.txt`; move still reports path `b`, and the next read of its returned path fails. This was explicitly included in audit-data and verify-data's D2 scope.

Required narrow correction: verify the actual final display name/path before returning success in the rename branch; do not fall back to reporting the requested path with a differently named source. Preserve or explicitly recover the original source when the provider cannot honor the exact request. Add same-directory provider-normalizes-rename coverage, separately from create-normalization. Existing new tests cover only cross-directory creation normalization and refusal.

## Blocker 2 / P1: recursive mirror invalidation destroys unsynced descendants during directory move

`feature/workspace/src/main/kotlin/app/amber/feature/workspace/WorkspaceManager.kt:107-109,206-219`

The active-lease gate and idle stale-copy invalidation correctly prevent the originally demonstrated overwrite. However `move(sourceDirectory,targetDirectory)` now recursively deletes both mirrored directory paths after the SAF move. The mirror is also the legitimate owner of private, unsynced terminal output, so it is not entirely disposable cache.

Concrete reachable sequence:

1. SAF contains `source/saf.txt`.
2. A detached builtin terminal runs with `syncWorkspace=false` (supported/default startJob behavior), writes `/workspace/source/local-only.txt`, and finishes. runProcessJob releases its mirror lease with `syncBack=false`; the local output is intentionally retained only in the mirror.
3. Approved `file_move source -> destination` copies/moves only the SAF directory and returns success.
4. invalidateMirrorPaths walks the entire mirrored `source` tree and deletes `local-only.txt`, although no copy exists in SAF or destination. The new idle test preserves only a mirror-only file outside the moved directory and therefore misses this loss.

Required correction: preserve mirror-only descendants when moving a directory, using the same mutex and existing path/ownership policy. Moving the corresponding mirror subtree with the logical directory move, with an explicit destination conflict policy, is one local option. Do not blindly treat all mirrored descendants as stale disposable copies. Add a mixed SAF-plus-private-mirror directory move regression and verify no old source resurrection at later flush/switch.

This does not request bidirectional delete propagation or preserving the overwritten contents of an explicitly replaced individual file. The regression is loss of unsynced descendants that the acknowledged directory move never copied.

## Reviewed without further blockers

- D1 uses `wt` for binary replacement and retains `wa` append; actual nontruncating SAF fixture reaches the owner.
- D3 validates all present migrated domains before main redaction. Missing domains keep defaults; malformed domains abort with existing MIGRATION_FAILED. Existing retired-search/TTS cleanup remains an intentional earlier migration boundary.
- D4 retains only an existing ciphertext/ref when decrypt is null; readable explicit clears still omit refs and owner deletion still permits orphan sweep. Blank MCP headers and credential-bearing URLs now reach that same owner, avoiding partial preservation. The added test retains existing clear/removal coverage.
- TerminalRuntime's initial asynchronous Termux RUNNING projection uses the existing expectedStatus=QUEUED CAS, preventing late resurrection without changing command/cancellation behavior.
- SYNC-1 skips only a STANDARD mask in sensitive identity fields; unchanged nonsecret scope must still match. Changed endpoint and legacy plaintext different-username negative tests preserve scope behavior.
- SYNC-2 compares href collection path instead of relying on PROPFIND order; path-form/absolute href and trailing slash tests cover the actual protocol repair.
- SYNC-3 derives filename/GET identity from href and leaves presentation displayname out of lookup. Existing ASCII snapshot naming makes arbitrary filename/encoding expansion unnecessary here.

No extra retries, fallback layers, permissions, dependencies, or broad architecture changes were introduced in these reviewed repairs. Build/JVM GREEN belongs to the coordinator; this report is independent source review, not device/provider acceptance.

## Follow-up resolution review

Re-reviewed only the two blocking corrections after the coordinator's confirmed follow-up RED. Both are resolved in current source; no remaining blocker found in this bounded pass.

- Blocker 1: the same-parent native rename now verifies `source.name == targetName` before success. A normalized result triggers one compensation to the captured original name, verifies that restoration, and reports failure with the surviving location if restoration cannot be confirmed. Ordinary supported rename and directory native rename remain intact. New tests independently control normalization on rename and cover exact success, refused/normalized target with original-source preservation, and directory contents.
- Blocker 2: directory moves preflight the private mirror destination before changing SAF. Successful directory moves relocate the existing mirrored subtree with Files.move, preserving unsynced private descendants instead of recursively deleting them; the old mirror source disappears and later flush cannot recreate it. Individual file writes/moves/deletes retain targeted invalidation. New mixed SAF/private directory test uses a real mirror lease released with syncBack=false and then a real syncFromMirror; the conflict test verifies SAF remains unchanged if a private mirror destination already exists.

Both changes stay under the existing mutation mutex and active-terminal gate. They add no synchronization framework, retries, permission or provider fallback. Filesystem/SAF operation failures continue to report failure rather than claiming successful movement; the correction does not promise atomic transactions across an external SAF provider and local filesystem. Fresh GREEN is owned by the coordinator, and no Gradle or production/test edits were performed by this reviewer.
