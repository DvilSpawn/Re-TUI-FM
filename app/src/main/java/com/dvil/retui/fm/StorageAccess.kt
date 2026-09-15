package com.dvil.retui.fm

internal fun legacyStoragePermissions(sdk: Int): List<String> = if (sdk < 30) listOf(
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE"
) else emptyList()
