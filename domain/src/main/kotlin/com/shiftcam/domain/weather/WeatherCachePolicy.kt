package com.shiftcam.domain.weather

/**
 * 天气缓存显示规则（ADR-011 / F3，替代旧的 24h 规则）：
 * 0~2h 直接显示；2~6h 显示并追加 "·缓存"；超过 6h 隐藏。
 */
object WeatherCachePolicy {

    const val FRESH_MILLIS: Long = 2 * 60 * 60 * 1000L
    const val STALE_MILLIS: Long = 6 * 60 * 60 * 1000L
    const val CACHE_SUFFIX: String = "·缓存"

    sealed interface Display {
        data object Hidden : Display
        data class Shown(val text: String, val cached: Boolean) : Display
    }

    fun decide(text: String?, cachedAt: Long?, now: Long): Display {
        if (text.isNullOrBlank() || cachedAt == null) return Display.Hidden
        val age = now - cachedAt
        if (age < 0) return Display.Hidden
        return when {
            age < FRESH_MILLIS -> Display.Shown(text.trim(), cached = false)
            age < STALE_MILLIS -> Display.Shown(text.trim() + CACHE_SUFFIX, cached = true)
            else -> Display.Hidden
        }
    }
}
