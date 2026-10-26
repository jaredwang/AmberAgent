# Android production audit and precise repair

Baseline: main@0971aa41ca7a2526e9ee17eb9497b7edc1db07be.
Pre-existing untracked .workflow/search-repair-20261025 is preserved.

Goal: review current production paths across this Android product, independently verify candidates, list confirmed findings and phased repairs, finish authorized fixes with an independent subagent review after each phase.

Success: every accepted defect has reachability and impact evidence, a focused reproduction/check, an owner-level fix, and independent phase review. Required JVM/build checks pass or external blockers are documented. Device/provider acceptance remains a separate claim.

Constraints: no sibling product sources; preserve existing work; no commit/push/publish, dead-code deletion, permission expansion, speculative fallback/retry/framework.

Phase 0: parallel read-only module audits, second-pass classification and repair plan.
Phase 1..N: repair by disjoint owners; reproduce before fix, verify after; independent phase review.
Final: cross-module verification, one bounded maintainability pass, evidence and limitations.

Build coordination: root agent owns Gradle invocations; focused JVM first, then relevant module suites and Full/Novel/DeepRead compile/assemble as justified.

## Repair phases

1. Runtime/tool effects/memory and durable data safety.
2. Provider/search and document/native parsers.
3. Novel/background/Council state and content wiring.
4. Chat UI/Web/MiniApp/Jev and final integration.

Independent verification reports decide acceptance; each implementer first writes and runs a failing regression. Root coordinates Gradle, and each phase has a separate independent review.

## Final completion

All four phases and their independent reviews closed. Final21-module JVM, unchanged native host evidence, three normal-config APK builds, six signatures/native-library/fixture checks passed. Detailed current evidence and acceptance limits: final-report.md and results/final-*-verification.json. No commit/push/publication; final APK device/provider acceptance not performed.
