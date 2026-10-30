package com.shiftcam.domain.reading

import java.math.BigDecimal
import java.math.RoundingMode

/** 设备读数单位。FIELD=小时表，METER=公里表，COUNT=计数表（INV-05）。 */
enum class ReadingUnit {
    FIELD,
    METER,
    COUNT,
}

/**
 * 读数格式化唯一入口（INV-05）。
 * UI / 水印 / 报表 / CSV 一律走这里，禁止在别处自行拼接单位后缀。
 */
object ReadingFormatter {

    const val MISSING: String = "--"

    private const val MAX_DECIMALS = 3

    fun format(
        value: Double?,
        unit: ReadingUnit,
        unitCustom: String? = null,
        decimals: Int = 1,
    ): String {
        if (value == null) return MISSING
        val digits = when (unit) {
            ReadingUnit.COUNT -> 0
            else -> decimals.coerceIn(0, MAX_DECIMALS)
        }
        val number = BigDecimal.valueOf(value)
            .setScale(digits, RoundingMode.HALF_UP)
            .toPlainString()
        return number + unitLabel(unit, unitCustom)
    }

    /** 只格式化数字，不带单位（CSV 数值列使用，保证数值不被公式转义）。 */
    fun formatNumber(value: Double?, unit: ReadingUnit, decimals: Int = 1): String {
        if (value == null) return ""
        val digits = when (unit) {
            ReadingUnit.COUNT -> 0
            else -> decimals.coerceIn(0, MAX_DECIMALS)
        }
        return BigDecimal.valueOf(value).setScale(digits, RoundingMode.HALF_UP).toPlainString()
    }

    fun unitLabel(unit: ReadingUnit, unitCustom: String? = null): String {
        if (!unitCustom.isNullOrBlank()) return unitCustom.trim()
        return when (unit) {
            ReadingUnit.FIELD -> "h"
            ReadingUnit.METER -> "km"
            ReadingUnit.COUNT -> "次"
        }
    }
}
