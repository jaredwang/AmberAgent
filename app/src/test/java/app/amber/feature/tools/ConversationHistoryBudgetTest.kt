package app.amber.feature.tools

import app.amber.ai.core.MessageRole
import app.amber.ai.ui.UIMessage
import app.amber.ai.ui.UIMessagePart
import app.amber.core.model.AMBER_AGENT_ID
import app.amber.core.model.Conversation
import app.amber.core.model.MessageNode
import app.amber.feature.history.SessionAccessGrantStore
import app.amber.feature.runtime.DurableRuntimeTestBase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationHistoryBudgetTest : DurableRuntimeTestBase() {
    private val grants = SessionAccessGrantStore()

    private suspend fun read(
        name: String,
        texts: List<String>,
        maxChars: Int = 1_000,
        grantBudget: Int? = null,
    ): Pair<JsonObject, String?> {
        val repository = conversationRepository()
        val messages = texts.map { UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text(it))) }
        val conversation = Conversation(
            assistantId = AMBER_AGENT_ID,
            messageNodes = messages.map(MessageNode::of),
        )
        repository.insertConversation(conversation)
        val grant = grantBudget?.let {
            grants.create(listOf(conversation.id.toString()), it, "history review", "parent")
        }
        val input = buildJsonObject {
            put("session_id", conversation.id.toString())
            put("source_id", messages.first().id.toString())
            put("radius", 8)
            put("max_chars", maxChars)
            grant?.let { put("grant_id", it.grantId) }
        }
        val tool = ConversationHistoryTools(repository, grants).tools().single { it.name == name }
        val output = tool.execute(input).filterIsInstance<UIMessagePart.Text>().single().text
        return Json.parseToJsonElement(output).jsonObject to grant?.grantId
    }

    @Test
    fun expandedMessagesRespectTheRequestedBudgetAndReportTruncation() = runBlocking {
        val (output) = read("session_expand", listOf("source-content-".repeat(400)))
        val text = output.getValue("messages").jsonArray.joinToString("") {
            it.jsonObject.getValue("text").jsonPrimitive.content
        }
        assertEquals(1_000, text.length)
        assertTrue(text.startsWith("source-content-"))
        assertTrue(output.getValue("truncated").jsonPrimitive.boolean)
    }

    @Test
    fun expandedMessageBudgetIsSharedAcrossTheWholeReturnedWindow() = runBlocking {
        val (output) = read("session_expand", listOf("a".repeat(600), "b".repeat(600), "c".repeat(600)))
        val texts = output.getValue("messages").jsonArray.map {
            it.jsonObject.getValue("text").jsonPrimitive.content
        }
        assertEquals(listOf("a".repeat(600), "b".repeat(400)), texts)
        assertTrue(output.getValue("truncated").jsonPrimitive.boolean)
    }

    @Test
    fun expandedMessagesConsumeTheActualReturnedGrantBudget() = runBlocking {
        val (output, grantId) = read("session_expand", listOf("x".repeat(4_000)), grantBudget = 1_000)
        val returnedChars = output.getValue("messages").jsonArray.sumOf {
            it.jsonObject.getValue("text").jsonPrimitive.content.length
        }
        assertEquals(1_000, returnedChars)
        assertEquals(returnedChars, grants.get(grantId!!)!!.usedChars)
        assertTrue(grants.validate(grantId, grants.get(grantId)!!.sessionIds.single(), 1_000)
            is SessionAccessGrantStore.GrantValidation.Denied)
    }

    @Test
    fun readingALongFirstMessageReturnsItsPrefixAndReportsTruncation() = runBlocking {
        val (output, grantId) = read("session_read", listOf("meaningful-source-".repeat(400)), grantBudget = 1_000)
        val transcript = output.getValue("transcript").jsonPrimitive.content
        assertEquals(1_000, transcript.length)
        assertTrue(transcript.contains("meaningful-source-"))
        assertTrue(output.getValue("truncated").jsonPrimitive.boolean)
        assertEquals(transcript.length, grants.get(grantId!!)!!.usedChars)
    }
}
