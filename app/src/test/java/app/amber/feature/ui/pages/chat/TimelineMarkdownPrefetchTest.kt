package app.amber.feature.ui.pages.chat

import app.amber.agent.data.model.nativebridge.RegexNativeSwitch
import app.amber.ai.core.MessageRole
import app.amber.ai.ui.UIMessage
import app.amber.ai.ui.UIMessagePart
import app.amber.core.model.AssistantAffectScope
import app.amber.core.model.AssistantRegex
import app.amber.core.model.MessageNode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.uuid.Uuid

class TimelineMarkdownPrefetchTest {
    private lateinit var previousConfig: RegexNativeSwitch.Config

    @Before
    fun disableNativeLibraryForHostTests() {
        previousConfig = RegexNativeSwitch.config
        RegexNativeSwitch.config = RegexNativeSwitch.DisabledConfig
    }

    @After
    fun restoreNativeConfig() {
        RegexNativeSwitch.config = previousConfig
    }

    @Test
    fun prefetchUsesAssistantVisualRegexAndStripsUserRouteTags() {
        val assistantRule = rule("SECRET", "VISIBLE", AssistantAffectScope.ASSISTANT)
        val userRule = rule("画", "绘", AssistantAffectScope.USER)
        val assistant = node(
            role = MessageRole.ASSISTANT,
            text = "before SECRET after",
        )
        val user = node(
            role = MessageRole.USER,
            text = "[ROUTE:svg]\n画一只猫",
        )
        assertEquals(
            listOf("before VISIBLE after"),
            assistant.prefetchMarkdownBodies(listOf(assistantRule, userRule)),
        )
        assertEquals(
            listOf("绘一只猫"),
            user.prefetchMarkdownBodies(listOf(assistantRule, userRule)),
        )
        assertFalse(
            assistant.prefetchMarkdownBodies(listOf(assistantRule)).contains("before SECRET after"),
        )
        assertFalse(
            user.prefetchMarkdownBodies(listOf(userRule)).any { it.contains("[ROUTE:") },
        )
    }

    @Test
    fun loadingEarlierHistoryDoesNotSkipNewNodesAtPreviousIndexes() {
        val newer = node(text = "newer body")
        val older = node(text = "older body")
        val requested = HashSet<MarkdownPrefetchKey>()
        val first = collectMarkdownPrefetchBodies(
            messageNodes = listOf(newer),
            regexes = emptyList(),
            messageIndexRange = 0..0,
            requested = requested,
        )
        assertEquals(listOf("newer body"), first)
        val second = collectMarkdownPrefetchBodies(
            messageNodes = listOf(older, newer),
            regexes = emptyList(),
            messageIndexRange = 0..1,
            requested = requested,
        )
        assertEquals(listOf("older body"), second)
        assertTrue(requested.any { it.nodeId == older.id })
        assertEquals(2, requested.size)
    }

    @Test
    fun regenerateWithSameNodeIdRewarmChangedContent() {
        val id = Uuid.random()
        val requested = HashSet<MarkdownPrefetchKey>()
        val first = collectMarkdownPrefetchBodies(
            messageNodes = listOf(node(id = id, text = "version one")),
            regexes = emptyList(),
            messageIndexRange = 0..0,
            requested = requested,
        )
        assertEquals(listOf("version one"), first)
        val second = collectMarkdownPrefetchBodies(
            messageNodes = listOf(node(id = id, text = "version two")),
            regexes = emptyList(),
            messageIndexRange = 0..0,
            requested = requested,
        )
        assertEquals(listOf("version two"), second)
        assertEquals(2, requested.size)
        assertTrue(requested.all { it.nodeId == id })
    }

    private fun node(
        vararg parts: UIMessagePart = emptyArray(),
        role: MessageRole = MessageRole.ASSISTANT,
        text: String? = null,
        id: Uuid = Uuid.random(),
    ) = MessageNode(
        id = id,
        messages = listOf(
            UIMessage(
                role = role,
                parts = if (text != null) listOf(UIMessagePart.Text(text)) else parts.toList(),
            ),
        ),
    )

    private fun rule(
        find: String,
        replacement: String,
        scope: AssistantAffectScope,
    ) = AssistantRegex(
        id = Uuid.random(),
        findRegex = find,
        replaceString = replacement,
        affectingScope = setOf(scope),
        visualOnly = true,
    )
}
