package app.amber.feature.ui.pages.board

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.amber.agent.R
import app.amber.core.font.SlidesFontRepository
import app.amber.core.settings.Settings
import app.amber.feature.board.DeepReadTemplateIds
import app.amber.feature.board.hotlist.deepread.DeepReadGenerationStage
import app.amber.feature.board.hotlist.deepread.DeepReadOutput
import app.amber.feature.board.hotlist.deepread.DeepReadSectionStatus
import app.amber.feature.board.hotlist.deepread.withSectionStatus
import app.amber.feature.ui.theme.AmberBase
import app.amber.feature.ui.theme.LocalAmberTokens
import app.amber.feature.ui.theme.buildAmberTokens
import app.amber.feature.ui.context.LocalSettings
import java.io.File
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w320dp-h900dp-xhdpi")
class DeepReadReaderHeroLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun initialReaderMastheadClearsFloatingActionsAndLongTitleCanWrapAtLargeFont() {
        val context = RuntimeEnvironment.getApplication()
        val title = "A long persisted article title that remains completely readable on a narrow screen"
        val output = DeepReadOutput(summary = "A retained overview is already readable while the remaining sections are pending.")
            .withSectionStatus(DeepReadGenerationStage.OVERVIEW, DeepReadSectionStatus.READY)
        val fonts = SlidesFontRepository(context, OkHttpClient(), Json { ignoreUnknownKeys = true })
        var backCalls = 0
        var shared: DeepReadOutput? = null
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalAmberTokens provides buildAmberTokens(AmberBase.LIGHT, Color(0xFFB8623A)),
                LocalDensity provides Density(density, fontScale = 1.5f),
                LocalSettings provides Settings(),
            ) {
                MaterialTheme {
                    DeepReadImmersiveWindowEffect(darkTheme = false)
                    Box(Modifier.size(320.dp, 900.dp)) {
                        DeepReadReaderContent(
                            title = title,
                            palette = magazinePalette(),
                            confirmed = true,
                            fromHistory = true,
                            historyLoading = false,
                            output = output,
                            generating = false,
                            complete = false,
                            historyExpired = false,
                            entryTemplateId = DeepReadTemplateIds.COMPOSE_MAGAZINE,
                            templateArticle = null,
                            customTemplateHtml = null,
                            templateSelected = false,
                            selectedCustomMissing = false,
                            synthesisPayloadBroken = false,
                            invalidTemplateCount = 0,
                            runError = null,
                            sectionFailureMessage = null,
                            initialDisplayError = null,
                            failureRetryLabel = "Retry",
                            readingFontFamily = null,
                            deepReadFontScale = 1f,
                            templateFontCss = "",
                            fontRepository = fonts,
                            listState = rememberLazyListState(),
                            appLocale = Locale.ENGLISH,
                            pressSeal = null,
                            pressSealFire = 0,
                            onDismissSeal = {},
                            onConfirm = {},
                            onRunAll = {},
                            onRunSection = {},
                            onRetryFirstFailure = {},
                            onRetryInitialFailure = {},
                            onBack = { backCalls++ },
                            onShare = { shared = it },
                        )
                    }
                }
            }
        }

        val back = compose.onNode(hasClickAction() and hasContentDescription(context.getString(R.string.back)))
        val share = compose.onNode(hasClickAction() and hasContentDescription(context.getString(R.string.share)))
        back.assertIsDisplayed()
        share.assertIsDisplayed()
        val backBounds = back.fetchSemanticsNode().boundsInRoot
        val shareBounds = share.fetchSemanticsNode().boundsInRoot
        val heading = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode()
        val masthead = compose.onAllNodesWithText("DEEP READ", useUnmergedTree = true)
            .fetchSemanticsNodes().filter { it.boundsInRoot.top < heading.boundsInRoot.top }
        assertEquals("Both real masthead labels must remain visible", 2, masthead.size)
        val mastheadLayouts = masthead.map(::textLayout)
        val titleLayout = textLayout(heading)
        masthead.forEachIndexed { index, node -> recordMetrics("masthead-$index", node, mastheadLayouts[index]) }
        recordMetrics("title", heading, titleLayout)
        val minimumGapPx = with(compose.density) { 8.dp.toPx() }
        val toolbarBottom = maxOf(backBounds.bottom, shareBounds.bottom)
        masthead.forEachIndexed { index, label ->
            val bounds = label.boundsInRoot
            assertFalse("Initial masthead $bounds overlaps Back $backBounds", bounds.overlaps(backBounds))
            assertFalse("Initial masthead $bounds overlaps Share $shareBounds", bounds.overlaps(shareBounds))
            assertTrue("Initial masthead must clear the floating actions by at least 8dp: label=$bounds toolbarBottom=$toolbarBottom",
                bounds.top >= toolbarBottom + minimumGapPx - 1f)
            assertCompleteTextFits(label, mastheadLayouts[index], "DEEP READ")
        }
        assertTrue("A long article title must wrap naturally", titleLayout.lineCount > 1)
        assertCompleteTextFits(heading, titleLayout, title)
        back.performClick()
        share.performClick()
        assertEquals(1, backCalls)
        assertEquals(output, shared)
    }

    private fun assertCompleteTextFits(node: SemanticsNode, layout: TextLayoutResult, expected: String) {
        assertEquals("The complete source text must remain in the real Text layout", expected, layout.layoutInput.text.text)
        assertTrue("The text must retain measured space", node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f)
        assertFalse("The text must not exceed its line budget", layout.multiParagraph.didExceedMaxLines)
        repeat(layout.lineCount) { line ->
            assertFalse("The text must not ellipsize line $line", layout.isLineEllipsized(line))
        }
        assertEquals("The last line must reach the complete source text", expected.length,
            layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        // TextLayout's paragraph can retain a wider constraint than the Text node.
        // Use its actual aligned glyph rectangles, with no recentering or scaling.
        expected.indices.filterNot { expected[it].isWhitespace() }.forEach { index ->
            val glyph = glyphInRoot(node, layout, index)
            val bounds = node.boundsInRoot
            assertTrue("Character $index must have finite visible geometry: $glyph",
                glyph.left.isFinite() && glyph.top.isFinite() && glyph.right.isFinite() && glyph.bottom.isFinite() &&
                    glyph.width > 0f && glyph.height > 0f)
            assertTrue("Character $index must fit the real Text bounds: $glyph in $bounds",
                glyph.left >= bounds.left - 1f && glyph.right <= bounds.right + 1f &&
                    glyph.top >= bounds.top - 1f && glyph.bottom <= bounds.bottom + 1f)
        }
    }

    private fun glyphInRoot(node: SemanticsNode, layout: TextLayoutResult, index: Int): Rect {
        val glyph = layout.getBoundingBox(index)
        val origin = node.positionInRoot
        return Rect(glyph.left + origin.x, glyph.top + origin.y, glyph.right + origin.x, glyph.bottom + origin.y)
    }

    private fun recordMetrics(name: String, node: SemanticsNode, layout: TextLayoutResult) {
        val metrics = buildJsonObject {
            put("name", name)
            put("fontScale", 1.5f)
            put("text", layout.layoutInput.text.text)
            put("nodeBoundsInRoot", node.boundsInRoot.toString())
            put("nodePositionInRoot", node.positionInRoot.toString())
            put("nodeSize", node.size.toString())
            put("layoutSize", layout.size.toString())
            put("paragraphWidth", layout.multiParagraph.width)
            put("paragraphHeight", layout.multiParagraph.height)
            put("textAlign", layout.layoutInput.style.textAlign.toString())
            put("constraints", layout.layoutInput.constraints.toString())
            put("softWrap", layout.layoutInput.softWrap)
            put("overflow", layout.layoutInput.overflow.toString())
            put("didOverflowWidth", layout.didOverflowWidth)
            put("didOverflowHeight", layout.didOverflowHeight)
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
        File("build/reports/deepread-hero-layout").apply { mkdirs() }
            .resolve("$name.json").writeText(metrics.toString())
        println("DeepReadHeroLayoutMetrics: $metrics")
    }

    private fun textLayout(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.runOnIdle {
            assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true)
        }
        return layouts.single()
    }
}
