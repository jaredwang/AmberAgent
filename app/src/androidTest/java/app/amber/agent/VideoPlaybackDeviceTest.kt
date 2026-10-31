package app.amber.agent

import android.view.View
import android.view.ViewGroup
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.amber.agent.R
import app.amber.feature.ui.components.media.ContainVideoView
import app.amber.feature.ui.components.media.VideoPlaybackDialog
import app.amber.feature.ui.components.media.containFitVideo
import app.amber.feature.ui.components.media.videoControlHeightPx
import app.amber.feature.ui.pages.chat.LocalChatTheme
import app.amber.feature.ui.pages.chat.toChatTheme
import app.amber.feature.ui.theme.AmberBase
import app.amber.feature.ui.theme.LocalAmberTokens
import app.amber.feature.ui.theme.buildAmberTokens
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated VideoPlaybackDialog: contain-fit, controls, ON_STOP pause, timeline scroll preserved. */
@RunWith(AndroidJUnit4::class)
class VideoPlaybackDeviceTest {
    @get:Rule(order = 0)
    val idleTimeout = object : org.junit.rules.TestWatcher() {
        override fun starting(description: org.junit.runner.Description) {
            androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
            androidx.test.espresso.IdlingPolicies.setIdlingResourceTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun portraitLandscapeAndSquareContainAndStayOperable() {
        assertContainAndPlay("portrait.mp4", 360, 640)
        assertContainAndPlay("landscape.mp4", 640, 360)
        assertContainAndPlay("square.mp4", 360, 360)
    }

    @Test
    fun stopPausesWithoutAutoResumeCloseIsSilentAndTimelineScrollStays() {
        val file = copyAsset("portrait.mp4")
        var showDialog by mutableStateOf(false)
        compose.setContent {
            FixtureTheme {
                val state = rememberLazyListState()
                Box(Modifier.fillMaxSize().testTag("timeline-root")) {
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("timeline")) {
                        items(List(40) { it }) { index ->
                            Text(
                                "row-$index",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .testTag("row-$index"),
                            )
                        }
                    }
                    if (showDialog) {
                        VideoPlaybackDialog(url = file.toURI().toString(), onDismiss = { showDialog = false })
                    }
                }
            }
        }
        compose.onNodeWithTag("timeline").performScrollToIndex(12)
        compose.onNodeWithTag("row-12").assertIsDisplayed()

        compose.runOnIdle { showDialog = true }
        compose.waitUntil(20_000) { videoViews().any { it.isPlaying || prepared(it) } }
        compose.waitUntil(20_000) { videoViews().any { it.isPlaying } }
        assertTrue(videoViews().any { it.isPlaying })

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
        assertTrue("ON_STOP must pause", videoViews().none { it.isPlaying })

        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        Thread.sleep(300)
        assertTrue("ON_START must not auto-resume", videoViews().none { it.isPlaying })

        val playLabel = context.getString(R.string.chat_video_play)
        compose.onNodeWithContentDescription(playLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(playLabel).performClick()
        compose.waitUntil(8_000) { videoViews().any { it.isPlaying } }

        compose.runOnIdle { showDialog = false }
        compose.waitForIdle()
        compose.waitUntil(8_000) { videoViews().isEmpty() || videoViews().none { it.isPlaying } }
        assertTrue("close must be silent", videoViews().none { it.isPlaying })
        compose.onNodeWithTag("row-12").assertIsDisplayed()
    }

    private fun assertContainAndPlay(asset: String, contentWidth: Int, contentHeight: Int) {
        val file = copyAsset(asset)
        var show by mutableStateOf(true)
        compose.setContent {
            FixtureTheme {
                Box(Modifier.fillMaxSize()) {
                    if (show) {
                        VideoPlaybackDialog(url = file.toURI().toString(), onDismiss = { show = false })
                    }
                }
            }
        }
        compose.waitUntil(20_000) { videoViews().isNotEmpty() }
        compose.waitUntil(20_000) { videoViews().any { prepared(it) } }
        val view = videoViews().single()
        val maxW = compose.activity.resources.displayMetrics.widthPixels
        val maxH = compose.activity.resources.displayMetrics.heightPixels
        val reserved = videoControlHeightPx(maxH, compose.activity.resources.displayMetrics.density.times(48).toInt())
        val fitted = containFitVideo(contentWidth, contentHeight, maxW, (maxH - reserved).coerceAtLeast(0))
        assertTrue("$asset width ${view.width} exceeds slot $maxW", view.width <= maxW)
        assertTrue("$asset height ${view.height} exceeds slot $maxH", view.height <= maxH)
        if (view is ContainVideoView && view.width > 0 && view.height > 0) {
            val ratio = view.width.toFloat() / view.height.toFloat()
            val expect = fitted.width.toFloat() / fitted.height.toFloat().coerceAtLeast(1f)
            assertEquals("contain aspect $asset", expect, ratio, 0.15f)
        }
        compose.onNodeWithContentDescription(context.getString(R.string.chat_video_play)).assertIsDisplayed()
        if (!view.isPlaying) {
            compose.onNodeWithContentDescription(context.getString(R.string.chat_video_play)).performClick()
            compose.waitUntil(8_000) { videoViews().any { it.isPlaying } }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.chat_video_pause)).assertIsDisplayed()
        compose.runOnIdle { show = false }
        compose.waitUntil(8_000) { videoViews().isEmpty() }
    }

    private fun copyAsset(name: String): File {
        val encoded = when (name) {
            "portrait.mp4" -> AmberS9VideoAssets.PORTRAIT_B64
            "landscape.mp4" -> AmberS9VideoAssets.LANDSCAPE_B64
            "square.mp4" -> AmberS9VideoAssets.SQUARE_B64
            else -> error("unknown video fixture $name")
        }
        val dest = File(context.cacheDir, "amber-s9-$name")
        dest.writeBytes(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
        return dest
    }

    private fun videoViews(): List<VideoView> {
        var views = emptyList<VideoView>()
        instrumentation.runOnMainSync {
            views = collectVideoViews(compose.activity.window.decorView)
        }
        return views
    }

    private fun collectVideoViews(view: View): List<VideoView> = when (view) {
        is VideoView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { collectVideoViews(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun prepared(view: VideoView): Boolean =
        view.width > 0 && view.height > 0

    @Composable
    private fun FixtureTheme(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        val tokens = buildAmberTokens(AmberBase.LIGHT, Color(0xFFB8623A))
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale = 1f),
            LocalAmberTokens provides tokens,
            LocalChatTheme provides tokens.toChatTheme(),
        ) {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(tokens.bg)) { content() }
            }
        }
    }
}
