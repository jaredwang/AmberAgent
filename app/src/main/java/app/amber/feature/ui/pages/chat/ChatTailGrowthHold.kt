package app.amber.feature.ui.pages.chat

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
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

/**
 * Idle pinned viewports may animate *streaming* height. Discrete jumps
 * after generation (settled tree, async image decode) must use reverseLayout's
 * native bottom pin: [animateContentSize] interpolates the measured height
 * and the last token overflows the timeline until the spring finishes
 * (device productionPinned: RICH_TAIL 2511 > timeline 2056, then 1741).
 */
@Composable
internal fun Modifier.streamingTailLayout(
    state: LazyListState,
    lazyIndex: Int,
    followBottomPx: Int,
    animate: Boolean = true,
    itemKey: Any? = null,
): Modifier {
    val animateHeight by remember(state, followBottomPx, animate) {
        derivedStateOf {
            animate && shouldAnimateStreamingTail(
                state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset,
                followBottomPx, state.isScrollInProgress,
            )
        }
    }
    return holdReadingOnTailGrowth(state, lazyIndex, followBottomPx, itemKey).then(
        if (animateHeight) Modifier.animateContentSize(
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        ) else Modifier,
    )
}

internal fun shouldAnimateStreamingTail(index: Int, offset: Int, threshold: Int, scrolling: Boolean): Boolean =
    !scrolling && index == 0 && offset <= threshold

/**
 * First-visible (index, offset) the idle hold should compensate against.
 *
 * LazyList publishes [publishedIndex]/[publishedOffset] a frame late.
 * Device completion on 392288d7 logged `first=0/0` while
 * `visibleItemsInfo` already listed the growing message at index 2;
 * treating that published pair as pinned dropped the last 19-char
 * (66–132px) streaming delta. Prefer an in-flight [requestedIndex]
 * that is still in [visibleItems], then the lowest index actually in
 * layoutInfo, then the published pair.
 */
internal fun readingAnchorFromLayout(
    visibleItems: List<LazyListItemInfo>,
    viewportStartOffset: Int,
    publishedIndex: Int,
    publishedOffset: Int,
    requestedIndex: Int,
    requestedOffset: Int,
): Pair<Int, Int> {
    if (requestedIndex >= 0 && visibleItems.any { it.index == requestedIndex }) {
        return requestedIndex to requestedOffset
    }
    val layoutFirst = visibleItems.minByOrNull { it.index } ?: return publishedIndex to publishedOffset
    val offset = if (layoutFirst.index == publishedIndex) {
        publishedOffset
    } else {
        (viewportStartOffset - layoutFirst.offset).coerceAtLeast(0)
    }
    return layoutFirst.index to offset
}

/** Hold iff the reader is not pinned to the reverseLayout bottom. */
internal fun shouldHoldReadingOnTailGrowth(
    firstIndex: Int,
    firstOffset: Int,
    followBottomPx: Int,
    growingItemVisible: Boolean,
): Boolean {
    if (firstIndex == 0 && firstOffset <= followBottomPx) return false
    return growingItemVisible
}

/**
 * Idle hold target after a height delta. Growing adds to the first-visible
 * offset. Shrinking below 0 walks toward newer items (lower index in
 * reverseLayout). Growing past the first-visible item's size walks toward
 * older items (higher index) so a small pinned-off tail does not clamp
 * away thousands of pixels of compensation.
 */
internal fun compensatedScrollTarget(
    firstVisibleIndex: Int,
    firstVisibleOffset: Int,
    delta: Int,
    itemSize: (Int) -> Int?,
): Pair<Int, Int> {
    var index = firstVisibleIndex
    var offset = firstVisibleOffset + delta
    while (offset < 0 && index > 0) {
        val prevSize = itemSize(index - 1) ?: break
        if (prevSize <= 0) break
        index -= 1
        offset += prevSize
    }
    while (true) {
        val size = itemSize(index) ?: break
        if (size <= 0 || offset < size) break
        offset -= size
        index += 1
    }
    return index to offset.coerceAtLeast(0)
}

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
    itemKey: Any? = null,
): Modifier = this then HoldReadingOnTailGrowthElement(state, lazyIndex, followBottomPx, itemKey)

private data class HoldReadingOnTailGrowthElement(
    val state: LazyListState,
    val lazyIndex: Int,
    val followBottomPx: Int,
    val itemKey: Any? = null,
) : ModifierNodeElement<HoldReadingOnTailGrowthNode>() {
    override fun create() = HoldReadingOnTailGrowthNode(state, lazyIndex, followBottomPx, itemKey)

    override fun update(node: HoldReadingOnTailGrowthNode) {
        node.state = state
        node.lazyIndex = lazyIndex
        node.followBottomPx = followBottomPx
        node.itemKey = itemKey
    }
}

private class HoldReadingOnTailGrowthNode(
    var state: LazyListState,
    var lazyIndex: Int,
    var followBottomPx: Int,
    var itemKey: Any? = null,
) : Modifier.Node(), LayoutModifierNode, GlobalPositionAwareModifierNode {
    private var lastHeight = -1
    private var applyingHold = false
    private var pendingDelta = 0
    private var pendingIdleDelta = 0
    private var applyScheduled = false
    private var reconcileScheduled = false
    // LazyList publishes firstVisibleItemScrollOffset a frame late.
    // Subsequent measure passes that re-read state overwrite the in-flight
    // requestScrollToItem. Remember the last requested target on this node
    // and add the next delta to that, not to stale state.
    private var requestedIndex = -1
    private var requestedOffset = -1
    // requestScrollToItem flags isScrollInProgress. That is not a user
    // gesture: skipping idle hold for it drops the next height delta
    // (device fling generating −144px after the same idle path as drag).
    private var programmaticScroll = false

    // LazyList 复用 slot 时上一 item 的高度不是本 item 的增高基线。
    override fun onReset() {
        lastHeight = -1
        pendingDelta = 0
        pendingIdleDelta = 0
        reconcileScheduled = false
        requestedIndex = -1
        requestedOffset = -1
        programmaticScroll = false
    }

    override fun onDetach() {
        pendingDelta = 0
        applyScheduled = false
        reconcileScheduled = false
        requestedIndex = -1
        requestedOffset = -1
        programmaticScroll = false
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        val height = placeable.height
        if (!state.isScrollInProgress) programmaticScroll = false
        // Idle / our own requestScrollToItem: compensate in measure.
        // A real user drag/fling owns isScrollInProgress without
        // programmaticScroll; requestScrollToItem would cancel it.
        if (!userGestureOwnsScroll()) {
            applyIdleHold(height)
        }
        return layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        // Device production completion (392288d7): requestScrollToItem(2, 171)
        // during measure of the growing first-visible item did not stick
        // (scrollValue stayed 1105 while the token moved 132px). After
        // layout, dispatch the missed offset. Skip when the published
        // pair already matches — chunk-ui same-frame hold still uses
        // requestScrollToItem alone.
        if (!userGestureOwnsScroll() && requestedIndex >= 0 && !applyingHold) {
            reconcileIdleRequest()
        }
        if (applyingHold || programmaticScroll || !state.isScrollInProgress) return
        // Real gesture: drop the idle target so the next idle hold
        // starts from the post-gesture first-visible item, not the
        // pre-drag requestScrollToItem offset.
        requestedIndex = -1
        requestedOffset = -1
        programmaticScroll = false
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

    private fun reconcileIdleRequest() {
        if (requestedIndex < 0) return
        if (state.firstVisibleItemIndex != requestedIndex) return
        val missed = requestedOffset - state.firstVisibleItemScrollOffset
        if (missed == 0 || reconcileScheduled) return
        reconcileScheduled = true
        coroutineScope.launch {
            try {
                withFrameNanos { }
                if (userGestureOwnsScroll() || requestedIndex < 0) return@launch
                if (state.firstVisibleItemIndex != requestedIndex) return@launch
                val stillMissed = requestedOffset - state.firstVisibleItemScrollOffset
                if (stillMissed == 0) return@launch
                state.dispatchRawDelta(stillMissed.toFloat())
            } finally {
                reconcileScheduled = false
            }
        }
    }

    private fun applyIdleHold(height: Int) {
        val previous = lastHeight
        val holding = shouldHold()
        if (previous < 0 || !holding) {
            lastHeight = height
            requestedIndex = -1
            requestedOffset = -1
            programmaticScroll = false
            return
        }
        val delta = height - previous
        lastHeight = height
        if (delta == 0) return
        if (userGestureOwnsScroll()) return
        if (applyingHold) {
            // requestScrollToItem remeasures this node. Fold the nested
            // height into a follow-up compensation instead of dropping it
            // by overwriting lastHeight.
            pendingIdleDelta += delta
            return
        }
        applyingHold = true
        var remaining = 0
        try {
            val visible = state.layoutInfo.visibleItemsInfo
            val anchor = readingAnchorFromLayout(
                visibleItems = visible,
                viewportStartOffset = state.layoutInfo.viewportStartOffset,
                publishedIndex = state.firstVisibleItemIndex,
                publishedOffset = state.firstVisibleItemScrollOffset,
                requestedIndex = requestedIndex,
                requestedOffset = requestedOffset,
            )
            var index = anchor.first
            var offset = anchor.second
            remaining = delta + pendingIdleDelta
            pendingIdleDelta = 0
            while (remaining != 0) {
                if (userGestureOwnsScroll()) break
                val vis = state.layoutInfo.visibleItemsInfo
                val sizes = vis.associate { it.index to it.size }
                val growingIndex = itemKey?.let { key ->
                    vis.firstOrNull { it.key == key }?.index
                } ?: lazyIndex
                val next = compensatedScrollTarget(
                    firstVisibleIndex = index,
                    firstVisibleOffset = offset,
                    delta = remaining,
                    itemSize = { itemIndex ->
                        if (itemIndex == growingIndex) lastHeight else sizes[itemIndex]
                    },
                )
                index = next.first
                offset = next.second
                requestedIndex = index
                requestedOffset = offset
                programmaticScroll = true
                state.requestScrollToItem(index, offset)
                remaining = pendingIdleDelta
                pendingIdleDelta = 0
            }
        } finally {
            applyingHold = false
            if (remaining != 0) pendingIdleDelta += remaining
        }
    }

    private fun userGestureOwnsScroll(): Boolean =
        state.isScrollInProgress && !programmaticScroll && requestedIndex < 0

    private fun shouldHold(): Boolean {
        val visible = state.layoutInfo.visibleItemsInfo
        val anchor = readingAnchorFromLayout(
            visibleItems = visible,
            viewportStartOffset = state.layoutInfo.viewportStartOffset,
            publishedIndex = state.firstVisibleItemIndex,
            publishedOffset = state.firstVisibleItemScrollOffset,
            requestedIndex = requestedIndex,
            requestedOffset = requestedOffset,
        )
        val growingVisible = itemKey?.let { key -> visible.any { it.key == key } }
            ?: (visible.any { it.index == lazyIndex } ||
                (requestedIndex >= 0 && visible.any { it.index == requestedIndex }))
        return shouldHoldReadingOnTailGrowth(
            firstIndex = anchor.first,
            firstOffset = anchor.second,
            followBottomPx = followBottomPx,
            growingItemVisible = growingVisible,
        )
    }
}
