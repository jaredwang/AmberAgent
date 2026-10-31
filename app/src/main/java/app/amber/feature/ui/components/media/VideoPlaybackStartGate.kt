package app.amber.feature.ui.components.media

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Guards auto-start when `MediaPlayer` prepare returns after the host has
 * already stopped, and drops callbacks that belong to a previous URL / retry.
 *
 * `prepared` is asynchronous. If the app is already `ON_STOP` (or the session
 * was invalidated by a URL change / dispose), [shouldStartOnPrepared] is false
 * so the new player does not start in the background.
 */
internal class VideoPlaybackStartGate(
    startAllowed: Boolean,
) {
    private val allowed = AtomicBoolean(startAllowed)
    private val generation = AtomicInteger(0)

    fun generation(): Int = generation.get()

    fun onHostStarted() {
        allowed.set(true)
    }

    fun onHostStopped() {
        allowed.set(false)
    }

    /**
     * Marks this session dead. In-flight `onPrepared` for [generation] must not
     * start, and a later player must use a new gate (new URL / retry).
     */
    fun invalidate() {
        generation.incrementAndGet()
        allowed.set(false)
    }

    fun shouldStartOnPrepared(preparedGeneration: Int): Boolean {
        return preparedGeneration == generation.get() && allowed.get()
    }
}
