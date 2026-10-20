package app.amber.feature.ui.pages.chat

import app.amber.ai.ui.UIMessage
import app.amber.ai.ui.UIMessagePart
import app.amber.core.model.Conversation
import app.amber.core.model.toMessageNode
import app.amber.feature.ui.components.message.ChatMessageVirtualItem
import app.amber.feature.ui.components.richtext.MarkdownParseResult
import app.amber.feature.ui.components.richtext.MarkdownPreparationOutcome
import app.amber.feature.ui.components.richtext.parseMarkdownUncached
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

    // ── Bounded preparation hand-off (cache-miss retry loop regression) ────────────

    @Test fun uncacheableTextNeverQueuesParsesOrLoops() {
        val gateway = FakeMarkdownPreparation(cacheable = { false })
        val cache = ChatVirtualItemCache(
            parseMissingMarkdownSynchronously = false,
            markdownPreparation = gateway,
        )
        val node = longAssistant("uncacheable")
        val first = cache.getOrBuild(node, emptyList(), false, false, false)
        assertNull("Uncacheable text keeps the non-virtualized raw fallback", first)
        assertEquals(0, cache.preparationRequests)
        assertEquals(0, cache.pendingMarkdownCount)
        repeat(5) { cache.getOrBuild(node, emptyList(), false, false, false) }
        assertEquals(0, cache.preparationRequests)
        assertTrue("Oversize text must not be parsed at all", gateway.prepareCalls.isEmpty())
    }

    @Test fun rejectedPreparationStopsAtBoundedAttempts() = runBlocking {
        val gateway = FakeMarkdownPreparation()
        val node = longAssistant("rejected")
        val content = node.currentMessage.parts.filterIsInstance<UIMessagePart.Text>().single().text
        gateway.rejections += content
        val cache = ChatVirtualItemCache(
            parseMissingMarkdownSynchronously = false,
            markdownPreparation = gateway,
        )
        repeat(8) {
            cache.getOrBuild(node, emptyList(), false, false, false)
            cache.preparePendingMarkdown()
        }
        assertEquals("Uncacheable result must not be retried forever", 1, gateway.prepareCalls.size)
        assertEquals(0, cache.pendingMarkdownCount)
        val requests = cache.preparationRequests
        repeat(4) {
            cache.getOrBuild(node, emptyList(), false, false, false)
            cache.preparePendingMarkdown()
        }
        assertEquals(requests, cache.preparationRequests)
        assertEquals(0, cache.pendingMarkdownCount)
    }

    @Test fun failureDoesNotBlockOtherPreparationsAndRetriesAreBounded() = runBlocking {
        val gateway = FakeMarkdownPreparation()
        val failing = longAssistant("failing")
        val healthy = longAssistant("healthy")
        val failingText = failing.currentMessage.parts.filterIsInstance<UIMessagePart.Text>().single().text
        gateway.failures += failingText
        val cache = ChatVirtualItemCache(
            parseMissingMarkdownSynchronously = false,
            markdownPreparation = gateway,
        )
        repeat(8) {
            cache.getOrBuild(failing, emptyList(), false, false, false)
            cache.getOrBuild(healthy, emptyList(), false, false, false)
            cache.preparePendingMarkdown()
        }
        assertEquals(3, gateway.prepareCalls.count { it == failingText })
        assertEquals(1, gateway.prepareCalls.count { it != failingText })
        assertNotNull(gateway.cached(healthy.currentMessage.parts.filterIsInstance<UIMessagePart.Text>().single().text))
        assertEquals(0, cache.pendingMarkdownCount)
    }

    @Test fun capacityEvictionCompletesWithBoundedWork() = runBlocking {
        val gateway = FakeMarkdownPreparation(cacheCapacity = 1)
        val first = longAssistant("first")
        val second = longAssistant("second")
        val cache = ChatVirtualItemCache(
            parseMissingMarkdownSynchronously = false,
            markdownPreparation = gateway,
        )
        repeat(10) { round ->
            val node = if (round % 2 == 0) first else second
            cache.getOrBuild(node, emptyList(), false, false, false)
            cache.preparePendingMarkdown()
        }
        val perText = gateway.prepareCalls.groupingBy { it }.eachCount()
        assertTrue("Each text must stop after its retry budget: $perText", perText.values.all { it <= 3 })
        assertEquals(0, cache.pendingMarkdownCount)
        val settledRequests = cache.preparationRequests
        repeat(5) { round ->
            val node = if (round % 2 == 0) first else second
            cache.getOrBuild(node, emptyList(), false, false, false)
            cache.preparePendingMarkdown()
        }
        assertEquals(settledRequests, cache.preparationRequests)
    }

    @Test fun changedTextIsPreparedAgain() = runBlocking {
        val gateway = FakeMarkdownPreparation()
        val cache = ChatVirtualItemCache(
            parseMissingMarkdownSynchronously = false,
            markdownPreparation = gateway,
        )
        val original = longAssistant("original")
        cache.getOrBuild(original, emptyList(), false, false, false)
        cache.preparePendingMarkdown()
        assertTrue(cache.prepareOrFail(original))

        val edited = original.copy(messages = listOf(
            original.currentMessage.copy(parts = listOf(UIMessagePart.Text(
                (0 until 30).joinToString("\n\n") { "## edited $it\n\n编辑后的正文内容。".repeat(4) }
            )))
        ))
        cache.getOrBuild(edited, emptyList(), false, false, false)
        cache.preparePendingMarkdown()
        assertTrue(cache.prepareOrFail(edited))
        assertEquals(2, gateway.prepareCalls.size)
    }

    @Test fun duplicateTextAcrossNodesIsPreparedOnce() = runBlocking {
        val gateway = FakeMarkdownPreparation()
        val cache = ChatVirtualItemCache(
            parseMissingMarkdownSynchronously = false,
            markdownPreparation = gateway,
        )
        val content = (0 until 30).joinToString("\n\n") {
            "## shared $it\n\n两个节点复用的完整长文内容。".repeat(4)
        }
        val first = UIMessage.assistant(content).toMessageNode()
        val second = UIMessage.assistant(content).toMessageNode()
        cache.getOrBuild(first, emptyList(), false, false, false)
        cache.getOrBuild(second, emptyList(), false, false, false)
        assertEquals(1, cache.preparationRequests)
        cache.preparePendingMarkdown()
        assertEquals(1, gateway.prepareCalls.size)
        assertTrue(cache.prepareOrFail(first))
        assertTrue(cache.prepareOrFail(second))
    }

    private fun ChatVirtualItemCache.prepareOrFail(node: app.amber.core.model.MessageNode): Boolean {
        val items = getOrBuild(node, emptyList(), false, false, false)
        return items?.any { it is ChatMessageVirtualItem.MarkdownChild } == true
    }

    private fun longAssistant(seed: String, blocks: Int = 30) = UIMessage.assistant(
        (0 until blocks).joinToString("\n\n") {
            "## $seed $it\n\n" + "流式长文 $seed $it 保留 Unicode 👩‍💻 é。".repeat(4)
        }
    ).toMessageNode()

    /** Deterministic in-memory LRU stand-in for [app.amber.feature.ui.components.richtext.MarkdownParseCache]. */
    private class FakeMarkdownPreparation(
        private val cacheCapacity: Int = 8,
        private val cacheable: (String) -> Boolean = { true },
    ) : MarkdownPreparationGateway {
        private val lock = Any()
        private val cache = LinkedHashMap<String, MarkdownParseResult>()
        private val calls = ArrayList<String>()
        val prepareCalls: List<String> get() = synchronized(lock) { calls.toList() }
        var cachedCalls: Int = 0
            private set
        val failures = mutableSetOf<String>()
        val rejections = mutableSetOf<String>()

        override fun isCacheable(content: String): Boolean = cacheable(content)

        override fun cached(content: String): MarkdownParseResult? = synchronized(lock) {
            cachedCalls++
            cache[content]
        }

        override fun prepare(content: String): MarkdownPreparationOutcome = synchronized(lock) {
            calls += content
            if (content in failures) return MarkdownPreparationOutcome.FAILED
            if (content in rejections || !cacheable(content)) {
                return MarkdownPreparationOutcome.UNCACHEABLE
            }
            cache[content] = parseMarkdownUncached(content)
            while (cache.size > cacheCapacity) {
                cache.remove(cache.keys.first())
            }
            MarkdownPreparationOutcome.PREPARED
        }
    }
}
