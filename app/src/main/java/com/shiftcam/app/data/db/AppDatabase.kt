package com.shiftcam.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shiftcam.domain.watermark.WatermarkLayoutSpec
import com.shiftcam.domain.watermark.WatermarkTemplateCodec

/**
 * 数据库版本与迁移（INV-12）：
 * - 只增不毁，全库禁用破坏性迁移（防漂移检查会 grep）；
 * - 新增版本时在此追加 Migration 并补 MigrationTestHelper 用例。
 */
object DbMigrations {
    // 模板：
    // val MIGRATION_1_2 = object : Migration(1, 2) {
    //     override fun migrate(db: SupportSQLiteDatabase) {
    //         db.execSQL("ALTER TABLE work_sessions ADD COLUMN xxx TEXT")
    //     }
    // }
    val ALL: Array<Migration> = emptyArray()
}

/**
 * 部分唯一索引无法用 Room 的 @Index 表达（Room 不支持 WHERE 子句），
 * 因此在建库回调里用原生 SQL 创建，索引名与文档一致：
 * - ux_session_running：全局最多 1 个 RUNNING 班次（INV-01）
 * - ux_segment_active：每班次最多 1 个激活分段（INV-02）
 */
object DbSql {
    const val UxSessionRunning: String =
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_session_running ON work_sessions(status) WHERE status = 'RUNNING'"

    const val UxSegmentActive: String =
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_segment_active ON work_segments(sessionId) " +
            "WHERE endTime IS NULL AND isVoided = 0"
}

object DbBootstrap : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        db.execSQL(DbSql.UxSessionRunning)
        db.execSQL(DbSql.UxSegmentActive)

        val now = System.currentTimeMillis()
        val spec = WatermarkLayoutSpec()
        val specJson = WatermarkTemplateCodec.encode(spec)
        db.execSQL(
            "INSERT INTO watermark_templates " +
                "(id, name, schemaVersion, fieldsConfig, styleConfig, isDefault, createdAt, updatedAt) " +
                "VALUES (1, ?, ?, ?, ?, 1, ?, ?)",
            arrayOf("默认水印", WatermarkLayoutSpec.CURRENT_SCHEMA_VERSION, specJson, specJson, now, now),
        )
        db.execSQL(
            "INSERT INTO app_settings " +
                "(id, businessDayBoundaryMinute, onboardingDone, createdAt, updatedAt) " +
                "VALUES (1, ?, 0, ?, ?)",
            arrayOf(4 * 60, now, now),
        )
    }
}

@Database(
    entities = [
        MachineEntity::class,
        WorkSiteEntity::class,
        WorkSessionEntity::class,
        WorkSegmentEntity::class,
        CountRecordEntity::class,
        RemarkEntity::class,
        PhotoEntity::class,
        WatermarkTemplateEntity::class,
        AppSettingEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun machineDao(): MachineDao
    abstract fun siteDao(): WorkSiteDao
    abstract fun sessionDao(): SessionDao
    abstract fun segmentDao(): SegmentDao
    abstract fun countDao(): CountDao
    abstract fun remarkDao(): RemarkDao
    abstract fun photoDao(): PhotoDao
    abstract fun templateDao(): TemplateDao
    abstract fun settingDao(): SettingDao

    companion object {
        const val VERSION: Int = 1
        private const val DB_NAME = "shiftcam.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .addCallback(DbBootstrap)
                .addMigrations(*DbMigrations.ALL)
                .build()
    }
}
