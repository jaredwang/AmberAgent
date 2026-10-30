# Tools / policy / Android access audit

Baseline 0971aa41. Read-only; no production/test changes and no Gradle run.
Terminal is a separate sidecar report, audit-terminal.md.

## Confirmed candidates (request executable regression before fix)

### T1 / P1: append writes are incorrectly recoverable as idempotent

- `feature/tools/api/src/main/kotlin/app/amber/feature/tools/ToolRegistry.kt:915-916,939` treats every file_write as IDEMPOTENT_WRITE, despite `WorkspaceTools.kt:89,100` exposing append=true.
- Production chain: RunToolCatalogFactory -> WorkspaceTools.fileWriteTool -> WorkspaceManager.writeText(content, append) -> SAF append; DefaultRunKernel:482 and AgentToolDispatcher:583 persist `toolDef.effectClass(tool.input)` before execution; RunRecoveryService:458-465 leaves STARTED idempotent writes retryable.
- Trigger: append succeeds, process/coroutine stops before ledger FINISHED; the approved call is resumed. The same content can be appended twice without an unknown-outcome decision.
- Second check: whole-file overwrite is intentionally idempotent, but append has a different observable effect and no idempotency key reaches the SAF writer. This is not solved by existing approval digest binding.
- Narrow fix: classify append=true as NON_IDEMPOTENT_WRITE using actual invocation args; retain overwrite's idempotent behavior. Tests: append/default/false classification plus interrupted recovery gate.

### T2 / P1: HTTP mutation effects are persisted as READ_ONLY

- `ToolRegistry.kt:902-917` has no http_request case; `mutatesState():628-676` misses this name. All methods therefore return READ_ONLY even though invocationPolicy:281-295 correctly treats POST/PUT/PATCH/DELETE as mutations.
- Real tool `WorkspaceArtifactTools.kt:64-85,423-451` accepts these methods and sends caller body. Same kernel/dispatcher/recovery chain as T1 labels an interrupted external mutation safe to retry.
- Trigger: remote API commits a POST/DELETE and execution is interrupted before result persistence; same call can execute again. No remote idempotency key exists.
- Second check: direct invocation approval is correct; the defect is effect/recovery semantics, independent of approval and ordinary HTTP error retry policy.
- Narrow fix: classify actual GET/HEAD as READ_ONLY and mutation methods as NON_IDEMPOTENT_WRITE; static metadata must not promise safe replay without invocation args. Tests: raw method/default cases and recovery.

### T3 / P1: GET plus body is executed as POST under read-only authorization

- `WorkspaceArtifactTools.kt:437-440` sets doOutput=true for any body, including explicit/default GET. Android HttpURLConnection changes GET to POST when configured for output. `ToolRegistry.kt:281-295` still evaluates the original GET as public read, no approval, concurrency-safe and retryable.
- Trigger: http_request with public URL and body but missing method, or method=GET. Actual external method is POST; an ordinary read call can mutate a service without approval and can be retried by `AgentToolDispatcher.canRetrySafely():807-814`.
- Primary platform evidence: https://developer.android.com/reference/java/net/HttpURLConnection (HTTP Methods); AOSP implementation https://android.googlesource.com/platform/external/okhttp/+/master/okhttp-urlconnection/src/main/java/com/squareup/okhttp/internal/huc/HttpURLConnectionImpl.java, initHttpEngine's GET/doOutput conversion.
- Second check: requestMethod is assigned before doOutput; this does not prevent Android's conversion. URL private-target gate and redirect disabling do not address methods.
- Narrow fix: reject GET/HEAD body before opening/writing a connection, retaining actual caller-requested method and explicit POST behavior. Do not silently normalize GET to POST. Request-level regression should prove body-bearing GET dispatches no external request; POST body remains supported.

### T4 / P1: namespaced MCP calls can be recoverable as reads

- `ToolRegistry.kt:628-676,902-917` bases effect class on lexical mutation verbs and omits the mcp__ prefix.
- Actual catalog: `McpToolFactory.kt:32-42,54-62` creates external executable entries `mcp__server__tool` and legacy aliases; `RunToolCatalogFactory.kt:261-280` dispatches into McpManager. Tools named transfer/execute/invoke have no recognized write verb and become READ_ONLY. `PermissionDecisionResolver.kt:255-262` explicitly forces these calls mutating because remote effects are unknown, but that adjustment does not reach ledger class.
- Trigger/impact: approved remote operation succeeds then interruption before FINISHED; recovery can replay it without unknown-outcome confirmation, duplicating remote action.
- Second check: direct mcp_call_tool is already classified mutating; expanded/legacy forms are supported production entries and are not merely display aliases. Correct existing approval does not fix replay.
- Narrow fix: mark executable mcp__ family mutating/non-idempotent in shared ToolRegistry classification. Keep direct gateway behavior. Regression examples should include names without lexical write verbs. SpeculativeRunner still calls PermissionDecisionResolver, so no separate speculative approval bypass is claimed.

## Rejected / by design / not elevated

- Whole-file file_write overwrite idempotency is intentional; only append variant is defective.
- http_request public host resolving privately is already checked at runtime and redirects are disabled. DNS rebinding TOCTOU was not demonstrated; do not add a network framework.
- External file canonical root checks contain symlink traversal; `..` archive entries are also rejected by WorkspacePaths.normalize. No path traversal finding.
- screen_stop deliberately blocks later host-bound goals and does not interrupt actions already in flight; low-level actions retain per-call approval. Do not turn it into a process-global kill switch.
- Screen list coverage is explicitly observed_screens_only and anonymous repeated rows are documented. No generic completeness claim defect.
- A missing screen snapshot mid-flow discards prior per-step receipts (ScreenFlowTools early return). It merits a regression investigation, but not included in confirmed inventory without proving a reachable failing UI/controller case.
- Read-only/screenshot screen effects and js_cell private runtime effects have explicit platform-local semantics; do not classify every private cache write as external non-idempotent action.
- Android permission checks, calendar mutation approval, contacts/SMS explicit approval, external file allowlist gates were traced. No additional proven bypass was accepted from static inspection.

## Suggested checks

Root-owned Gradle: :feature:tools:api:testDebugUnitTest (ExternalActionEffectPolicyTest), app dispatcher/recovery regression and WorkspaceArtifactTools request tests, then app compile. Real SMS/contacts/calendar/phone/accessibility device actions were not performed in this read-only audit.
