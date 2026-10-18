# Audit E: Compose, chat input/media, themes, adaptive layout, MiniApp entry points

Baseline: `d983c0b782c6eedce7c826f62003aa26973cc17c` plus the existing theme/two-pane WIP. Review is confined to this Android root. Existing WIP was read and retained. No Gradle task or device operation was run by this work packet.

## Confirmed findings after a second production-path check

### E1 · P2 · A MiniApp's accepted HTTPS document crashes when its chat attachment is opened

- Owner/location: `app/src/main/java/app/amber/feature/ui/components/message/ChatMessageMessagePartsBlock.kt:604–613`, especially the unconditional `part.url.toUri().toFile()` at line 610.
- Trigger: an authorized MiniApp calls `host.sendToConversation` with `{kind:"document", url:"https://example.com/report.pdf", name:"report.pdf"}`; the user opens that message's attachment chip.
- Reachability recheck: `MiniAppConversationWriter.kt:80–108` explicitly accepts HTTPS URLs for `kind=document`. `MiniAppBridge.kt:274–321` passes those attachments into the production conversation writer; `snapshotConversationImages` at 515–523 snapshots only Image parts, leaving Document URLs unchanged. `DataSourceModule.kt:473–483` connects the writer to `ChatService.sendMessage`. This is an accepted host contract, not an arbitrary malformed persisted value.
- Impact: AndroidX `Uri.toFile()` rejects a non-file scheme before an intent is launched. The click callback has no catch, so opening this accepted attachment throws on the UI thread.
- Minimum repair: distinguish remote URLs from local files in the attachment-opening owner. Pass HTTPS directly to a normal `ACTION_VIEW`; retain FileProvider for app-owned local files. Handle a missing opener as a user-visible failure rather than replacing the attachment with another URI. Do not add downloading/retry infrastructure.
- Regression added: `ChatDocumentOpenTest.httpsDocumentAcceptedFromMiniAppOpensWithoutTreatingItAsALocalFile` renders the real `MessagePartsBlock` with a Document produced by the real MiniApp attachment parser, clicks its chip, and expects an opener containing the original URL. Gradle execution is pending with the coordinator.

### E2 · P2 · Editing an attachment-only message silently drops newly entered text

- Owner/location: `app/src/main/java/app/amber/feature/ui/hooks/ChatInputState.kt:126–155`.
- Trigger: edit a user message whose original parts contain an image/document but no Text part, type an explanation, and save or regenerate.
- Reachability recheck: `MiniAppConversationWriter.writeAndSend` at 52–62 omits Text when the supplied text is blank, so attachment-only messages exist through a supported production entry. `ChatPage.kt:1306–1307` uses that message's actual parts to initialize edit state. `confirmEdit` and the send callback both consume `getContents()`.
- Cause/impact: `editedTextIndex` is `-1`, so the original-parts loop never inserts the new `textContent`. It still returns the retained attachments as nonempty input, allowing the save to succeed while discarding the user's new explanation.
- Minimum repair: in the existing edit merge, insert nonblank new text once when the original message has no Text part. Keep original text-part ordering and attachment-removal behavior for other edits.
- Regression added: `ChatInputStateTest.editingAttachmentOnlyMessage_keepsNewlyTypedTextAndOriginalAttachment`, using the real state object. Gradle execution is pending with the coordinator.

### E3 · P2 · Send preflight allows duplicate submissions and consumes a newer composer draft

- Owner/location: `app/src/main/java/app/amber/feature/ui/components/ai/ChatInput.kt:323–384`; consumption at `ChatPage.kt:1137–1182`.
- Trigger A: attach an image while the chat model needs the configured vision-model fallback, tap send twice while the first validation is still running, and let both validations succeed.
- Trigger B: during the same suspended validation, change the typed text or enter editing for another message before validation succeeds.
- Reachability recheck: `ImageAttachmentValidator.firstBlockingIssueForSend` at 158–163 performs a real `VisionModelHealthChecker.probe`, in addition to image encoding. Each send tap independently launches a coroutine, captures the parts, suspends through IO, then submits those captured parts. There is no in-flight send guard. The callback reads the current `inputState.isEditing()`/`editingMessage` rather than a captured edit target, and clears the current state whenever the old snapshot is accepted.
- Impact: multiple taps can produce duplicate user messages/provider work; a newer draft can be cleared after the older snapshot is sent; switching the edit target during preflight can direct old parts to the newly selected message.
- Minimum repair: give send preparation one short-lived owner per composer. Allow one preflight at a time and capture parts plus edit target and conversation identity. Send the clicked snapshot even when the user types the next draft during validation; pass the captured edit target rather than reading the new one. Clear only when the current composer still matches the submitted snapshot, and retain later text/edits/attachment imports. Cancellation or a changed conversation blocks the late submission. Apply the same owner to tap and long-press. A mutex around network work or another durable queue is unnecessary.
- Verification required: a production seam with an injected suspending validator and a `CompletableDeferred` barrier. Assert (1) a second tap does not start a second validation/send, (2) editing/typing during the barrier is not cleared or sent to another edit target, (3) rejected validation and cancellation release the in-flight state, and (4) normal accepted send clears only the submitted draft. Phase 3 adds the actual `ChatInputSubmissionOwner` seam and direct behavioral tests; see `repair-E.md`. No source-string test or duplicated implementation harness is used.

### E4 · P2 · Timeline image prefetch includes every hidden message variant

- Owner/location: `app/src/main/java/app/amber/feature/ui/pages/chat/TimelineImagePrefetch.kt:83–95`, introduced by HEAD.
- Trigger: regenerate/edit a message several times so a node contains multiple variants with different images, then open/scroll the conversation near that node.
- Reachability recheck: `MessageNode.messages` contains alternatives and `selectIndex/currentMessage` selects the visible one (`core/model/.../Conversation.kt:209–219`). Timeline rendering and shelf collection use the selected message, but `prefetchImageUrls()` explicitly loops through all `messages`.
- Impact: the eight-node window eagerly requests images from hidden alternatives. The amount of work grows with all historical regenerations, although only the selected variant can become visible by scrolling. This adds unnecessary network and image-decoding work in a newly added performance path.
- Minimum repair: collect only `messages.getOrNull(selectIndex)?.parts` (or the valid current message under the existing node contract), retaining Image and Markdown-image collection rules for that variant. No extra cache or scheduling policy is needed.
- Regression added: `TimelineImagePrefetchTest.prefetchesOnlyTheSelectedMessageVariant`, with three real `UIMessage` alternatives and a nonzero selection, then a selection change. Gradle execution is pending with the coordinator.

## Rejected or deliberately unreported candidates

- **Manual accent/family selection after applying a portable theme: false positive.** Looking only at the control callbacks suggests that `themePack` wins over the changed accent. The actual owner `SettingAppearancePage.updateDisplaySetting` at 57–83 discards an active try-on and clears both `themePack` and `appliedThemePackageId` on a real manual theme change. The existing production wiring already handles it; no repair/test is proposed.
- **Removal of the currently selected theme card: not established as a defect.** The selected card uses a check indicator; another card can be selected before removal. No data-loss or unusable-flow evidence was established.
- **Two-pane thresholds and reading-column widths: by design in the current tests.** The scene intentionally uses a shared list/detail stack and a 760dp reading column. No proven layout failure was found in the narrow code review; screenshots/device measurements remain a different verification gate.
- **Video covers for remote/content URLs: by design.** The code explicitly avoids remote full-video reads just to make a thumbnail and falls back to the play icon. This was not reported as missing functionality.
- **Markdown-image collector syntax cases: not accepted into the fix set.** A source-level concern about inline code/escaping/parenthesized destinations needs the actual renderer to recognize the same input differently. That comparison has not been executed, so no regex-only issue is counted or repair requested.
- **MiniApp theme-provider refresh: not pursued.** Review was deliberately closed before expanding the accepted repair scope.

## Suggested repair grouping

1. Fix E2 and E4 in their small existing owners; run their focused JVM/Robolectric tests and have a separate agent review the diff.
2. Fix E1 in the attachment-opening path; run the real Compose click regression and local-file opener regression; separate-agent review.
3. Fix E3 with a narrow send-preparation owner and controllable suspending tests; verify real tap/long-press production wiring; separate-agent review.
4. Run the coordinator's affected-module checks/build and report those separately from installation, launch, provider behavior, and physical-device acceptance.

The audit initially changed only the three named regression test files and this report. The later authorized Phase 3 repair and final file list are recorded in `repair-E.md`. Existing production WIP and existing test WIP were not replaced.
