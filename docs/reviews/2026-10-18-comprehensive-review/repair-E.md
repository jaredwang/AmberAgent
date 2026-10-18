# Phase 3 · UI repair packet E

Scope: E1, E2, E3 and E4 from `audit-E.md`. The owner granted this packet the chat-input, chat-page, message-document-opening and timeline-prefetch files plus their tests. Theme/two-pane/ChatInputComposers WIP was not changed.

## Changes

- **E1 — accepted remote document opening:** `MessagePartsBlock` uses FileProvider only for a `file:` URI. HTTPS and other already formed document URIs pass to the existing external opener intact. The chooser and local-file read grant remain in place; no download path, extra permission or retry was introduced. A second real Compose click test reads the local FileProvider URI back to prove that the existing local-document path still works.
- **E2 — attachment-only message editing:** the existing edit merge inserts nonblank newly typed text when the original message had no Text part. Existing multi-Text ordering and original attachments are preserved; both shapes have direct state tests.
- **E3 — composer preflight/submission ownership:** `ChatInputSubmissionOwner` owns one in-flight preparation and the snapshot's parts/edit target/conversation. It releases the in-flight guard in `finally`, checks coroutine cancellation and conversation identity after suspended validation, passes the captured edit target to the real ChatPage callback, and clears only an unchanged accepted draft. A later text draft, different edit target, or new attachment import stays in the composer. Ordinary sending still sends the clicked snapshot even if the user has already typed the next draft. Both click and long-press use this owner and one validation path. The production callback now returns whether the service accepted the message; ChatPage no longer clears an unrelated current input. Delayed keyboard hiding also leaves an active new draft/edit/import alone.
- **E4 — selected-variant prefetch:** image collection reads only `messages[selectIndex]`, preserving the selected variant's Image and Markdown-image behavior.

The submission owner is local to the existing composer. It adds no durable queue, global state, DI binding, mutex around network work, or permissions.

## Evidence and checks

- The coordinator's original-code RED run is `/tmp/amber-review-20261018-red-phase2-ui.log`: the real HTTPS-document Compose click fails with `IllegalArgumentException`; the attachment-only edit and selected-variant prefetch tests fail their behavior assertions.
- `ChatInputSubmissionOwnerTest` calls the actual production owner with a `CompletableDeferred` validation barrier. It covers duplicate taps, preserving and submitting the correct text snapshot, preserving a later edit while submitting the original captured edit target, retaining a new in-progress attachment, conversation change, validation rejection, service rejection, and cancellation followed by retry. It is not a source-string assertion or copied implementation harness.
- A single bounded maintainability pass used `code-simplifier` and the Kotlin/Compose guidance. The common tap/long-press validation path and the explicit callback acceptance result keep the mutation/consumption owner visible. Existing product and WIP behavior outside the four fixes was retained.
- Targeted `git diff --check` passed. This worker did not run Gradle. Runtime tests, app compilation/build and independent phase review are for the coordinator's serialized gate; they are not claimed passed here.
- No installation, launch, provider-call or physical-device result is claimed.

## Suggested coordinator gate

Run the affected app unit tests for `ChatDocumentOpenTest`, `ChatInputStateTest`, `TimelineImagePrefetchTest`, and `ChatInputSubmissionOwnerTest`, with existing composer interaction tests if included in the phase's app gate. Have an independent reviewer check the real `ChatInput -> ChatInputSubmissionOwner -> ChatPage -> ChatVM` callback wiring and the current WIP boundary before marking Phase 3 complete.

Production files changed by E: `ChatInput.kt`, new `ChatInputSubmissionOwner.kt`, `ChatInputState.kt`, `ChatPage.kt`, `ChatMessageMessagePartsBlock.kt`, and `TimelineImagePrefetch.kt`. Tests changed by E: `ChatInputStateTest.kt`, `TimelineImagePrefetchTest.kt`, new `ChatDocumentOpenTest.kt`, and new `ChatInputSubmissionOwnerTest.kt`.
