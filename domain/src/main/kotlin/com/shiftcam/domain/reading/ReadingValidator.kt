package com.shiftcam.domain.reading

import com.shiftcam.domain.session.ReadingCheck
import com.shiftcam.domain.session.ReadingCheckCode

/**
 * 读数校验（R31）。不解 UI，只给结论：正常 / 缺失 / 倒挂需二次确认。
 * 下限由设备单位决定：小时表与公里表不允许负数，计数表必须为整数。
 */
object ReadingValidator {

    fun checkStart(value: Double?, unit: ReadingUnit): ReadingCheck = when {
        value == null -> ReadingCheck(ReadingCheckCode.MISSING, "上班读数不能为空")
        value < 0 -> ReadingCheck(ReadingCheckCode.INVALID, "读数不能为负数")
        unit == ReadingUnit.COUNT && value % 1.0 != 0.0 -> ReadingCheck(ReadingCheckCode.INVALID, "计数表读数必须为整数")
        else -> ReadingCheck(ReadingCheckCode.OK, null)
    }

    fun checkEnd(value: Double?, unit: ReadingUnit, previous: Double?): ReadingCheck = when {
        value == null -> ReadingCheck(ReadingCheckCode.MISSING, "下班读数留空将记为缺读数班次")
        value < 0 -> ReadingCheck(ReadingCheckCode.INVALID, "读数不能为负数")
        unit == ReadingUnit.COUNT && value % 1.0 != 0.0 -> ReadingCheck(ReadingCheckCode.INVALID, "计数表读数必须为整数")
        previous != null && value < previous -> ReadingCheck(ReadingCheckCode.ANOMALY, "读数小于上班读数，需要二次确认")
        else -> ReadingCheck(ReadingCheckCode.OK, null)
    }
}
