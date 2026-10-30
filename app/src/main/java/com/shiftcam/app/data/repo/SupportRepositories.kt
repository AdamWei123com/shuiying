package com.shiftcam.app.data.repo

import androidx.room.withTransaction
import com.shiftcam.app.data.db.AppDatabase
import com.shiftcam.app.data.db.MachineEntity
import com.shiftcam.app.data.db.PhotoEntity
import com.shiftcam.app.data.db.RemarkEntity
import com.shiftcam.app.data.db.WorkSiteEntity
import com.shiftcam.domain.reading.ReadingUnit
import com.shiftcam.domain.session.RemarkOwnerType
import com.shiftcam.domain.watermark.WatermarkLayoutSpec
import com.shiftcam.domain.watermark.WatermarkTemplateCodec
import kotlinx.coroutines.flow.Flow

/** 基础资料（设备 / 工地），P2。 */
class BaseDataRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    fun observeMachines(): Flow<List<MachineEntity>> = db.machineDao().observeActive()
    fun observeSites(): Flow<List<WorkSiteEntity>> = db.siteDao().observeActive()

    suspend fun getMachine(id: Long): MachineEntity? = db.machineDao().getById(id)

    suspend fun addMachine(
        name: String,
        code: String = "",
        unit: ReadingUnit = ReadingUnit.FIELD,
        unitCustom: String? = null,
        decimals: Int = 1,
    ): Long {
        require(name.isNotBlank()) { "设备名称不能为空" }
        val now = clock()
        return db.machineDao().insert(
            MachineEntity(
                name = name.trim(),
                code = code.trim(),
                readingUnit = unit,
                readingUnitCustom = unitCustom?.trim()?.takeIf { it.isNotEmpty() },
                readingDecimals = decimals.coerceIn(0, 3),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun addSite(name: String, note: String? = null): Long {
        require(name.isNotBlank()) { "工地名称不能为空" }
        val now = clock()
        return db.siteDao().insert(
            WorkSiteEntity(name = name.trim(), note = note?.trim(), createdAt = now, updatedAt = now),
        )
    }

    suspend fun archiveMachine(id: Long, archived: Boolean) {
        val machine = db.machineDao().getById(id) ?: throw DomainException.MachineNotFound()
        db.machineDao().update(machine.copy(isArchived = archived, updatedAt = clock()))
    }

    /** 归档不物理删，历史报表靠快照（INV-07）。 */
    suspend fun softDeleteMachine(id: Long) {
        db.machineDao().softDelete(id, clock())
    }

    suspend fun softDeleteSite(id: Long) {
        db.siteDao().softDelete(id, clock())
    }
}

/** 备注组件（owner C，P5）。4 类 owner 共用一套读写。 */
class RemarkRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    fun observe(ownerType: RemarkOwnerType, ownerId: Long): Flow<RemarkEntity?> =
        db.remarkDao().observeForOwner(ownerType, ownerId)

    fun observeBySession(sessionId: Long): Flow<List<RemarkEntity>> =
        db.remarkDao().observeBySession(sessionId)

    suspend fun get(ownerType: RemarkOwnerType, ownerId: Long): RemarkEntity? =
        db.remarkDao().getForOwner(ownerType, ownerId)

    /** 同一 owner 只有一条有效备注：有则更新，无则新建；内容清空视为软删。 */
    suspend fun save(
        ownerType: RemarkOwnerType,
        ownerId: Long,
        sessionId: Long?,
        content: String,
    ) {
        val now = clock()
        db.withTransaction {
            val existing = db.remarkDao().getForOwner(ownerType, ownerId)
            val text = content.trim()
            when {
                existing == null && text.isNotEmpty() -> db.remarkDao().insert(
                    RemarkEntity(
                        ownerType = ownerType,
                        ownerId = ownerId,
                        sessionId = sessionId,
                        content = text,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                existing != null && text.isEmpty() -> db.remarkDao().softDelete(existing.id, now)
                existing != null -> db.remarkDao().update(
                    existing.copy(content = text, sessionId = sessionId ?: existing.sessionId, updatedAt = now),
                )
            }
        }
    }
}

/** 水印模板（owner D）。MVP 只有一套默认模板，但表结构已预留多套。 */
class TemplateRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    fun observeAll() = db.templateDao().observeAll()

    suspend fun currentSpec(): WatermarkLayoutSpec {
        val entity = db.templateDao().getDefault() ?: return WatermarkLayoutSpec()
        return WatermarkTemplateCodec.decode(entity.styleConfig)
    }

    suspend fun saveDefaultSpec(spec: WatermarkLayoutSpec) {
        val now = clock()
        val existing = db.templateDao().getDefault() ?: return
        val json = WatermarkTemplateCodec.encode(spec.copy(schemaVersion = WatermarkLayoutSpec.CURRENT_SCHEMA_VERSION))
        db.templateDao().update(
            existing.copy(
                schemaVersion = WatermarkLayoutSpec.CURRENT_SCHEMA_VERSION,
                styleConfig = json,
                fieldsConfig = json,
                updatedAt = now,
            ),
        )
    }
}

/** 全局设置（单行表 id=1）。 */
class SettingRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    fun observe() = db.settingDao().observe()

    suspend fun get(): com.shiftcam.app.data.db.AppSettingEntity =
        db.settingDao().get() ?: com.shiftcam.app.data.db.AppSettingEntity(
            createdAt = clock(),
            updatedAt = clock(),
        )

    suspend fun setOnboardingDone(done: Boolean = true) {
        val current = get()
        db.settingDao().upsert(current.copy(onboardingDone = done, updatedAt = clock()))
    }

    suspend fun setBusinessDayBoundary(minute: Int) {
        val current = get()
        db.settingDao().upsert(
            current.copy(businessDayBoundaryMinute = minute.coerceIn(0, 24 * 60), updatedAt = clock()),
        )
    }

    suspend fun cacheWeather(text: String, at: Long = clock()) {
        val current = get()
        db.settingDao().upsert(current.copy(lastWeatherText = text, lastWeatherAt = at, updatedAt = at))
    }
}

/** 成片记录（Photo 是照片事实源）。 */
class PhotoRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    fun observeBySession(sessionId: Long): Flow<List<PhotoEntity>> = db.photoDao().observeBySession(sessionId)
    fun observeRecent(limit: Int = 50): Flow<List<PhotoEntity>> = db.photoDao().observeRecent(limit)

    suspend fun insert(entity: PhotoEntity): Long = db.photoDao().insert(entity)

    suspend fun softDelete(id: Long) = db.photoDao().softDelete(id, clock())
}
