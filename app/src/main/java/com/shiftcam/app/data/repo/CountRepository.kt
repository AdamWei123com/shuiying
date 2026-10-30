package com.shiftcam.app.data.repo

import androidx.room.withTransaction
import com.shiftcam.app.data.db.AppDatabase
import com.shiftcam.app.data.db.CountRecordEntity
import com.shiftcam.domain.session.WorkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data class CountTap(
    val sessionId: Long,
    val workType: WorkType,
    val plateNo: String?,
    val amount: Int,
    val at: Long,
)

/**
 * 计数仓储（owner C）。写入按 300ms 窗口合并批量落库（R8 / 02-01 工程约束），
 * 避免司机连点时每点一次走一次磁盘事务。
 *
 * 接口冻结：add / undoLatest / softDelete。
 */
class CountRepository(
    private val db: AppDatabase,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val batchWindowMillis: Long = 300L,
) {

    private val mutex = Mutex()
    private val pending = mutableListOf<CountTap>()
    private var flushJob: Job? = null

    fun observeTotal(sessionId: Long): Flow<Int> = db.countDao().observeTotalCount(sessionId)

    fun observeRecords(sessionId: Long): Flow<List<CountRecordEntity>> = db.countDao().observeRecords(sessionId)

    /** 司机点一次：先入内存批次，300ms 内合并写库。 */
    suspend fun add(
        sessionId: Long,
        workType: WorkType,
        plateNo: String? = null,
        amount: Int = 1,
        at: Long = clock(),
    ) {
        require(amount > 0) { "计数增量必须为正" }
        mutex.withLock {
            pending += CountTap(sessionId, workType, plateNo, amount, at)
            flushJob?.cancel()
            flushJob = scope.launch(Dispatchers.IO) {
                delay(batchWindowMillis)
                flushPending()
            }
        }
    }

    /** 撤销最近一次计数（含尚未落库的批次）。 */
    suspend fun undoLatest(sessionId: Long): Boolean {
        flushPending()
        return db.withTransaction {
            val latest = db.countDao().latestActive(sessionId) ?: return@withTransaction false
            db.countDao().void(latest.id, clock())
            true
        }
    }

    /** 单条软删（计数页长按删除）。 */
    suspend fun softDelete(recordId: Long) {
        db.withTransaction { db.countDao().softDelete(recordId, clock()) }
    }

    suspend fun flushPending() {
        mutex.withLock {
            val items = pending.toList()
            pending.clear()
            if (items.isEmpty()) return
            db.withTransaction {
                val segmentIds = items.map { it.sessionId }.distinct()
                    .associateWith { db.segmentDao().activeSegment(it)?.id }
                db.countDao().insertAll(
                    items.map { tap ->
                        CountRecordEntity(
                            sessionId = tap.sessionId,
                            segmentId = segmentIds[tap.sessionId],
                            workType = tap.workType,
                            plateNo = tap.plateNo,
                            countValue = tap.amount,
                            countedAt = tap.at,
                            createdAt = tap.at,
                        )
                    },
                )
            }
        }
    }
}
