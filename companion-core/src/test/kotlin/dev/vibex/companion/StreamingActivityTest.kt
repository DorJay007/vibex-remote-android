package dev.vibex.companion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamingActivityTest {
    @Test
    fun nextVerbStaysInDesktopSetAndAvoidsRepeat() {
        val next = nextStreamingActivityVerb(STREAMING_ACTIVITY_VERBS, "Thinking") { 0.0 }
        assertTrue(next in STREAMING_ACTIVITY_VERBS)
        assertTrue(next != "Thinking")
        assertEquals("Cooking…", streamingActivityLabel("Cooking"))
    }

    @Test
    fun emptyPoolFallsBackToThinking() {
        assertEquals("Thinking", nextStreamingActivityVerb(emptyList()))
    }
}
