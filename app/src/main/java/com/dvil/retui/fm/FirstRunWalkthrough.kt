package com.dvil.retui.fm

internal fun shouldShowFirstRunWalkthrough(
    completed: Boolean,
    floatingWindow: Boolean,
    restoringState: Boolean,
    incomingRequest: Boolean
): Boolean = !completed && !floatingWindow && !restoringState && !incomingRequest

internal fun shouldRequestInitialStorageAccess(
    completed: Boolean,
    floatingWindow: Boolean,
    incomingRequest: Boolean
): Boolean = completed || floatingWindow || incomingRequest
