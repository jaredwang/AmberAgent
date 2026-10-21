package app.amber.feature.ui.components.richtext.tree

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.amber.core.settings.Settings
import app.amber.feature.ui.components.richtext.LocalMarkdownParseDispatcher
import app.amber.feature.ui.components.richtext.MARKDOWN_SYNC_PARSE_MAX_CHARS
import app.amber.feature.ui.components.richtext.MarkdownBlock
import app.amber.feature.ui.components.richtext.MarkdownParseResult
import app.amber.feature.ui.components.richtext.StreamingMarkdownParseCache
import app.amber.feature.ui.context.LocalNavController
import app.amber.feature.ui.context.LocalSettings
import app.amber.feature.ui.context.LocalToaster
import app.amber.feature.ui.context.Navigator
import app.amber.highlight.Highlighter
import app.amber.highlight.LocalHighlighter
import com.dokar.sonner.ToasterState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Round-4 P0: settled [MarkdownBlock] on a cache miss must not invent trailing
 * `$` / `**` nodes (streaming repair leaking into the freeze path), and must
 * not flash raw Markdown/HTML markup on any frame.
 *
 * Waiting for the background parse uses a paused [StandardTestDispatcher] for
 * first-frame assertions, and [androidx.compose.ui.test.junit4.ComposeTestRule.waitUntil]
 * on the formatted dump for the completed-tree assertions. No sleep.
 *
 * Documented ready signal for the async path (content longer than
 * [MARKDOWN_SYNC_PARSE_MAX_CHARS]): the semantics dump contains the formatted
 * heading (`text=Heading`) and not the raw marker (`text=## Heading`). A
 * testTag was not used — it unmerges semantics and would rewrite every golden.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class MarkdownSettledParseRegressionTest {

    private val swallowAsyncLoadFailures = CoroutineExceptionHandler { _, t ->
        val highlighterUnavailable = generateSequence<Throwable>(t) { it.cause }.any {
            it is UnsatisfiedLinkError || it.javaClass.name == "com.whl.quickjs.wrapper.QuickJSException"
        }
        if (!highlighterUnavailable) throw t
    }

    @get:Rule
    val compose = createComposeRule(swallowAsyncLoadFailures)

    @Test
    fun finalParseOfSample08DoesNotAppendDollarNode() {
        val content = corpus("08-fenced-code-no-lang.md")
        val result = StreamingMarkdownParseCache().finalParse(content)
        val last = result.trailingVisibleText()
        assertFalse(
            "settled parse of sample 08 ended with extra node '$last' " +
                "(preprocessed ends with '${result.preprocessed.takeLast(8)}')",
            last == "$" || last == "**",
        )
        assertFalse(
            "settled preprocessed text grew a synthetic closer: " +
                "sourceLen=${content.length} parsedLen=${result.preprocessed.length}",
            result.preprocessed.length > content.length &&
                result.preprocessed.removePrefix(content) in setOf("$", "**", "$$"),
        )
    }

    @Test
    fun finalParseOfSample17DoesNotAppendStrongCloser() {
        val content = corpus("17-thematic-breaks.md")
        val result = StreamingMarkdownParseCache().finalParse(content)
        val last = result.trailingVisibleText()
        assertFalse(
            "settled parse of sample 17 ended with extra node '$last'",
            last == "$" || last == "**",
        )
        assertFalse(
            "settled preprocessed text grew a synthetic closer",
            result.preprocessed.length > content.length &&
                result.preprocessed.removePrefix(content) in setOf("$", "**", "$$"),
        )
    }

    @Test
    fun sample08SettledDumpHasNoTrailingDollar() {
        assertSettledDumpHasNoSyntheticCloser("08-fenced-code-no-lang.md", "Fenced Code Blocks")
    }

    @Test
    fun sample17SettledDumpHasNoTrailingStrongCloser() {
        assertSettledDumpHasNoSyntheticCloser("17-thematic-breaks.md", "Thematic Breaks")
    }

    @Test
    fun cacheMissFirstFrameDoesNotShowRawHeadingMarkup() {
        val content = corpus("10-inline-code.md")
        val paused = StandardTestDispatcher()
        compose.setContent {
            CompositionLocalProvider(LocalMarkdownParseDispatcher provides paused) {
                MarkdownHost(content)
            }
        }
        val dump = compose.onRoot().fetchSemanticsNode().dumpNormalized()
        assertFalse(
            "cache-miss first frame showed raw heading markup:\n$dump",
            dump.contains("text=## Inline Code") || dump.contains("text=## Inline"),
        )
        assertTrue(
            "formatted heading never appeared; dump was:\n$dump",
            dump.contains("text=Inline Code"),
        )
    }

    @Test
    fun overSyncBudgetCacheMissDoesNotShowRawMarkup() {
        val content = "## Over Budget Heading\n\n" + "word ".repeat(MARKDOWN_SYNC_PARSE_MAX_CHARS / 4)
        check(content.length > MARKDOWN_SYNC_PARSE_MAX_CHARS)
        val paused = StandardTestDispatcher()
        compose.setContent {
            CompositionLocalProvider(LocalMarkdownParseDispatcher provides paused) {
                MarkdownHost(content)
            }
        }
        val dump = compose.onRoot().fetchSemanticsNode().dumpNormalized()
        assertFalse(
            "over-budget placeholder leaked raw heading markup:\n$dump",
            dump.contains("## Over Budget Heading") || dump.contains("text=## "),
        )
    }

    @Test
    fun cacheMissFirstFrameDoesNotShowRawHtmlMarkup() {
        val content = corpus("20-html-block.md")
        val paused = StandardTestDispatcher()
        compose.setContent {
            CompositionLocalProvider(LocalMarkdownParseDispatcher provides paused) {
                MarkdownHost(content)
            }
        }
        val dump = compose.onRoot().fetchSemanticsNode().dumpNormalized()
        assertFalse(
            "cache-miss first frame showed raw HTML heading/markup:\n$dump",
            dump.contains("text=## HTML Blocks") || dump.contains("<div"),
        )
        assertTrue(
            "formatted HTML heading never appeared; dump was:\n$dump",
            dump.contains("text=HTML Blocks"),
        )
    }

    private fun assertSettledDumpHasNoSyntheticCloser(sample: String, formattedHeading: String) {
        val content = corpus(sample)
        compose.setContent { MarkdownHost(content) }
        compose.waitUntil(8_000) {
            val dump = compose.onRoot().fetchSemanticsNode().dumpNormalized()
            dump.contains("text=$formattedHeading") && !dump.contains("text=## $formattedHeading")
        }
        val dump = compose.onRoot().fetchSemanticsNode().dumpNormalized()
        val lastText = dump.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.startsWith("text=") }
        assertFalse(
            "settled dump for $sample ended with synthetic closer $lastText:\n$dump",
            lastText == "text=$" || lastText == "text=**",
        )
    }

    @androidx.compose.runtime.Composable
    private fun MarkdownHost(content: String) {
        val highlighter = Highlighter(RuntimeEnvironment.getApplication())
        CompositionLocalProvider(
            LocalSettings provides Settings(),
            LocalNavController provides Navigator(mutableListOf()),
            LocalHighlighter provides highlighter,
            LocalToaster provides ToasterState(CoroutineScope(Dispatchers.Unconfined)),
        ) {
            MaterialTheme {
                MarkdownBlock(content = content)
            }
        }
    }

    private fun corpus(name: String): String =
        File("src/test/resources/markdown-corpus", name).readText()
}

private fun MarkdownParseResult.trailingVisibleText(): String {
    val texts = ArrayList<String>()
    fun walk(node: MdNode, source: String) {
        if (node.children.isEmpty()) {
            if (node.type == MdNodeType.Text || node.type == MdNodeType.Unknown) {
                val end = node.endOffset.coerceAtMost(source.length)
                val start = node.startOffset.coerceIn(0, end)
                val slice = source.substring(start, end).trim()
                if (slice.isNotEmpty()) texts += slice
            }
        } else {
            node.children.forEach { walk(it, source) }
        }
    }
    stableTopLevelBlocks.forEach { walk(it.parseResult.tree, it.parseResult.preprocessed) }
    walk(tree, preprocessed)
    return texts.lastOrNull().orEmpty()
}
