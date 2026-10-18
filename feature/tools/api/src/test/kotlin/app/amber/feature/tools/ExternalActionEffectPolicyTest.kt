package app.amber.feature.tools

import app.amber.ai.core.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalActionEffectPolicyTest {
    private fun tool(name: String) = Tool(
        name = name,
        description = name,
        needsApproval = true,
        allowsAutoApproval = false,
        execute = { emptyList() },
    )

    @Test
    fun screenActionsCannotBeRecoveredAsReadOnlyObservations() {
        val names = listOf(
            "screen_click", "screen_long_click", "screen_swipe", "screen_input_text",
            "screen_tap_text", "screen_back", "screen_home", "screen_open_app",
            "screen_open_url", "screen_scroll_until", "screen_collect_list",
        )
        for (name in names) {
            val action = tool(name)
            val metadata = ToolRegistry.from(listOf(action)).metadataFor(name)!!
            assertTrue("$name changes external screen state", metadata.mutates)
            assertEquals(name, ToolEffectClass.NON_IDEMPOTENT_WRITE, action.effectClass("{}"))
            assertEquals(name, ToolEffectClass.NON_IDEMPOTENT_WRITE, metadata.effectClass)
        }
    }

    @Test
    fun commandExecutionAndWorkspaceFlushCannotBeSilentlyRetried() {
        for (name in listOf(
            "terminal_execute", "terminal_job_start", "terminal_session_exec", "terminal_workspace_flush",
            "call_phone", "intent_open",
        )) {
            assertEquals(name, ToolEffectClass.NON_IDEMPOTENT_WRITE, tool(name).effectClass("{}"))
        }
    }

    @Test
    fun screenAndTerminalObservationsKeepTheirReadOnlyRecovery() {
        for (name in listOf(
            "screen_read_ui", "screen_find_text", "screen_wait_for_text", "screen_status",
            "screen_apps", "screen_screenshot", "terminal_job_read", "terminal_job_wait",
            "terminal_session_read", "terminal_ssh_profiles",
        )) {
            val observation = tool(name)
            val metadata = ToolRegistry.from(listOf(observation)).metadataFor(name)!!
            assertFalse(name, metadata.mutates)
            assertEquals(name, ToolEffectClass.READ_ONLY, observation.effectClass("{}"))
        }
    }
}
