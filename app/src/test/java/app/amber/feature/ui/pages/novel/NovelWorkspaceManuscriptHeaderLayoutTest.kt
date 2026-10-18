package app.amber.feature.ui.pages.novel

import android.app.Application
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import app.amber.agent.R
import androidx.test.core.app.ApplicationProvider
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
class NovelWorkspaceManuscriptHeaderLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w320dp-h812dp-xxhdpi")
    fun narrowLargeFontKeepsContentsOnOneLineAndHeaderActionsVisible() {
        showManuscript(fontScale = 1.5f)

        val heading = completeSingleLine(label(R.string.novel_manuscript_directory))
        val polish = completeSingleLine(label(R.string.novel_batch_polish))
        val count = completeSingleLine(chapterCount())
        val more = headerMoreButton()
        assertFalse("heading must not overlap polish", heading.boundsInRoot.overlaps(clickBounds(polish)))
        assertFalse("chapter count must not overlap overflow action", count.boundsInRoot.overlaps(more.boundsInRoot))
        assertFalse("polish and overflow actions must not overlap", clickBounds(polish).overlaps(more.boundsInRoot))
    }

    @Test
    @Config(qualifiers = "w411dp-h812dp-xxhdpi")
    fun defaultWidthRetainsSingleRowHeader() {
        showManuscript(fontScale = 1f)

        val heading = completeSingleLine(label(R.string.novel_manuscript_directory))
        val polish = completeSingleLine(label(R.string.novel_batch_polish))
        val count = completeSingleLine(chapterCount())
        val more = headerMoreButton()
        assertEquals(heading.boundsInRoot.center.y, clickBounds(polish).center.y, 1f)
        assertEquals(heading.boundsInRoot.center.y, count.boundsInRoot.center.y, 1f)
        assertEquals(heading.boundsInRoot.center.y, more.boundsInRoot.center.y, 1f)
    }

    private fun showManuscript(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MaterialTheme {
                    MarkdownWorkspaceManuscript(
                        state = NovelWorkspaceManuscriptState(
                            branchSlug = "main", chapters = listOf(
                                NovelMarkdownChapterUi("branches/main/chapters/001.md", "First chapter", 1, 66),
                                NovelMarkdownChapterUi("branches/main/chapters/002.md", "Second chapter", 2, 58),
                            ), ghostwriteJob = null, busy = false, errorMessage = null,
                            canUndo = false, consistencyChecking = false,
                        ),
                        onOpenPolish = {}, onOpenChapter = {}, onHistory = {}, onDiscard = {},
                        listState = rememberLazyListState(), onManageProjects = {},
                        loadDiscardedChapters = { null }, readFileBody = { null },
                        onRestoreDiscardedChapter = { _, _, _, _ -> }, onRewriteChapter = { false },
                        onUndoLast = {}, onRunConsistencyCheck = {}, onClearError = {},
                    )
                }
            }
        }
    }

    private fun completeSingleLine(text: String): SemanticsNode {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.runOnIdle { assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true) }
        val layout = layouts.single()
        assertEquals("$text must stay on one readable line", 1, layout.lineCount)
        assertEquals("the full label must remain present", text, layout.layoutInput.text.text)
        assertFalse("$text must not truncate", layout.multiParagraph.didExceedMaxLines)
        assertFalse("$text must not ellipsize", layout.isLineEllipsized(0))
        assertTrue("$text line must fit its actual height", layout.getLineBottom(0) <= node.size.height + 1f)
        assertTrue("$text line must fit its actual width", layout.getLineRight(0) - layout.getLineLeft(0) <= node.size.width + 1f)
        return node
    }

    private fun headerMoreButton() = compose.onAllNodesWithContentDescription(label(R.string.novel_more_actions))
        .onFirst().assertIsDisplayed().fetchSemanticsNode()

    private fun clickBounds(node: SemanticsNode) = generateSequence(node) { it.parent }
        .first { it.config.contains(SemanticsActions.OnClick) }.boundsInRoot

    private fun label(id: Int) = ApplicationProvider.getApplicationContext<Application>().getString(id)
    private fun chapterCount() = ApplicationProvider.getApplicationContext<Application>().getString(R.string.novel_directory_count, 2)
}
