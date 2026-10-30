package com.shiftcam.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shiftcam.app.data.db.AppDatabase
import com.shiftcam.app.data.db.DbBootstrap
import com.shiftcam.app.data.db.MachineEntity
import com.shiftcam.app.data.repo.DomainException.AlreadyRunning
import com.shiftcam.app.data.repo.DomainException.ConfirmRequired
import com.shiftcam.domain.session.SessionStatus
import com.shiftcam.domain.session.WorkType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 数据层不变量回归（INV-01/02/04/07/08）。
 * 使用内存 Room + DbBootstrap（部分唯一索引由 onCreate 建出，因此这里同时验证索引真的生效）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: SessionRepository
    private val clock = Clock()

    private class Clock {
        var now: Long = 1_700_000_000_000L
            private set

        fun advance(millis: Long): Long {
            now += millis
            return now
        }

        fun read(): Long = now
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DbBootstrap)
            .allowMainThreadQueries()
            .build()
        repo = SessionRepository(db, clock = clock::read)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun machine(): MachineEntity = runBlocking {
        val now = clock.read()
        db.machineDao().insert(MachineEntity(name = "挖机 1", code = "EX-01", createdAt = now, updatedAt = now))
        db.machineDao().getById(1)!!
    }

    private fun expectFailure(block: suspend () -> Unit): Throwable {
        try {
            runBlocking { block() }
        } catch (t: Throwable) {
            return t
        }
        fail("期望抛出异常，但实际成功")
        throw AssertionError("unreachable")
    }

    @Test
    fun `INV-01 同时只能有一个 RUNNING 班次`() = runBlocking {
        val machine = machine()
        repo.start(StartShiftInput(machine.id, startReading = 100.0, startTime = clock.advance(0)))

        val failure = expectFailure {
            repo.start(StartShiftInput(machine.id, startReading = 100.0, startTime = clock.advance(1_000)))
        }
        assertEquals(AlreadyRunning::class.java, failure::class.java)
        assertEquals(1, db.sessionDao().listBetween(0, Long.MAX_VALUE).size)
    }

    @Test
    fun `INV-02 切换后仅一个激活分段且按 seq 递增`() = runBlocking {
        val machine = machine()
        val sessionId = repo.start(
            StartShiftInput(machine.id, startReading = 100.0, workType = WorkType.DIGGING, startTime = clock.advance(0)),
        )
        val first = db.segmentDao().activeSegment(sessionId)!!
        assertEquals(1, first.seq)

        clock.advance(60_000)
        repo.switchWorkType(WorkType.LOADING, at = clock.read())

        val active = db.segmentDao().activeSegment(sessionId)!!
        assertEquals(2, active.seq)
        assertEquals(WorkType.LOADING, active.workType)
        assertEquals(first.id, active.prevSegmentId)

        val segments = repo.listSegments(sessionId)
        assertEquals(2, segments.size)
        assertEquals(1, segments.count { it.endTime == null && !it.isVoided })
    }

    @Test
    fun `INV-08 撤销恢复 A 的 end 值且 B 保留审计`() = runBlocking {
        val machine = machine()
        val sessionId = repo.start(
            StartShiftInput(machine.id, startReading = 100.0, workType = WorkType.DIGGING, startTime = clock.advance(0)),
        )
        val first = db.segmentDao().activeSegment(sessionId)!!
        assertNull(first.endTimeBeforeVoid)

        clock.advance(60_000)
        repo.switchWorkType(WorkType.LEVELING, at = clock.read())
        val second = db.segmentDao().activeSegment(sessionId)!!

        clock.advance(1_000)
        val restored = repo.undoLastSwitch()

        assertEquals(first.id, restored)
        val restoredA = db.segmentDao().getById(first.id)!!
        assertNull(restoredA.endTime)
        assertNull(restoredA.endReading)
        assertNull(restoredA.endTimeBeforeVoid)

        val voidedB = db.segmentDao().getById(second.id)!!
        assertTrue(voidedB.isVoided)
        assertNotNull(voidedB.voidedAt)

        val active = db.segmentDao().activeSegment(sessionId)!!
        assertEquals(first.id, active.id)
    }

    @Test
    fun `INV-08 超过 3s 撤销窗口拒绝`() = runBlocking {
        val machine = machine()
        repo.start(StartShiftInput(machine.id, startReading = 100.0, startTime = clock.advance(0)))
        clock.advance(1_000)
        repo.switchWorkType(WorkType.LOADING, at = clock.read())
        clock.advance(SessionRepository.UNDO_WINDOW_MILLIS + 1_000)

        val failure = expectFailure { repo.undoLastSwitch() }
        assertEquals(DomainException.UndoExpired::class.java, failure::class.java)
    }

    @Test
    fun `INV-04 结班工时为班次读数差`() = runBlocking {
        val machine = machine()
        val sessionId = repo.start(StartShiftInput(machine.id, startReading = 100.0, startTime = clock.advance(0)))
        clock.advance(3_600_000)
        repo.finish(endReading = 108.5)

        val session = db.sessionDao().getById(sessionId)!!
        assertEquals(SessionStatus.FINISHED, session.status)
        assertEquals(8.5, session.meterHours!!, 1e-9)
        assertEquals(false, session.readingMissing)
        assertNotNull(session.endTime)
        // 结班同时关闭激活分段
        assertNull(db.segmentDao().activeSegment(sessionId))
    }

    @Test
    fun `R31 下班读数倒挂需二次确认`() = runBlocking {
        val machine = machine()
        val sessionId = repo.start(StartShiftInput(machine.id, startReading = 100.0, startTime = clock.advance(0)))

        val failure = expectFailure { repo.finish(endReading = 99.0) }
        assertEquals(ConfirmRequired::class.java, failure::class.java)

        clock.advance(1_000)
        repo.finish(endReading = 99.0, anomalyConfirmed = true)
        val session = db.sessionDao().getById(sessionId)!!
        assertEquals(true, session.readingAnomaly)
        assertEquals(-1.0, session.meterHours!!, 1e-9)
    }

    @Test
    fun `INV-01 无读数结班后可重新上班`() = runBlocking {
        val machine = machine()
        // 上班读数必填（R31），仅下班允许留空
        val sessionId = repo.start(StartShiftInput(machine.id, startReading = 100.0, startTime = clock.advance(0)))
        clock.advance(1_000)
        repo.finish(endReading = null)

        val finished = db.sessionDao().getById(sessionId)!!
        assertEquals(true, finished.readingMissing)
        assertNull(finished.meterHours)

        clock.advance(1_000)
        repo.start(StartShiftInput(machine.id, startReading = 100.0, startTime = clock.read()))
        assertNotNull(repo.getRunning())
    }

    @Test
    fun `INV-07 作废不物理删且恢复只限已结班`() = runBlocking {
        val machine = machine()
        val sessionId = repo.start(StartShiftInput(machine.id, startReading = 100.0, startTime = clock.advance(0)))
        clock.advance(2_000)
        repo.voidSession(sessionId, "误操作")

        val voided = db.sessionDao().getById(sessionId)!!
        assertEquals(SessionStatus.VOIDED, voided.status)
        assertNotNull(voided.voidedAt)
        assertNull(repo.getRunning())

        // 未结班的作废班次不能恢复
        val failure = expectFailure { repo.restoreSession(sessionId) }
        assertEquals(ConfirmRequired::class.java, failure::class.java)
    }

    @Test
    fun `INV-07 已结班作废可恢复为 FINISHED`() = runBlocking {
        val machine = machine()
        val sessionId = repo.start(StartShiftInput(machine.id, startReading = 100.0, startTime = clock.advance(0)))
        clock.advance(1_000)
        repo.finish(endReading = 105.0)
        repo.voidSession(sessionId, "误操作")
        repo.restoreSession(sessionId)

        val restored = db.sessionDao().getById(sessionId)!!
        assertEquals(SessionStatus.FINISHED, restored.status)
        assertNull(restored.voidedAt)
    }
}
