package tw.hoonsor.weather.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 寬鬆的 JSON 讀取：鍵名不分大小寫、可給多個候選名稱。
 * 原因：氣象署 2024-12 改版後鍵名大小寫改過，實際格式要有金鑰才能核對（DATA_SOURCES.md「未確認」）。
 */
object J {
    fun key(o: JSONObject?, name: String): String? {
        if (o == null) return null
        if (o.has(name)) return name
        val it = o.keys()
        while (it.hasNext()) { val k = it.next(); if (k.equals(name, ignoreCase = true)) return k }
        return null
    }

    fun any(o: JSONObject?, vararg names: String): Any? {
        for (n in names) { val k = key(o, n) ?: continue; val v = o!!.opt(k); if (v != null && v != JSONObject.NULL) return v }
        return null
    }

    fun obj(o: JSONObject?, vararg names: String) = any(o, *names) as? JSONObject
    fun arr(o: JSONObject?, vararg names: String): JSONArray? = when (val v = any(o, *names)) {
        is JSONArray -> v
        is JSONObject -> JSONArray().put(v)          // 只有一筆時有些 API 會給物件而不是陣列
        else -> null
    }
    fun str(o: JSONObject?, vararg names: String): String? = any(o, *names)?.toString()?.trim()?.takeIf { it.isNotEmpty() && it != "-" && it != "null" }
    fun num(o: JSONObject?, vararg names: String): Double? = str(o, *names)?.toDoubleOrNull()
    fun int(o: JSONObject?, vararg names: String): Int? = num(o, *names)?.roundToInt()

    fun objs(a: JSONArray?): List<JSONObject> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it) }

    /** 深度搜尋：所有符合條件的物件（含自己） */
    fun findObjects(root: Any?, match: (JSONObject) -> Boolean): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        fun walk(v: Any?) {
            when (v) {
                is JSONObject -> { if (match(v)) out += v; v.keys().forEach { walk(v.opt(it)) } }
                is JSONArray -> for (i in 0 until v.length()) walk(v.opt(i))
            }
        }
        walk(root)
        return out
    }

    /** 物件裡第一個鍵名符合 regex 的值（深度搜尋，例：七級風半徑的實際鍵名不確定） */
    fun deepValue(root: Any?, keyRegex: Regex): Any? {
        when (root) {
            is JSONObject -> {
                root.keys().forEach { k -> val v = root.opt(k); if (keyRegex.containsMatchIn(k) && v !is JSONObject && v !is JSONArray) return v }
                root.keys().forEach { k -> deepValue(root.opt(k), keyRegex)?.let { return it } }
            }
            is JSONArray -> for (i in 0 until root.length()) deepValue(root.opt(i), keyRegex)?.let { return it }
        }
        return null
    }

    private val plain = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 「2026-11-10T18:00:00+08:00」「2026-11-10 18:00:00」「2026-11-10T18:00」都接受，一律轉台灣當地時間 */
    fun time(s: String?): LocalDateTime? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        return runCatching { OffsetDateTime.parse(t).atZoneSameInstant(java.time.ZoneId.of("Asia/Taipei")).toLocalDateTime() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(t.replace(' ', 'T')) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(t, plain) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(t.replace('/', '-').replace(' ', 'T')) }.getOrNull()
    }
}

/** 縣市、鄉鎮名稱正規化：台→臺（氣象署用「臺」），去空白 */
object Names {
    val counties = listOf(
        "臺北市", "新北市", "基隆市", "桃園市", "新竹市", "新竹縣", "苗栗縣", "臺中市", "彰化縣", "南投縣", "雲林縣",
        "嘉義市", "嘉義縣", "臺南市", "高雄市", "屏東縣", "宜蘭縣", "花蓮縣", "臺東縣", "澎湖縣", "金門縣", "連江縣",
    )

    fun norm(s: String): String = s.trim().replace(" ", "").replace("　", "").replace("台", "臺")
        .replace("臺灣省", "").replace("臺灣", "").replace("中華民國", "")

    /** 「桃園市中壢區」→（桃園市, 中壢區）；只有鄉鎮時 county＝null */
    fun split(full: String): Pair<String?, String> {
        val n = norm(full)
        val c = counties.firstOrNull { n.startsWith(it) }
        return if (c != null) c to n.removePrefix(c) else null to n
    }

    /** 文字裡提到的縣市（停班停課、颱風警報範圍用）；「臺中」這種省略「市」的也算 */
    fun countiesIn(text: String): Set<String> {
        val n = norm(text)
        return counties.filter { n.contains(it) || n.contains(it.dropLast(1)) && !(it == "新竹市" || it == "新竹縣" || it == "嘉義市" || it == "嘉義縣") }.toSet()
    }
}
