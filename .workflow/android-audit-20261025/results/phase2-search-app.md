# Phase 2 search app regression preparation

Ownership: app SearchAggregator/SearchOrchestrator and DeepRead/subagent search consumers; S01 SettingsAggregatorHelpersTest coordinated with audit_data (no ownership conflict). Root owns Gradle and logging/DI. Provider protocol/cancellation and FreeSearchAggregator tests are separate packets.

Current status: TEST ONLY. No production changes, no Gradle execution by this worker.

## Prepared behavioral regressions

- `app.amber.core.ai.tools.SearchAuditRegressionTest`: native Serper/SerpAPI news routing retaining date hints; Tavily time/recency params without decoration and no invalid `any`; Zhipu original query fallback for host enrichment and all generated variant requests <=70; answer-only success retained by both configured aggregation and orchestration with source provenance; different canonical URLs retain independent text despite title normalization; case-sensitive query values remain distinct.
- Proposed answer API asserted by tests: `answers:[{source_service,answer}]`, with `ok` (or `partial` when another source fails) for nonblank answer-only output. No artificial URL item. Root informed of this contract.
- `app.amber.feature.board.hotlist.deepread.DeepReadSearchFallbackTest`: actual private selection used by collect() obeys disabled built-ins; merged DDG/Bing switch and separate Jina switch work; explicit enabled configured Bing/Jina remain enabled independently.
- `app.amber.feature.ui.components.message.SubAgentCitationInteractionTest`: open actual subagent capsule and sheet, click legacy six-character citation inside Markdown, assert actual source URL launches. Real manager read-projection maps are seeded while unrelated model/storage execution is omitted. A fixture failure must be corrected before classifying product RED.
- `app.amber.core.settings.prefs.SettingsAggregatorHelpersTest`: explicit empty enabled configured search IDs and dangling-only IDs remain empty through current consistency boundary. The missing-key legacy migration still needs prefs-read boundary coverage during implementation.

## Root-coordinated next checks

Run the new app classes and core/settings Helpers first against unmodified production; preserve meaningful failures. Then authorize Phase 2 owner changes, rerun affected suites, and have an independent reviewer trace the final paths.

Do not repair S09 five-source budget or S15 MiniApp configured-only adapter; both were excluded as by design. Do not modify natural URL citation pills, which already work.
