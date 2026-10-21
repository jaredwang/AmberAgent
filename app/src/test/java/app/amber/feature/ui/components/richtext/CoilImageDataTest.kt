package app.amber.feature.ui.components.richtext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoilImageDataTest {
    @Test
    fun fileUriUsesFileFetcherPath() {
        val data = coilImageData("file:///data/user/0/app.amber.agent.graphite/files/pic.png")
        assertTrue(data is File)
        assertEquals("/data/user/0/app.amber.agent.graphite/files/pic.png", (data as File).path)
    }

    @Test
    fun angleBracketFileUriUsesFileFetcherPath() {
        val data = coilImageData("<file:///cache/fixture.png>")
        assertTrue(data is File)
        assertEquals("/cache/fixture.png", (data as File).path)
    }

    @Test
    fun httpsUrlStaysAString() {
        assertEquals("https://example.com/a.png", coilImageData("https://example.com/a.png"))
    }
}
