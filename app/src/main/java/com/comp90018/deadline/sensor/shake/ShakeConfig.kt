package com.comp90018.deadline.sensor.shake

/**
 * Tuning for [ShakeDetector]: how hard ([thresholdG], linear acceleration in g after removing
 * gravity) and how long ([requiredHits] consecutive readings) a shake must be, the
 * [cooldownMillis] between shuffles, and the [smoothing] factor of the filter.
 * Defaults are the values tuned on devices; Remote Config can replace them (#52).
 */
data class ShakeConfig(
    val thresholdG: Float = DEFAULT_THRESHOLD_G,
    val requiredHits: Int = DEFAULT_REQUIRED_HITS,
    val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
    val smoothing: Float = DEFAULT_SMOOTHING,
) {
    init {
        require(thresholdG.isFinite() && thresholdG > 0f) { "Shake threshold must be positive." }
        require(requiredHits >= 1) { "A shake needs at least one reading." }
        require(cooldownMillis >= 0L) { "Shake cooldown must be non-negative." }
        require(smoothing.isFinite() && smoothing > 0f && smoothing <= 1f) { "Shake smoothing must be within (0, 1]." }
    }

    companion object {
        const val DEFAULT_THRESHOLD_G = 1.7f
        const val DEFAULT_REQUIRED_HITS = 2
        const val DEFAULT_COOLDOWN_MILLIS = 900L
        const val DEFAULT_SMOOTHING = 0.25f
    }
}
