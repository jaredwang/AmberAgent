package app.amber.feature.ui.pages.chat

import androidx.compose.runtime.Immutable
import app.amber.ai.core.MessageRole
import app.amber.ai.ui.UIMessageAnnotation
import app.amber.ai.ui.UIMessagePart
import app.amber.core.ai.generative.GenerativeWidgetParser
import app.amber.core.ai.generative.GenerativeWidgetSanitizeStatus
import app.amber.core.ai.generative.GenerativeWidgetSanitizer
import app.amber.core.ai.generative.GenerativeWidgetSegment
import app.amber.core.ai.generative.GuizangHtmlDeckValidator
import app.amber.core.model.Conversation
import app.amber.core.settings.GenerativeUiSetting
import kotlin.uuid.Uuid

enum class ShelfItemKind { IMAGE, FILE, MINI_APP, HTML, WEB }

@Immutable
data class ShelfItem(
    val kind: ShelfItemKind,
    val label: String,
    /** Image/file URL, web URL, widget key, or mini-app id. */
    val target: String,
    val nodeId: Uuid,
    /** Selected message id of [nodeId] when collected. */
    val messageId: String,
)

/**
 * Artifacts on the current branch, newest first: images and files from messages and
 * tool outputs, mini apps, inline HTML/generative widgets, and cited web pages.
 * Duplicate targets keep the newest.
 */
fun Conversation.collectShelfItems(
    generativeUiSetting: GenerativeUiSetting = GenerativeUiSetting(),
): List<ShelfItem> {
    val seen = HashSet<String>()
    val items = ArrayList<ShelfItem>()
    for (node in messageNodes.asReversed()) {
        val message = node.messages.getOrNull(node.selectIndex) ?: continue
        val messageId = message.id.toString()
        val found = ArrayList<ShelfItem>()
        var textPartIndex = 0
        fun visit(part: UIMessagePart, fromToolOutput: Boolean = false) {
            when (part) {
                is UIMessagePart.Image -> if (part.url.isNotBlank()) {
                    found += ShelfItem(ShelfItemKind.IMAGE, part.url.fileLabel(), part.url, node.id, messageId)
                }
                is UIMessagePart.Document -> if (part.url.isNotBlank()) {
                    found += ShelfItem(ShelfItemKind.FILE, part.fileName.ifBlank { part.url.fileLabel() }, part.url, node.id, messageId)
                }
                is UIMessagePart.MiniApp -> if (part.appId.isNotBlank()) {
                    found += ShelfItem(ShelfItemKind.MINI_APP, part.title, part.appId, node.id, messageId)
                }
                is UIMessagePart.Text -> if (!fromToolOutput && message.role == MessageRole.ASSISTANT && generativeUiSetting.enabled) {
                    val currentPartIdx = textPartIndex++
                    if (GenerativeWidgetParser.containsWidgetFence(part.text) ||
                        GenerativeWidgetParser.containsFullHtmlDeckPayload(part.text)
                    ) {
                        var widgetOrdinal = 0
                        GenerativeWidgetParser.parse(part.text, streaming = false)
                            .filterIsInstance<GenerativeWidgetSegment.Widget>()
                            .forEach { widget ->
                                val sanitized = GenerativeWidgetSanitizer.sanitize(widget.widgetCode, generativeUiSetting)
                                val openable = sanitized.status == GenerativeWidgetSanitizeStatus.READY &&
                                    (widget.renderer != GuizangHtmlDeckValidator.RENDERER || !widget.specJson.isNullOrBlank())
                                if (openable) {
                                    val label = widget.title?.takeIf { it.isNotBlank() }
                                        ?: when (widget.renderer) {
                                            GuizangHtmlDeckValidator.RENDERER, "slides" -> "HTML 演示"
                                            "vchart", "chart" -> "图表"
                                            else -> ""
                                        }
                                    val target = "html:$messageId:$currentPartIdx:${widgetOrdinal++}"
                                    found += ShelfItem(ShelfItemKind.HTML, label, target, node.id, messageId)
                                }
                            }
                    }
                }
                is UIMessagePart.Tool -> part.output.forEach { visit(it, fromToolOutput = true) }
                else -> Unit
            }
        }
        message.parts.forEach { visit(it, fromToolOutput = false) }
        message.annotations.filterIsInstance<UIMessageAnnotation.UrlCitation>().forEach { citation ->
            if (citation.url.isNotBlank()) {
                found += ShelfItem(ShelfItemKind.WEB, citation.title.ifBlank { citation.url }, citation.url, node.id, messageId)
            }
        }
        // Within one message keep reading order; across messages newest first.
        found.forEach { if (seen.add(it.target)) items += it }
    }
    return items
}

/** Last path segment; blank for inline data URLs so the UI falls back to the kind label. */
private fun String.fileLabel(): String =
    if (startsWith("data:")) "" else substringAfterLast('/').substringBefore('?')
