# Phase 1: sync repairs

Owner: /root/audit_data/audit_sync. Scope: SyncRedactor, WebDavClient, WebDavSyncProvider and focused existing app tests. Other agents own core/settings secret behavior; no edits there.

## Regression preparation

Tests added before production changes:

- SecretBackupExportTest: STANDARD round trip preserves unchanged local WebDAV username/password and SearXNG credentials; changed WebDAV/SearXNG endpoint cannot inherit local credentials.
- WebDavSyncProviderTest: child-before-self PROPFIND response remains discoverable with path-form/trailing slash and absolute self href; sidecar identity comes from href with temporary/friendly displayname.

Root confirmed all four new positive regressions failed in the baseline JVM run; see `baseline-verification.md` and `logs/baseline-online.log`.

## Production repair

- SyncRedactor skips masked sensitive values only as scope discriminators. It still compares actual legacy plaintext usernames and the endpoint/id/path identity; the added legacy-username negative case protects this distinction.
- WebDavClient excludes the requested collection by href path rather than response position, including absolute/path-form href and optional trailing slash.
- WebDavSyncProvider derives snapshot filenames/IDs and GET paths from href. Presentation displayname no longer participates in identity.

No settings-secret owner code, dependencies, permissions, retry or fallback was added. Root's sole Gradle coordinator owns GREEN execution; production changes are ready for that run.
