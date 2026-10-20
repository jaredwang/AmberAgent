package app.amber.feature.ui.pages.chat

import app.amber.core.model.Conversation
import app.amber.core.model.MessageNode
import app.amber.core.model.AssistantRegex
import app.amber.feature.ui.components.message.ChatMessageVirtualItem
import app.amber.feature.ui.components.message.buildChatMessageVirtualItems
import kotlin.uuid.Uuid
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import app.amber.feature.ui.components.richtext.cachedMarkdownParseResult
import app.amber.feature.ui.components.richtext.prewarmMarkdownContent
import app.amber.feature.ui.components.richtext.parseMarkdownContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CHAT_TIMELINE_PLAN_CACHE_MAX_ENTRIES = 2048
private const val CHAT_TIMELINE_PLAN_CACHE_MAX_MARKDOWN_CHARS = 1_200_000

private data class ChatTimelinePlanInputs(
    val assistant: List<AssistantRegex>,
    val showAssistantBubble: Boolean,
    val timelineLoading: Boolean,
    val hasHistoryLoadingItem: Boolean,
    val pendingMessageCount: Int,
    val postSendState: PostSendTimelineState,
    val protectedStreamingNodeIds: Set<Uuid>,
    val preparedRevision: Int,
)

/** entry 若新增依赖相邻节点或其它 composition 状态，必须同步加入 [ChatTimelinePlanInputs]。 */
internal class ChatTimelinePlanReuseCache {
    private var previousNodes: List<MessageNode>? = null
    private var previousInputs: ChatTimelinePlanInputs? = null
    private var previousPlan: ChatTimelinePlan? = null
    private var reusablePrefix: ChatTimelinePrefix? = null

    fun build(
        conversation: Conversation,
        assistant: List<AssistantRegex>?,
        showAssistantBubble: Boolean,
        timelineLoading: Boolean,
        hasHistoryLoadingItem: Boolean,
        pendingMessageCount: Int,
        postSendState: PostSendTimelineState,
        virtualItemCache: ChatVirtualItemCache,
        protectedStreamingNodeIds: Set<Uuid> = emptySet(),
    ): ChatTimelinePlan {
        val nodes = conversation.messageNodes
        val inputs = ChatTimelinePlanInputs(
            assistant = assistant.orEmpty().toList(),
            showAssistantBubble = showAssistantBubble,
            timelineLoading = timelineLoading,
            hasHistoryLoadingItem = hasHistoryLoadingItem,
            pendingMessageCount = pendingMessageCount,
            postSendState = postSendState,
            protectedStreamingNodeIds = protectedStreamingNodeIds.toSet(),
            preparedRevision = virtualItemCache.preparedRevision,
        )
        val priorNodes = previousNodes
        val priorPlan = previousPlan
        val prefixStart = if (
            priorNodes != null && priorPlan != null && previousInputs == inputs &&
            nodes.size == priorNodes.size && nodes.isNotEmpty() &&
            (0 until nodes.lastIndex).all { index -> nodes[index] === priorNodes[index] }
        ) {
            if (nodes.size == 1) {
                if (hasHistoryLoadingItem) priorPlan.entries.lastIndex else priorPlan.entries.size
            } else {
                priorPlan.lazyIndexForMessage(nodes.lastIndex - 1)
            }
        } else {
            null
        }
        val prefix = if (prefixStart != null && priorPlan != null) {
            reusablePrefix ?: priorPlan.historyPrefix(prefixStart)
        } else null
        val plan = buildChatTimelinePlan(
            conversation = conversation,
            assistant = assistant,
            showAssistantBubble = showAssistantBubble,
            timelineLoading = timelineLoading,
            hasHistoryLoadingItem = hasHistoryLoadingItem,
            pendingMessageCount = pendingMessageCount,
            postSendState = postSendState,
            virtualItemCache = virtualItemCache,
            protectedStreamingNodeIds = protectedStreamingNodeIds,
            reusedPrefix = prefix,
        )
        reusablePrefix = prefix
        previousNodes = nodes
        previousInputs = inputs
        previousPlan = plan
        return plan
    }
}

internal class ChatVirtualItemCache(
    private val maxEntries: Int = CHAT_TIMELINE_PLAN_CACHE_MAX_ENTRIES,
    private val maxMarkdownChars: Int = CHAT_TIMELINE_PLAN_CACHE_MAX_MARKDOWN_CHARS,
    private val parseMissingMarkdownSynchronously: Boolean = true,
) {
    private val entries = object : LinkedHashMap<ChatVirtualItemCacheKey, List<ChatMessageVirtualItem>?>(
        maxEntries.coerceAtLeast(1),
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<ChatVirtualItemCacheKey, List<ChatMessageVirtualItem>?>,
        ): Boolean = false
    }
    private var totalMarkdownChars: Int = 0
    var hits: Int = 0
        private set
    var misses: Int = 0
        private set

    private val pendingMarkdown = LinkedHashSet<String>()
    var preparationRequests by mutableIntStateOf(0)
        private set
    var preparedRevision by mutableIntStateOf(0)
        private set

    suspend fun preparePendingMarkdown() {
        val batch = pendingMarkdown.toList()
        if (batch.isEmpty()) return
        withContext(Dispatchers.Default) { batch.forEach(::prewarmMarkdownContent) }
        pendingMarkdown.removeAll(batch.toSet())
        // Results cached as "single item while missing" must be rebuilt as well.
        preparedRevision++
    }

    private val visualCounts = mutableStateMapOf<Uuid, Int>()
    var protectionRevision by mutableIntStateOf(0)
        private set

    fun noteVisualActivity(nodeId: Uuid, active: Boolean) {
        val count = (visualCounts[nodeId] ?: 0) + if (active) 1 else -1
        if (count > 0) visualCounts[nodeId] = count else visualCounts.remove(nodeId)
    }

    fun activeVisualNodeIds(): Set<Uuid> = visualCounts.keys.toSet()

    fun releaseCompletedOffscreen(visibleNodeIds: Set<Uuid>, loadingNodeId: Uuid?) {
        val changed = streamingAssistantNodeIds.removeAll { nodeId ->
            nodeId != loadingNodeId && nodeId !in visibleNodeIds && nodeId !in visualCounts
        }
        if (changed) protectionRevision++
    }

    private val streamingAssistantNodeIds = ArrayDeque<Uuid>()

    /**
     * Record only assistant nodes that were actually observed while loading.
     * A finished historical tail never enters this set, so opening an old
     * conversation does not force its Markdown into one expensive item.
     */
    fun observeStreamingAssistant(nodeId: Uuid) {
        if (nodeId in streamingAssistantNodeIds) return
        streamingAssistantNodeIds.addLast(nodeId)
    }

    fun protectedStreamingNodeIds(): Set<Uuid> = streamingAssistantNodeIds.toSet()

    fun getOrBuild(
        node: MessageNode,
        regexes: List<AssistantRegex>,
        showAssistantBubble: Boolean,
        loading: Boolean,
        lastMessage: Boolean,
        forceSingleItem: Boolean = false,
    ): List<ChatMessageVirtualItem>? {
        val key = ChatVirtualItemCacheKey.of(
            node = node,
            regexes = regexes,
            showAssistantBubble = showAssistantBubble,
            loading = loading,
            lastMessage = lastMessage,
            forceSingleItem = forceSingleItem,
        )
        if (entries.containsKey(key)) {
            hits++
            return entries[key]
        }
        misses++
        var awaitingMarkdown = false
        val value = buildChatMessageVirtualItems(
            node = node,
            assistant = regexes,
            showAssistantBubble = showAssistantBubble,
            loading = loading,
            lastMessage = lastMessage,
            forceSingleItem = forceSingleItem,
            parseMarkdown = { content ->
                if (parseMissingMarkdownSynchronously) parseMarkdownContent(content) else {
                    cachedMarkdownParseResult(content) ?: run {
                        awaitingMarkdown = true
                        if (pendingMarkdown.add(content)) preparationRequests++
                        null
                    }
                }
            },
        )
        if (awaitingMarkdown) return value
        val markdownChars = value.markdownCharCost()
        if (markdownChars > maxMarkdownChars) {
            return value
        }
        entries[key] = value
        totalMarkdownChars += markdownChars
        trimToBudget()
        return value
    }

    private fun trimToBudget() {
        while (
            entries.size > maxEntries ||
            totalMarkdownChars > maxMarkdownChars
        ) {
            val iterator = entries.entries.iterator()
            if (!iterator.hasNext()) break
            val eldest = iterator.next()
            totalMarkdownChars -= eldest.value.markdownCharCost()
            iterator.remove()
        }
    }
}

private fun List<ChatMessageVirtualItem>?.markdownCharCost(): Int {
    if (this == null) return 0
    var cost = 0
    val seen = HashSet<String>()
    for (item in this) {
        if (item is ChatMessageVirtualItem.MarkdownChild && seen.add(item.content)) {
            cost += item.content.length
        }
    }
    return cost
}

private data class ChatVirtualItemCacheKey(
    val nodeId: Uuid,
    val messageId: Uuid,
    val messageIdentity: Int,
    val partsIdentity: Int,
    val assistantSignature: Int,
    val showAssistantBubble: Boolean,
    val loading: Boolean,
    val lastMessage: Boolean,
    val forceSingleItem: Boolean,
) {
    companion object {
        fun of(
            node: MessageNode,
            regexes: List<AssistantRegex>,
            showAssistantBubble: Boolean,
            loading: Boolean,
            lastMessage: Boolean,
            forceSingleItem: Boolean,
        ): ChatVirtualItemCacheKey {
            val message = node.currentMessage
            return ChatVirtualItemCacheKey(
                nodeId = node.id,
                messageId = message.id,
                messageIdentity = System.identityHashCode(message),
                partsIdentity = System.identityHashCode(message.parts),
                assistantSignature = regexes.renderSignature(),
                showAssistantBubble = showAssistantBubble,
                loading = loading,
                lastMessage = lastMessage,
                forceSingleItem = forceSingleItem,
            )
        }
    }
}

private fun List<AssistantRegex>.renderSignature(): Int {
    var result = 1
    forEach { regex ->
        result = 31 * result + regex.id.hashCode()
        result = 31 * result + regex.enabled.hashCode()
        result = 31 * result + regex.findRegex.hashCode()
        result = 31 * result + regex.replaceString.hashCode()
        result = 31 * result + regex.affectingScope.hashCode()
        result = 31 * result + regex.visualOnly.hashCode()
    }
    return result
}
