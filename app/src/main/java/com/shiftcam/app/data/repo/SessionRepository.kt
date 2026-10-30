package com.shiftcam.app.data.repo

import androidx.room.withTransaction
import com.shiftcam.app.data.db.AppDatabase
import com.shiftcam.app.data.db.WorkSegmentEntity
import com.shiftcam.app.data.db.WorkSessionEntity
import com.shiftcam.app.data.repo.DomainException.AlreadyRunning
import com.shiftcam.app.data.repo.DomainException.ConfirmRequired
import com.shiftcam.app.data.repo.DomainException.MachineNotFound
import com.shiftcam.app.data.repo.DomainException.NoRunningSession
import com.shiftcam.app.data.repo.DomainException.NothingToUndo
import com.shiftcam.app.data.repo.DomainException.ReadingRejected
import com.shiftcam.app.data.repo.DomainException.SameWorkType
import com.shiftcam.app.data.repo.DomainException.SessionNotFound
import com.shiftcam.app.data.repo.DomainException.UndoExpired
import com.shiftcam.domain.reading.ReadingValidator
import com.shiftcam.domain.session.ReadingCheckCode
import com.shiftcam.domain.session.SessionStatus
import com.shiftcam.domain.session.TimeRules
import com.shiftcam.domain.session.WorkType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

data class StartShiftInput(
    val machineId: Long,
    val siteId: Long? = null,
    val startReading: Double? = null,
    val workType: WorkType = WorkType.DIGGING,
    val startTime: Long = System.currentTimeMillis(),
    /** 读数倒挂二次确认（R31）。 */
    val anomalyConfirmed: Boolean = false,
)

/**
 * 班次仓储（owner B）。全部写操作单事务 + Mutex（INV-02/INV-08）。
 * 接口签名冻结：start / finish / switchWorkType / undoLastSwitch / voidSession。
 */
class SessionRepository(
    private val db: AppDatabase,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    companion object {
        const val UNDO_WINDOW_MILLIS: Long = 3_000L
    }

    private val mutex = Mutex()

    private val sessionDao get() = db.sessionDao()
    private val segmentDao get() = db.segmentDao()
    private val machineDao get() = db.machineDao()
    private val siteDao get() = db.siteDao()

    fun observeRunning(): Flow<WorkSessionEntity?> = sessionDao.observeRunning()

    fun observeActiveSessions(limit: Int = 20): Flow<List<WorkSessionEntity>> =
        sessionDao.observeActiveSessions(limit)

    fun observeVoidedSessions(limit: Int = 20): Flow<List<WorkSessionEntity>> =
        sessionDao.observeVoidedSessions(limit)

    fun observeSegments(sessionId: Long): Flow<List<WorkSegmentEntity>> =
        segmentDao.observeActiveSegments(sessionId)

    fun observeActiveSegment(sessionId: Long): Flow<WorkSegmentEntity?> =
        segmentDao.observeActiveSegment(sessionId)

    suspend fun getRunning(): WorkSessionEntity? = sessionDao.getRunning()

    suspend fun getSession(id: Long): WorkSessionEntity? = sessionDao.getById(id)

    suspend fun listSegments(sessionId: Long): List<WorkSegmentEntity> =
        segmentDao.listBySession(sessionId)

    suspend fun start(input: StartShiftInput): Long = mutex.withLock {
        db.withTransaction {
            if (sessionDao.getRunning() != null) throw AlreadyRunning()

            val machine = machineDao.getById(input.machineId) ?: throw MachineNotFound()
            val site = input.siteId?.let { siteDao.getById(it) }
            val startTime = input.startTime

            val check = ReadingValidator.checkStart(input.startReading, machine.readingUnit)
            if (check.code != ReadingCheckCode.OK) {
                throw ReadingRejected(check.code.name, check.message ?: "上班读数不合法")
            }

            val previous = sessionDao.latestFinishedForMachine(machine.id)
            val previousReading = previous?.endReading ?: previous?.startReading
            val anomaly = input.startReading != null &&
                previousReading != null &&
                input.startReading < previousReading
            if (anomaly && !input.anomalyConfirmed) {
                throw ConfirmRequired("上班读数 ${input.startReading} 小于上次结班读数 $previousReading，请确认后继续")
            }

            val shiftNo = sessionDao.countForMachineSince(machine.id, businessDayStart(startTime)) + 1
            val sessionId = sessionDao.insert(
                WorkSessionEntity(
                    machineId = machine.id,
                    machineNameSnapshot = machine.name,
                    siteId = site?.id,
                    siteNameSnapshot = site?.name ?: "",
                    shiftNo = shiftNo,
                    startTime = startTime,
                    startReading = input.startReading,
                    status = SessionStatus.RUNNING,
                    readingAnomaly = anomaly,
                    createdAt = startTime,
                    updatedAt = startTime,
                ),
            )
            segmentDao.insert(
                WorkSegmentEntity(
                    sessionId = sessionId,
                    workType = input.workType,
                    seq = 1,
                    startTime = startTime,
                    startReading = input.startReading,
                    createdAt = startTime,
                    updatedAt = startTime,
                ),
            )
            sessionId
        }
    }

    /**
     * 下班打卡。工时 = 班次读数差（INV-04），无读数时 meterHours 为空且 readingMissing=true。
     */
    suspend fun finish(
        endReading: Double?,
        anomalyConfirmed: Boolean = false,
    ): Long = mutex.withLock {
        db.withTransaction {
            val session = sessionDao.getRunning() ?: throw NoRunningSession()
            val machine = machineDao.getById(session.machineId) ?: throw MachineNotFound()

            val check = ReadingValidator.checkEnd(endReading, machine.readingUnit, session.startReading)
            when (check.code) {
                ReadingCheckCode.INVALID -> throw ReadingRejected(check.code.name, check.message ?: "下班读数不合法")
                ReadingCheckCode.ANOMALY -> if (!anomalyConfirmed) {
                    throw ConfirmRequired(check.message ?: "读数倒挂，请确认后继续")
                }
                else -> Unit
            }

            val at = clock()
            segmentDao.activeSegment(session.id)?.let { active ->
                segmentDao.update(
                    active.copy(endTime = at, endReading = endReading, updatedAt = at),
                )
            }

            sessionDao.update(
                session.copy(
                    endTime = at,
                    endReading = endReading,
                    meterHours = TimeRules.readingHours(session.startReading, endReading),
                    status = SessionStatus.FINISHED,
                    readingMissing = endReading == null,
                    readingAnomaly = check.code == ReadingCheckCode.ANOMALY,
                    updatedAt = at,
                ),
            )
            session.id
        }
    }

    /** 切换施工方式：单事务关闭 A（先备份 end 值）、按 seq+1 打开 B、记录双向链。 */
    suspend fun switchWorkType(workType: WorkType, at: Long = clock()): Long = mutex.withLock {
        db.withTransaction {
            val session = sessionDao.getRunning() ?: throw NoRunningSession()
            val current = segmentDao.activeSegment(session.id) ?: throw NoRunningSession()
            if (current.workType == workType) throw SameWorkType()

            segmentDao.update(
                current.copy(
                    endTimeBeforeVoid = current.endTime,
                    endReadingBeforeVoid = current.endReading,
                    endTime = at,
                    updatedAt = at,
                ),
            )
            val nextSeq = segmentDao.maxSeq(session.id) + 1
            segmentDao.insert(
                WorkSegmentEntity(
                    sessionId = session.id,
                    workType = workType,
                    seq = nextSeq,
                    startTime = at,
                    startReading = current.endReading,
                    prevSegmentId = current.id,
                    createdAt = at,
                    updatedAt = at,
                ),
            )
        }
    }

    /**
     * 撤销上一次切换（3s 窗口，INV-08）：
     * 作废 B（保留审计），把 A 恢复为激活并还原 end 备份值，避免「沿用上次读数」退化。
     */
    suspend fun undoLastSwitch(windowMillis: Long = UNDO_WINDOW_MILLIS): Long = mutex.withLock {
        db.withTransaction {
            val session = sessionDao.getRunning() ?: throw NoRunningSession()
            val active = segmentDao.activeSegment(session.id) ?: throw NothingToUndo()
            val prevId = active.prevSegmentId ?: throw NothingToUndo()
            val now = clock()
            if (now - active.startTime > windowMillis) throw UndoExpired()

            val prev = segmentDao.getById(prevId) ?: throw NothingToUndo()

            segmentDao.update(
                active.copy(isVoided = true, voidedAt = now, endTime = now, updatedAt = now),
            )
            segmentDao.update(
                prev.copy(
                    endTime = prev.endTimeBeforeVoid,
                    endReading = prev.endReadingBeforeVoid,
                    endTimeBeforeVoid = null,
                    endReadingBeforeVoid = null,
                    updatedAt = now,
                ),
            )
            prev.id
        }
    }

    /** 作废班次：状态置 VOIDED，不物理删；进行中的分段同时关闭（释放 INV-01 名额）。 */
    suspend fun voidSession(sessionId: Long, reason: String? = null) = mutex.withLock {
        db.withTransaction {
            val session = sessionDao.getById(sessionId) ?: throw SessionNotFound()
            if (session.status == SessionStatus.VOIDED) return@withTransaction
            val at = clock()
            segmentDao.activeSegment(session.id)?.let { active ->
                segmentDao.update(active.copy(endTime = at, updatedAt = at))
            }
            sessionDao.update(
                session.copy(
                    status = SessionStatus.VOIDED,
                    voidedAt = at,
                    voidReason = reason,
                    updatedAt = at,
                ),
            )
        }
    }

    /** 恢复作废班次：仅已结班的班次可恢复为 FINISHED；未结班需重新打卡（不抢 RUNNING 名额）。 */
    suspend fun restoreSession(sessionId: Long) = mutex.withLock {
        db.withTransaction {
            val session = sessionDao.getById(sessionId) ?: throw SessionNotFound()
            if (session.status != SessionStatus.VOIDED) return@withTransaction
            if (session.endTime == null) {
                throw ConfirmRequired("未结班的作废班次不能恢复，请重新上班打卡")
            }
            if (sessionDao.getRunning() != null) throw AlreadyRunning()
            sessionDao.update(
                session.copy(
                    status = SessionStatus.FINISHED,
                    voidedAt = null,
                    voidReason = null,
                    updatedAt = clock(),
                ),
            )
        }
    }

    private suspend fun businessDayStart(epochMillis: Long): Long {
        val boundary = db.settingDao().get()?.businessDayBoundaryMinute
            ?: TimeRules.DEFAULT_BUSINESS_DAY_BOUNDARY_MINUTE
        val date = TimeRules.businessDate(epochMillis, zone, boundary)
        return date.atStartOfDay(zone).plusMinutes(boundary.toLong()).toInstant().toEpochMilli()
    }
}
