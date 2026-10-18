package app.amber.feature.tools

import android.app.Application
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import app.amber.ai.ui.UIMessagePart
import app.amber.core.automation.AccessibilityActive
import app.amber.core.automation.AccessibilityController
import app.amber.core.automation.AccessibilityTextMatch
import app.amber.core.automation.AmberAccessibilityService
import app.amber.core.automation.ScreenCaptureManager
import app.amber.core.automation.ScreenNode
import app.amber.core.automation.ScreenSnapshot
import app.amber.feature.runtime.AgentToolActivityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ScreenAutomationAcceptanceTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        AccessibilityActive.activeController = null
        Dispatchers.resetMain()
    }

    @Test
    fun emptyEditableHintDoesNotCountAsAnEnteredValue() = runTest {
        // Use the real Android-node projection: its display label includes the hint.
        val projected = projectedEditable(text = "", hint = "Search")
        assertEquals("Search", projected.nodes.single().label)
        val result = clickWithExpectation(FakeController(projected), """{"kind":"value_equals","value":"Search"}""")
        assertFalse(result.getValue("verification").jsonObject.getValue("met").jsonPrimitive.boolean)
    }

    @Test
    fun enteredValueIsComparedWithoutDisplayLabelTruncation() = runTest {
        val value = "x".repeat(240)
        val projected = projectedEditable(text = value)
        assertTrue(projected.nodes.single().label.length < value.length)
        assertEquals(value, projected.nodes.single().editableValue)
        val result = clickWithExpectation(FakeController(projected), """{"kind":"value_equals","value":"$value"}""")
        assertTrue(result.getValue("verification").jsonObject.getValue("met").jsonPrimitive.boolean)
    }

    @Test
    fun passwordTextNeverEntersValueAcceptance() = runTest {
        val projected = projectedEditable(text = "private text", password = true)
        assertNull(projected.nodes.single().editableValue)
        val result = clickWithExpectation(FakeController(projected), """{"kind":"value_equals","value":"private text"}""")
        assertFalse(result.getValue("verification").jsonObject.getValue("met").jsonPrimitive.boolean)
    }

    @Test
    fun hiddenLegacySearchMatchCannotSatisfyVisibleAcceptance() = runTest {
        val controller = FakeController(snapshot(listOf(node("root/0", "Current page"))))
        // Legacy text search includes cached hidden nodes; the acceptance observation does not.
        controller.searchMatches = listOf(textMatch("Saved"))
        val result = clickWithExpectation(controller, """{"kind":"visible","target":"Saved"}""")
        assertFalse(result.getValue("verification").jsonObject.getValue("met").jsonPrimitive.boolean)
    }

    @Test
    fun aSubstringOfAnotherLabelCannotSatisfyExactAcceptance() = runTest {
        val controller = FakeController(snapshot(listOf(node("root/0", "Not saved"))))
        controller.searchMatches = listOf(textMatch("Not saved"))
        val result = clickWithExpectation(controller, """{"kind":"enabled","target":"saved"}""")
        assertFalse(result.getValue("verification").jsonObject.getValue("met").jsonPrimitive.boolean)
    }

    @Test
    fun anAggregateParentLabelDoesNotDuplicateTheVisibleTarget() = runTest {
        val controller = FakeController(snapshot(listOf(
            node("root", "Saved").copy(hasOwnLabel = false),
            node("root/0", "Saved"),
        )))
        val result = clickWithExpectation(controller, """{"kind":"enabled","target":"Saved"}""")
        assertTrue(result.getValue("verification").jsonObject.getValue("met").jsonPrimitive.boolean)
    }

    @Test
    fun listCollectionContainsOnlyTheChosenContainerSubtree() = runTest {
        val controller = FakeController(snapshot(listOf(
            node("root", "Whole page"),
            node("root/0", "Target list", scrollable = true),
            node("root/0/0", "Target row"),
            node("root/1", "Unrelated sidebar"),
        )))
        AccessibilityActive.activeController = controller
        val result = executeTool("screen_collect_list", """{"container":"Target list","direction":"down","max_swipes":0}""")
        assertEquals(
            listOf("Target row"),
            result.getValue("items").jsonArray.map { it.jsonObject.getValue("label").jsonPrimitive.content },
        )
    }

    @Test
    fun listCollectionDoesNotCountSyntheticRowWrappersAsAdditionalItems() = runTest {
        val controller = FakeController(snapshot(listOf(
            node("root/0", "Target list", scrollable = true),
            node("root/0/0", "First row").copy(hasOwnLabel = false),
            node("root/0/0/0", "First row"),
            node("root/0/1", "Second row").copy(hasOwnLabel = false),
            node("root/0/1/0", "Second row"),
        )))
        AccessibilityActive.activeController = controller
        val result = executeTool(
            "screen_collect_list",
            """{"container":"Target list","direction":"down","max_swipes":0,"max_items":2}""",
        )
        assertEquals(
            listOf("First row", "Second row"),
            result.getValue("items").jsonArray.map { it.jsonObject.getValue("label").jsonPrimitive.content },
        )
    }

    private suspend fun clickWithExpectation(controller: FakeController, expectation: String): JsonObject {
        AccessibilityActive.activeController = controller
        return executeTool("screen_click", """{"x":1,"y":1,"expectation":$expectation}""")
    }

    private suspend fun executeTool(name: String, input: String): JsonObject {
        val tools = ScreenAutomationTools(context, ScreenCaptureManager(context), AgentToolActivityStore())
        val output = tools.getTools().single { it.name == name }.execute(Json.parseToJsonElement(input))
        return Json.parseToJsonElement((output.single() as UIMessagePart.Text).text).jsonObject
    }

    private fun snapshot(nodes: List<ScreenNode>) = ScreenSnapshot("snapshot", "test.target", 1, nodes)

    private fun node(ref: String, label: String, scrollable: Boolean = false) = ScreenNode(
        ref = ref, label = label, viewId = "", className = "View",
        left = 0, top = 0, right = 200, bottom = 200,
        clickable = false, editable = false, scrollable = scrollable, enabled = true,
    )

    private fun textMatch(text: String) = AccessibilityTextMatch(
        text = text, contentDescription = "", className = "View", viewId = null,
        bounds = Rect(0, 0, 200, 200), clickable = false, enabled = true,
    )

    private fun projectedEditable(text: String, hint: String = "", password: Boolean = false): ScreenSnapshot {
        val serviceController = Robolectric.buildService(AmberAccessibilityService::class.java).create()
        val root = AccessibilityNodeInfo.obtain().apply {
            packageName = "test.target"
            className = "android.widget.EditText"
            this.text = text
            hintText = hint
            isPassword = password
            isEditable = true
            isEnabled = true
            isVisibleToUser = true
            setBoundsInScreen(Rect(0, 0, 200, 80))
        }
        return try {
            val method = AmberAccessibilityService::class.java.getDeclaredMethod(
                "buildScreenSnapshot", AccessibilityNodeInfo::class.java,
                Int::class.javaPrimitiveType, String::class.java,
            ).apply { isAccessible = true }
            method.invoke(serviceController.get(), root, 1, "test.target") as ScreenSnapshot
        } finally {
            root.recycle()
            serviceController.destroy()
        }
    }

    private class FakeController(private val observation: ScreenSnapshot) : AccessibilityController {
        var searchMatches: List<AccessibilityTextMatch> = emptyList()
        override fun captureScreenSnapshot(): ScreenSnapshot = observation
        override fun findTextNodes(query: String, maxNodes: Int): List<AccessibilityTextMatch> =
            searchMatches.filter { it.text.contains(query, ignoreCase = true) }
        override suspend fun tap(x: Float, y: Float, durationMillis: Long): Boolean = true
        override suspend fun longPress(x: Float, y: Float, durationMillis: Long): Boolean = true
        override suspend fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMillis: Long): Boolean = true
        override fun setFocusedText(text: String): Boolean = true
        override fun dumpUiTree(maxNodes: Int): String = ""
        override fun back(): Boolean = true
        override fun home(): Boolean = true
    }
}
