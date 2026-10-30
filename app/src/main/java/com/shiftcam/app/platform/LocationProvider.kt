package com.shiftcam.app.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class GeoPoint(
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
)

/**
 * 定位 + 地址降级（INV-09）：
 * - 无权限 / 无定位 / 反查失败都不阻塞拍照；
 * - 精度 > 100m 时不显示经纬度；
 * - 坐标 (0,0) 一律视为无效。
 */
class LocationProvider(private val context: Context) {

    companion object {
        const val ACCURACY_LIMIT_METERS: Float = 100f
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** 调用前已做运行时权限检查；定位失败一律降级为 null，不阻塞拍照。 */
    @SuppressLint("MissingPermission")
    fun lastKnown(): GeoPoint? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val candidates = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )
        val location: Location = candidates
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?: return null
        if (location.latitude == 0.0 && location.longitude == 0.0) return null
        return GeoPoint(location.latitude, location.longitude, location.accuracy)
    }

    suspend fun addressOf(point: GeoPoint?): String? {
        if (point == null) return null
        if (point.accuracy > ACCURACY_LIMIT_METERS) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                val geocoder = Geocoder(context, Locale.CHINA)
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(point.lat, point.lng, 1)
                    ?.firstOrNull()
                    ?.let { item ->
                        listOfNotNull(item.locality, item.subLocality, item.featureName)
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString("")
                    }
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }
    }

    /** 水印位置文本：精度达标才写坐标，地址优先。 */
    fun watermarkText(point: GeoPoint?, address: String?): String = when {
        address != null -> address
        point != null && point.accuracy <= ACCURACY_LIMIT_METERS ->
            String.format(Locale.US, "%.4f,%.4f", point.lat, point.lng)
        else -> ""
    }
}
