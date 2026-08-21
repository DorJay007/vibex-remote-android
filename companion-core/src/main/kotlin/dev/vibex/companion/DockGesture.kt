package dev.vibex.companion

enum class DockGesture { Tap, Drag }

fun classifyDockGesture(traveledPx: Float, slopPx: Float = 12f): DockGesture =
    if (traveledPx < slopPx) DockGesture.Tap else DockGesture.Drag
