package app.amber.feature.ui.pages.chat

import app.amber.ai.core.MessageRole
import app.amber.ai.ui.UIMessage
import app.amber.ai.ui.UIMessagePart
import app.amber.core.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 预取只负责"值得提前下载的图片 URL"。
 *
 * 规则：Image part 与正文 markdown 图都要，`data:` 内联图不重复入队（消息体里已经有了），
 * 视频/音频不在这里预取（视频封面只对本地文件取帧，没有网络请求）。
 */
class TimelineImagePrefetchTest {

    private fun node(vararg parts: UIMessagePart) = MessageNode(
        messages = listOf(UIMessage(role = MessageRole.ASSISTANT, parts = parts.toList())),
    )

    @Test
    fun collectsImagePartsAndMarkdownImagesInOrder() {
        val urls = node(
            UIMessagePart.Image("https://example.com/a.png"),
            UIMessagePart.Text("看图 ![说明](https://example.com/b.jpg) 完"),
            UIMessagePart.Image("https://example.com/c.webp"),
        ).prefetchImageUrls()

        assertEquals(
            listOf(
                "https://example.com/a.png",
                "https://example.com/b.jpg",
                "https://example.com/c.webp",
            ),
            urls,
        )
    }

    @Test
    fun skipsInlineDataUrls() {
        val urls = node(
            UIMessagePart.Image("data:image/png;base64,AAAA"),
            UIMessagePart.Image("https://example.com/real.png"),
        ).prefetchImageUrls()

        assertEquals(listOf("https://example.com/real.png"), urls)
    }

    @Test
    fun skipsVideoAndAudio() {
        val urls = node(
            UIMessagePart.Video(url = "file:///v/clip.mp4"),
            UIMessagePart.Audio(url = "file:///a/voice.m4a"),
        ).prefetchImageUrls()

        assertEquals(emptyList<String>(), urls)
    }

    @Test
    fun ignoresMarkdownImageSyntaxInsideCodeFence() {
        val urls = node(
            UIMessagePart.Text("```\n![x](https://example.com/code.png)\n```"),
        ).prefetchImageUrls()

        assertEquals(emptyList<String>(), urls)
    }
}
