package com.dvil.retui.fm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunWalkthroughTest {
    @Test fun freshLaunchesShowSetupInEitherWindow() {
        assertTrue(shouldShowSetup(false, false, false, false, true))
        assertTrue(shouldShowSetup(false, true, false, false, true))
        assertTrue(shouldShowSetup(false, false, false, false, false))
    }

    @Test fun completedSetupStaysAwayWhenAccessIsGranted() {
        assertFalse(shouldShowSetup(true, false, false, false, true))
        assertFalse(shouldShowSetup(true, true, false, false, true))
    }

    @Test fun restoringStateNeverShowsSetup() {
        assertFalse(shouldShowSetup(false, false, true, false, false))
        assertFalse(shouldShowSetup(false, true, true, true, false))
    }

    @Test fun launcherRequestsSkipSetupOnlyWhenAccessIsGranted() {
        assertFalse(shouldShowSetup(false, false, false, true, true))
        assertTrue(shouldShowSetup(true, false, false, true, false))
        assertTrue(shouldShowSetup(true, true, false, false, false))
    }

    @Test fun skippedStandaloneLaunchUsesTheHomeReminder() {
        assertFalse(shouldShowSetup(true, false, false, false, false))
    }
}
