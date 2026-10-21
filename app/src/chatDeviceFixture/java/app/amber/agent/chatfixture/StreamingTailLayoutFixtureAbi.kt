package app.amber.agent.chatfixture

import androidx.annotation.Keep
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.amber.feature.ui.pages.chat.streamingTailLayout as productionStreamingTailLayout

/**
 * Fixture-only streamingTailLayout overloads. Compiled into the app only
 * when `-PchatDeviceFixture=true`. Production `main` does not ship this
 * class. Device tests import this package so HEAD and the b4477f42b
 * worktree baseline resolve the same JVM class.
 */
@Keep
@Composable
fun Modifier.streamingTailLayout(
    state: LazyListState,
    lazyIndex: Int,
    followBottomPx: Int,
): Modifier = productionStreamingTailLayout(
    state, lazyIndex, followBottomPx, animate = true, itemKey = null,
)

@Keep
@Composable
fun Modifier.streamingTailLayout(
    state: LazyListState,
    lazyIndex: Int,
    followBottomPx: Int,
    animate: Boolean,
    itemKey: Any?,
): Modifier = productionStreamingTailLayout(
    state, lazyIndex, followBottomPx, animate, itemKey,
)
