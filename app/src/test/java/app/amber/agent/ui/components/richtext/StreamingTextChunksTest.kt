package app.amber.feature.ui.components.richtext

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class StreamingTextChunksTest {
    @Test fun chunksReconstructEveryUnicodeCharacterAndExplicitBreak() {
        val source = (0 until 100).joinToString("\n") { "$it 中文 👩‍💻 é 🇨🇳 " + "body ".repeat(8) }
        val text = buildAnnotatedString {
            append(source)
            addStyle(SpanStyle(color = Color.Blue), 50, 3300)
            addStringAnnotation("link", "https://example.com", 80, 3400)
        }
        val chunks = splitStreamingTextChunks(text)
        assertTrue(chunks.size > 1)
        assertEquals(source, chunks.joinToString("") { it.text.text + it.lineBreak.text })
        for (chunk in chunks) {
            assertEquals(text.subSequence(chunk.start, chunk.start + chunk.text.length), chunk.text)
            if (chunk.lineBreak.isNotEmpty()) {
                assertEquals("\n", source.substring(chunk.start + chunk.text.length, chunk.start + chunk.text.length + 1))
            }
        }
    }
    @Test fun activeFadeWindowDoesNotDirtyCompletedChunks() {
        val prefix = AnnotatedString((0 until 100).joinToString("\n") { "paragraph-$it " + "字".repeat(35) })
        val first = splitStreamingTextChunks(prefix)
        val second = splitStreamingTextChunks(AnnotatedString(prefix.text + "追加文字 👩‍💻"))
        first.dropLast(1).forEachIndexed { index, chunk -> assertEquals(chunk, second[index]) }
        assertTrue(first.last().text.length >= 96)
    }
    @Test fun longSingleParagraphAndCrLfKeepTheOriginalSingleText() {
        val noBreak = AnnotatedString("字".repeat(5000))
        assertEquals(1, splitStreamingTextChunks(noBreak).size)
        val crlf = AnnotatedString("paragraph\r\n".repeat(500))
        assertEquals(listOf(StreamingTextChunk(0, crlf)), splitStreamingTextChunks(crlf))
    }
}
