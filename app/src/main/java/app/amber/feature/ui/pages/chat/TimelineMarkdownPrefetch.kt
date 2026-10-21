package app.amber.feature.ui.pages.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import app.amber.ai.ui.UIMessagePart
import app.amber.core.model.MessageNode
import app.amber.feature.ui.components.richtext.cachedMarkdownParseResult
import app.amber.feature.ui.components.richtext.prepareMarkdownContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * Parse nearby Markdown before the item enters the viewport so history-cold
 * does not show the transparent pending placeholder for multiple frames.
 *
 * [ChatVirtualItemCache] already queues preparation, but FIFO can leave the
 * visible window waiting. This pass walks the current visible range plus
 * [leadItems] and warms [app.amber.feature.ui.components.richtext.MarkdownParseCache]
 * on Default.
 */
@Composable
internal fun TimelineMarkdownPrefetch(
    listState: LazyListState,
    plan: ChatTimelinePlan,
    messageNodes: List<MessageNode>,
    leadItems: Int = 8,
) {
    val requested = remember { HashSet<Int>() }
    LaunchedEffect(listState, plan, messageNodes) {
        snapshotFlow {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()?.index
            val last = info.visibleItemsInfo.lastOrNull()?.index
            if (first == null || last == null) -1 to -1 else first to last
        }
            .distinctUntilChanged()
            .collect { (firstVisible, lastVisible) ->
                if (messageNodes.isEmpty() || firstVisible < 0) return@collect
                val lastIndex = plan.entries.lastIndex
                val from = (firstVisible - leadItems).coerceAtLeast(0)
                val to = (lastVisible + leadItems).coerceAtMost(lastIndex)
                if (to < from) return@collect
                val bodies = buildList {
                    for (lazyIndex in from..to) {
                        val messageIndex = plan.messageIndexAt(lazyIndex) ?: continue
                        if (!requested.add(messageIndex)) continue
                        val node = messageNodes.getOrNull(messageIndex) ?: continue
                        addAll(node.prefetchMarkdownBodies())
                    }
                }
                if (bodies.isEmpty()) return@collect
                withContext(Dispatchers.Default) {
                    bodies.forEach { body ->
                        if (cachedMarkdownParseResult(body) == null) {
                            prepareMarkdownContent(body)
                        }
                    }
                }
            }
    }
}

internal fun MessageNode.prefetchMarkdownBodies(): List<String> {
    val message = messages.getOrNull(selectIndex) ?: return emptyList()
    return message.parts.mapNotNull { part ->
        (part as? UIMessagePart.Text)?.text?.takeIf { it.isNotBlank() }
    }
}
