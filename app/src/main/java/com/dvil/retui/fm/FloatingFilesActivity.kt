package com.dvil.retui.fm

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle

class FloatingFilesActivity : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, MainActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP
        )
        super.onCreate(savedInstanceState)
    }
}
