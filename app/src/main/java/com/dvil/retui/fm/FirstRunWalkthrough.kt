package com.dvil.retui.fm

internal fun shouldShowFirstRunWalkthrough(
    completed: Boolean,
    floatingWindow: Boolean,
    restoringState: Boolean,
    incomingRequest: Boolean
): Boolean = !completed && !floatingWindow && !restoringState && !incomingRequest

/** Standalone launches rely on setup and the home reminder; only Launcher-driven launches jump to settings. */
internal fun shouldRequestInitialStorageAccess(
    completed: Boolean,
    floatingWindow: Boolean,
    incomingRequest: Boolean
): Boolean = floatingWindow || incomingRequest
