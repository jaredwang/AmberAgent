# Terminal audit (Phase 0, read-only)

Scope: feature/terminal, feature/tools/impl/TerminalTools.kt, runtime models and direct consumers. Baseline 0971aa41. No production/test edits or Gradle execution.

## Confirmed candidate T1: delayed Termux RUNNING projection can resurrect a terminal task (P2)

- Location: feature/terminal/src/main/kotlin/app/amber/feature/terminal/TerminalRuntime.kt:568-570.
- Production entry: terminal_job_start / terminal_execute / terminal_install_packages -> TerminalRuntime.startJob -> startTermuxJob. startJob registers a QUEUED AgentTaskSnapshot before this call.
- Trigger: after the runtime job CAS QUEUED -> RUNNING, startTermuxJob launches an independent Dispatchers.IO coroutine that unconditionally updates the task to RUNNING. It then sends the Termux command. A fast result receiver or concurrent user stop can persist COMPLETED/FAILED/INTERRUPTED before that independent coroutine acquires the AgentTaskStore mutex. The late coroutine subsequently writes RUNNING.
- Relevant exact owner sequence: handleTermuxResult:296-307 -> finishJob:618-659 (asynchronous terminal projection); stopJob:267-283 also explicitly awaits a terminal projection for Termux. AgentTaskStore.update:109-110 only rejects stale status when expectedStatus is supplied; the startTermuxJob call omits it.
- Observable impact: real job is terminal and readJob returns terminal, but durable task snapshot/tasksFlow and task UI report RUNNING/ACTIVE. Process restart then recovers a task that had already finished as interrupted. Runtime job status/completionNotified do not guard this separate durable update.
- Second-pass challenge: Dispatchers.IO launch order does not guarantee mutex-acquisition order. Neither the Termux broadcast path nor AgentTaskStore.cancel serializes this coroutine with finishJob. No retry/reuse behavior needs an unconditional RUNNING overwrite for a UUID job. This is not a deliberate unknown-remote-state boundary.
- Narrow fix: pass expectedStatus = AgentTaskStatus.QUEUED for this initial RUNNING projection, or make that projection complete before sending the external command. Existing CAS parameter is preferable to a new queue/framework.
- Regression: force/delay the initial projection across a real terminal update using the existing AgentTaskStore and exercise the late projection. Verify terminal status/queue state remain terminal, while initial QUEUED -> RUNNING still succeeds. Include direct source/call-path wiring evidence; do not weaken cancellation assertions.
- Verification boundary: source/interleaving proven; no failing JVM regression was run in this read-only packet. Root should reproduce before applying fix.

## Rejected / by-design candidates

1. terminal_execute defaults to builtin_alpine instead of the settings default: explicitly documented by TerminalTools schema line46; terminal_job_start defaults to settings. No change.
2. SSH/Termux stop/timeout reports INTERRUPTED with no exit code: intentional unknown remote-process status, documented in README and tool descriptions. No fake success/cancel guarantee.
3. Multiple local jobs/sessions cannot concurrently acquire the mirror even though overall concurrency is 4: WorkspaceManager.acquireTerminalMirror explicitly admits one local mirror owner; other slots support nonlocal backends. No change.
4. WorkspaceManager.syncToMirror/withMirrorSync lack activeTerminalMirrors checks: independently forwarded to data audit; data reviewer verified there are no production callers at current HEAD. Unreachable, no change.
5. Session stop appears to kill only its root process: builtin session launches proot with --kill-on-exit in init-host.sh. Without actual Android/proot counterevidence, a bare host shell reproduction would not prove the production defect; excluded.
6. LocalTerminalProcess descendant cleanup of TERM-resistant children may have a residual-child edge, but no Android runtime reproduction was obtained and proot owns the normal builtin tree. Not promoted into a confirmed finding.
7. Terminal log files outlive the 64 retained in-memory jobs: durable AgentTaskStore snapshots intentionally retain their output paths, and task cleanup supports deletePrivateOutput. No automatic log deletion was added.
8. Runtime repair concurrency: current-revision repair writes staging assets/init scripts; stale runtime reset while a prior APK process is active was not shown reachable. No speculative installer-lock change.
9. SSH credentials and host identity: per-command profile snapshot, credentialsFor equality guard, per-command JSch, pinned host-key repository and streaming redactor are wired. Existing opt-in loopback tests cover authentication/trust/timeout; no real server was contacted during this audit.

Suggested focused verification after repair: app TerminalAdmissionTest plus feature/terminal JVM suite. Loopback/physical Android/Termux acceptance are separate and were not run by this packet.
