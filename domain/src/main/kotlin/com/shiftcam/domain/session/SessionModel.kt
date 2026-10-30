package com.shiftcam.domain.session

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 班次状态（枚举冻结：只有这三个值）。 */
enum class SessionStatus {
    RUNNING,
    FINISHED,
    VOIDED,
}

/** 施工方式（P4 高频切换项；名称可在基础资料维护，MVP 用固定枚举）。 */
enum class WorkType(val label: String) {
    DIGGING("挖掘"),
    LOADING("装载"),
    LEVELING("平整"),
    BREAKING("破碎"),
    OTHER("其他"),
}

/** 备注归属的 4 类 owner（R14 / P5）。 */
enum class RemarkOwnerType {
    SESSION,
    SEGMENT,
    COUNT,
    PHOTO,
}

/** 成片类型（旧 PhotoType.SITE 已废弃，只保留 FIELD）。 */
enum class PhotoType {
    FIELD,
}

/** 地址来源，便于回溯（07-02 §3 Photo.addressSource）。 */
enum class AddressSource {
    MANUAL,
    CACHE,
    ONLINE,
}

/**
 * 时间口径（R21 业务日 / R25 跨零点 / R30 工时对账）。
 *
 * 规则：
 * 1. 业务日按 [boundaryMinute]（默认 04:00）切分，凌晨 3 点算前一天。
 * 2. 跨零点不按比例拆读数差：读数差整班归上班日，只拆在场工时/计数（此处不实现拆分）。
 */
object TimeRules {

    const val DEFAULT_BUSINESS_DAY_BOUNDARY_MINUTE: Int = 4 * 60

    fun businessDate(
        epochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        boundaryMinute: Int = DEFAULT_BUSINESS_DAY_BOUNDARY_MINUTE,
    ): LocalDate {
        val local = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val minuteOfDay = local.hour * 60 + local.minute
        val date = local.toLocalDate()
        return if (minuteOfDay < boundaryMinute.coerceIn(0, 24 * 60)) date.minusDays(1) else date
    }

    /** 班次工时 = 读数差（INV-04），保留两位小数；任一端缺失返回 null。 */
    fun readingHours(startReading: Double?, endReading: Double?): Double? {
        if (startReading == null || endReading == null) return null
        val diff = endReading - startReading
        return Math.round(diff * 100.0) / 100.0
    }

    /** 在场时长（毫秒差），用于分段合计参考，不作为结班工时基准。 */
    fun durationMillis(startMillis: Long, endMillis: Long): Long =
        (endMillis - startMillis).coerceAtLeast(0L)

    fun durationHours(startMillis: Long, endMillis: Long): Double =
        Math.round(durationMillis(startMillis, endMillis) / 360000.0) / 10.0
}
