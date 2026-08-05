package com.dvil.retui.fm

internal data class CrtLayers(
    val tint: Boolean,
    val scanlines: Boolean,
    val beams: Boolean,
    val maskLines: Boolean,
    val vignette: Boolean
)

internal object CrtAppearance {
    fun resolveVignette(canonical: Boolean?, alias: Boolean?, local: Boolean?): Boolean =
        canonical ?: alias ?: local ?: true

    fun layers(crtEnabled: Boolean, vignetteEnabled: Boolean) = CrtLayers(
        tint = crtEnabled,
        scanlines = crtEnabled,
        beams = crtEnabled,
        maskLines = crtEnabled,
        vignette = crtEnabled && vignetteEnabled
    )
}
