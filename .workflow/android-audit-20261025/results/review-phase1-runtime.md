# Independent Phase 1 review: runtime, memory/context, tool effects

Reviewer: audit_ui. Read current diff from HEAD and owner reports; no Phase 1 production authorship, no edits, no Gradle execution. Search changes were excluded because they belong to the separately active search workflow.

## Verdict

No blocking correctness or maintainability finding in the reviewed Phase 1 implementation, including the newly applied stale approval guard. Focused GREEN execution remains owned by root; this report is independent source review, not a test result or device/provider acceptance.

## Reviewed behavior

- `SubAgentManager`: task publication, write-ahead, and turn registration now share the existing per-thread lifecycle lock with cancel/interrupt/followup. The startup error path uses `finishLocked` under that lock, preserves the captured restore context, settles admission through the established terminal claim, and propagates the original exception. No new retry/state machine was introduced. Public task cancellation cannot complete and then be overwritten by a startup whose turn is still unregistered.
- `NotificationApprovalDispatch` / `ChatRunCoordinator`: approvals wait for the predecessor's normal pause handoff and reject a cancelled or superseded predecessor. Admission executes on the supplied AppScope dispatcher (production Main), so the non-suspending identity check and session job installation stay serialized with UI session ownership. Persistence acknowledgment remains separate from the continuation lifetime; Stop still cancels the installed decision/continuation job.
- `ChatApprovalActions` follow-up guard: only a still-pending tool in selected history admits the user decision. Duplicate/stale input returns `applied=false`; coordinator now skips the persisted acknowledgment and resume for that result. Recipe resume arguments and ask_user answer state remain on the original path. Existing active continuation is not replaced by a stale approval.
- `ConversationContextPlanner`: the last USER plus its tool continuation is retained as a unit for soft-budget trimming. Only preceding history is removed; the existing final provider hard gate remains authoritative. No misleading claim that the soft target is always met is introduced.
- `MemoryCandidateFilter` / deterministic dream maintenance: full normalized content replaces the 200-character prefix identity, so distinct long suffix facts survive and exact duplicates still collapse.
- `MemoryDreamPlanner`: response reference validation now uses exactly the bounded record/candidate snapshots placed in the model prompt. It does not broaden model mutation authority or add fallback planning.
- `MemoryImportExportManager`: import first establishes local IDs, then remaps lineage via existing CAS. Missing/self links cannot become unrelated local links. CAS conflicts preserve concurrent edits; result counts exclude affected imported source rows. Topic member remapping remains limited to the non-topic map, preserving existing topic membership semantics.
- `ToolRegistry`: invocation-specific append and HTTP methods drive recovery effect classification; input-free metadata no longer promises overwrite-only safety. Expanded/legacy MCP names align with the existing external-call permission owner. This changes effect metadata, without inventing a new permission system.
- `WorkspaceArtifactTools`: GET/default GET/HEAD body rejection occurs before connection/output setup. It prevents HttpURLConnection's implicit GET-to-POST conversion; explicit write methods keep their body and ordinary GET behavior remains intact.

## Regression review

The prepared tests exercise real owner transitions and delayed boundaries: startup write-ahead/cancel and failed admission, predecessor parking/Stop/successor approval behavior, duplicate/stale decision acknowledgment, current-turn budget retention, long-prefix collisions, prompt-visible target references, foreign-to-local lineage and CAS conflict, persisted tool effect recovery, and loopback HTTP method/body behavior. The old never-ending approval predecessor fixture was changed because cancellation of that predecessor was the defect; its persistence/continuation ownership assertions remain meaningful.

No additional broad audit or speculative hardening is requested by this phase review. After the pending focused GREEN, Phase 1 can be closed.
