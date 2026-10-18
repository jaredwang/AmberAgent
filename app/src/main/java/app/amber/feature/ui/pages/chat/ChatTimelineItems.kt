package app.amber.feature.ui.pages.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.amber.ai.core.MessageRole
import app.amber.ai.ui.UIMessage
import app.amber.core.context.CompactLifecycleState
import app.amber.core.settings.Settings
import app.amber.core.settings.findModelById
import app.amber.core.settings.getCurrentChatModel
import app.amber.core.model.Conversation
import app.amber.core.model.MessageNode
import app.amber.core.service.ConversationTimelineLoadState
import app.amber.core.service.PendingUserMessage
import app.amber.feature.ui.components.message.ChatMessage
import app.amber.feature.ui.components.message.ChatMessageVirtualItemContent
import app.amber.feature.ui.components.ui.ListSelectableItem
import app.amber.core.utils.ChatSendTransitionTracker
import kotlin.uuid.Uuid
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.State
import app.amber.feature.ui.components.message.ThemeTryOnCardTarget

// iMessage-style send entrance: the just-sent user bubble springs up from the
// input bar's direction into its slot. The distance approximates the visual
// gap between the input field and where the bubble lands (above the bottom
// padding + tail reserve).
internal val SendEntranceSlideDistance = 84.dp

internal fun ChatTimelineEntry.lazyItemKey(
    conversationId: Uuid,
    postSendState: PostSendTimelineState,
    pendingMessages: List<PendingUserMessage>,
    tailCompactItemKey: String,
): Any = when (this) {
    ChatTimelineEntry.HistoryLoading -> HistoryLoadingItemKey
    is ChatTimelineEntry.PostSendHiddenAssistant -> node.id
    is ChatTimelineEntry.Message -> node.id
    is ChatTimelineEntry.VirtualMessage -> "${node.id}:${item.keySuffix}"
    ChatTimelineEntry.PostSendWaitingAssistant ->
        "post-send-waiting-${postSendState.sentUserMessageId ?: conversationId}"
    is ChatTimelineEntry.Pending -> "pending-${pendingMessages[pendingIndex].id}"
    ChatTimelineEntry.TailCompactMarkers -> tailCompactItemKey
    ChatTimelineEntry.TimelineTail -> TimelineTailKey
}

internal fun ChatTimelineEntry.lazyContentType(): String = when (this) {
    ChatTimelineEntry.HistoryLoading -> "history-loading"
    is ChatTimelineEntry.PostSendHiddenAssistant -> "post-send-hidden-assistant"
    is ChatTimelineEntry.Message -> "message-${node.currentMessage.role}"
    is ChatTimelineEntry.VirtualMessage ->
        "message-${node.currentMessage.role}-virtual-${item.keySuffix.substringBefore('-')}"
    ChatTimelineEntry.PostSendWaitingAssistant -> "post-send-waiting-assistant"
    is ChatTimelineEntry.Pending -> "pending"
    ChatTimelineEntry.TailCompactMarkers -> "compact-timeline-tail"
    ChatTimelineEntry.TimelineTail -> "timeline-tail"
}

internal fun LazyListScope.chatTimelineItems(
    conversation: Conversation,
    chatTimelinePlan: ChatTimelinePlan,
    timelineLoadState: ConversationTimelineLoadState,
    pendingUserMessagesState: State<List<PendingUserMessage>>,
    compactLifecycleState: CompactLifecycleState,
    compaction: TimelineCompactionPresentation,
    selection: TimelineSelectionState,
    state: LazyListState,
    bottomPinBufferPx: Int,
    drainAmortizeGrace: Boolean,
    tailIndicatorReserveVisible: Boolean,
    tailIndicatorDotVisible: Boolean,
    showBottomFollowAnimation: Boolean,
    processingStatus: String?,
    settings: Settings,
    themeTryOnCardTarget: ThemeTryOnCardTarget?,
    onRegenerate: (UIMessage) -> Unit,
    onEdit: (UIMessage) -> Unit,
    onQuote: (String) -> Unit,
    onForkMessage: (UIMessage) -> Unit,
    onDelete: (UIMessage) -> Unit,
    onUpdateMessage: (MessageNode) -> Unit,
    onToggleFavorite: ((MessageNode) -> Unit)?,
    onSaveToWorkspace: ((UIMessage) -> Unit)?,
    onToolApproval: ((String, Boolean, String) -> Unit)?,
    onToolAnswer: ((String, String) -> Unit)?,
    onOpenWorkspaceFile: ((String) -> Unit)?,
    onGenerativeWidgetAction: (String) -> Unit,
    onMiniAppModify: (String) -> Boolean,
    onCancelPendingMessage: (String) -> Unit,
    onOpenQueue: () -> Unit,
) {
    val currentPendingUserMessages by pendingUserMessagesState
    val conversationId = conversation.id.toString()
    val postSendState = chatTimelinePlan.postSendState
    val timelineLoading = chatTimelinePlan.timelineLoading
    val chatRegexes = settings.regexes
    val hasTailCompletedCompactMarkers =
        compaction.completedMarkersByTimelineEndIndex[compaction.tailTimelineEndIndex].orEmpty().isNotEmpty()
    val hasTailLifecycleCompletedCompactMarker =
        compaction.lifecycleCompletedTimelineEndIndex == compaction.tailTimelineEndIndex
    val hasTailActiveCompactMarker =
        compaction.activeCompactTimelineEndIndex == compaction.tailTimelineEndIndex
    val timelineEntries = chatTimelinePlan.entries
    items(
        count = timelineEntries.size,
        key = { planIndex ->
            timelineEntries[planIndex].lazyItemKey(
                conversation.id, postSendState, currentPendingUserMessages, compaction.tailCompactItemKey,
            )
        },
        contentType = { planIndex -> timelineEntries[planIndex].lazyContentType() },
    ) { planIndex ->
        val entry = timelineEntries[planIndex]
        when (entry) {
            ChatTimelineEntry.HistoryLoading -> {
                TimelineHistoryLoadingIndicator(
                    prefetching = timelineLoadState.prefetchingOlder,
                    loadedNodeCount = timelineLoadState.loadedNodeCount,
                    totalNodeCount = timelineLoadState.totalNodeCount,
                    modifier = Modifier.padding(bottom = TimelineItemSpacing),
                )
            }

            is ChatTimelineEntry.PostSendHiddenAssistant -> {
                // Defensive zero-size placeholder: the plan counts
                // this entry, and under reversed emission every
                // older message would come after it — skipping the
                // item would misalign lazyItemMessageIndexes with
                // real lazy indexes. (Currently unreachable: the
                // hidden tail assistant is always protected as a
                // single item, see tailAssistantMessageIndexes.)
                Spacer(Modifier.fillMaxWidth())
            }

            is ChatTimelineEntry.Message -> {
                val index = entry.messageIndex
                val node = entry.node
                val isLastMessage = index == conversation.messageNodes.lastIndex
                val isLoadingMessage = timelineLoading && isLastMessage
                val isPreCompacted = compaction.visualCompactedTimelineEndIndex?.let { index <= it } == true
                // iMessage-style send entrance: the just-sent user
                // bubble springs up from the input bar's direction.
                // The claim is one-shot and windowed (see tracker),
                // and only the freshly landed tail can take it —
                // scroll-backs re-composing old user items never
                // re-animate.
                val playSendEntrance = node.currentMessage.role == MessageRole.USER &&
                    index >= conversation.messageNodes.lastIndex - 1 &&
                    remember(node.id) {
                        ChatSendTransitionTracker.consumeSendEntrance(conversationId)
                    }
                Column(
                    modifier = Modifier
                        .then(
                            // 流式尾部平滑生长：reverseLayout 锚定底部边，
                            // 每次换行/增块的高度增长默认是单帧阶梯跳
                            // （"一行一行往上跳"）。单处 animateContentSize
                            // 把每次增高变成连续伸展 → 已读内容平滑上滑。
                            // 规格用 no-bounce 弹簧而不是 tween：换行每
                            // ~150ms 重定向一次，弹簧重定向时保持当前速度
                            // （速度连续），tween 每次重启都从 0 加速——
                            // 那是 2026-08-16 录屏里 6Hz 脉动的来源。
                            // 启用窗口覆盖 loading + drain 宽限期（生成结束
                            // 后尾巴还在逐帧生长、动作行也在此刻插入）。
                            // 注意 2026-05-14 教训（ChatMessage.kt:235 注释）：
                            // 嵌套多层动画曾致流式卡顿——只允许这一处、
                            // 永远不要在消息内部再叠加。
                            // 离底阅读时由 holdReadingOnTailGrowth 抵消增高（滞后一帧），
                            // 近底仍原生钉底跟随；它须在最外层，读到动画后的尺寸。
                            if (isLoadingMessage || (isLastMessage && drainAmortizeGrace)) {
                                Modifier.holdReadingOnTailGrowth(
                                    state = state,
                                    lazyIndex = planIndex,
                                    followBottomPx = bottomPinBufferPx,
                                ).animateContentSize(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(bottom = TimelineItemSpacing)
                ) {
                    TimelineCompactMarkers(
                        timelineEndIndex = index - 1,
                        completedMarkersByTimelineEndIndex = compaction.completedMarkersByTimelineEndIndex,
                        lifecycleCompletedTimelineEndIndex = compaction.lifecycleCompletedTimelineEndIndex,
                        compactLifecycleState = compactLifecycleState,
                        activeCompactTimelineEndIndex = compaction.activeCompactTimelineEndIndex,
                        activeCompactStreamingSummary = compaction.activeCompactStreamingSummary,
                        modifier = Modifier.padding(bottom = TimelineItemSpacing),
                    )
                    ListSelectableItem(
                        modifier = (if (isPreCompacted) Modifier.alpha(0.4f) else Modifier)
                            .then(sendEntranceModifier(play = playSendEntrance))
                            .timelineHighlight(node.id),
                        key = node.id,
                        onSelectChange = { selection.toggle(node.id) },
                        selectedKeys = selection.selectedItems,
                        enabled = selection.selecting,
                    ) {
                        val messageModel = remember(
                            node.currentMessage.modelId,
                            node.currentMessage.role,
                            isLoadingMessage,
                            settings.providers,
                            settings.chatModelId,
                        ) {
                            node.currentMessage.modelId?.let { settings.findModelById(it) }
                                ?: if (isLoadingMessage && node.currentMessage.role == MessageRole.ASSISTANT) {
                                    settings.getCurrentChatModel()
                                } else {
                                    null
                                }
                        }
                        ChatMessage(
                            node = node,
                            model = messageModel,
                            regexes = chatRegexes,
                            loading = isLoadingMessage,
                            timelineLoading = timelineLoading,
                            onRegenerate = {
                                onRegenerate(node.currentMessage)
                            },
                            onEdit = {
                                onEdit(node.currentMessage)
                            },
                            onQuote = onQuote,
                            onFork = {
                                onForkMessage(node.currentMessage)
                            },
                            onDelete = {
                                onDelete(node.currentMessage)
                            },
                            onShare = { selection.shareThrough(conversation, index) },
                            onUpdate = {
                                onUpdateMessage(it)
                            },
                            isFavorite = node.isFavorite,
                            onToggleFavorite = {
                                onToggleFavorite?.invoke(node)
                            },
                            onSaveToWorkspace = onSaveToWorkspace,
                            onToolApproval = onToolApproval,
                            onToolAnswer = onToolAnswer,
                            onOpenWorkspaceFile = onOpenWorkspaceFile,
                            themeTryOnCardTarget = themeTryOnCardTarget,
                            onGenerativeWidgetAction = onGenerativeWidgetAction,
                            onMiniAppModify = onMiniAppModify,
                            lastMessage = isLastMessage,
                        )
                    }
                }
            }

            is ChatTimelineEntry.VirtualMessage -> {
                val index = entry.messageIndex
                val node = entry.node
                val virtualItem = entry.item
                val isLastMessage = index == conversation.messageNodes.lastIndex
                val isLoadingMessage = timelineLoading && isLastMessage
                // In reversed emission the entry at planIndex - 1 is the
                // slice visually BELOW this one — a slice's bottom
                // padding separates it from that lower slice (padding
                // stays in visual coordinates under reverseLayout).
                // The reading-first slice (virtualIndex == 0) is
                // emitted last and shows the compact markers /
                // selection checkbox at visual top.
                val sliceBelowEntry = chatTimelinePlan.entries.getOrNull(planIndex - 1)
                val sliceBelow = (sliceBelowEntry as? ChatTimelineEntry.VirtualMessage)
                    ?.takeIf { it.messageIndex == index }
                val bottomPadding = when {
                    sliceBelow == null -> TimelineItemSpacing
                    sliceBelow.item.isAdjacentMarkdownChild(virtualItem) -> 0.dp
                    else -> TimelineMessageInnerSpacing
                }
                Column(
                    modifier = Modifier.padding(bottom = bottomPadding),
                ) {
                    if (entry.virtualIndex == 0) {
                        TimelineCompactMarkers(
                            timelineEndIndex = index - 1,
                            completedMarkersByTimelineEndIndex = compaction.completedMarkersByTimelineEndIndex,
                            lifecycleCompletedTimelineEndIndex = compaction.lifecycleCompletedTimelineEndIndex,
                            compactLifecycleState = compactLifecycleState,
                            activeCompactTimelineEndIndex = compaction.activeCompactTimelineEndIndex,
                            activeCompactStreamingSummary = compaction.activeCompactStreamingSummary,
                            modifier = Modifier.padding(bottom = TimelineItemSpacing),
                        )
                    }
                    TimelineSelectableMessageItem(
                        modifier = (if (compaction.visualCompactedTimelineEndIndex?.let { index <= it } == true) {
                            Modifier.alpha(0.4f)
                        } else {
                            Modifier
                        }).timelineHighlight(node.id, slice = true),
                        key = node.id,
                        onSelectChange = { selection.toggle(node.id) },
                        selectedKeys = selection.selectedItems,
                        enabled = selection.selecting,
                        showCheckbox = entry.virtualIndex == 0,
                    ) {
                        val messageModel = remember(
                            node.currentMessage.modelId,
                            node.currentMessage.role,
                            isLoadingMessage,
                            settings.providers,
                            settings.chatModelId,
                        ) {
                            node.currentMessage.modelId?.let { settings.findModelById(it) }
                                ?: if (isLoadingMessage && node.currentMessage.role == MessageRole.ASSISTANT) {
                                    settings.getCurrentChatModel()
                                } else {
                                    null
                                }
                        }
                        ChatMessageVirtualItemContent(
                            node = node,
                            item = virtualItem,
                            model = messageModel,
                            regexes = chatRegexes,
                            loading = isLoadingMessage,
                            onRegenerate = {
                                onRegenerate(node.currentMessage)
                            },
                            onEdit = {
                                onEdit(node.currentMessage)
                            },
                            onQuote = onQuote,
                            onFork = {
                                onForkMessage(node.currentMessage)
                            },
                            onDelete = {
                                onDelete(node.currentMessage)
                            },
                            onShare = { selection.shareThrough(conversation, index) },
                            onUpdate = {
                                onUpdateMessage(it)
                            },
                            isFavorite = node.isFavorite,
                            onToggleFavorite = {
                                onToggleFavorite?.invoke(node)
                            },
                            onSaveToWorkspace = onSaveToWorkspace,
                            onToolApproval = onToolApproval,
                            onToolAnswer = onToolAnswer,
                            onOpenWorkspaceFile = onOpenWorkspaceFile,
                            themeTryOnCardTarget = themeTryOnCardTarget,
                            onGenerativeWidgetAction = onGenerativeWidgetAction,
                            onMiniAppModify = onMiniAppModify,
                            lastMessage = isLastMessage,
                        )
                    }
                }
            }

            ChatTimelineEntry.PostSendWaitingAssistant -> {
                PostSendWaitingIndicator(
                    visible = showBottomFollowAnimation,
                    modifier = Modifier.padding(bottom = TimelineItemSpacing),
                )
            }

            is ChatTimelineEntry.Pending -> {
                val pendingMessage = currentPendingUserMessages[entry.pendingIndex]
                Box(modifier = Modifier.padding(bottom = TimelineItemSpacing)) {
                    PendingUserMessageBubble(
                        message = pendingMessage,
                        onCancel = { onCancelPendingMessage(pendingMessage.id) },
                        queueCount = if (entry.pendingIndex == currentPendingUserMessages.lastIndex) {
                            currentPendingUserMessages.size
                        } else {
                            null
                        },
                        onOpenQueue = onOpenQueue,
                    )
                }
            }

            ChatTimelineEntry.TailCompactMarkers -> {
                if (
                    hasTailCompletedCompactMarkers ||
                    hasTailLifecycleCompletedCompactMarker ||
                    hasTailActiveCompactMarker
                ) {
                    TimelineCompactMarkers(
                        timelineEndIndex = compaction.tailTimelineEndIndex,
                        completedMarkersByTimelineEndIndex = compaction.completedMarkersByTimelineEndIndex,
                        lifecycleCompletedTimelineEndIndex = compaction.lifecycleCompletedTimelineEndIndex,
                        compactLifecycleState = compactLifecycleState,
                        activeCompactTimelineEndIndex = compaction.activeCompactTimelineEndIndex,
                        activeCompactStreamingSummary = compaction.activeCompactStreamingSummary,
                        modifier = Modifier.padding(bottom = TimelineItemSpacing),
                    )
                } else {
                    // Zero-size placeholder: the plan always counts
                    // this entry, so skipping the item would shift
                    // lazyItemMessageIndexes against real lazy indexes.
                    Spacer(Modifier.fillMaxWidth())
                }
            }

            ChatTimelineEntry.TimelineTail -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (tailIndicatorReserveVisible) {
                        TimelineTailWorkingIndicator(
                            processingStatus = processingStatus,
                            visible = tailIndicatorDotVisible,
                            modifier = Modifier.padding(bottom = TimelineItemSpacing),
                        )
                    }
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(ScrollBottomSpacerHeight)
                    )
                }
            }
        }
    }
}

/**
 * iMessage-style send entrance for the just-sent user bubble: a spring slide
 * up from the input bar's direction with a slight bottom-right-anchored scale.
 * Pure graphicsLayer — the item's layout slot is final from frame one, the
 * bubble just travels into it, so the surrounding timeline never reflows.
 */
@Composable
private fun sendEntranceModifier(play: Boolean): Modifier {
    if (!play) return Modifier
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }
    val slidePx = with(LocalDensity.current) { SendEntranceSlideDistance.toPx() }
    return Modifier.graphicsLayer {
        val eased = progress.value
        translationY = slidePx * (1f - eased)
        alpha = (0.4f + 0.6f * eased).coerceIn(0f, 1f)
        val scale = (0.96f + 0.04f * eased).coerceAtMost(1.01f)
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(0.92f, 1f)
    }
}
