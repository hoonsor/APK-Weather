package tw.hoonsor.weather.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tw.hoonsor.weather.domain.Town
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 目前位置（F03）：系統 LocationManager（不依賴 Google Play 服務）＋ Geocoder 轉鄉鎮。
 * 拿不到新位置時用最後已知位置；都沒有就回 null，由 Repository 退回常用地點。
 */
class Locator(private val context: Context) {
    private val lm = context.getSystemService(LocationManager::class.java)

    fun hasPermission() = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackground() = context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun location(): Location? {
        if (!hasPermission()) return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        for (p in providers) {
            val fresh = withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    runCatching { lm.getCurrentLocation(p, null, context.mainExecutor) { cont.resume(it) } }
                        .onFailure { cont.resume(null) }
                }
            }
            if (fresh != null) return fresh
        }
        return providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    /** 座標 → 鄉鎮；Geocoder 失敗時回 null（畫面顯示座標附近） */
    @Suppress("DEPRECATION")
    suspend fun townOf(lat: Double, lon: Double): Town? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val a = runCatching { Geocoder(context, Locale.TAIWAN).getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull() ?: return@withContext null
        val county = listOfNotNull(a.adminArea, a.subAdminArea).map(Names::norm).firstOrNull { it in Names.counties } ?: return@withContext null
        val town = listOfNotNull(a.locality, a.subLocality, a.subAdminArea).map(Names::norm)
            .firstOrNull { it != county && (it.endsWith("區") || it.endsWith("鄉") || it.endsWith("鎮") || it.endsWith("市")) } ?: return@withContext null
        Town(county, town, round2(lat), round2(lon))
    }

    /** 「桃園市中壢區」→ 座標（沒有氣象署授權碼時，設定地點用） */
    @Suppress("DEPRECATION")
    suspend fun geocode(county: String, town: String): Town? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val a = runCatching { Geocoder(context, Locale.TAIWAN).getFromLocationName("$county$town", 1)?.firstOrNull() }.getOrNull() ?: return@withContext null
        Town(county, town, round2(a.latitude), round2(a.longitude))
    }

    private fun round2(v: Double) = Math.round(v * 100) / 100.0
}
