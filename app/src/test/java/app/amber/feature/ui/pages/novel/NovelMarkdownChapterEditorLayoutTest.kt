package app.amber.feature.ui.pages.novel

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
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
@Config(sdk = [34], application = Application::class)
class NovelMarkdownChapterEditorLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w320dp-h812dp-xxhdpi")
    fun narrowLargeFontKeepsBothActionsOnOneLineAndTitleFullyVisible() {
        showEditor(fontScale = 1.5f)

        val cancel = assertSingleLineAction("Cancel")
        val save = assertSingleLineAction("Save")
        val (titleNode, titleLayout) = textLayout("Edit chapter")
        assertFalse("the title must fit within its two available lines", titleLayout.multiParagraph.didExceedMaxLines)
        repeat(titleLayout.lineCount) { line ->
            assertFalse("the title must remain complete", titleLayout.isLineEllipsized(line))
            assertTrue("the header must grow to fit the title", titleLayout.getLineBottom(line) <= titleNode.size.height + 1f)
        }
        assertFalse("actions must not overlap", clickBounds(cancel).overlaps(clickBounds(save)))
        assertTrue("Cancel must leave room for the title", clickBounds(cancel).right <= titleNode.boundsInRoot.left + 1f)
        assertTrue("Save must leave room for the title", titleNode.boundsInRoot.right <= clickBounds(save).left + 1f)
    }

    @Test
    @Config(qualifiers = "w411dp-h812dp-xxhdpi")
    fun defaultWidthRetainsSingleLineTitleAndActionAlignment() {
        showEditor(fontScale = 1f)

        val cancel = assertSingleLineAction("Cancel")
        val save = assertSingleLineAction("Save")
        val (titleNode, titleLayout) = textLayout("Edit chapter")
        assertEquals("default title stays on one line", 1, titleLayout.lineCount)
        assertFalse(titleLayout.isLineEllipsized(0))
        val cancelBounds = clickBounds(cancel)
        val saveBounds = clickBounds(save)
        assertEquals(cancelBounds.center.y, saveBounds.center.y, 1f)
        assertEquals(cancelBounds.center.y, titleNode.boundsInRoot.center.y, 1f)
        assertFalse(cancelBounds.overlaps(saveBounds))
    }

    private fun showEditor(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MaterialTheme {
                    MarkdownChapterEditor(
                        chapter = NovelMarkdownChapterUi("branches/main/chapters/001.md", "First chapter", 1, 4),
                        initialBody = "body", busy = false, writeLocked = false,
                        onSave = { _, _, _ -> }, onCancel = {},
                    )
                }
            }
        }
    }

    private fun assertSingleLineAction(label: String): SemanticsNode {
        val (node, layout) = textLayout(label)
        assertEquals("$label must remain complete", label, layout.layoutInput.text.text)
        assertEquals("$label must not split into multiple lines", 1, layout.lineCount)
        assertFalse("$label must not truncate", layout.multiParagraph.didExceedMaxLines)
        assertFalse("$label must not ellipsize", layout.isLineEllipsized(0))
        assertTrue("$label line must fit its actual text node", layout.getLineBottom(0) <= node.size.height + 1f)
        assertTrue("$label width must fit its actual text node", layout.getLineRight(0) - layout.getLineLeft(0) <= node.size.width + 1f)
        return node
    }

    private fun textLayout(label: String): Pair<SemanticsNode, TextLayoutResult> {
        val node = compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.runOnIdle { assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true) }
        return node to layouts.single()
    }

    private fun clickBounds(node: SemanticsNode) = generateSequence(node) { it.parent }
        .first { it.config.contains(SemanticsActions.OnClick) }.boundsInRoot
}
