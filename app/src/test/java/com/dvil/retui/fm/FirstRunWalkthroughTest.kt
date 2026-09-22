package com.dvil.retui.fm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunWalkthroughTest {
    @Test fun onlyFreshStandaloneLaunchIsEligible() {
        assertTrue(shouldShowFirstRunWalkthrough(false, false, false, false))
        assertFalse(shouldShowFirstRunWalkthrough(true, false, false, false))
        assertFalse(shouldShowFirstRunWalkthrough(false, true, false, false))
        assertFalse(shouldShowFirstRunWalkthrough(false, false, true, false))
        assertFalse(shouldShowFirstRunWalkthrough(false, false, false, true))
    }

    @Test fun storagePromptWaitsForCompletionExceptForBypassedLaunches() {
        assertFalse(shouldRequestInitialStorageAccess(false, false, false))
        assertTrue(shouldRequestInitialStorageAccess(true, false, false))
        assertTrue(shouldRequestInitialStorageAccess(false, true, false))
        assertTrue(shouldRequestInitialStorageAccess(false, false, true))
    }
}
