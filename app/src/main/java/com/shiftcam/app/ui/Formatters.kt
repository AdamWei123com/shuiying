package com.shiftcam.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

internal fun formatClock(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(clockFormatter)

internal fun formatDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateFormatter)
