package com.minimalist.launcher.core.model

data class GestureSettings(
    val swipeUp:    GestureAction = GestureAction.APP_DRAWER,
    val swipeDown:  GestureAction = GestureAction.SEARCH,
    val swipeLeft:  GestureAction = GestureAction.NONE,
    val swipeRight: GestureAction = GestureAction.NONE,
    val doubleTap:  GestureAction = GestureAction.SCRATCH_PAD,
)
