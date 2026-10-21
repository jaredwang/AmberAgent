package app.amber.feature.ui.pages.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.launch

/** Only an idle, pinned viewport animates height; reading/gestures use natural layout. */
@Composable
internal fun Modifier.streamingTailLayout(
    state: LazyListState,
    lazyIndex: Int,
    followBottomPx: Int,
): Modifier {
    val animateHeight by remember(state, followBottomPx) {
        derivedStateOf {
            shouldAnimateStreamingTail(
                state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset,
                followBottomPx, state.isScrollInProgress,
            )
        }
    }
    return holdReadingOnTailGrowth(state, lazyIndex, followBottomPx).then(
        if (animateHeight) Modifier.animateContentSize(
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        ) else Modifier,
    )
}

internal fun shouldAnimateStreamingTail(index: Int, offset: Int, threshold: Int, scrolling: Boolean): Boolean =
    !scrolling && index == 0 && offset <= threshold

/**
 * 离底阅读时钉住正在流式增高的尾消息上方的已读内容。
 *
 * reverseLayout 下 LazyList 保持最底可见 item 的底边不动：尾消息（或它下方的
 * 尾部 item）作锚时，每次增高 ΔH 都会把已读内容顶上去 ΔH——上滑后仍被持续带走。
 * 空闲时在 **measure** 里 `requestScrollToItem`（可在测量阶段调用，下一轮
 * 同帧 layout 用上新 offset）。不能在 measure 里 `dispatchRawDelta`：会在
 * 测量中 forceRemeasure，Robolectric 上抛 IllegalArgumentException。
 * 拖动/惯性中不调用 `requestScrollToItem`（它会取消手势）；手势期间高度
 * 变化在 onGloballyPositioned 之后用协程 `dispatchRawDelta` 补偿，不取消
 * 手势。不能在 OnPositioned 回调里同步滚：补偿越出乐观平移时 LazyList
 * 会同步 forceRemeasure、移除挤出视口的 item 节点，而
 * OnPositionedDispatcher 正在遍历同一批 children → NPE（Issue #21）。
 * 补偿只随自然高度变化发生；离底与手势期间不叠加高度动画。
 * 近底（≤ [followBottomPx]）时不补偿，保留原生钉底跟随。
 */
internal fun Modifier.holdReadingOnTailGrowth(
    state: LazyListState,
    lazyIndex: Int,
    followBottomPx: Int,
): Modifier = this then HoldReadingOnTailGrowthElement(state, lazyIndex, followBottomPx)

private data class HoldReadingOnTailGrowthElement(
    val state: LazyListState,
    val lazyIndex: Int,
    val followBottomPx: Int,
) : ModifierNodeElement<HoldReadingOnTailGrowthNode>() {
    override fun create() = HoldReadingOnTailGrowthNode(state, lazyIndex, followBottomPx)

    override fun update(node: HoldReadingOnTailGrowthNode) {
        node.state = state
        node.lazyIndex = lazyIndex
        node.followBottomPx = followBottomPx
    }
}

private class HoldReadingOnTailGrowthNode(
    var state: LazyListState,
    var lazyIndex: Int,
    var followBottomPx: Int,
) : Modifier.Node(), LayoutModifierNode, GlobalPositionAwareModifierNode {
    private var lastHeight = -1
    private var applyingHold = false
    private var pendingDelta = 0
    private var applyScheduled = false

    // LazyList 复用 slot 时上一 item 的高度不是本 item 的增高基线。
    override fun onReset() {
        lastHeight = -1
        pendingDelta = 0
    }

    override fun onDetach() {
        pendingDelta = 0
        applyScheduled = false
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        val height = placeable.height
        // Idle: requestScrollToItem is legal in measure and the next layout
        // pass in this frame places with the compensated offset. Skip while a
        // gesture owns the viewport — that API cancels drag/fling.
        if (!state.isScrollInProgress) {
            if (!applyingHold) {
                applyingHold = true
                try {
                    applyIdleHold(height)
                } finally {
                    applyingHold = false
                }
            } else {
                lastHeight = height
            }
        }
        return layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!state.isScrollInProgress) return
        val height = coordinates.size.height
        val previous = lastHeight
        lastHeight = height
        if (previous < 0 || !shouldHold()) return
        val delta = height - previous
        if (delta == 0) return
        pendingDelta += delta
        if (applyScheduled) return
        applyScheduled = true
        coroutineScope.launch {
            applyScheduled = false
            val pending = pendingDelta
            pendingDelta = 0
            if (pending != 0 && shouldHold()) {
                state.dispatchRawDelta(pending.toFloat())
            }
        }
    }

    private fun applyIdleHold(height: Int) {
        val previous = lastHeight
        lastHeight = height
        if (previous < 0 || !shouldHold()) return
        val delta = height - previous
        if (delta == 0 || state.isScrollInProgress) return
        val index = state.firstVisibleItemIndex
        val offset = (state.firstVisibleItemScrollOffset + delta).coerceAtLeast(0)
        state.requestScrollToItem(index, offset)
    }

    private fun shouldHold(): Boolean {
        val nearBottom = state.firstVisibleItemIndex == 0 &&
            state.firstVisibleItemScrollOffset <= followBottomPx
        if (nearBottom || lazyIndex < state.firstVisibleItemIndex) return false
        val lastVisibleIndex = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
        return lazyIndex <= lastVisibleIndex
    }
}
