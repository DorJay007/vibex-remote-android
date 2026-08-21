package dev.vibex.companion

import kotlin.test.Test
import kotlin.test.assertEquals

class DockGestureTest {
    @Test
    fun tapWithoutMovementIsStillATap() {
        assertEquals(DockGesture.Tap, classifyDockGesture(0f))
        assertEquals(DockGesture.Tap, classifyDockGesture(11f))
        assertEquals(DockGesture.Drag, classifyDockGesture(12f))
    }
}
