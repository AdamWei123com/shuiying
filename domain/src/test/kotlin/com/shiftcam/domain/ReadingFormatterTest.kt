package com.shiftcam.domain

import com.shiftcam.domain.reading.ReadingFormatter
import com.shiftcam.domain.reading.ReadingUnit
import com.shiftcam.domain.reading.ReadingValidator
import com.shiftcam.domain.session.ReadingCheckCode
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingFormatterTest {

    @Test
    fun `null 读数显示占位符`() {
        assertEquals("--", ReadingFormatter.format(null, ReadingUnit.FIELD))
    }

    @Test
    fun `小时表按 decimals 格式化并带 h`() {
        assertEquals("9.0h", ReadingFormatter.format(9.0, ReadingUnit.FIELD, decimals = 1))
        assertEquals("9.05h", ReadingFormatter.format(9.049, ReadingUnit.FIELD, decimals = 2))
        assertEquals("1234h", ReadingFormatter.format(1234.0, ReadingUnit.FIELD, decimals = 0))
    }

    @Test
    fun `公里表不能显示 h`() {
        assertEquals("12345.6km", ReadingFormatter.format(12345.6, ReadingUnit.METER, decimals = 1))
    }

    @Test
    fun `自定义单位优先于默认单位`() {
        assertEquals("88.5亩", ReadingFormatter.format(88.5, ReadingUnit.METER, unitCustom = "亩"))
    }

    @Test
    fun `计数表忽略小数位`() {
        assertEquals("12次", ReadingFormatter.format(12.4, ReadingUnit.COUNT, decimals = 2))
    }

    @Test
    fun `小数位上限截断到 3`() {
        assertEquals("1.235h", ReadingFormatter.format(1.23456, ReadingUnit.FIELD, decimals = 9))
    }

    @Test
    fun `上班读数不允许缺失或负数`() {
        assertEquals(ReadingCheckCode.MISSING, ReadingValidator.checkStart(null, ReadingUnit.FIELD).code)
        assertEquals(ReadingCheckCode.INVALID, ReadingValidator.checkStart(-1.0, ReadingUnit.FIELD).code)
        assertEquals(ReadingCheckCode.OK, ReadingValidator.checkStart(1.0, ReadingUnit.FIELD).code)
    }

    @Test
    fun `倒挂读数需要二次确认`() {
        val check = ReadingValidator.checkEnd(10.0, ReadingUnit.FIELD, previous = 20.0)
        assertEquals(ReadingCheckCode.ANOMALY, check.code)
        assertEquals(true, check.requiresConfirm)
        assertEquals(ReadingCheckCode.OK, ReadingValidator.checkEnd(20.0, ReadingUnit.FIELD, previous = 20.0).code)
    }
}
