package app.amber.feature.ui.pages.chat

import app.amber.ai.ui.UIMessage
import app.amber.ai.ui.UIMessagePart
import app.amber.core.model.Conversation
import app.amber.core.model.toMessageNode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid

class ChatStreamingPerformanceTest {
    @Test fun hundredsOfTailUpdatesShareHistoryStorageAndKeepJumpIndexes() {
        val history = (0 until 80).map { UIMessage.assistant("历史 $it").toMessageNode() }
        var conversation = Conversation(assistantId = Uuid.random(),
            messageNodes = history + UIMessage.assistant("tail").toMessageNode())
        val reuse = ChatTimelinePlanReuseCache()
        val cache = ChatVirtualItemCache()
        val post = PostSendTimelineState(null, null, null, null, false)
        fun build() = reuse.build(conversation, emptyList(), false, true, true, 1, post, cache)
        build()
        var historyStorage: List<ChatTimelineEntry>? = null
        var indexStorage: List<Int?>? = null
        repeat(250) { frame ->
            val tail = conversation.messageNodes.last()
            conversation = conversation.copy(messageNodes = history + tail.copy(messages = listOf(
                tail.currentMessage.copy(parts = listOf(UIMessagePart.Text("tail $frame"))))))
            val plan = build()
            val joined = plan.entries as JoinedTimelineList
            val indexes = plan.lazyItemMessageIndexes as JoinedTimelineList
            if (historyStorage == null) { historyStorage = joined.history; indexStorage = indexes.history }
            assertSame(historyStorage, joined.history)
            assertSame(indexStorage, indexes.history)
            val full = buildChatTimelinePlan(conversation, emptyList(), false, true, true, 1, post, cache)
            assertEquals(full, plan)
            history.indices.forEach { assertEquals(full.lazyIndexForMessage(it), plan.lazyIndexForMessage(it)) }
        }
    }

    @Test fun protectionWaitsForDrainAndOffscreenButPreservesLoadingNode() {
        val cache = ChatVirtualItemCache()
        val id = Uuid.random()
        cache.observeStreamingAssistant(id)
        cache.releaseCompletedOffscreen(emptySet(), id)
        assertTrue(id in cache.protectedStreamingNodeIds())
        cache.noteVisualActivity(id, true)
        cache.releaseCompletedOffscreen(emptySet(), null)
        assertTrue(id in cache.protectedStreamingNodeIds())
        cache.noteVisualActivity(id, false)
        cache.releaseCompletedOffscreen(setOf(id), null)
        assertTrue(id in cache.protectedStreamingNodeIds())
        cache.releaseCompletedOffscreen(emptySet(), null)
        assertFalse(id in cache.protectedStreamingNodeIds())
    }

    @Test fun newGenerationsCannotRekeyAStillVisibleOlderStream() {
        val cache = ChatVirtualItemCache()
        val ids = List(4) { Uuid.random() }
        ids.forEach(cache::observeStreamingAssistant)
        cache.releaseCompletedOffscreen(ids.toSet(), ids.last())
        assertEquals(ids.toSet(), cache.protectedStreamingNodeIds())
        cache.releaseCompletedOffscreen(setOf(ids.first()), ids.last())
        assertEquals(setOf(ids.first(), ids.last()), cache.protectedStreamingNodeIds())
    }

    @Test fun missingHistoryParseIsPreparedAsynchronouslyAndThenVirtualized() = runBlocking {
        val cache = ChatVirtualItemCache(parseMissingMarkdownSynchronously = false)
        val node = UIMessage.assistant((0 until 35).joinToString("\n\n") {
            "## $it ${Uuid.random()}\n\n这段内容仅用于验证异步准备历史长消息。".repeat(3)
        }).toMessageNode()
        assertNull(cache.getOrBuild(node, emptyList(), false, false, false))
        assertTrue(cache.preparationRequests > 0)
        cache.preparePendingMarkdown()
        assertNotNull(cache.getOrBuild(node, emptyList(), false, false, false))
        assertEquals(1, cache.preparedRevision)
    }

    @Test fun onlyIdlePinnedViewportAnimatesTailHeight() {
        assertTrue(shouldAnimateStreamingTail(0, 10, 24, false))
        assertFalse(shouldAnimateStreamingTail(0, 10, 24, true))
        assertFalse(shouldAnimateStreamingTail(0, 25, 24, false))
        assertFalse(shouldAnimateStreamingTail(1, 0, 24, false))
    }
}
