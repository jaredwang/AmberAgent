# Phase 0 UI audit

Baseline: 0971aa41. Read-only production/test review; no Gradle run or production edits.

## U1 — P2 — Edit confirmation bypasses the retained submission owner

- Source: `app/src/main/java/app/amber/feature/ui/components/ai/ChatInputSubmissionOwner.kt:14-24`, versus `submit:39-53`.
- Production chain: ChatInput main send/IME -> `prepareSend` (`ChatInput.kt:216-240`) -> `submit`, which marks `inFlight` before suspending validation on IO. The still-visible edit confirmation bar -> `ChatPageInputActions.confirmEdit` -> `confirmEdit`, which does not check `inFlight`.
- Trigger: edit a saved text message (or one containing historical image attachments), click main Send, then click Save Only while validation is suspended. The second action saves and clears the editor; the first action resumes with its captured parts and still passes `canSubmitCapturedAttachments` (text has no attachment checks; historical URLs are explicitly allowed even after edit target changes), then requests save-and-regenerate.
- Impact: two edits admitted for the same draft, and Save Only can be followed by unintended regeneration. This conflicts with the existing single-owner contract already enforced between main-send and long-send.
- Second pass: not prevented by the generating guard, because validation happens before generation starts; not prevented by disabled buttons, because the bar is disabled only for `loading`, and validation is not loading. Main send is rendered during editing (`ChatInput.kt:555-558`). Historical-target changes are intentionally allowed for snapshot correctness, so weakening that rule would be the wrong fix.
- Narrow fix: `confirmEdit` must respect the same in-flight admission. Do not add a second submission state or change widget actions, which do not consume the composer draft.
- Regression: extend `ChatInputSubmissionOwnerTest` with deferred `submit.validate`, call `confirmEdit(false)` while blocked, assert it is rejected, release validation, assert one submission and original captured edit id. Also retain retry after cancellation/rejection.

## U2 — P1 candidate — Rename saves an obsolete entire conversation during streaming

- Source: `ChatVM.kt:580-584`, authoritative persistence `ConversationStore.kt:491-516`.
- Production chain: Chat recap panel Rename -> `ChatTitleRenameDialog` -> `vm.updateTitle` (`ChatPageScaffold.kt:530-534`) -> `saveConversation`.
- Trigger: a fully loaded conversation is generating. Rename captures `conversation.value.copy(title)`; `saveConversationInternal` suspends at `existsConversationById`; another generation flush or finalization publishes newer text/nodes; rename continues and assigns/persists its older full snapshot. The rename UI has no generating guard.
- Impact: newer streamed/final content can be overwritten and durable messages rolled back; `checkFilesDelete` can also treat newly added attachments as removed. A later flush may reintroduce content, so the permanent-loss schedule is a rename finishing after the final generation flush.
- Second pass: `withConversationWrite` is the restore gate, not a per-conversation snapshot mutation mutex; no CAS/rebase after the exists read. Window merge only applies to partially loaded timelines and still replaces matching nodes. `setConversationAutoApproveToolCalls` and `saveConversationAsync` have no production callers and are excluded from the count.
- Existing narrow seam: repository `updateConversationMetadata` (`ConversationRepository.kt:335-349`) updates title/FTS without rewriting nodes. Route rename through the conversation metadata owner and update current live title without copying historical message state; preserve restore epoch guard. Avoid changing generic save semantics for real branch edits.
- Required reproduction before acceptance: a controlled delayed DAO/exists boundary with a newer live+durable tail published before rename resumes, then assert final title and newer tail both survive. Existing `ConversationStoreTest`/Room fixtures are suitable. No runtime reproduction was run in this read-only packet.

## U3 — P2 conditional candidate — Camera/crop result URI is lost on host recreation

- `ChatAttachmentLaunchers.kt:45-47` keeps output URI/file and camera owner in plain `remember`; `ChatCropLauncher.kt:27` does the same for crop output URI. ActivityResult callback requires a nonnull remembered URI; a redelivered successful result after host recreation is discarded.
- Manifest handles orientation/screenSize, so simple rotation is not a valid failure claim. Need a process-death or other real host recreation while camera/crop is foreground, followed by success result redelivery. Pending output identifiers can be saveable without persisting the entire composer.
- Do not accept or implement solely from static suspicion. No lifecycle runtime reproduction performed here.

## Rejected / by design

- Apparent self-recursive properties in `useEditState`: false positive. A standalone Kotlin 2.3.21 reproduction of the exact lexical shadow pattern printed false then true; Kotlin captures the outer local as intended. No fix.
- Reduced ChatPage input conversation snapshot ignores same-id message chunks: by design; reachable composer consumers only need latest id. The context indicator that would need all messages has no production callers. No fix.
- Shelf HTML ordinal/renderer concerns: excluded; shelf target is a dedupe identity and non-image items navigate to the source node, not an ordinal resolver.
- Timeline tail reserved spacing and protected streaming nodes: explicitly documented behavior; no correctness evidence.
- Clearing generated queue selection asynchronously and volume-scroll stale capture: not elevated without consequential reachable reproduction.
- Host draft consumed on any accepted send is explicitly documented in `ChatVM`; do not reinterpret it as an unrequested feature change.

## Verification guidance

Root coordinates Gradle. Start with focused `ChatInputSubmissionOwnerTest` and the owner-level metadata reproduction, then relevant app chat/ConversationStore tests. Camera/crop requires meaningful recreation evidence before adding a fix. No visual, device, live-provider, or full app acceptance is claimed by this packet.
