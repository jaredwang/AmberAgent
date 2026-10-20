package app.amber.feature.ui.components.richtext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString

internal const val STREAMING_TEXT_CHUNK_CHARS = 768
internal const val STREAMING_TEXT_CHUNK_MIN_LENGTH = 3_072

/** A newline is represented by the vertical boundary between adjacent Text nodes. */
@Immutable
internal data class StreamingTextChunk(
    val start: Int, val text: AnnotatedString, val lineBreak: AnnotatedString = AnnotatedString(""),
)

/**
 * Split only at existing line boundaries; never at a surrogate or a wrapped line.
 * Retain at least 96 UTF-16 units in the active tail, so the <=48-codepoint ink
 * window cannot dirty a completed chunk. Joining with '\n' exactly recovers text.
 */
internal fun splitStreamingTextChunks(text: AnnotatedString): List<StreamingTextChunk> {
    if (text.length < STREAMING_TEXT_CHUNK_MIN_LENGTH || '\r' in text.text || text.paragraphStyles.isNotEmpty()) return listOf(StreamingTextChunk(0, text))
    return buildList {
        var start = 0
        val stableEnd = text.length - 96
        while (start + STREAMING_TEXT_CHUNK_CHARS < stableEnd) {
            val newline = text.text.indexOf('\n', start + STREAMING_TEXT_CHUNK_CHARS)
            if (newline < 0 || newline >= stableEnd) break
            add(StreamingTextChunk(start, text.subSequence(start, newline), text.subSequence(newline, newline + 1)))
            start = newline + 1
        }
        add(StreamingTextChunk(start, text.subSequence(start, text.length)))
    }
}

/** Two native lines reproduce the baseline distance across a chunk seam. */
internal fun streamingChunkBridge(
    previous: TextLayoutResult,
    next: TextLayoutResult,
    lineBreak: AnnotatedString,
): AnnotatedString {
    fun line(layout: TextLayoutResult, index: Int): AnnotatedString {
        val text = layout.layoutInput.text
        val start = layout.getLineStart(index)
        var end = layout.getLineEnd(index, visibleEnd = false)
        while (end > start && text[end - 1] == '\n') end--
        if (end > start) return text.subSequence(start, end)
        // A zero-width glyph carries an empty line's inherited font metrics.
        val offset = (start - 1).coerceIn(0, (text.length - 1).coerceAtLeast(0))
        return buildAnnotatedString {
            append('\u200B')
            text.spanStyles.filter { it.start <= offset && it.end > offset }.forEach {
                addStyle(it.item, 0, 1)
            }
        }
    }
    return buildAnnotatedString {
        append(line(previous, previous.lineCount - 1))
        append(lineBreak)
        append(line(next, 0))
    }
}

/** Cache only the currently displayed chunk layouts; all access is on the UI thread. */
private class StreamingChunkLayouts {
    val values = HashMap<Int, TextLayoutResult>()
}

@Composable
internal fun StreamingChunkedText(
    text: AnnotatedString,
    modifier: Modifier,
    style: TextStyle,
) {
    val chunks = remember(text) { splitStreamingTextChunks(text) }
    if (chunks.size == 1) {
        Text(text, modifier, style = style,
            softWrap = true, overflow = TextOverflow.Visible)
        return
    }
    val layouts = remember { StreamingChunkLayouts() }
    val measurer = rememberTextMeasurer(cacheSize = 128)
    Layout(
        modifier = modifier,
        content = {
            chunks.forEachIndexed { index, chunk ->
                key(chunk.start) {
                    val onLayout = remember(layouts, chunk.start) {
                        { result: TextLayoutResult -> layouts.values[chunk.start] = result; Unit }
                    }
                    // A RenderNode records completed glyphs once; no offscreen
                    // bitmap is allocated. Only the active tail dirties its text.
                    val layer = if (index < chunks.lastIndex) Modifier.graphicsLayer()
                        else Modifier
                    Text(chunk.text, modifier = layer, style = style,
                        softWrap = true, overflow = TextOverflow.Visible,
                        onTextLayout = onLayout)
                }
            }
        },
    ) { children, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = children.map { it.measure(childConstraints) }
        val tops = IntArray(placeables.size)
        var top = 0f
        for (index in 1..chunks.lastIndex) {
            val previous = checkNotNull(layouts.values[chunks[index - 1].start])
            val current = checkNotNull(layouts.values[chunks[index].start])
            val bridge = measurer.measure(
                streamingChunkBridge(previous, current, chunks[index - 1].lineBreak),
                style = style,
                constraints = Constraints(maxWidth = constraints.maxWidth),
            )
            // Native line-height trimming differs at the first/last line of
            // a Text. Reproduce the seam's two-line baseline distance instead
            // of summing child heights (which would compress the message).
            top += previous.getLineBaseline(previous.lineCount - 1) +
                bridge.getLineBaseline(1) - bridge.getLineBaseline(0) - current.getLineBaseline(0)
            tops[index] = kotlin.math.round(top).toInt()
        }
        val height = constraints.constrainHeight(kotlin.math.round(top + placeables.last().height).toInt())
        val width = constraints.constrainWidth(placeables.maxOf { it.width })
        val activeKeys = chunks.mapTo(HashSet()) { it.start }
        layouts.values.keys.retainAll(activeKeys)
        layout(width, height) {
            placeables.forEachIndexed { index, child -> child.placeRelative(0, tops[index]) }
        }
    }
}
