package com.shiftcam.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shiftcam.domain.session.RemarkOwnerType
import kotlinx.coroutines.flow.Flow

/**
 * 统计/列表统一过滤（INV-03）：status<>'VOIDED'、deletedAt IS NULL、isVoided=0。
 * 禁止在业务层手写 SQL 绕过这三个过滤条件。
 */

@Dao
interface MachineDao {

    @Query("SELECT * FROM machines WHERE deletedAt IS NULL ORDER BY isArchived ASC, createdAt DESC")
    fun observeActive(): Flow<List<MachineEntity>>

    @Query("SELECT * FROM machines WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: Long): MachineEntity?

    @Insert
    suspend fun insert(entity: MachineEntity): Long

    @Update
    suspend fun update(entity: MachineEntity)

    @Query("UPDATE machines SET deletedAt = :at, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: Long, at: Long)
}

@Dao
interface WorkSiteDao {

    @Query("SELECT * FROM work_sites WHERE deletedAt IS NULL ORDER BY isArchived ASC, createdAt DESC")
    fun observeActive(): Flow<List<WorkSiteEntity>>

    @Query("SELECT * FROM work_sites WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: Long): WorkSiteEntity?

    @Insert
    suspend fun insert(entity: WorkSiteEntity): Long

    @Update
    suspend fun update(entity: WorkSiteEntity)

    @Query("UPDATE work_sites SET deletedAt = :at, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: Long, at: Long)
}

@Dao
interface SessionDao {

    @Query("SELECT * FROM work_sessions WHERE status = 'RUNNING' AND deletedAt IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getRunning(): WorkSessionEntity?

    @Query("SELECT * FROM work_sessions WHERE status = 'RUNNING' AND deletedAt IS NULL ORDER BY startTime DESC LIMIT 1")
    fun observeRunning(): Flow<WorkSessionEntity?>

    @Query("SELECT * FROM work_sessions WHERE id = :id")
    suspend fun getById(id: Long): WorkSessionEntity?

    @Query("SELECT * FROM work_sessions WHERE machineId = :machineId AND status = 'FINISHED' AND deletedAt IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun latestFinishedForMachine(machineId: Long): WorkSessionEntity?

    @Query("SELECT COUNT(*) FROM work_sessions WHERE machineId = :machineId AND startTime >= :since AND deletedAt IS NULL")
    suspend fun countForMachineSince(machineId: Long, since: Long): Int

    @Query("SELECT * FROM work_sessions WHERE deletedAt IS NULL AND status <> 'VOIDED' ORDER BY startTime DESC LIMIT :limit")
    fun observeActiveSessions(limit: Int = 20): Flow<List<WorkSessionEntity>>

    @Query("SELECT * FROM work_sessions WHERE deletedAt IS NULL AND status = 'VOIDED' ORDER BY startTime DESC LIMIT :limit")
    fun observeVoidedSessions(limit: Int = 20): Flow<List<WorkSessionEntity>>

    @Query("SELECT * FROM work_sessions WHERE deletedAt IS NULL AND status <> 'VOIDED' AND startTime BETWEEN :from AND :to ORDER BY startTime DESC")
    suspend fun listBetween(from: Long, to: Long): List<WorkSessionEntity>

    @Insert
    suspend fun insert(entity: WorkSessionEntity): Long

    @Update
    suspend fun update(entity: WorkSessionEntity)
}

@Dao
interface SegmentDao {

    @Query("SELECT * FROM work_segments WHERE sessionId = :sessionId AND endTime IS NULL AND isVoided = 0 ORDER BY seq DESC LIMIT 1")
    suspend fun activeSegment(sessionId: Long): WorkSegmentEntity?

    @Query("SELECT * FROM work_segments WHERE sessionId = :sessionId AND endTime IS NULL AND isVoided = 0 ORDER BY seq DESC LIMIT 1")
    fun observeActiveSegment(sessionId: Long): Flow<WorkSegmentEntity?>

    @Query("SELECT * FROM work_segments WHERE sessionId = :sessionId ORDER BY seq ASC")
    suspend fun listBySession(sessionId: Long): List<WorkSegmentEntity>

    @Query("SELECT * FROM work_segments WHERE sessionId = :sessionId AND isVoided = 0 ORDER BY seq ASC")
    fun observeActiveSegments(sessionId: Long): Flow<List<WorkSegmentEntity>>

    @Query("SELECT COALESCE(MAX(seq), 0) FROM work_segments WHERE sessionId = :sessionId")
    suspend fun maxSeq(sessionId: Long): Int

    @Query("SELECT * FROM work_segments WHERE id = :id")
    suspend fun getById(id: Long): WorkSegmentEntity?

    @Insert
    suspend fun insert(entity: WorkSegmentEntity): Long

    @Update
    suspend fun update(entity: WorkSegmentEntity)
}

@Dao
interface CountDao {

    @Insert
    suspend fun insertAll(items: List<CountRecordEntity>)

    @Query("SELECT COALESCE(SUM(countValue), 0) FROM count_records WHERE sessionId = :sessionId AND deletedAt IS NULL AND isVoided = 0")
    suspend fun totalCount(sessionId: Long): Int

    @Query("SELECT COALESCE(SUM(countValue), 0) FROM count_records WHERE sessionId = :sessionId AND deletedAt IS NULL AND isVoided = 0")
    fun observeTotalCount(sessionId: Long): Flow<Int>

    @Query("SELECT * FROM count_records WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY countedAt DESC, id DESC")
    fun observeRecords(sessionId: Long): Flow<List<CountRecordEntity>>

    @Query("SELECT * FROM count_records WHERE sessionId = :sessionId AND deletedAt IS NULL AND isVoided = 0 ORDER BY countedAt DESC, id DESC LIMIT 1")
    suspend fun latestActive(sessionId: Long): CountRecordEntity?

    @Query("UPDATE count_records SET isVoided = 1, voidedAt = :at WHERE id = :id AND isVoided = 0")
    suspend fun void(id: Long, at: Long)

    @Query("UPDATE count_records SET deletedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: Long, at: Long)
}

@Dao
interface RemarkDao {

    @Query("SELECT * FROM remarks WHERE ownerType = :ownerType AND ownerId = :ownerId AND deletedAt IS NULL LIMIT 1")
    suspend fun getForOwner(ownerType: RemarkOwnerType, ownerId: Long): RemarkEntity?

    @Query("SELECT * FROM remarks WHERE ownerType = :ownerType AND ownerId = :ownerId AND deletedAt IS NULL LIMIT 1")
    fun observeForOwner(ownerType: RemarkOwnerType, ownerId: Long): Flow<RemarkEntity?>

    @Query("SELECT * FROM remarks WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeBySession(sessionId: Long): Flow<List<RemarkEntity>>

    @Insert
    suspend fun insert(entity: RemarkEntity): Long

    @Update
    suspend fun update(entity: RemarkEntity)

    @Query("UPDATE remarks SET deletedAt = :at, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: Long, at: Long)
}

@Dao
interface PhotoDao {

    @Insert
    suspend fun insert(entity: PhotoEntity): Long

    @Query("SELECT * FROM photos WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY takenAt DESC")
    fun observeBySession(sessionId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE deletedAt IS NULL ORDER BY takenAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: Long): PhotoEntity?

    @Query("UPDATE photos SET deletedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: Long, at: Long)
}

@Dao
interface TemplateDao {

    @Query("SELECT * FROM watermark_templates WHERE isDefault = 1 AND deletedAt IS NULL ORDER BY id ASC LIMIT 1")
    suspend fun getDefault(): WatermarkTemplateEntity?

    @Query("SELECT * FROM watermark_templates WHERE isDefault = 1 AND deletedAt IS NULL ORDER BY id ASC LIMIT 1")
    fun observeDefault(): Flow<WatermarkTemplateEntity?>

    @Query("SELECT * FROM watermark_templates WHERE deletedAt IS NULL ORDER BY isDefault DESC, id ASC")
    fun observeAll(): Flow<List<WatermarkTemplateEntity>>

    @Insert
    suspend fun insert(entity: WatermarkTemplateEntity): Long

    @Update
    suspend fun update(entity: WatermarkTemplateEntity)
}

@Dao
interface SettingDao {

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): AppSettingEntity?

    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<AppSettingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppSettingEntity)
}
