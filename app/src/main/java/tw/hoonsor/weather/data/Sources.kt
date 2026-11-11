package tw.hoonsor.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 連線失敗（不含網址：網址裡有授權碼，不能進 log 或畫面） */
class SourceException(val source: String, val httpCode: Int?, message: String) : IOException(message)

/**
 * 所有對外連線集中在這裡（DATA_SOURCES.md）。只回原始文字，解析交給 *Parse（純邏輯、可測試）。
 * 隱私：只送鄉鎮名稱；Open-Meteo 的座標四捨五入到小數 2 位（約 1 公里）。
 */
class Sources(private val http: OkHttpClient = defaultClient) {
    private suspend fun get(name: String, url: HttpUrl): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", "HoonsorWeather/1 (Android)").build()
        try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw SourceException(name, r.code, "$name 回應 ${r.code}")
                r.body?.string() ?: throw SourceException(name, r.code, "$name 沒有內容")
            }
        } catch (e: SourceException) { throw e } catch (e: IOException) {
            throw SourceException(name, null, "$name 連不上（${e.javaClass.simpleName}）")
        }
    }

    private fun cwa(id: String, key: String) = "https://opendata.cwa.gov.tw/api/v1/rest/datastore/$id".toHttpUrl().newBuilder()
        .addQueryParameter("Authorization", key).addQueryParameter("format", "JSON")

    /** 一個鄉鎮的 3 天＋1 週預報（F-D0047-093，locationId 一次帶兩個資料集） */
    suspend fun cwaForecast(key: String, county: String, town: String): String {
        val (d3, dw) = CwaParse.datasets[Names.norm(county)] ?: throw SourceException("氣象署", null, "不認得的縣市：$county")
        return get("氣象署", cwa("F-D0047-093", key).addQueryParameter("locationId", "$d3,$dw").addQueryParameter("LocationName", Names.norm(town)).build())
    }

    /** 某縣市所有鄉鎮（3 天資料集，只要名稱與座標；設定地點時比對用） */
    suspend fun cwaCountyTowns(key: String, county: String): String {
        val (d3, _) = CwaParse.datasets[Names.norm(county)] ?: throw SourceException("氣象署", null, "不認得的縣市：$county")
        return get("氣象署", cwa(d3, key).addQueryParameter("ElementName", "溫度").build())
    }

    suspend fun cwaTyphoonTrack(key: String) = get("氣象署颱風路徑", cwa("W-C0034-005", key).build())
    suspend fun cwaTyphoonWarning(key: String) = get("氣象署颱風警報", cwa("W-C0034-001", key).build())

    suspend fun openMeteo(lat: Double, lon: Double): String = get(
        "Open-Meteo",
        "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", String.format(Locale.US, "%.2f", lat))
            .addQueryParameter("longitude", String.format(Locale.US, "%.2f", lon))
            .addQueryParameter("hourly", "temperature_2m,apparent_temperature,precipitation_probability,weather_code,relative_humidity_2m,wind_speed_10m")
            .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,precipitation_probability_max,uv_index_max,wind_speed_10m_max")
            .addQueryParameter("timezone", "Asia/Taipei").addQueryParameter("forecast_days", "8").build(),
    )

    private fun moenv(id: String, key: String) = "https://data.moenv.gov.tw/api/v2/$id".toHttpUrl().newBuilder()
        .addQueryParameter("api_key", key).addQueryParameter("format", "JSON")

    suspend fun moenvStations(key: String) = get("環境部空品", moenv("aqx_p_432", key).addQueryParameter("limit", "1000").build())

    /** 空品預報：要最新的幾次發布，先試排序參數，不支援就退回不排序 */
    suspend fun moenvForecast(key: String): String = try {
        get("環境部空品預報", moenv("aqf_p_01", key).addQueryParameter("limit", "200").addQueryParameter("sort", "publishtime desc").build())
    } catch (e: SourceException) {
        if (e.httpCode == null) throw e
        get("環境部空品預報", moenv("aqf_p_01", key).addQueryParameter("limit", "200").build())
    }

    suspend fun calendarIndex() = get("政府資料開放平臺", "https://data.gov.tw/api/v2/rest/dataset/14718".toHttpUrl())
    /** 下載文字檔。辦公日曆 CSV 有 UTF-8 也有 Big5（114 年修正檔），不是合法 UTF-8 就改用 Big5 解碼 */
    suspend fun download(name: String, url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url.toHttpUrl()).header("User-Agent", "HoonsorWeather/1 (Android)").build()
        try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw SourceException(name, r.code, "$name 回應 ${r.code}")
                decodeText(r.body?.bytes() ?: throw SourceException(name, r.code, "$name 沒有內容"))
            }
        } catch (e: SourceException) { throw e } catch (e: IOException) {
            throw SourceException(name, null, "$name 連不上（${e.javaClass.simpleName}）")
        }
    }
    suspend fun ncdrOffWork() = get("停班停課（NCDR）", "https://alerts.ncdr.nat.gov.tw/RssAtomFeed.ashx?AlertType=33".toHttpUrl())
    suspend fun dgpaOffWork() = get("停班停課（人事總處）", "https://www.dgpa.gov.tw/typh/daily/nds.html".toHttpUrl())

    companion object {
        fun decodeText(b: ByteArray): String = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(b)).toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            String(b, charset("Big5"))
        }

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).callTimeout(14, TimeUnit.SECONDS).build()
    }
}
