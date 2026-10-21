package app.amber.feature.ui.pages.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.amber.feature.ui.components.richtext.StreamingPresentationHandover
import kotlinx.coroutines.delay

/**
 * Whether a drained streaming message may swap to the settled renderer now.
 *
 * The timeline is a reverseLayout LazyColumn: the bottom edge of the first
 * visible item is the scroll anchor and lazy offsets grow upward. A height
 * change of any visible message therefore moves everything above it, even
 * when the reading anchor is in an older message.
 *
 * [holdReadingOnTailGrowth] compensates reverseLayout growth two ways:
 * idle viewports call [LazyListState.requestScrollToItem] during measure so
 * the next layout pass in the same frame places the content above with the
 * compensated offset; in-progress drag/fling uses a coroutine
 * [LazyListState.dispatchRawDelta] from onGloballyPositioned so the gesture
 * is not cancelled. Visible off-bottom swaps are therefore allowed as soon
 * as the reader is idle. After a gesture ends the next gate poll swaps
 * within the same idle frame (well under 2s).
 */
@Suppress("UNUSED_PARAMETER")
internal fun streamingSettleAllowed(
    firstVisibleIndex: Int,
    firstVisibleOffset: Int,
    followBottomPx: Int,
    itemVisible: Boolean,
    scrollInProgress: Boolean,
): Boolean {
    if (scrollInProgress) return false
    // Geometry is compensated in the same measure pass by
    // [holdReadingOnTailGrowth]; pinned, invisible, and visible off-bottom
    // swaps are all safe once the reader is idle.
    return true
}

/**
 * Per-message handover state owned by the timeline. The gate reads
 * [LazyListState.layoutInfo] only when a waiting block polls it through a
 * snapshotFlow, so idle and settled messages add no per-frame work and no
 * recomposition.
 */
@Stable
internal class TimelineStreamingHandover(
    private val state: LazyListState,
    private val itemKey: Any,
    private val followBottomPx: Int,
) : StreamingPresentationHandover {
    private var awaitingCount by mutableIntStateOf(0)

    val awaiting: Boolean get() = awaitingCount > 0

    override fun allowsSettledPresentation(): Boolean = streamingSettleAllowed(
        firstVisibleIndex = state.firstVisibleItemIndex,
        firstVisibleOffset = state.firstVisibleItemScrollOffset,
        followBottomPx = followBottomPx,
        // visibleItemsInfo excludes beyond-bounds/prefetched extras.
        itemVisible = state.layoutInfo.visibleItemsInfo.any { it.key == itemKey },
        scrollInProgress = state.isScrollInProgress,
    )

    override fun onAwaitingChange(awaiting: Boolean) {
        awaitingCount = (awaitingCount + if (awaiting) 1 else -1).coerceAtLeast(0)
    }
}

/**
 * Keeps the reading hold attached while [active] (generating or awaiting a
 * handover) and for a short grace after it. The latch bridges the frame where
 * loading ends (the final coalesced chunk lands then, before drain grace or
 * the awaiting report exist) and the settled tree's first asynchronous height
 * adjustments; a gap would recreate the hold node and lose that height delta.
 */
@Composable
internal fun rememberHandoverReadingHold(active: Boolean): Boolean {
    var lingering by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        if (active) {
            lingering = true
        } else if (lingering) {
            delay(StreamingDrainAmortizeGraceMs)
            lingering = false
        }
    }
    return active || lingering
}
