package app.amber.feature.ui.pages.sessionhome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.amber.core.model.AMBER_AGENT_ID
import app.amber.core.model.Conversation
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeSessionRowSwipeDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun ltrStartToEndFollowsFingerAndPins() = swipe(LayoutDirection.Ltr, delete = false)
    @Test fun ltrEndToStartFollowsFingerAndDeletes() = swipe(LayoutDirection.Ltr, delete = true)
    @Test fun rtlStartToEndFollowsFingerAndPins() = swipe(LayoutDirection.Rtl, delete = false)
    @Test fun rtlEndToStartFollowsFingerAndDeletes() = swipe(LayoutDirection.Rtl, delete = true)

    private fun swipe(direction: LayoutDirection, delete: Boolean) {
        val pins = AtomicInteger()
        val deletes = AtomicInteger()
        val title = "Swipe direction fixture"
        val conversation = Conversation(assistantId = AMBER_AGENT_ID, title = title, messageNodes = emptyList())
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize()) {
                        HomeSessionRow(
                            conversation = conversation,
                            tileColor = Color.Gray,
                            isLastVisited = false,
                            isFirst = true,
                            isLast = true,
                            onOpen = {},
                            onDelete = { deletes.incrementAndGet() },
                            onTogglePin = { pins.incrementAndGet() },
                            modifier = Modifier.testTag("swipe-row"),
                        )
                    }
                }
            }
        }
        val row = compose.onNodeWithTag("swipe-row")
        val text = compose.onNodeWithText(title, useUnmergedTree = true)
        // Pin is start -> end; delete is end -> start. In RTL both physical
        // directions mirror, but the visible row must always follow the finger.
        val sign = (if (direction == LayoutDirection.Rtl) -1f else 1f) * (if (delete) -1f else 1f)
        repeat(if (delete) 1 else 2) { completed ->
            val initialX = text.getUnclippedBoundsInRoot().left.value
            row.performTouchInput {
                down(center)
                moveBy(Offset(sign * width * 0.25f, 0f), delayMillis = 250)
            }
            compose.waitForIdle()
            val displacement = text.getUnclippedBoundsInRoot().left.value - initialX
            assertTrue("$direction delete=$delete moved $displacement against finger sign=$sign",
                displacement * sign > 10f)
            row.performTouchInput {
                moveBy(Offset(sign * width * 0.4f, 0f), delayMillis = 400)
            }
            compose.waitForIdle()
            assertEquals("No action while pointer is held", completed, pins.get() + deletes.get())
            row.performTouchInput {
                advanceEventTime(200)
                up()
            }
            compose.waitUntil(5_000) { pins.get() + deletes.get() > completed }
            compose.waitForIdle()
            assertEquals(if (delete) 0 else completed + 1, pins.get())
            assertEquals(if (delete) 1 else 0, deletes.get())
        }
    }
}
