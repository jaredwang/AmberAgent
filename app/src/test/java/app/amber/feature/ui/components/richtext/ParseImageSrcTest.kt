package app.amber.feature.ui.components.richtext
import app.amber.feature.ui.components.richtext.tree.MdNodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class ParseImageSrcTest {
    @Test fun fileUriImageSrcIsPreserved() {
        val md = "![FIXTURE_IMAGE_ALT](file:///data/user/0/app.amber.agent.graphite/files/chat-device-fixture/files/amber-fixture-image.png)\n"
        val result = MarkdownParseCache.getOrParse(md)
        val images = mutableListOf<String?>()
        fun walk(n: app.amber.feature.ui.components.richtext.tree.MdNode) {
            if (n.type == MdNodeType.Image) images += n.imageSrc
            n.children.forEach(::walk)
        }
        walk(result.tree)
        println("IMAGES=$images")
        assertTrue(images.isNotEmpty())
        assertEquals(
            "file:///data/user/0/app.amber.agent.graphite/files/chat-device-fixture/files/amber-fixture-image.png",
            images.single(),
        )
    }
}
