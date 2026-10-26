# Search candidates: independent second verification

Current HEAD: `0971aa41ca7a2526e9ee17eb9497b7edc1db07be`.
Read-only pass; no production/test edits, no Gradle, no paid provider calls.
Reviewed current owner/caller code and the pre-existing `/tmp/amber-search-review-20261025` probe sources, XML and verification.json. SHA-256 of the current SearchAggregator/SearchOrchestrator matches the probe's copied-source manifest. Prior probe results are existing evidence, not newly executed tests or app/device acceptance.

## Classification of the 15 candidate groups

| ID | Candidate | Second-pass conclusion | Exact necessary change |
| --- | --- | --- | --- |
| S01 | Explicit empty enabled IDs is repopulated | **True / P1** | Remove unconditional selected-service fallback from `SettingsAggregator.kt:480-486`; put legacy missing-key migration at the prefs read boundary so stored `[]` stays empty. |
| S02 | SearXNG response schema rejects current payload | **True / P1, narrowed** | `SearXNGService.kt:139-140` requires `number_of_results`, but current upstream JSON omits it. Make obsolete count optional/default (or retain unused declaration with a default). Do not classify absent content as proven; see below. |
| S03 | News topic does not reach Serper/SerpAPI | **True / P2** | `SearchAggregator.buildServiceParams:152-166/301-303` recognizes only Tavily; forward `topic=news` for Serper/SerpAPI whose adapters already select native news at Serper:61-68 and SerpApi:61-69. Preserve time hints for adapters without native date fields. |
| S04 | Tavily drops requested date window | **True / P2** | `SearchAggregator:153-155` skips query enrichment for Tavily, but does not forward time parameters, and Tavily request body:93-101 ignores them. Wire `time_range` and explicit recency via supported date boundaries in both param and HTTP body owners. Do not send `any` as native enum or invent an obsolete `days` contract. |
| S05 | Firecrawl news is discarded behind web results | **True / P2** | Orchestrator requests `[news, web]` (`SearchAggregator:163-164`), but `FirecrawlSearchService:200-220` appends all web before all news then globally truncates. Preserve requested source priority (or prioritize news for the news request), keeping total common result size. No generic fallback needed. |
| S06 | Available publication dates are lost | **True / P2, bounded** | Map already-decoded Exa `publishedDate` to standard `publishedAt` (`Exa:101-106/171-172`); same for SearXNG `publishedDate` (`SearXNG:114-119/178-179`). Current official Tavily news returns `published_date`, and Zhipu returns `publish_date`: add optional DTO fields and map these exact known fields. Do not infer dates from arbitrary age/text or invent blanket mappings across unverified providers. |
| S07 | Generated Zhipu variants exceed 70 chars | **True / P2** | `buildQueryVariants:406-407` creates a 96-char query even from `AI`; `buildServiceParams` and `ZhipuSearchService:62-65` forward it unchanged. Official endpoint schema maxLength is 70. Keep short original query for this provider when host-added enrichment cannot fit; do not arbitrarily truncate meaningful original text. Direct overlength inputs should return an explicit local validation failure. |
| S08 | Search cancellation stops only before headers; Exa/Zhipu do not cancel at all | **True / P1** | Shared `SearchService.kt:299-318` await resumes with Response at headers, disposing cancellation hook while callers synchronously read body; Exa:89 and Zhipu:74 use blocking execute. Use one cancellable response-consumption owner whose cancel hook spans body reading and closes/cancels request, then migrate affected calls (including Exa/Zhipu). Root should reproduce with a real delayed socket, not assume cancellation of a deliberately uninterruptible fake interceptor is possible. |
| S09 | Five configured providers prevent built-in free fallback | **By design / no repair** | `SearchOrchestrator:33-34/62/307-317` intentionally takes five highest-priority sources with configured-first order and ten source-variant call budget; strategy explain uses the same selection. The old probe asserts a sixth call beyond this budget without establishing a reserve-slot/failure-retry contract. Do not add fallback machinery or reorder user choices. Wording can be assessed separately if desired. |
| S10 | Provider answer disappears in aggregation | **True / P2** | Both SearchAggregator and SearchOrchestrator merge only `result.items` and omit `SearchResult.answer`; Grok supplies its grounded answer there while citations may have empty text. Preserve nonblank provider answers, attributed to source, in output; answer-only successful results must not be labeled empty. Bound output using the existing API shape rather than constructing fake URL items. |
| S11 | Case-sensitive URL/title dedupe merges distinct pages | **True / P2** | FreeSearchAggregator:141-145 lowercases the whole path/query; preserve case of path/query while normalizing scheme/host. Orchestrator:485-486 merges by domain + lowercased/punctuation-stripped/truncated title even when two valid canonical URLs differ; Latex versus LaTeX is a proven counterexample. Prefer canonical URL identity; use title fallback only where URL identity is unavailable, if such inputs are intentionally accepted. Preserve existing duplicate same-URL merging. |
| S12 | Brave token is logged | **True / P1** | BraveSearchService:66 sends `X-Subscription-Token`; add its case-insensitive key to RequestLoggingInterceptor SENSITIVE_HEADERS:21-30 **and** DataSourceModule debug HttpLoggingInterceptor redaction:628-637. Shared client initialization at DataSourceModule:640 proves both are production reachable. |
| S13 | DeepRead ignores disabled built-ins | **True / P2** | `DeepReadSourcePrefetcher:805-829` unconditionally adds Bing/Jina fallback whenever configured enabled services are nonempty, ignoring free aggregate/Jina toggles. Pass current settings into fallback selection and gate Bing by the merged free switch (DDG OR Bing) and Jina by its switch. Enabled configured Jina/Bing profiles retain their own explicit enablement. |
| S14 | Subagent citation pills do not open | **True / P2, narrowed** | `ChatMessageSubAgentStep:649-654` calls MarkdownBlock without onClickCitation, default `{}` at `richtext/Markdown.kt:81`. Legacy citation pills use it (`MarkdownInlineText:590-600`). Reuse `rememberClickCitationHandler(liveParts)` from ChatMessageCommon:98-119 in the subagent sheet. Natural URL source pills already call context.openUrl; do not claim all subagent source links are broken. |
| S15 | MiniApp uses configured-provider adapter rather than built-in orchestrator | **By design / no repair** | `MiniAppSearchBridge:28-35` deliberately uses SearchAggregator's configured-only API and maps a bounded MiniApp schema. No current spec proves built-in sources or orchestration behavior is required for this surface. Existing probe itself labels this a design observation. Do not silently switch adapters. |

## Reachability and false-positive challenges

### Settings disable semantics

Settings UI explicitly removes the last enabled ID (`SettingSearchPage:151-159`), then SettingsAggregator read consistency puts one back. This is not merely fresh-install defaulting: the same function runs on every assembled read. `SearchPrefs.readFrom:71-73` currently collapses missing versus explicit empty, so a correct change must keep only the intended legacy defaulting at that boundary. S01's pre-existing failure is consistent with this path.

### SearXNG count versus content

The current [upstream JSON response producer](https://github.com/searxng/searxng/blob/master/searx/webutils.py) creates query/results/answers/etc. without number_of_results; this directly supports S02. The existing probe exercises exactly this missing count and fails. The current [result type implementation](https://github.com/searxng/searxng/blob/master/searx/result_types/_base.py) supplies `content=""` for MainResult and LegacyResult and returns the field through as_dict. Therefore the broader claim that valid current results omit content is **unproven/false-positive for the inspected current contract**. Avoid changing all required identity fields or adding blanket lenient decoding based on synthetic incomplete fixtures.

### Native time/topic

Serper/SerpAPI's own current adapters prove native news routing is present but unreachable through shared param construction. Merely adding these types to `supportsNativeTopic` would remove their current time hints because this same Boolean controls query enrichment: separate those concerns in the minimal request builder change. Tavily's [official current search endpoint](https://docs.tavily.com/documentation/api-reference/endpoint/search) supports time_range and start/end dates; news enables publication-date output. Use supported fields and preserve recencyDays precedence.

### Firecrawl ordering

The [official endpoint](https://docs.firecrawl.dev/api-reference/endpoint/search) defines result limit per source type for multi-source search. Both arrays may legitimately contain resultSize items; current app then takes only web. The explicit news-first caller request establishes intended priority, so this is not random relevance speculation.

### Zhipu length and dates

The [official web_search API](https://docs.bigmodel.cn/api-reference/%E5%B7%A5%E5%85%B7-api/%E7%BD%91%E7%BB%9C%E6%90%9C%E7%B4%A2) shows maximum string length 70 for search_query and publish_date in response. Description also says a short query is recommended, but the schema maxLength independently establishes the constraint. Avoid asserting exact remote status/error without a paid request: the defect is host-generated invalid input against the documented contract.

### Cancellation evidence limits

The delayed-header-body socket probe demonstrates the shared await lifetime gap directly: cancellation waits about 1.1s for a deliberately delayed 1.2s response body. Exa/Zhipu old probes use Thread.sleep inside a synthetic interceptor; these establish their callers block, but such an interceptor itself is not interruptible just because Call.cancel is wired. Replace or supplement these tests with a delayed local socket when validating the repair. Canceled blocking body reads are currently able to delay the whole coroutineScope and therefore tool cancellation/DeepRead's 36s collection bound. No extra retry or timeout layer is needed.

### Dedupe and answers

Current copied-source hashes match the preserved red probes. Distinct Latex/LaTeX URLs and titles become a single output, and the longer snippet can replace the retained item's text while its URL remains the other entity's URL. This is actual wrong attribution, not just fewer results. Grok's answer loss likewise removes the only answer text while retaining citation rows with empty snippets. Preserve answer provenance rather than hiding it in arbitrary first-item text.

### DeepRead and subagent surfaces

DeepRead's fallback code is inside the real collect path (`:97`); two user-visible toggles exist and save settings, so ignoring them is a wiring bug. Subagent citation scope is only legacy `[citation,host](six-character-id)` click handling; ordinary Markdown URLs remain functional and should not be changed.

## Validation to request after implementation

Reuse focused settings/search tests and the preserved raw-response/orchestrator probes, updating assertions to current verified contracts. For S08 use actual delayed local HTTP headers/body and verify prompt cancel + closed request. Add focused consumer checks for DeepRead toggle combinations and the subagent citation callback. Then app Full compile and relevant UI smoke if available. This pass does not claim new JVM/build/UI/provider success.
