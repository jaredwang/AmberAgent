package app.amber.feature.ui.components.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackStartGateTest {

    @Test
    fun preparedWhileStartedCanStart() {
        val gate = VideoPlaybackStartGate(startAllowed = true)
        assertTrue(gate.shouldStartOnPrepared(gate.generation()))
    }

    @Test
    fun preparedAfterStopDoesNotStart() {
        val gate = VideoPlaybackStartGate(startAllowed = true)
        val generation = gate.generation()
        gate.onHostStopped()
        assertFalse(gate.shouldStartOnPrepared(generation))
    }

    @Test
    fun preparedAfterInvalidateDoesNotStart() {
        val gate = VideoPlaybackStartGate(startAllowed = true)
        val generation = gate.generation()
        gate.invalidate()
        assertFalse(gate.shouldStartOnPrepared(generation))
        assertEquals(generation + 1, gate.generation())
    }

    @Test
    fun staleGenerationDoesNotStartOnNewSession() {
        val gate = VideoPlaybackStartGate(startAllowed = true)
        val old = gate.generation()
        gate.invalidate()
        gate.onHostStarted()
        assertFalse(gate.shouldStartOnPrepared(old))
        assertTrue(gate.shouldStartOnPrepared(gate.generation()))
    }

    @Test
    fun stopThenStartAllowsALaterPrepare() {
        val gate = VideoPlaybackStartGate(startAllowed = true)
        val generation = gate.generation()
        gate.onHostStopped()
        assertFalse(gate.shouldStartOnPrepared(generation))
        gate.onHostStarted()
        assertTrue(gate.shouldStartOnPrepared(generation))
    }

    @Test
    fun constructedWhileStoppedDoesNotAutoStart() {
        val gate = VideoPlaybackStartGate(startAllowed = false)
        assertFalse(gate.shouldStartOnPrepared(gate.generation()))
        gate.onHostStarted()
        assertTrue(gate.shouldStartOnPrepared(gate.generation()))
    }
}
