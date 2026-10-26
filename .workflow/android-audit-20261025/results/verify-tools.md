# Independent second-pass verification of tool candidates

Reviewer: runtime owner, not the original tool auditor. Current baseline 0971aa41; no production/test edits and no Gradle run.

## T1 ACCEPTED: append file_write is not idempotent

`WorkspaceTools.kt:83-102` exposes append and passes it to WorkspaceManager.writeText. `WorkspaceDocuments.kt:86-94` opens SAF output in `wa` mode for append. Repeating the same invocation appends the content again. Yet `ToolRegistry.kt:902-917,937-939` uses only the tool name and classifies every file_write as IDEMPOTENT_WRITE.

Production authority: DefaultRunKernel.kt:482 and AgentToolDispatcher.kt:583 prepare the durable effect using effectClass(actual input); the classifier ignores append. RunRecoveryService.kt:458-467 leaves STARTED idempotent effects retryable while NON_IDEMPOTENT_WRITE becomes OUTCOME_UNKNOWN. Ledger prepare reuses STARTED rows (`ToolEffectLedger.kt:238-244,446-450`). A process stop after the SAF append commits but before FINISHED therefore permits duplicate appends on retry without an unknown-outcome decision.

Not by design: the classifier's own contract promises same-argument convergence; its comment assumes whole-file overwrite, contradicted by current append implementation. Keep replace mode idempotent, classify explicit append=true as NON_IDEMPOTENT_WRITE. No need to change file-write approval or remove append. Add actual-input classification plus recovery regression. Static metadata lacking args must not promise append-safe idempotency; idempotencyKeySupported is currently descriptive metadata only, not used to pass a key to SAF.

## T2 ACCEPTED: http_request mutating methods are stored READ_ONLY

ToolRegistry.kt:282-298 correctly computes per-invocation mutation/approval from HTTP method. However effectClass at :915 calls static mutatesState; `http_request` does not match its list/name heuristics, so POST/PUT/PATCH/DELETE all become READ_ONLY. Both kernel and dispatcher pass real input, but the current classifier consumes it only for wm_signed_fetch.

The same STARTED recovery chain as T1 allows a remote mutation which committed before interruption to be replayed without OUTCOME_UNKNOWN. Initial approval still happens correctly for explicit POST; do not claim a blanket approval bypass here. Minimal repair: classify http_request with actual method; GET/HEAD are read, writes are non-idempotent without an explicit external idempotency mechanism. Input-less metadata should be conservative. Tests should assert GET vs POST/DELETE and STARTED recovery.

## T3 ACCEPTED: declared GET plus body becomes a POST after read authorization

WorkspaceArtifactTools.kt:423-439 defaults method to GET, opens HttpURLConnection with that method, then enables doOutput and gets outputStream whenever a body string is present (including an empty string). ToolRegistry.invocationPolicy authorizes public GET as approval-free read/concurrency-safe and ignores body. Android HttpURLConnection's implementation changes GET to POST when doOutput is set. Thus a model can submit method=GET + body to a public HTTP endpoint and execute POST under the GET policy.

Primary-source verification (live checked): [Android HttpURLConnectionImpl.java](https://android.googlesource.com/platform/external/okhttp/+/refs/heads/main/okhttp-urlconnection/src/main/java/com/squareup/okhttp/internal/huc/HttpURLConnectionImpl.java#325), initHttpEngine lines 325-333; doOutput + GET assigns POST. GET outputStream calls connect/initHttpEngine first. HEAD+body fails rather than mutating; do not count it as the same bypass.

The private-target guard does not fix this: public GET is exactly the approval-free route. Minimal repair should preserve the declared wire method and policy agreement: reject unsupported bodies on GET/HEAD before opening a connection, or explicitly normalize the effective method in both policy and executor. Do not silently ignore body or allow transport to choose a write verb. Use a request-method/body fixture plus actual local transport regression if the test environment permits it.

## T4 ACCEPTED: expanded MCP effect classification depends on incidental name tokens

Production McpToolFactory.kt:33-43 exposes `mcp__server__tool` and legacy single-underscore-prefix aliases, and PermissionDecisionResolver.kt:250-262 explicitly treats expanded MCP as unknown external mutation requiring approval. ToolRegistry.mutatesState :628-679 handles only literal `mcp_call_tool` plus lexical mutation hints. An expanded name such as mcp__bank__transfer has no matched write token, so effectClass returns READ_ONLY despite actually invoking the external server (`McpToolFactory` execute closure).

Narrow impact: permission resolver still enforces approval initially; this is an effect/recovery bug, not a proven initial permission bypass. Interrupted approved transfers can stay retryable instead of OUTCOME_UNKNOWN. A name that happens to contain _write is classified mutating already, so use a neutral side-effect name in regression. The MCP schema has no verified safe-effect contract, and the existing resolver documents the conservative external-call policy; matching expanded/legacy MCP prefixes in the effect owner is justified. No broad per-server policy framework is needed. Explicit nonexecuting ambiguity aliases can remain a lower-cost conservative case; avoid expanding scope to metadata redesign.

## Focused checks

Existing seams: app tests AgentToolDispatcherLedgerTest and RunRecoveryServiceTest; invocation-policy tests under agent/data/agent/runtime. Test classification with actual input, then a STARTED effect through production recovery. These verify observable replay gating rather than only duplicated enum expectations.
