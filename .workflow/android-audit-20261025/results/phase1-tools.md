# Phase 1 tools implementation evidence

Owner: ToolRegistry.kt, WorkspaceArtifactTools.kt and focused policy/effect/HTTP regressions.
Second-pass acceptance: verify-tools.md by independent runtime reviewer.

## RED prepared

- ExternalActionEffectPolicyTest: per-invocation file overwrite vs append, HTTP read vs mutation methods/static metadata, neutral-name expanded/legacy MCP calls.
- ToolInvocationRecoveryRegressionTest: dispatcher persists actual invocation effects, simulated interruption leaves STARTED, real recovery must escalate append/HTTP POST/MCP to OUTCOME_UNKNOWN.
- WorkspaceArtifactHttpRequestTest: actual loopback wire method/body; GET/default GET/HEAD with a body must throw before sending, explicit POST body and ordinary GET remain supported.

Root confirmed RED from baseline-online.log and the delegated phase1 API RED run, then authorized production edits.

## Implemented

- ToolRegistry.effectClass consumes actual append args; overwrite/default invocation remains idempotent, append and input-less metadata are non-idempotent.
- HTTP effects reuse invocationPolicy's real method mutation decision, sharing signed-fetch behavior; GET/HEAD remain read effects and explicit write verbs require unknown-outcome reconciliation after interruption.
- Expanded/legacy mcp__ names are classified mutating in the shared owner, aligning effect/scheduling metadata with the existing resolver's external-call policy.
- WorkspaceArtifactTools rejects GET/HEAD body before any connection/output setup. Explicit write methods retain their supplied body and ordinary GET remains unchanged.
- Existing AgentToolDispatcherLedgerTest overwrite assertion now supplies real overwrite args; the idempotent assertion is retained and generic metadata safety has its separate regression.

Files changed: ToolRegistry.kt, WorkspaceArtifactTools.kt, ExternalActionEffectPolicyTest.kt, AgentToolDispatcherLedgerTest.kt, new ToolInvocationRecoveryRegressionTest.kt and WorkspaceArtifactHttpRequestTest.kt.

`git diff --check` passed after edits. Awaiting single Gradle coordinator GREEN; this worker did not run Gradle.
