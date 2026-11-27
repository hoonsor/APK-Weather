package tw.hoonsor.weather.domain

/**
 * 語音說的地點名稱（例如「阿嬤家」「西屯區那邊」）比對設定頁的地點（倬 2026-11-27）。
 * 純函式，JVM 測試在 PlaceMatchTest。
 */
object PlaceMatch {
    /** 比對結果：ref 是地點參照（home／school／x:<id>），label 是要唸的名稱 */
    data class Hit(val ref: String, val label: String)

    private val tails = listOf("那邊", "那裡", "那里", "這邊", "附近", "的天氣", "天氣")

    fun norm(s: String): String {
        var t = s.trim().replace(" ", "").replace("　", "").replace('台', '臺')
        var changed = true
        while (changed) {
            changed = false
            for (x in tails) if (t.endsWith(x) && t.length > x.length) { t = t.removeSuffix(x); changed = true }
        }
        return t.removeSuffix("的")
    }

    /**
     * 依序比對，取第一層有結果的：
     * 1. 名稱完全相同（含「家」「家裡」「學校」）
     * 2. 名稱互相包含（「阿嬤」對到「阿嬤家」）
     * 3. 鄉鎮名或縣市＋鄉鎮相同／包含（「西屯區」「西屯」）
     * 回傳多筆時由呼叫端反問。
     */
    fun match(name: String, extras: List<SavedPlace>, home: Town?, school: Town?): List<Hit> {
        val q = norm(name)
        if (q.isEmpty()) return emptyList()
        val all = buildList {
            home?.let { add(Triple(Hit("home", "家"), listOf("家", "家裡", "我家", "住家"), it)) }
            school?.let { add(Triple(Hit("school", "學校"), listOf("學校", "上班的地方"), it)) }
            extras.forEach { p -> add(Triple(Hit(p.ref, p.label), listOf(norm(p.label)), p.town)) }
        }
        val exact = all.filter { (_, names, _) -> q in names }
        if (exact.isNotEmpty()) return exact.map { it.first }
        val contains = all.filter { (_, names, _) -> names.any { n -> n.length >= 2 && q.length >= 2 && (n.contains(q) || q.contains(n)) } }
        if (contains.isNotEmpty()) return contains.map { it.first }
        val town = all.filter { (_, _, t) ->
            val n = norm(t.name); val f = norm(t.full)
            q == n || q == f || (q.length >= 2 && (n.startsWith(q) || f.endsWith(q) || q.endsWith(n)))
        }
        return town.map { it.first }.distinctBy { it.ref }
    }
}
