# Independent second pass: Novel domain and UI

Verifier: audit_novel_storage; read-only, no production/test edits or Gradle. Baseline 0971aa41.

## Novel domain candidates

### ND1 accepted, P2

Independently traced `WriteChapterWorkflow.advanceChapter` rewriteRequired branch -> `generateCandidate(repairInstructions)` -> `ChapterGenerator.ghostwriteOneChapter`. `GhostwriteJobs.beginCandidateTurn` preserves the private pending candidate while changing the stage. The generator has that candidate available, but the new prompt contains only the original plan and repair instructions. The new TurnRequest sets no history; Runtime history defaults to empty. ContextAssembler reads only public book nodes/plan, and workspace tools explicitly reject hidden `.amber` paths. The source candidate therefore is absent from the writing turn. Review having seen it does not transfer its body to the separate writing turn.

Accepted impact: the host requests preservation of unaffected prose without supplying the prose, so a targeted rewrite necessarily relies on regeneration. Repair must include the immediately preceding candidate's title and full body with the current repair instructions. Preserve owner, candidate identity, attempt limit, plan and CAS. Regression should capture the actual second and third writing request through the existing fake kernel, asserting the source advances with each candidate revision.

### ND2 accepted, P2

Independently traced generator finalText -> `splitChapterTitle` -> both version-bound `recordCandidate` and legacy `commitGhostwrittenChapter`. Current condition `trimmed.startsWith("第") && trimmed.contains("章")` matches the ordinary complete opening paragraph `第一次看见那枚徽章时，他并不知道它的来历。`. It removes that paragraph from body and stores it as title. Nothing downstream retains the omitted paragraph as prose, and later review receives the already damaged candidate. The function's declared contract is a chapter heading `第N章`, not arbitrary 第/章 placement.

Narrow repair: recognize a numbered chapter heading (existing Chinese/Arabic numeral forms, optional spaces) while keeping the existing Markdown heading path and fallback title. Do not add speculative title inference. Regression must exercise the actual generator with a substantial no-heading chapter and assert the exact opening remains in candidate body, alongside valid numbered-heading controls.

## UI candidates

### U1 accepted, P2

Independently traced `ChatInput.prepareSend -> submissionOwner.submit` and `ChatPageComposerHost.UserMessageEditConfirmBar -> ChatPageInputActions.confirmEdit -> submissionOwner.confirmEdit`. Both use the same retained owner. The submit path marks inFlight before its IO attachment validation suspends, but confirmEdit never reads inFlight. The edit confirmation bar is disabled only by generation loading; validation has not started generation. For text or previously saved historical image parts, consuming the editor through Save Only does not invalidate the first submission's captured-attachment check, deliberately allowing that original snapshot to retain its editing identity. `ChatVM.handleMessageEdit` also sees no generation during Save Only. The first action can therefore later request save-and-regenerate after Save Only was already accepted.

Repair at the shared owner's admission check. Keep the intended captured-target and historical-attachment ownership semantics. Existing deferred-validation owner fixture directly reproduces the duplicate admission; assert one accepted dispatch, rather than weakening attachment rules or adding a second busy state.

### U2 accepted, P1, regression required before repair acceptance

Independently traced recap rename -> `ChatVM.updateTitle` capturing `conversation.value.copy(title)` -> `ChatService.saveConversation` -> `ConversationStore.saveConversationInternal`. The exists query suspends before the captured snapshot replaces current live state and durable message nodes. The fully loaded path copies that old snapshot directly; the partially loaded merge also replaces matching nodes with old ones. Repository updateConversation deletes and reinserts nodes, so this is a real data overwrite path, not merely a stale title label.

Important schedule correction: `SyncRestoreWriteGate.withCurrentWriterOrCancel` DOES serialize durable writes with writeMutex. It does not serialize live memory projection. A permanent-loss interleaving remains reachable: rename suspends in exists; the final generation tail is projected into live state; onRunFinished captures the fresh tail, projects its finishReasoning snapshot and then waits to checkpoint behind rename; rename resumes and replaces live state with the older full snapshot; the queued forced checkpoint may briefly persist the fresh tail, but `persistFinalWindow` later reads the now-old live state and persists that value again. `checkFilesDelete` can also remove new attachment references when rename compares against the newer live snapshot. A controlled fixture should establish this concrete final-tail survival invariant before implementing the fix.

Narrow repair: route rename through a title/metadata-only ConversationStore/ChatService operation under existing restore ownership, reuse repository.updateConversationMetadata, and rebase live title changes on current live state rather than historical nodes. Keep real edit/branch save semantics unchanged. Regression must preserve both live and durable final text plus the new title, and should cover a partially loaded conversation if the new owner supports it.

### U3 unaccepted / conditional

Camera/crop pending output identifiers use plain remember, but manifest handles orientation/screenSize and no real successful result-redelivery after process death/host recreation has been demonstrated. No lifecycle fix is authorized from this static suspicion alone; leave it in the conditional inventory and do not add state persistence speculatively.
