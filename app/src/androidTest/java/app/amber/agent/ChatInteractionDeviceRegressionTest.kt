package app.amber.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.amber.ai.provider.*
import app.amber.ai.ui.UIMessage
import app.amber.ai.ui.UIMessagePart
import app.amber.core.model.Conversation
import app.amber.core.model.MessageNode
import app.amber.core.repository.ConversationRepository
import app.amber.core.service.ChatService
import app.amber.core.settings.Settings
import app.amber.core.settings.prefs.SettingsAggregator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.koin.java.KoinJavaComponent.getKoin
import java.security.MessageDigest
import kotlin.uuid.Uuid

/** Real UI -> ChatService/CAS -> Room, with a scripted provider and isolated app storage. */
class ChatInteractionDeviceRegressionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun requireFixture() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("amberDeviceRegression") == "true")
        check(context.applicationContext is ChatDeviceFixtureApplication) { "Requires -PchatDeviceFixture=true" }
        Thread.sleep(1500) // Allow the host's one-shot foreground kick to finish before opening chat.
    }
    private fun open(id: Uuid): ActivityScenario<RouteActivity> {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "am start -W -n ${context.packageName}/androidx.activity.ComponentActivity",
        )).bufferedReader().use { it.readText() }
        return ActivityScenario.launch(Intent(context, RouteActivity::class.java).putExtra("conversationId", id.toString()))
    }
    private fun text(service: ChatService, id: Uuid) = service.getConversationFlow(id).value.currentMessages
        .lastOrNull { it.role == app.amber.ai.core.MessageRole.ASSISTANT }?.parts
        ?.filterIsInstance<UIMessagePart.Text>()?.joinToString("") { it.text }.orEmpty()
    private fun storedText(conversation: Conversation) = conversation.currentMessages.last().parts
        .filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }
    private fun sendStopButton(resource: Int) = context.getString(resource).let { description ->
        compose.onAllNodes(hasClickAction() and (hasContentDescription(description) or
            hasAnyDescendant(hasContentDescription(description))), useUnmergedTree = true).onFirst()
    }
    private fun openMenu(marker: String) {
        compose.waitUntil(30_000) {
            val matches = compose.onAllNodesWithText(marker, substring = true, useUnmergedTree = true)
            matches.fetchSemanticsNodes().indices.any { runCatching { matches[it].isDisplayed() }.getOrDefault(false) }
        }
        compose.onAllNodesWithText(marker, substring = true, useUnmergedTree = true).onFirst()
            .performTouchInput { longClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(context.getString(R.string.copy)).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun tap(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            check(instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
    }

    @Test(timeout = 180_000)
    fun cancelRegenerateSwitchHistoryAndCopy(): Unit = runBlocking {
        requireFixture()
        val koin = getKoin()
        val settings: SettingsAggregator = koin.get()
        withTimeout(15_000) { settings.settingsFlow.first { !it.init } }
        val model = Model(modelId = "device-script", displayName = "Isolated device script", type = ModelType.CHAT)
        val provider = ProviderSetting.OpenAI(name = "Isolated fixture only", models = listOf(model), apiKey = "fixture-only")
        val base = Settings()
        settings.update(base.copy(providers = listOf(provider), chatModelId = model.id,
            titleModelId = model.id, suggestionModelId = model.id, compressModelId = model.id,
            streamOutput = true, maxTokens = 8192, enableWebSearch = false,
            enabledMcpServerIds = emptySet(), presetMessages = emptyList(), regexes = emptyList(),
            systemPrompt = "Local device regression. Do not call tools.",
            agentRuntime = base.agentRuntime.copy(enableCoreMemory = false, enableShortTermMemory = false,
                enableLongTermMemory = false, enableRecentChatsReference = false,
                enableLiveStatusNotification = false, keepGenerationAliveInBackground = false)))
        context.getSharedPreferences("amber_agent.preferences", Context.MODE_PRIVATE).edit()
            .putString(LAUNCH_START_MODE_PREF, LaunchStartMode.HOME.name).apply()
        val repository: ConversationRepository = koin.get()
        val database: app.amber.agent.data.db.AppDatabase = koin.get()
        Log.i(TAG, "sandboxDatabase=${database.openHelper.readableDatabase.path}")
        val service: ChatService = koin.get()
        val gateway: FixtureTextGateway = koin.get()
        val id = Uuid.random()
        val historyId = Uuid.random()
        val historyTail = "DEVICE_HISTORY_TAIL_$historyId"
        val historyHead = "DEVICE_HISTORY_HEAD_$historyId"
        val history = buildList {
            repeat(60) { turn ->
                add(MessageNode.of(UIMessage.user(if (turn == 0) historyHead else "Fixture history question $turn")))
                add(MessageNode.of(UIMessage.assistant(if (turn == 59) historyTail else "Fixture history answer $turn")))
            }
        }
        repository.insertConversation(Conversation.ofId(id).copy(title = "Device interaction fixture"))
        repository.insertConversation(Conversation.ofId(historyId, messages = history).copy(title = "Device history fixture"))
        var cancelPrefix = ""
        var finalReply = ""
        try {
            gateway.reply = "DEVICE_CANCEL_MARKER 中文 👩‍💻 é。\n\n" + "这是正在生成的可取消回答，保持全部已输出内容。\n\n".repeat(150)
            gateway.delayMs = 33
            open(id).use { scenario ->
                compose.waitUntil(30_000) { compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true).onFirst().performTextInput("Run isolated device stream")
                val send = sendStopButton(R.string.send)
                val stopPoint = send.fetchSemanticsNode().boundsInWindow.center
                var initialIme = 0
                scenario.onActivity { initialIme = it.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom }
                compose.mainClock.autoAdvance = false
                tap(stopPoint.x, stopPoint.y)
                // Compose's idle synchronization may wait until the stream drains.
                // Inject the real stop touch without waiting for a continuously updating UI.
                withTimeout(30_000) {
                    while (text(service, id).length < 300) {
                        compose.mainClock.advanceTimeBy(16)
                        delay(16)
                    }
                }
                compose.mainClock.advanceTimeBy(200)
                var currentIme = 0
                scenario.onActivity { currentIme = it.window.decorView.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom }
                val stopY = stopPoint.y + initialIme - currentIme
                Log.i(TAG, "before-stop chars=${text(service, id).length} active=${service.getGenerationJobStateFlow(id).value?.isActive} point=$stopPoint ime=$initialIme->$currentIme stopY=$stopY")
                tap(stopPoint.x, stopY)
                withTimeout(20_000) {
                    while (service.getGenerationJobStateFlow(id).value?.isActive == true) {
                        compose.mainClock.advanceTimeBy(16)
                        delay(16)
                    }
                }
                compose.mainClock.autoAdvance = true
                cancelPrefix = text(service, id)
                check(cancelPrefix.startsWith("DEVICE_CANCEL_MARKER") && cancelPrefix.length < gateway.reply.length) {
                    "Cancellation was not partial: chars=${cancelPrefix.length} expectedMax=${gateway.reply.length}"
                }
                withTimeout(15_000) {
                    while (storedText(requireNotNull(repository.getConversationById(id))) != cancelPrefix) delay(100)
                }
                Log.i(TAG, "cancel passed chars=${cancelPrefix.length} durable=true")
                finalReply = "# Device regenerated answer\n\n" +
                    (0 until 40).joinToString("\n\n") {
                        "**完整答案 $it** preserves Unicode 👩‍💻 é and copy text. 这是一段完整的中文说明，用于真实长消息交互验证。"
                    } + "\n\n```kotlin\nfun verified() = 42\n```\n\nDEVICE_FINAL_COPY_MARKER 最终文本完整。"
                gateway.reply = finalReply
                gateway.delayMs = 12
                val calls = gateway.calls.get()
                openMenu("DEVICE_CANCEL_MARKER")
                compose.onNodeWithText(context.getString(R.string.regenerate)).performClick()
                compose.waitUntil(30_000) { gateway.calls.get() > calls && text(service, id) == finalReply &&
                    service.getGenerationJobStateFlow(id).value?.isActive != true }
                withTimeout(15_000) {
                    while (storedText(requireNotNull(repository.getConversationById(id))) != finalReply) delay(100)
                }
                Log.i(TAG, "regenerate passed chars=${finalReply.length} durable=true")
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                var prior: ClipData? = null
                instrumentation.runOnMainSync { prior = clipboard.primaryClip }
                try {
                    openMenu("DEVICE_FINAL_COPY_MARKER")
                    compose.onNodeWithText(context.getString(R.string.select_and_copy)).performClick()
                    compose.onNodeWithText(context.getString(R.string.copy_all)).performClick()
                    var copied = ""
                    instrumentation.runOnMainSync { copied = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context).toString() }
                    check(copied == finalReply) { "Copy changed complete Markdown/Unicode source" }
                    Log.i(TAG, "select-copy passed sha256=${sha(finalReply)}")
                } finally {
                    instrumentation.runOnMainSync {
                        val previous = prior
                        if (previous != null) clipboard.setPrimaryClip(previous) else clipboard.clearPrimaryClip()
                    }
                }
            }
            open(historyId).use {
                compose.waitUntil(30_000) { compose.onAllNodesWithText(historyTail, substring = true, useUnmergedTree = true)
                    .fetchSemanticsNodes().any { it.boundsInRoot.height > 0 } }
                check(service.getTimelineLoadStateFlow(historyId).value.loadedNodeCount == 80)
                compose.onAllNodes(hasScrollAction(), useUnmergedTree = true).onFirst().performScrollToIndex(82)
                compose.waitUntil(45_000) { service.getTimelineLoadStateFlow(historyId).value.loadedNodeCount == 120 }
                check(service.getConversationFlow(historyId).value.messageNodes.first().currentMessage.parts
                    .filterIsInstance<UIMessagePart.Text>().single().text == historyHead)
                Log.i(TAG, "switch-history passed initial=80 loaded=120 headVerified=true")
            }
            open(id).use {
                compose.waitUntil(30_000) { compose.onAllNodesWithText("DEVICE_FINAL_COPY_MARKER", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNodes().any { it.boundsInRoot.height > 0 } }
                check(text(service, id) == finalReply)
                Log.i(TAG, "switch-back passed chars=${finalReply.length}")
            }
            context.getSharedPreferences(PROOF_PREF, Context.MODE_PRIVATE).edit()
                .putString("id", id.toString()).putString("text", finalReply).commit()
            Log.i(TAG, "first-process complete conversation=$id sha256=${sha(finalReply)}")
        } finally {
            service.stopGeneration(id)
            repository.getConversationById(historyId)?.let { service.deleteConversation(it) }
        }
    }

    @Test(timeout = 90_000)
    fun reopenDurableReplyInFreshProcess(): Unit = runBlocking {
        requireFixture()
        val proof = context.getSharedPreferences(PROOF_PREF, Context.MODE_PRIVATE)
        val id = Uuid.parse(requireNotNull(proof.getString("id", null)))
        val expected = requireNotNull(proof.getString("text", null))
        val repository: ConversationRepository = getKoin().get()
        val stored = requireNotNull(repository.getConversationById(id))
        check(storedText(stored) == expected)
        open(id).use {
            compose.waitUntil(30_000) { compose.onAllNodesWithText("DEVICE_FINAL_COPY_MARKER", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().any { it.boundsInRoot.height > 0 } }
            Log.i(TAG, "fresh-process reopen passed conversation=$id chars=${expected.length} sha256=${sha(expected)}")
        }
        getKoin().get<ChatService>().deleteConversation(stored)
        proof.edit().clear().commit()
    }
    private fun sha(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
    companion object { private const val TAG = "AmberDeviceRegression"; private const val PROOF_PREF = "chat-device-proof" }
}
