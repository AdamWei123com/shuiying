package com.shiftcam.domain

import com.shiftcam.domain.session.TimeRules
import com.shiftcam.domain.weather.WeatherCachePolicy
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeRulesTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `凌晨三点属于前一业务日`() {
        val at3 = LocalDate.of(2026, 10, 30).atTime(3, 30).atZone(zone).toInstant().toEpochMilli()
        val at4 = LocalDate.of(2026, 10, 30).atTime(4, 10).atZone(zone).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 29), TimeRules.businessDate(at3, zone))
        assertEquals(LocalDate.of(2026, 10, 30), TimeRules.businessDate(at4, zone))
    }

    @Test
    fun `工时等于读数差并保留两位`() {
        assertEquals(7.75, TimeRules.readingHours(100.5, 108.25))
        assertNull(TimeRules.readingHours(100.5, null))
    }

    @Test
    fun `跨零点的读数差整班归属上班日`() {
        val start = LocalDate.of(2026, 10, 30).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 10, 31).atTime(2, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 30), TimeRules.businessDate(start, zone))
        assertEquals(LocalDate.of(2026, 10, 30), TimeRules.businessDate(end, zone))
    }
}

class WeatherCachePolicyTest {

    private val now = 1_000_000_000_000L

    @Test
    fun `两小时内直接显示`() {
        val d = WeatherCachePolicy.decide("晴 25℃", now - 60 * 60 * 1000L, now)
        assertTrue(d is WeatherCachePolicy.Display.Shown)
        assertEquals("晴 25℃", (d as WeatherCachePolicy.Display.Shown).text)
    }

    @Test
    fun `两到六小时显示缓存标记`() {
        val d = WeatherCachePolicy.decide("晴 25℃", now - 3 * 60 * 60 * 1000L, now)
        assertTrue(d is WeatherCachePolicy.Display.Shown)
        assertTrue((d as WeatherCachePolicy.Display.Shown).cached)
        assertEquals("晴 25℃·缓存", d.text)
    }

    @Test
    fun `超过六小时隐藏`() {
        assertTrue(WeatherCachePolicy.decide("晴", now - 7 * 60 * 60 * 1000L, now) is WeatherCachePolicy.Display.Hidden)
        assertTrue(WeatherCachePolicy.decide(null, null, now) is WeatherCachePolicy.Display.Hidden)
    }
}
