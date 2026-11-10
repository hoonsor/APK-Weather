package tw.hoonsor.weather.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.weather.domain.PlaceKind
import tw.hoonsor.weather.domain.Town
import java.security.KeyStore
import java.time.LocalDate
import java.time.LocalTime
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 一般設定（settings.xml，會進系統備份與 APP 備份檔）。
 * 授權碼不在這裡，在 [Vault]（Keystore 加密、不備份）。
 */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    /** 任何設定改變就 +1，畫面用來重組 */
    val version: StateFlow<Int> = _version
    private fun bump() { _version.value++ }

    fun place(kind: PlaceKind): Town? = p.getString("place_${kind.key}", null)?.let { runCatching { SnapshotJson.town(JSONObject(it)) }.getOrNull() }
    fun setPlace(kind: PlaceKind, t: Town?) { p.edit().apply { if (t == null) remove("place_${kind.key}") else putString("place_${kind.key}", SnapshotJson.town(t).toString()) }.apply(); bump() }

    /** 怕冷 +、怕熱 −（度），穿著規則整體平移 */
    var shift: Int
        get() = p.getInt("shift", 0)
        set(v) { p.edit().putInt("shift", v.coerceIn(-4, 4)).apply(); bump() }

    var broadcastAir: Boolean
        get() = p.getBoolean("broadcast_air", true)
        set(v) { p.edit().putBoolean("broadcast_air", v).apply(); bump() }
    var broadcastTyphoon: Boolean
        get() = p.getBoolean("broadcast_typhoon", true)
        set(v) { p.edit().putBoolean("broadcast_typhoon", v).apply(); bump() }

    var notifyTyphoon: Boolean
        get() = p.getBoolean("notify_typhoon", true)
        set(v) { p.edit().putBoolean("notify_typhoon", v).apply(); bump() }
    var notifyAir: Boolean
        get() = p.getBoolean("notify_air", true)
        set(v) { p.edit().putBoolean("notify_air", v).apply(); bump() }
    var morningRain: Boolean
        get() = p.getBoolean("morning_rain", true)
        set(v) { p.edit().putBoolean("morning_rain", v).apply(); bump() }
    var morningTime: LocalTime
        get() = runCatching { LocalTime.parse(p.getString("morning_time", "06:50")) }.getOrDefault(LocalTime.of(6, 50))
        set(v) { p.edit().putString("morning_time", v.toString()).apply(); bump() }

    /** 小工具更新間隔（分鐘）：30 或 60 */
    var refreshMinutes: Int
        get() = p.getInt("refresh_minutes", 60)
        set(v) { p.edit().putInt("refresh_minutes", if (v <= 30) 30 else 60).apply(); bump() }
    /** 新放上桌面的 4×1 預設版面：A 或 C */
    var widgetDefault: String
        get() = p.getString("widget_default", "A") ?: "A"
        set(v) { p.edit().putString("widget_default", if (v == "C") "C" else "A").apply(); bump() }

    /** 倬手動標的上班／放假日 */
    var calendarOverrides: Map<LocalDate, Boolean>
        get() = runCatching { JSONObject(p.getString("cal_overrides", "{}")!!).let { o -> o.keys().asSequence().associate { LocalDate.parse(it) to o.getBoolean(it) } } }.getOrDefault(emptyMap())
        set(v) { p.edit().putString("cal_overrides", JSONObject().apply { v.forEach { (d, w) -> put(d.toString(), w) } }.toString()).apply(); bump() }

    /** 環境部金鑰的取得日（效期 1 年，到期前 30 天提醒） */
    var moenvKeyDate: LocalDate?
        get() = p.getString("moenv_key_date", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(v) { p.edit().apply { if (v == null) remove("moenv_key_date") else putString("moenv_key_date", v.toString()) }.apply(); bump() }

    var lastError: String?
        get() = p.getString("last_error", null)
        set(v) { p.edit().putString("last_error", v).apply() }
    var lastRefresh: String?
        get() = p.getString("last_refresh", null)
        set(v) { p.edit().putString("last_refresh", v).apply() }

    /** 已發過的通知（避免重複），只留最近 50 筆 */
    fun notifiedOnce(key: String): Boolean {
        val set = p.getStringSet("notified", emptySet())!!.toMutableList()
        if (key in set) return false
        set += key
        p.edit().putStringSet("notified", set.takeLast(50).toSet()).apply()
        return true
    }

    // ---- 備份用：一般設定整包匯出／匯入（不含授權碼） ----
    private val backupKeys = listOf("place_current", "place_home", "place_school", "shift", "broadcast_air", "broadcast_typhoon", "notify_typhoon", "notify_air",
        "morning_rain", "morning_time", "refresh_minutes", "widget_default", "cal_overrides", "moenv_key_date")

    fun export(): JSONObject = JSONObject().apply { backupKeys.forEach { k -> p.all[k]?.let { put(k, it) } } }

    fun import(o: JSONObject, merge: Boolean) {
        val e = p.edit()
        if (!merge) backupKeys.forEach { e.remove(it) }
        o.keys().forEach { k ->
            if (k !in backupKeys) return@forEach
            when (val v = o.get(k)) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Number -> e.putInt(k, v.toInt())
                is String -> e.putString(k, v)
                is JSONArray, is JSONObject -> e.putString(k, v.toString())
            }
        }
        e.apply(); bump()
    }
}

/** 授權碼保險箱：Android Keystore AES-GCM，存在 vault.xml（不進任何備份） */
class Vault(context: Context) {
    private val p = context.getSharedPreferences("vault", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return g.generateKey()
    }

    fun get(name: String): String? {
        val raw = p.getString(name, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(raw, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            String(c.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        }.getOrNull()
    }

    fun set(name: String, value: String?) {
        if (value.isNullOrBlank()) { p.edit().remove(name).apply(); _version.value++; return }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        p.edit().putString(name, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
        _version.value++
    }

    fun has(name: String) = get(name) != null

    companion object {
        private const val ALIAS = "weather_vault"
        const val CWA = "cwa"
        const val MOENV = "moenv"
    }
}
