package app.amber.feature.ui.components.media

import android.content.Context
import android.content.Intent
import android.widget.VideoView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.amber.agent.R

/**
 * Dialog video player: created only after the user taps a timeline chip.
 *
 * Display size follows prepared `videoWidth`/`videoHeight` (and later size
 * changes), contain-fitted into the dialog's inset-padded slot. Unknown sizes
 * use 16:9. Close, leave composition, or change URL releases the player.
 * `ON_STOP` pauses and blocks a late `onPrepared` from starting.
 *
 * Before this change the Surface was a hardcoded 16:9 `fillMaxWidth` box and
 * `onPrepared` always called `start()`. Failure UI offered external open only.
 * This dialog keeps external open and adds a retry that rebuilds the view.
 */
@Composable
internal fun VideoPlaybackDialog(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    var failed by remember(url) { mutableStateOf(false) }
    var retryNonce by remember(url) { mutableIntStateOf(0) }
    var metrics by remember(url, retryNonce) { mutableStateOf(VideoTrackMetrics(0, 0)) }
    val playerView = remember(url, retryNonce) { mutableStateOf<VideoView?>(null) }
    val gate = remember(url, retryNonce) {
        VideoPlaybackStartGate(
            startAllowed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
        )
    }

    DisposableEffect(lifecycleOwner, url, retryNonce) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> gate.onHostStarted()
                Lifecycle.Event.ON_STOP -> {
                    gate.onHostStopped()
                    runCatching { playerView.value?.pause() }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            gate.invalidate()
            val view = playerView.value
            playerView.value = null
            if (view != null) {
                view.setOnPreparedListener(null)
                view.setOnErrorListener(null)
                view.setOnCompletionListener(null)
                runCatching { view.stopPlayback() }
            }
        }
    }

    val content = displaySizeFromPlayerMetrics(metrics)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val fitted = containFitVideo(
                    contentWidth = content.width,
                    contentHeight = content.height,
                    availableWidth = constraints.maxWidth,
                    availableHeight = constraints.maxHeight,
                )
                Surface(
                    modifier = Modifier.size(
                        width = with(density) { fitted.width.toDp() },
                        height = with(density) { fitted.height.toDp() },
                    ),
                    color = Color.Black,
                ) {
                    if (failed) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.chat_video_playback_failed),
                                    color = Color.White,
                                )
                                TextButton(
                                    onClick = {
                                        failed = false
                                        metrics = VideoTrackMetrics(0, 0)
                                        retryNonce++
                                    },
                                ) {
                                    Text(stringResource(R.string.retry))
                                }
                                val external = remember(url) {
                                    runCatching {
                                        Intent(Intent.ACTION_VIEW).apply {
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            data = videoPlaybackUri(context, url)
                                        }
                                    }.getOrNull()
                                }
                                if (external != null) {
                                    TextButton(onClick = { context.startActivity(Intent.createChooser(external, null)) }) {
                                        Text(stringResource(R.string.chat_video_open_external))
                                    }
                                }
                            }
                        }
                    } else {
                        key(url, retryNonce) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    val preparedGeneration = gate.generation()
                                    ContainVideoView(ctx).apply {
                                        playerView.value = this
                                        setOnPreparedListener { player ->
                                            post {
                                                if (gate.generation() != preparedGeneration) return@post
                                                player.isLooping = false
                                                metrics = applyTrackMetrics(player)
                                                player.setOnVideoSizeChangedListener { changed, _, _ ->
                                                    post {
                                                        if (gate.generation() != preparedGeneration) return@post
                                                        metrics = applyTrackMetrics(changed)
                                                    }
                                                }
                                                if (gate.shouldStartOnPrepared(preparedGeneration)) {
                                                    start()
                                                }
                                            }
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            failed = true
                                            true
                                        }
                                        runCatching { setVideoURI(videoPlaybackUri(ctx, url)) }
                                            .onFailure { failed = true }
                                    }
                                },
                                update = { view ->
                                    view.setContentSize(content.width, content.height)
                                },
                                onRelease = { view ->
                                    view.setOnPreparedListener(null)
                                    view.setOnErrorListener(null)
                                    view.setOnCompletionListener(null)
                                    runCatching { view.stopPlayback() }
                                    if (playerView.value === view) playerView.value = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun videoPlaybackUri(context: Context, url: String) =
    if (url.startsWith("http")) {
        url.toUri()
    } else {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            url.toUri().toFile(),
        )
    }
