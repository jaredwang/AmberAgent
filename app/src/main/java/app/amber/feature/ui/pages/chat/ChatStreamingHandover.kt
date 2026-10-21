package app.amber.feature.ui.pages.chat

import androidx.compose.foundation.lazy.LazyListLayoutInfo
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
 * Midpoint of the LazyList viewport in the same offset space as
 * [androidx.compose.foundation.lazy.LazyListItemInfo.offset] / size.
 *
 * Device tests treat a visible token's unclipped `positionInRoot` +
 * `getBoundingBox` as the reading anchor. That token sits on screen, so
 * the viewport midpoint is the layoutInfo equivalent: a long reply that
 * fills the viewport contains the midpoint (anchor inside the reply);
 * a short reply stuck at the bottom under older content does not
 * (anchor in the older message).
 */
internal fun viewportReadingLine(layoutInfo: LazyListLayoutInfo): Int =
    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2

/** True when [viewportReadingLine] falls in the item identified by [itemKey]. */
internal fun readingLineInsideItem(layoutInfo: LazyListLayoutInfo, itemKey: Any): Boolean {
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey } ?: return false
    val line = viewportReadingLine(layoutInfo)
    return line >= item.offset && line < item.offset + item.size
}

/**
 * Whether a drained streaming message may swap to the settled renderer now.
 *
 * The timeline is a reverseLayout LazyColumn: the bottom edge of the first
 * visible item is the scroll anchor and lazy offsets grow upward. A height
 * change of any visible message therefore moves everything above it.
 *
 * [holdReadingOnTailGrowth] keeps older-item tops in the same measure pass,
 * so a visible reply may swap immediately when the reader is looking at
 * older content. Swapping while the reader is looking *inside* the reply
 * jumps the character they are reading: the settled block tree does not
 * share the streaming single-Text geometry. That case stays deferred until
 * the viewport is pinned, the reply leaves the viewport, or the item
 * leaves composition.
 *
 * Idle rules:
 * 1. Pinned to the timeline bottom → swap.
 * 2. Reply not in `visibleItemsInfo` → swap.
 * 3. [readingLineInsideItem] → defer.
 * 4. Otherwise (older-message anchor, reply still visible) → swap.
 * A drag or fling always defers; the next idle poll applies 1–4.
 */
internal fun streamingSettleAllowed(
    firstVisibleIndex: Int,
    firstVisibleOffset: Int,
    followBottomPx: Int,
    itemVisible: Boolean,
    readingLineInsideItem: Boolean,
    scrollInProgress: Boolean,
): Boolean {
    if (scrollInProgress) return false
    if (!itemVisible) return true
    val pinnedToBottom = firstVisibleIndex == 0 && firstVisibleOffset <= followBottomPx
    if (pinnedToBottom) return true
    if (readingLineInsideItem) return false
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

    override fun allowsSettledPresentation(): Boolean {
        val info = state.layoutInfo
        return streamingSettleAllowed(
            firstVisibleIndex = state.firstVisibleItemIndex,
            firstVisibleOffset = state.firstVisibleItemScrollOffset,
            followBottomPx = followBottomPx,
            // visibleItemsInfo excludes beyond-bounds/prefetched extras.
            itemVisible = info.visibleItemsInfo.any { it.key == itemKey },
            readingLineInsideItem = readingLineInsideItem(info, itemKey),
            scrollInProgress = state.isScrollInProgress,
        )
    }

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
