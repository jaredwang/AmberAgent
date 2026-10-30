# Independent second-pass verification: memory/context and Novel storage

Verifier is independent of the memory/context and storage audit authors. Read current production owners, caller wiring, prompt contract, durable mapping/applier gates, and existing tests. No code/tests edited, no Gradle run.

## MC1 — accepted, P1; preserve the current request and its continuation chain

`ChatGenerationRoundEngine.generateRound` calls prepareContext on every generation round with current loop messages. `ConversationContextEngine` invokes soft fitting even when notifyOnly prevents compaction, whenever the estimate exceeds forceRatio. `ConversationContextPlanner.fitMessagesToTokenBudget` scans the non-system tail backwards and stops at the first over-budget message; it has no current-USER floor. A current USER preceding an executed-tool assistant message is therefore dropped when only the tool fits. This is production reachable on normal tool continuation rounds, rather than a malformed message fixture. The later provider TokenBudgetFitter classifies/protects CURRENT_USER and TOOL_RESULT, but receives the already-filtered list and cannot restore the user.

Narrow implementation boundary: retain the last user and every following message as one protected current-turn tail, alongside system summaries; spend the remaining soft budget only on earlier history. If the current turn alone exceeds soft target, leave it intact for the existing hard gate. Do not globally retain every historical tool result or introduce a new compaction/retry owner. Nonpositive-budget handling should use the same current-turn protection rather than takeLast(1).

## MC2 — accepted, P1; full normalized identity is required for automatic merges

Both extraction filter and deterministic maintenance compute full lowercased/alphanumeric content and then discard everything after character 200. Different long records with the same first 200 normalized characters collide. `planMaintenance` uses those collisions to select a surviving record and archive the others; `MemoryWorkerSetting` explicitly states auto-applied deterministic maintenance is for exact duplicates. The near-duplicate semantic planner is separately implemented, so treating truncated prefixes as identity is not an intentional semantic-dedupe feature.

Narrow fix: remove truncation at both dedupe identity functions and keep current normalization semantics. No hashing/index/framework needed. A long-prefix distinct-suffix case plus existing exact duplicate behavior is enough to verify the fix.

## MC3 — accepted, P2; supersedesIds are local row references

The codec serializes and restores supersedes_ids. Import allocates fresh local IDs (or matches an existing local record), but passes supersedesIds through unchanged. In contrast, topic member IDs use remappedIds. The applier compares supersedesIds directly with current local oldRecords IDs when recognizing an existing replacement. This is a real persisted cross-database key error, independent of import traversal order; not the same as source conversation/message provenance.

Narrow fix: complete original-to-local mapping, then rebase imported supersession references through that mapping. Do not retain unmapped foreign IDs as possible local references. Keep current upsert/CAS and matched-row metadata semantics; an order-independent follow-up pass is sufficient. Include replacement-before-original order and a nonempty destination in regression. Missing-file references must not be silently interpreted as existing destination IDs.

## MC4 — accepted, P2; model response authority exceeds supplied rows

`planWithModel` intentionally supplies only promptRecords (active/unexpired first 80) and first 50 candidates. It then calls parseModelPlanJson with all records/candidates. That parser admits merge/promote/archive/supersede/topic/ignore targets from the passed IDs. Thus its validity check covers rows the model never saw. This can occur without guessing opaque IDs: MemoryDreamPrompt's illustrative schema literally contains IDs 1..9; getAllRecords can supply enough newer records to omit older existing IDs. A copied example ID can resolve to a real unseen row. User review remains a gate, so P2 is appropriate, but does not justify allowing out-of-snapshot target IDs.

Narrow fix: bind promptCandidates once and pass the exact promptRecords/promptCandidates shown to parseModelPlanJson. Leave deterministic maintenance's full-library input untouched. Validate via the actual planner/gateway call site, because a parser-only unit test cannot prove the corrected input set.

## NS2 — accepted, P2; per-kind flags bypassed by neighborhood traversal

Assembler filters always nodes by foreshadowing/decisions flags and has tests explicitly demonstrating those category flags apply even when neighborhood is on. The smart neighborhood path instead passes all nodes to Nodes.neighborhood and filters only always afterward. Nodes.neighborhood excludes injection=off but does not receive per-kind flags. A smart decision or foreshadow node directly named in the chapter plan, or reached from an eligible relation, therefore enters the prompt while its category toggle is off. Worker and runtime pass the persisted flags through this exact path.

Narrow fix: apply the current kind gates to neighborhood input before matching/traversal, using a small shared predicate only if it avoids duplicating the existing always decision. No new source selection system. Cover directly named and relation-reached disabled categories, plus an enabled normal character and re-enabled flags.

## NS1 — corroborated while reading, P2

Although the assigned storage target was NS2, source inspection also corroborates NS1: tool prefix checks permit unsupported extensions, whereas Store.list/fileTree excludes them. Store documentation explicitly defines md/yaml book files. Refuse unsupported target paths at acceptance/proposal boundary; do not expand the book/export format.

## Verdict

MC1..4 and NS2 are real, production-reachable defects with owner-level repairs. No requested finding was rejected or downgraded beyond the current severities. Evidence is source/call-contract based; root still needs behavioral regression checks and independent phase review after implementation.
