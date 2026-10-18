package app.amber.feature.ui.pages.councilroom

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.amber.agent.Screen
import app.amber.ai.ui.UIMessagePart
import app.amber.feature.modelcouncil.COUNCIL_ROOM_USER_ID
import app.amber.feature.modelcouncil.CouncilMessage
import app.amber.feature.modelcouncil.CouncilRoom
import app.amber.feature.modelcouncil.CouncilRoomMode
import app.amber.feature.modelcouncil.CouncilRoomStatus
import app.amber.feature.ui.context.LocalNavController
import app.amber.feature.ui.context.Navigator
import app.amber.feature.ui.pages.chat.LocalChatTheme
import app.amber.feature.ui.pages.chat.toChatTheme
import app.amber.feature.ui.theme.AmberBase
import app.amber.feature.ui.theme.LocalAmberTokens
import app.amber.feature.ui.theme.buildAmberTokens
import java.io.File
import kotlin.uuid.Uuid
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w320dp-h900dp")
class CouncilChromeAndAttachmentLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun topBarSubtitleGlyphsFitAt320dpWithLargeText() {
        val id = Uuid.random()
        val room = CouncilRoom(
            id = id.toString(),
            conversationId = id,
            hostAssistantId = Uuid.random(),
            objective = "Local layout evidence",
            status = CouncilRoomStatus.FINALIZED,
            createdAtMs = 1L,
        )
        showLargeText {
            Column(Modifier.width(320.dp)) {
                CouncilRoomTopBar(
                    room = room,
                    onRestart = null,
                    modeControlsEnabled = false,
                    modeMenuOpen = false,
                    onToggleMode = {},
                )
            }
        }
        val subtitle = compose.onNodeWithText("Open discussion", useUnmergedTree = true)
        val node = subtitle.fetchSemanticsNode()
        val layout = subtitle.textLayout()
        recordMetrics("top-bar-subtitle", node, layout)

        assertEquals("Open discussion", layout.layoutInput.text.text)
        assertEquals(1, layout.lineCount)
        assertFalse("the mode label must remain complete", layout.isLineEllipsized(0))
        assertEquals(layout.layoutInput.text.length, layout.getLineEnd(0, visibleEnd = true))
        assertTrue("subtitle needs actual visible width", node.boundsInRoot.width > 0f)
        assertTrue("the subtitle line must fit its measured height",
            layout.getLineBottom(0) <= node.size.height + 1f)
        layout.layoutInput.text.text.indices.filterNot { layout.layoutInput.text[it].isWhitespace() }
            .forEach { index ->
                val glyph = glyphInRoot(node, layout, index)
                assertTrue("subtitle glyph $index must fit vertically: $glyph in ${node.boundsInRoot}",
                    glyph.top >= node.boundsInRoot.top - 1f &&
                        glyph.bottom <= node.boundsInRoot.bottom + 1f)
            }
    }

    @Test
    fun documentChipShowsFileNameAndExplicitTruncationAt320dpWithLargeText() {
        val fileName = "p4-local-evidence.txt"
        val label = "File · $fileName"
        val message = CouncilMessage(
            id = "attachment-layout",
            authorId = COUNCIL_ROOM_USER_ID,
            authorName = "You",
            role = "",
            round = 1,
            mode = CouncilRoomMode.EXPLORE,
            text = "",
            attachments = listOf(UIMessagePart.Document("file:///local/$fileName", fileName)),
            createdAtMs = 1L,
        )
        showLargeText {
            // The actual timeline has 16dp side padding; the production user bubble
            // owns its 82% width and internal padding inside this available row.
            Box(Modifier.width(288.dp)) {
                TimelineMessageRow(
                    msg = message,
                    modelLabel = "",
                    referenceAuthors = CouncilReferenceAuthors(null, null),
                    isTopic = true,
                )
            }
        }
        val chip = compose.onNodeWithText(label, useUnmergedTree = true)
        val node = chip.fetchSemanticsNode()
        val layout = chip.textLayout()
        recordMetrics("document-chip", node, layout)
        val filenameStart = label.indexOf(fileName)
        val visibleEnd = layout.getLineEnd(0, visibleEnd = true)

        assertEquals("attachment semantics retain the complete filename", label, layout.layoutInput.text.text)
        assertEquals("the chip remains a single line", 1, layout.lineCount)
        assertTrue("the chip must draw filename characters after the File prefix", visibleEnd >= filenameStart + 3)
        assertTrue("a truncated filename must visibly indicate ellipsis",
            visibleEnd == label.length || layout.isLineEllipsized(0))
        (filenameStart until filenameStart + 3).forEach { index ->
            val glyph = glyphInRoot(node, layout, index)
            assertTrue("filename glyph $index must have visible geometry", glyph.width > 0f && glyph.height > 0f)
            assertTrue("filename glyph $index must fit the actual chip: $glyph in ${node.boundsInRoot}",
                glyph.left >= node.boundsInRoot.left - 1f && glyph.right <= node.boundsInRoot.right + 1f &&
                    glyph.top >= node.boundsInRoot.top - 1f && glyph.bottom <= node.boundsInRoot.bottom + 1f)
        }
    }

    private fun showLargeText(content: @Composable () -> Unit) {
        val tokens = buildAmberTokens(AmberBase.LIGHT, Color(0xFFB8623A))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 1.5f),
                LocalAmberTokens provides tokens,
                LocalChatTheme provides tokens.toChatTheme(),
                LocalNavController provides Navigator(mutableListOf(Screen.SessionHome, Screen.SettingExperimentalModelCouncil)),
            ) {
                MaterialTheme { content() }
            }
        }
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        val action = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue("real Text layout must be available", action?.invoke(layouts) == true)
        return layouts.single()
    }

    private fun glyphInRoot(node: SemanticsNode, layout: TextLayoutResult, index: Int): Rect {
        val box = layout.getBoundingBox(index)
        val origin = node.positionInRoot
        return Rect(box.left + origin.x, box.top + origin.y, box.right + origin.x, box.bottom + origin.y)
    }

    private fun recordMetrics(name: String, node: SemanticsNode, layout: TextLayoutResult) {
        val metrics = buildJsonObject {
            put("name", name)
            put("fontScale", 1.5f)
            put("text", layout.layoutInput.text.text)
            put("nodeSize", node.size.toString())
            put("nodeBoundsInRoot", node.boundsInRoot.toString())
            put("nodePositionInRoot", node.positionInRoot.toString())
            put("layoutSize", layout.size.toString())
            put("paragraphWidth", layout.multiParagraph.width)
            put("paragraphHeight", layout.multiParagraph.height)
            put("textAlign", layout.layoutInput.style.textAlign.toString())
            put("softWrap", layout.layoutInput.softWrap)
            put("overflow", layout.layoutInput.overflow.toString())
            put("lineCount", layout.lineCount)
            put("lines", buildJsonArray {
                repeat(layout.lineCount) { line ->
                    add(buildJsonObject {
                        put("start", layout.getLineStart(line))
                        put("visibleEnd", layout.getLineEnd(line, visibleEnd = true))
                        put("top", layout.getLineTop(line))
                        put("bottom", layout.getLineBottom(line))
                        put("ellipsized", layout.isLineEllipsized(line))
                    })
                }
            })
            put("glyphs", buildJsonArray {
                layout.layoutInput.text.text.indices.forEach { index ->
                    add(buildJsonObject {
                        put("index", index)
                        put("character", layout.layoutInput.text[index].toString())
                        put("boundsInParagraph", layout.getBoundingBox(index).toString())
                        put("boundsInRoot", glyphInRoot(node, layout, index).toString())
                    })
                }
            })
        }
        File("build/reports/council-chrome-layout").apply { mkdirs() }
            .resolve("$name.json").writeText(metrics.toString())
        println("CouncilChromeLayoutMetrics: $metrics")
    }
}
