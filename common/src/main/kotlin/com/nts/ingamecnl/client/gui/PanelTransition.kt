package com.nts.ingamecnl.client.gui

internal const val SLIDE_DURATION_NANOS = 180_000_000L

internal enum class PanelTarget {
    EXPANDED,
    COLLAPSED,
}

internal data class PanelTransition(
    val origin: Float,
    val target: PanelTarget,
    val startedAtNanos: Long,
) {
    fun fractionAt(nowNanos: Long, durationNanos: Long = SLIDE_DURATION_NANOS): Float {
        val distance = (nowNanos - startedAtNanos).coerceAtLeast(0).toFloat() / durationNanos.coerceAtLeast(1L)
        return when (target) {
            PanelTarget.EXPANDED -> (origin + distance).coerceAtMost(1f)
            PanelTarget.COLLAPSED -> (origin - distance).coerceAtLeast(0f)
        }
    }
}
