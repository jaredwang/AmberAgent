# Phase 2 · Composer U1–U4

Owner scope: ChatInput.kt, ChatInputState.kt, ChatInputSubmissionOwner.kt, ChatInputAttachments.kt, ChatPage.kt and the actual setContents callers/tests needed by U2. Existing E1–E4 repairs and theme/layout WIP are preserved. Gradle execution is serialized by the root coordinator.

## RED preparation

- `ChatInputSubmissionOwnerTest`: added two Deferred-barrier attachment ownership regressions, edit-confirmation coverage for save/regenerate with IMPORTING/FAILED attachments, and a widget-action regression that preserves historical-edit ownership/file protection.
- `ChatInputStateTest`: added READY import replacement and late IMPORTING completion rejection regressions.
- Production test seam only: moved the original edit-confirmation mutation and widget-action edit-owner mutation into the existing submission owner without correcting their behavior. The real ChatPage callbacks call these methods. ChatPageContent creates the single owner and passes it to the only ChatInput caller, so later fixes share one in-flight guard.
- The first coordinator compile caught the seam being remembered in the wrapper instead of ChatPageContent. The remember was moved to the actual callback scope and passed explicitly to ChatInput before requesting the next RED run. This was a development wiring error, not product RED evidence.

Filters for the coordinator:

```
app.amber.feature.ui.components.ai.ChatInputSubmissionOwnerTest
app.amber.feature.ui.hooks.ChatInputStateTest
```

No Gradle result is claimed by this packet. Targeted diff whitespace checks passed at RED readiness. Production fixes wait for the coordinator's RED evidence.
