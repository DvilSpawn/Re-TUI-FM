package com.dvil.retui.fm

/**
 * Setup always runs inside the FM window. Launcher-driven launches without storage access get setup
 * instead of being sent to Android settings; standalone launches after a skip rely on the home reminder.
 */
internal fun shouldShowSetup(
    completed: Boolean,
    floatingWindow: Boolean,
    restoringState: Boolean,
    incomingRequest: Boolean,
    storageGranted: Boolean
): Boolean = when {
    restoringState -> false
    !storageGranted && (floatingWindow || incomingRequest) -> true
    else -> !completed && !incomingRequest
}
