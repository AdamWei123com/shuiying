package com.shiftcam.app

import android.app.Application
import android.content.Context
import com.shiftcam.app.data.db.AppDatabase
import com.shiftcam.app.data.repo.BaseDataRepository
import com.shiftcam.app.data.repo.CountRepository
import com.shiftcam.app.data.repo.PhotoRepository
import com.shiftcam.app.data.repo.RemarkRepository
import com.shiftcam.app.data.repo.SessionRepository
import com.shiftcam.app.data.repo.SettingRepository
import com.shiftcam.app.data.repo.TemplateRepository
import com.shiftcam.app.platform.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ShiftCamApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }

    companion object {
        fun graphOf(context: Context): AppGraph =
            (context.applicationContext as ShiftCamApp).graph
    }
}

/**
 * 手写依赖容器：单机 App 不引 Hilt，减少依赖与编译风险。
 * 所有 Repository 共享一个 DB 与 IO 作用域。
 */
class AppGraph(context: Context) {

    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db: AppDatabase = AppDatabase.build(appContext)

    val sessionRepository = SessionRepository(db)
    val countRepository = CountRepository(db, scope)
    val baseDataRepository = BaseDataRepository(db)
    val remarkRepository = RemarkRepository(db)
    val templateRepository = TemplateRepository(db)
    val settingRepository = SettingRepository(db)
    val photoRepository = PhotoRepository(db)
    val locationProvider = LocationProvider(appContext)

    fun close() {
        scope.cancel()
    }
}
