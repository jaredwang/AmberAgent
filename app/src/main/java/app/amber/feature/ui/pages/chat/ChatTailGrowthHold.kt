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
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints

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
 * 补偿在 **measure** 里同步 `dispatchRawDelta`，好让同一次布局的 place
 * 用上新的 scroll offset（onGloballyPositioned 后再滚会晚一帧）。
 * dispatchRawDelta 不经 scroll mutex，不取消进行中的拖动/惯性。不能在
 * OnPositioned 回调里同步滚：补偿越出乐观平移时 LazyList 会同步
 * forceRemeasure、移除挤出视口的 item 节点，而 OnPositionedDispatcher
 * 正在遍历同一批 children → NPE（Issue #21）。
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
) : Modifier.Node(), LayoutModifierNode {
    private var lastHeight = -1
    private var applyingHold = false

    // LazyList 复用 slot 时上一 item 的高度不是本 item 的增高基线。
    override fun onReset() {
        lastHeight = -1
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        val height = placeable.height
        if (!applyingHold) {
            applyingHold = true
            try {
                applyHold(height)
            } finally {
                applyingHold = false
            }
        } else {
            lastHeight = height
        }
        return layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }

    private fun applyHold(height: Int) {
        val previous = lastHeight
        lastHeight = height
        if (previous < 0) return
        val nearBottom = state.firstVisibleItemIndex == 0 &&
            state.firstVisibleItemScrollOffset <= followBottomPx
        // 增长 item 已在锚点之下时，按 key 锚定本就不会牵动可见内容。
        if (nearBottom || lazyIndex < state.firstVisibleItemIndex) return
        // A composed item wholly above the viewport (pinned/beyond-bounds) moves
        // nothing visible when it changes height; compensating would.
        val lastVisibleIndex = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return
        if (lazyIndex > lastVisibleIndex) return
        val delta = height - previous
        if (delta == 0) return
        state.dispatchRawDelta(delta.toFloat())
    }
}
