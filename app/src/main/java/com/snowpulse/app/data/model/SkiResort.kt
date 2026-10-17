package com.snowpulse.app.data.model

import android.content.Context
import org.json.JSONArray
import kotlin.math.*

data class SkiResort(
    val id: String,
    val name: String,
    val kana: String = "",
    val latitude: Double,
    val longitude: Double,
    val elevationTopM: Int? = null,
    val snowboardAllowed: Boolean = true,
    val nightSkiing: Boolean = false,
    val url: String = "",
    val area: String = "",
    val prefecture: String = "",
    val snowDepthCm: Int = if (java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1 in 5..10) 0 else 240,
    val tempC: Float = if (java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1 in 5..10) 14.5f else -3.5f,
    val weatherIcon: String = if (java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1 in 5..10) "🍂" else "❄️",
    val weatherText: String = if (java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1 in 5..10) "秋晴れ (シーズン前)" else "パウダースノー",
    val isPowder: Boolean = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1 !in 5..10,
    var distanceKm: Float? = null
) {
    /**
     * 現在地からの距離表示文字列。GPSオフまたは未取得時はハイフン「- km」
     */
    val distanceDisplay: String
        get() = if (distanceKm != null && distanceKm!! >= 0) "%.1f km".format(distanceKm) else "- km"
}

object SkiResortData {
    @Volatile
    private var masterResortsCache: List<SkiResort>? = null

    // コンテキストが未提供の際（ユニットテスト等）に利用する代表的スキー場フォールバック
    val DEFAULT_RESORTS = listOf(
        SkiResort(
            id = "ski_0056",
            name = "川場スキー場",
            kana = "かわばすきーじょう",
            latitude = 36.77781,
            longitude = 139.11532,
            elevationTopM = 1870,
            snowboardAllowed = true,
            nightSkiing = false,
            url = "https://www.kawaba.co.jp/snow/",
            area = "沼田・川場エリア",
            prefecture = "群馬県",
            snowDepthCm = 265,
            tempC = -3.0f,
            weatherIcon = "❄️",
            weatherText = "パウダー (パーク充実)",
            isPowder = true
        ),
        SkiResort(
            id = "ski_0195",
            name = "野沢温泉スキー場",
            kana = "のざわおんせんすきーじょう",
            latitude = 36.91707,
            longitude = 138.46904,
            elevationTopM = 1650,
            snowboardAllowed = true,
            nightSkiing = false,
            area = "北信州エリア",
            prefecture = "長野県",
            snowDepthCm = 285,
            tempC = -4.0f,
            weatherIcon = "❄️",
            weatherText = "粉雪パウダー",
            isPowder = true
        ),
        SkiResort(
            id = "ski_0218",
            name = "斑尾高原スキー場",
            latitude = 36.84843,
            longitude = 138.28038,
            elevationTopM = 1350,
            snowboardAllowed = true,
            nightSkiing = false,
            area = "飯山・斑尾エリア",
            prefecture = "長野県",
            snowDepthCm = 240,
            tempC = -3.5f,
            weatherIcon = "🌨️",
            weatherText = "ツリーラン",
            isPowder = true
        ),
        SkiResort(
            id = "ski_0178",
            name = "志賀高原 焼額山スキー場",
            latitude = 36.7526,
            longitude = 138.51977,
            elevationTopM = 2009,
            snowboardAllowed = true,
            nightSkiing = false,
            area = "志賀高原エリア",
            prefecture = "長野県",
            snowDepthCm = 270,
            tempC = -6.5f,
            weatherIcon = "❄️",
            weatherText = "極上ドライパウダー",
            isPowder = true
        ),
        SkiResort(
            id = "ski_0169",
            name = "妙高杉ノ原スキー場",
            latitude = 36.86346,
            longitude = 138.13715,
            elevationTopM = 1855,
            snowboardAllowed = true,
            nightSkiing = false,
            area = "妙高エリア",
            prefecture = "新潟県",
            snowDepthCm = 310,
            tempC = -2.5f,
            weatherIcon = "🌨️",
            weatherText = "新雪 25cm",
            isPowder = true
        ),
        SkiResort(
            id = "ski_0136",
            name = "白馬八方尾根",
            latitude = 36.70369,
            longitude = 137.81895,
            elevationTopM = 1831,
            snowboardAllowed = true,
            nightSkiing = false,
            area = "白馬バレー",
            prefecture = "長野県",
            snowDepthCm = 260,
            tempC = -3.0f,
            weatherIcon = "🌤️",
            weatherText = "晴れ時々雪",
            isPowder = false
        ),
        SkiResort(
            id = "ski_0206",
            name = "苗場スキー場",
            latitude = 36.79391,
            longitude = 138.77102,
            elevationTopM = 1789,
            snowboardAllowed = true,
            nightSkiing = true,
            area = "湯沢・南魚沼エリア",
            prefecture = "新潟県",
            snowDepthCm = 210,
            tempC = -1.5f,
            weatherIcon = "☁️",
            weatherText = "くもり",
            isPowder = false
        ),
        SkiResort(
            id = "ski_0369",
            name = "ニューグリーンピア津南",
            kana = "にゅーぐりーんぴあつなん",
            latitude = 36.95265,
            longitude = 138.68827,
            elevationTopM = 1006,
            snowboardAllowed = true,
            nightSkiing = false,
            area = "津南・十日町エリア",
            prefecture = "新潟県",
            snowDepthCm = 290,
            tempC = -2.8f,
            weatherIcon = "❄️",
            weatherText = "豪雪パウダー",
            isPowder = true
        )
    )

    val ALL_RESORTS: List<SkiResort>
        get() = getMasterResorts()

    /**
     * JSON文字列から全国スキー場マスター一覧をパース
     */
    fun parseMasterJson(jsonStr: String): List<SkiResort> {
        val array = JSONArray(jsonStr)
        val list = ArrayList<SkiResort>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.optString("id", "ski_$i")
            val name = obj.optString("name", "")
            val kana = obj.optString("kana", "")
            val lat = obj.optDouble("latitude", 0.0)
            val lon = obj.optDouble("longitude", 0.0)
            val elevation = if (obj.has("elevation_top_m") && !obj.isNull("elevation_top_m")) obj.getInt("elevation_top_m") else null
            val features = obj.optJSONObject("features")
            val snowboard = features?.optBoolean("snowboard", true) ?: true
            val night = features?.optBoolean("night", false) ?: false
            val url = obj.optString("url", "")

            list.add(
                SkiResort(
                    id = id,
                    name = name,
                    kana = kana,
                    latitude = lat,
                    longitude = lon,
                    elevationTopM = elevation,
                    snowboardAllowed = snowboard,
                    nightSkiing = night,
                    url = url
                )
            )
        }
        return list
    }

    /**
     * コンテキストからマスターファイル (assets/data/ski_resorts_master.json) を読み込みキャッシュ
     */
    fun initialize(context: Context): List<SkiResort> {
        masterResortsCache?.let { return it }
        try {
            val jsonStr = context.assets.open("data/ski_resorts_master.json").bufferedReader().use { it.readText() }
            val parsed = parseMasterJson(jsonStr)
            if (parsed.isNotEmpty()) {
                masterResortsCache = parsed
                return parsed
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return DEFAULT_RESORTS
    }

    /**
     * マスターデータを取得
     */
    fun getMasterResorts(context: Context? = null): List<SkiResort> {
        if (masterResortsCache != null) return masterResortsCache!!
        if (context != null) {
            return initialize(context)
        }
        return DEFAULT_RESORTS
    }

    /**
     * 2点間の緯度経度から大円距離 (km) を算出 (Haversine formula)
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val r = 6371.0 // 地球の半径 km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c).toFloat()
    }

    /**
     * 現在地から近い順にスキー場リストを取得 (GPS検索)
     * GPSオフ・位置情報未取得の場合はデフォルト座標には倒さず、distanceKm = null (ハイフン表示) とする
     */
    fun getResortsNear(currentLat: Double?, currentLng: Double?, context: Context? = null): List<SkiResort> {
        val source = getMasterResorts(context)

        // GPSオフまたは未取得の場合は、距離計算を行わず null (ハイフン表示対象) として返却
        if (currentLat == null || currentLng == null) {
            return source.map { resort ->
                resort.copy(distanceKm = null)
            }
        }

        // GPSが有効な場合のみ実測距離を算出して近い順にソート
        return source.map { resort ->
            val dist = calculateDistanceKm(currentLat, currentLng, resort.latitude, resort.longitude)
            resort.copy(distanceKm = dist)
        }.sortedBy { it.distanceKm ?: Float.MAX_VALUE }
    }

    /**
     * スキー場名・かな検索
     */
    fun searchResorts(query: String, currentLat: Double?, currentLng: Double?, context: Context? = null): List<SkiResort> {
        val nearList = getResortsNear(currentLat, currentLng, context)
        val q = query.trim()
        if (q.isBlank()) return nearList

        return nearList.filter {
            it.name.contains(q, ignoreCase = true) ||
            (it.kana.isNotBlank() && it.kana.contains(q, ignoreCase = true))
        }
    }
}
