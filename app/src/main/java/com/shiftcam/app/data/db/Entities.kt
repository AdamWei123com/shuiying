package com.shiftcam.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shiftcam.domain.reading.ReadingUnit
import com.shiftcam.domain.session.AddressSource
import com.shiftcam.domain.session.PhotoType
import com.shiftcam.domain.session.RemarkOwnerType
import com.shiftcam.domain.session.SessionStatus
import com.shiftcam.domain.session.WorkType

/**
 * 全部表结构。字段名以 02-01 数据字典为准；缺失文档处按 07-02 §3 缺口清单与 INV 推导，
 * 推导字段已在交付卡「未决 / 风险」中标注。
 *
 * 通用约定：删除一律软删（deletedAt），归档不物理删（INV-07）；历史名称用 *NameSnapshot。
 */

@Entity(
    tableName = "machines",
    indices = [Index("name"), Index("deletedAt")],
)
data class MachineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String = "",
    val readingUnit: ReadingUnit = ReadingUnit.FIELD,
    val readingUnitCustom: String? = null,
    val readingDecimals: Int = 1,
    val isArchived: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "work_sites",
    indices = [Index("name"), Index("deletedAt")],
)
data class WorkSiteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String? = null,
    val isArchived: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "work_sessions",
    foreignKeys = [
        ForeignKey(
            entity = MachineEntity::class,
            parentColumns = ["id"],
            childColumns = ["machineId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = WorkSiteEntity::class,
            parentColumns = ["id"],
            childColumns = ["siteId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("status"), Index("startTime"), Index("machineId"), Index("siteId"), Index("deletedAt")],
)
data class WorkSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val machineId: Long,
    val machineNameSnapshot: String,
    val siteId: Long? = null,
    val siteNameSnapshot: String = "",
    val shiftNo: Int = 1,
    val startTime: Long,
    val startReading: Double? = null,
    val endTime: Long? = null,
    val endReading: Double? = null,
    /** 结班工时 = 班次读数差（INV-04），不是分段合计。 */
    val meterHours: Double? = null,
    val status: SessionStatus = SessionStatus.RUNNING,
    val readingMissing: Boolean = false,
    val readingAnomaly: Boolean = false,
    val voidedAt: Long? = null,
    val voidReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "work_segments",
    foreignKeys = [
        ForeignKey(
            entity = WorkSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["sessionId", "seq"], unique = true),
        Index("sessionId"),
    ],
)
data class WorkSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val workType: WorkType,
    /** 同毫秒切换会乱序，必须按 seq 排序（R32）。 */
    val seq: Int,
    val startTime: Long,
    val endTime: Long? = null,
    val startReading: Double? = null,
    val endReading: Double? = null,
    /** 双向链：上一分段 id，撤销时用于回滚（ADR-002）。 */
    val prevSegmentId: Long? = null,
    val isVoided: Boolean = false,
    val voidedAt: Long? = null,
    /** 撤销前备份 A 的 end 值，撤销时不丢读数（F6）。 */
    val endTimeBeforeVoid: Long? = null,
    val endReadingBeforeVoid: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "count_records",
    indices = [Index("sessionId"), Index("countedAt"), Index("deletedAt")],
)
data class CountRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val segmentId: Long? = null,
    val workType: WorkType = WorkType.DIGGING,
    val plateNo: String? = null,
    val countValue: Int = 1,
    val countedAt: Long,
    val isVoided: Boolean = false,
    val voidedAt: Long? = null,
    val deletedAt: Long? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "remarks",
    indices = [Index(value = ["ownerType", "ownerId"]), Index("sessionId"), Index("deletedAt")],
)
data class RemarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerType: RemarkOwnerType,
    val ownerId: Long,
    val sessionId: Long? = null,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "photos",
    indices = [Index("sessionId"), Index("takenAt"), Index("deletedAt")],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long? = null,
    val segmentId: Long? = null,
    val countRecordId: Long? = null,
    /** 成片在 MediaStore 的 Uri 字符串（水印已烘焙）。 */
    val fileUri: String,
    /** 原图私有目录路径，用于重渲染。 */
    val originalPath: String? = null,
    val type: PhotoType = PhotoType.FIELD,
    val takenAt: Long,
    val lat: Double? = null,
    val lng: Double? = null,
    /** 精度不足 100m 时不显示经纬度（INV-09）。 */
    val locationAccuracy: Float? = null,
    val address: String? = null,
    val addressSource: AddressSource? = null,
    val watermarkTemplateId: Long? = null,
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "watermark_templates",
    indices = [Index("deletedAt")],
)
data class WatermarkTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val schemaVersion: Int = 2,
    /** 字段显隐/顺序 JSON（旧名 fieldsJson 已废弃）。 */
    val fieldsConfig: String,
    /** 版式比例 JSON（旧名 styleConfigJson 已废弃）。 */
    val styleConfig: String,
    val isDefault: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val businessDayBoundaryMinute: Int = 4 * 60,
    val onboardingDone: Boolean = false,
    val lastWeatherText: String? = null,
    val lastWeatherAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val SINGLE_ROW_ID: Int = 1
    }
}
