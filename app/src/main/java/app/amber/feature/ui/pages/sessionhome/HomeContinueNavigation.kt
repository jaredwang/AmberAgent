package app.amber.feature.ui.pages.sessionhome

import app.amber.agent.Screen
import app.amber.feature.home.ContinueRoute

/** P8-08：继续路由 → 应用内页面（点击准确路由到任务焦点）。 */
internal fun ContinueRoute.toScreen(): Screen = when (this) {
    is ContinueRoute.CouncilRoom -> Screen.CouncilRoom(conversationId = conversationId)
    is ContinueRoute.DeepRead -> Screen.DeepRead(
        topicId = topicId,
        title = title,
        sourceUrl = sourceUrl,
    )
    is ContinueRoute.Chat -> Screen.Chat(
        id = conversationId,
        messageId = messageId,
        toolCallId = toolCallId,
    )
    is ContinueRoute.ImageGeneration -> Screen.Chat(
        id = conversationId,
        messageId = messageId,
        toolCallId = toolCallId,
    )
    is ContinueRoute.MiniAppRunner -> Screen.MiniAppRunner(appId = appId)
    is ContinueRoute.NovelWorkspace -> Screen.NovelMarkdown(
        projectId = projectId,
        branchSlug = branchSlug,
        jobId = jobId,
    )
}

/** Chat-backed Continue routes must still own a persisted conversation at click time. */
internal suspend fun canOpenContinueRoute(
    route: ContinueRoute,
    conversationExists: suspend (String) -> Boolean,
): Boolean {
    val conversationId = when (route) {
        is ContinueRoute.Chat -> route.conversationId
        is ContinueRoute.ImageGeneration -> route.conversationId
        else -> return true
    }
    return conversationExists(conversationId)
}
