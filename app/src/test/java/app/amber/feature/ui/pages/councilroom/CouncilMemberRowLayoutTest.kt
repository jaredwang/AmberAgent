package app.amber.feature.ui.pages.councilroom

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.amber.feature.modelcouncil.CouncilParticipant
import app.amber.feature.modelcouncil.CouncilParticipantKind
import app.amber.feature.modelcouncil.CouncilParticipantStatus
import app.amber.feature.modelcouncil.CouncilRoom
import app.amber.feature.modelcouncil.CouncilRoomStatus
import app.amber.feature.ui.pages.chat.LocalChatTheme
import app.amber.feature.ui.pages.chat.toChatTheme
import app.amber.feature.ui.theme.AmberBase
import app.amber.feature.ui.theme.LocalAmberTokens
import app.amber.feature.ui.theme.buildAmberTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.uuid.Uuid
import java.io.File
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w411dp-h900dp")
class CouncilMemberRowLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun longMemberNameLeavesAReadableRoleAt411dp() = assertReadableMemberRole(fontScale = 1f)

    @Test
    @Config(qualifiers = "en-rUS-w320dp-h900dp")
    fun longMemberNameLeavesAReadableRoleAt320dpWithLargeText() = assertReadableMemberRole(fontScale = 1.5f)

    private fun assertReadableMemberRole(fontScale: Float) {
        val member = CouncilParticipant(
            id = "guest-layout",
            name = "P4 Multilingual Evidence Reviewer · 本地长成员名称",
            role = "Evidence / risk reviewer",
            kind = CouncilParticipantKind.GUEST,
            status = CouncilParticipantStatus.SPOKEN,
            modelName = "Long local model display label",
        )
        val id = Uuid.random()
        val room = CouncilRoom(
            id = id.toString(),
            conversationId = id,
            hostAssistantId = Uuid.random(),
            objective = "Member layout",
            status = CouncilRoomStatus.FINALIZED,
            participants = listOf(member),
            createdAtMs = 1L,
        )
        val tokens = buildAmberTokens(AmberBase.LIGHT, Color(0xFFB8623A))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalAmberTokens provides tokens,
                LocalChatTheme provides tokens.toChatTheme(),
            ) {
                MaterialTheme {
                    // Exercise the real private MemberRow through its production sheet.
                    CouncilMembersSheet(room = room, onDismiss = {})
                }
            }
        }
        val name = compose.onNodeWithText(member.name, useUnmergedTree = true)
        val role = compose.onNodeWithText(member.role, useUnmergedTree = true)
        val model = compose.onNodeWithText(member.modelName, useUnmergedTree = true)
        val nameBounds = name.fetchSemanticsNode().boundsInRoot
        val roleNode = role.fetchSemanticsNode()
        val roleBounds = roleNode.boundsInRoot
        val modelBounds = model.fetchSemanticsNode().boundsInRoot
        val nameLayout = name.textLayout()
        val roleLayout = role.textLayout()
        val glyphs = member.role.indices.filterNot { member.role[it].isWhitespace() }.map { index ->
            val local = roleLayout.getBoundingBox(index)
            val origin = roleNode.positionInRoot
            val inRoot = Rect(
                local.left + origin.x, local.top + origin.y,
                local.right + origin.x, local.bottom + origin.y,
            )
            Triple(index, local, inRoot)
        }
        val metrics = buildJsonObject {
            put("fontScale", fontScale)
            put("role", member.role)
            put("layoutText", roleLayout.layoutInput.text.text)
            put("nodeSize", roleNode.size.toString())
            put("layoutSize", roleLayout.size.toString())
            put("nodePositionInRoot", roleNode.positionInRoot.toString())
            put("nodeBoundsInRoot", roleBounds.toString())
            put("paragraphWidth", roleLayout.multiParagraph.width)
            put("paragraphHeight", roleLayout.multiParagraph.height)
            put("textAlign", roleLayout.layoutInput.style.textAlign.toString())
            put("constraints", roleLayout.layoutInput.constraints.toString())
            put("didOverflowWidth", roleLayout.didOverflowWidth)
            put("didExceedMaxLines", roleLayout.multiParagraph.didExceedMaxLines)
            put("lineCount", roleLayout.lineCount)
            put("nameBoundsInRoot", nameBounds.toString())
            put("modelBoundsInRoot", modelBounds.toString())
            put("lines", buildJsonArray {
                repeat(roleLayout.lineCount) { line ->
                    add(buildJsonObject {
                        put("index", line)
                        put("start", roleLayout.getLineStart(line))
                        put("visibleEnd", roleLayout.getLineEnd(line, visibleEnd = true))
                        put("left", roleLayout.getLineLeft(line))
                        put("right", roleLayout.getLineRight(line))
                        put("top", roleLayout.getLineTop(line))
                        put("bottom", roleLayout.getLineBottom(line))
                        put("ellipsized", roleLayout.isLineEllipsized(line))
                    })
                }
            })
            put("glyphs", buildJsonArray {
                glyphs.forEach { (index, local, inRoot) ->
                    add(buildJsonObject {
                        put("index", index)
                        put("character", member.role[index].toString())
                        put("boundsInParagraph", local.toString())
                        put("boundsInRoot", inRoot.toString())
                        put("leftFits", inRoot.left >= roleBounds.left - 1f)
                        put("rightFits", inRoot.right <= roleBounds.right + 1f)
                        put("topFits", inRoot.top >= roleBounds.top - 1f)
                        put("bottomFits", inRoot.bottom <= roleBounds.bottom + 1f)
                    })
                }
            })
        }
        val metricsDirectory = File("build/reports/council-member-layout").apply { mkdirs() }
        File(metricsDirectory, "font-$fontScale.json").writeText(metrics.toString())
        println("CouncilMemberRowLayoutMetrics: $metrics")

        assertTrue("the member name must retain visible width", nameBounds.width > 0f)
        assertEquals("name ellipsis remains a one-line design", 1, nameLayout.lineCount)
        assertTrue("the role must receive actual horizontal space", roleBounds.width > 0f)
        assertTrue("the role must not wrap into a character-height column", roleLayout.lineCount <= 3)
        assertEquals("the role must retain its complete source text", member.role, roleLayout.layoutInput.text.text)
        assertFalse("the role must not exceed its line budget", roleLayout.multiParagraph.didExceedMaxLines)
        repeat(roleLayout.lineCount) { line ->
            assertFalse("the role must not ellipsize line $line", roleLayout.isLineEllipsized(line))
        }
        // The paragraph may retain a larger maxWidth than the actual Text node.
        // Check real character rectangles in the node's root coordinates instead.
        glyphs.forEach { (index, _, inRoot) ->
            assertTrue("role character $index must have finite visible geometry: $inRoot",
                inRoot.left.isFinite() && inRoot.top.isFinite() &&
                    inRoot.right.isFinite() && inRoot.bottom.isFinite() &&
                    inRoot.width > 0f && inRoot.height > 0f)
            assertTrue("role character $index must fit the actual Text bounds: $inRoot in $roleBounds",
                inRoot.left >= roleBounds.left - 1f && inRoot.right <= roleBounds.right + 1f &&
                    inRoot.top >= roleBounds.top - 1f && inRoot.bottom <= roleBounds.bottom + 1f)
        }
        assertEquals(member.role.length, roleLayout.getLineEnd(roleLayout.lineCount - 1, visibleEnd = true))
        assertTrue("the role must not create a screen-height roster row", roleBounds.height <= with(compose.density) { 80.dp.toPx() })
        assertTrue("the model label stays below the role", modelBounds.top + 1f >= roleBounds.bottom)
        assertTrue("the model label must retain visible width", modelBounds.width > 0f)
        assertTrue("member metadata stays compact", modelBounds.bottom - nameBounds.top <= with(compose.density) { 140.dp.toPx() })
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        val action = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue("real Text layout must be available", action?.invoke(layouts) == true)
        return layouts.single()
    }
}
