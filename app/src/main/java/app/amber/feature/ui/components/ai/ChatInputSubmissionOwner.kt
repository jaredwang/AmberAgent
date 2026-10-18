package app.amber.feature.ui.components.ai

import app.amber.ai.ui.UIMessagePart
import app.amber.ai.ui.isEmptyInputMessage
import app.amber.feature.ui.hooks.ChatInputState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.uuid.Uuid

/** Owns one composer submission while image validation can suspend. Called on the UI dispatcher. */
internal class ChatInputSubmissionOwner(private val input: ChatInputState) {
    private var inFlight = false

    fun confirmEdit(
        regenerate: Boolean,
        send: (parts: List<UIMessagePart>, messageId: Uuid, regenerate: Boolean) -> Boolean,
    ): Boolean {
        val parts = input.getContents()
        if (parts.isEmptyInputMessage()) return false
        val accepted = send(parts, input.editingMessage!!, regenerate)
        if (accepted) input.clearInput()
        return accepted
    }

    fun sendWidgetAction(
        parts: List<UIMessagePart>,
        send: (List<UIMessagePart>) -> Boolean,
    ): Boolean {
        input.editingMessage = null
        return send(parts)
    }

    suspend fun submit(
        conversationId: String,
        validate: suspend (List<UIMessagePart>) -> Boolean,
        send: (parts: List<UIMessagePart>, editingMessageId: Uuid?) -> Boolean,
    ): Boolean {
        if (inFlight || !input.isCurrentConversation(conversationId)) return false
        currentCoroutineContext().ensureActive()
        val parts = input.getContents()
        val editingMessage = input.editingMessage
        inFlight = true
        return try {
            if (!validate(parts)) return false
            currentCoroutineContext().ensureActive()
            if (!input.isCurrentConversation(conversationId)) return false

            val accepted = send(parts, editingMessage)
            // A later draft or attachment import belongs to the user's next submission.
            if (accepted && input.isCurrentConversation(conversationId) &&
                input.editingMessage == editingMessage && input.getContents() == parts &&
                !input.hasUnresolvedAttachmentImports()
            ) {
                input.clearInput()
            }
            accepted
        } finally {
            inFlight = false
        }
    }
}
