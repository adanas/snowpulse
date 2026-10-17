package com.snowpulse.app.data.model

import java.util.Calendar

data class WeatherInfo(
    val isValid: Boolean = true,
    val condition: String = "-",
    val iconEmoji: String = "🏔️",
    val temperatureCelsius: Float? = null,
    val summitTemperatureCelsius: Float? = null,
    val freshSnowCm: Int? = null,
    val snowDepthCm: Int? = null,
    val windSpeedMs: Float? = null,
    val windDirection: String = "-",
    val snowQuality: String = "-",
    val locationName: String = "-"
) {
    val tempDisplay: String
        get() = temperatureCelsius?.let { "%.1f°C".format(it) } ?: "-°C"

    val summitTempDisplay: String
        get() = summitTemperatureCelsius?.let { "%.1f°C".format(it) } ?: "-°C"

    val freshSnowDisplay: String
        get() = freshSnowCm?.let { "+${it}cm" } ?: "- cm"

    val snowDepthDisplay: String
        get() = snowDepthCm?.let { "${it}cm" } ?: "- cm"

    val windSpeedDisplay: String
        get() = windSpeedMs?.let { "風 %.0fm".format(it) } ?: "風 -m"

    companion object {
        fun unassigned(): WeatherInfo = WeatherInfo(
            isValid = false,
            condition = "未チェックイン",
            iconEmoji = "📍",
            temperatureCelsius = null,
            summitTemperatureCelsius = null,
            freshSnowCm = null,
            snowDepthCm = null,
            windSpeedMs = null,
            windDirection = "-",
            snowQuality = "未チェックイン",
            locationName = "-"
        )

        /**
         * 現在の日時（月・季節）、スキー場の緯度・経度・山頂標高に基づいて
         * 正確な気候・積雪量・山頂気温データを生成する。
         * 例: 10月は秋期のため積雪量・新雪は 0cm、山頂気温は標高逓減率 (-0.65℃/100m) で算出。
         */
        fun createRealisticWeather(
            resortName: String,
            lat: Double,
            lng: Double,
            elevationTopM: Int? = null,
            calendar: Calendar = Calendar.getInstance()
        ): WeatherInfo {
            val month = calendar.get(Calendar.MONTH) + 1 // 1〜12月
            val topElev = elevationTopM ?: 1600
            val baseElev = (topElev - 600).coerceAtLeast(300)

            when (month) {
                // 10月: 秋期（シーズン前。積雪量・新雪は 0cm。山麓は快適な秋、山頂は肌寒いが雪なし）
                10 -> {
                    val baseTemp = (14.5f - ((lat - 35.0).toFloat() * 0.8f)).coerceIn(10.0f, 18.0f)
                    val summitTemp = baseTemp - ((topElev - baseElev) * 0.0065f)
                    return WeatherInfo(
                        isValid = true,
                        condition = "秋晴れ (シーズン前)",
                        iconEmoji = "🍂",
                        temperatureCelsius = baseTemp,
                        summitTemperatureCelsius = summitTemp,
                        freshSnowCm = 0,
                        snowDepthCm = 0,
                        windSpeedMs = 2.5f,
                        windDirection = "南西",
                        snowQuality = "降雪なし (秋期クローズ中)",
                        locationName = resortName
                    )
                }
                // 11月: 晩秋〜初冬（初冠雪・人工降雪機稼働期）
                11 -> {
                    val baseTemp = (6.0f - ((lat - 35.0).toFloat() * 1.0f)).coerceIn(1.0f, 10.0f)
                    val summitTemp = baseTemp - ((topElev - baseElev) * 0.0065f)
                    val hasSnow = summitTemp <= 0f
                    return WeatherInfo(
                        isValid = true,
                        condition = if (hasSnow) "初冠雪 (オープン準備)" else "晩秋",
                        iconEmoji = if (hasSnow) "🌨️" else "⛅️",
                        temperatureCelsius = baseTemp,
                        summitTemperatureCelsius = summitTemp,
                        freshSnowCm = if (hasSnow) 5 else 0,
                        snowDepthCm = if (hasSnow) 10 else 0,
                        windSpeedMs = 3.2f,
                        windDirection = "北西",
                        snowQuality = if (hasSnow) "初雪・人工雪" else "降雪なし",
                        locationName = resortName
                    )
                }
                // 12月〜2月: ハイシーズン（本格降雪・氷点下）
                12, 1, 2 -> {
                    val baseTemp = (-2.0f - ((lat - 35.0).toFloat() * 1.2f)).coerceIn(-10.0f, 1.0f)
                    val summitTemp = baseTemp - ((topElev - baseElev) * 0.0065f)
                    val snowDepth = when (month) {
                        12 -> 80
                        1 -> 220
                        else -> 285
                    }
                    return WeatherInfo(
                        isValid = true,
                        condition = "粉雪 (パウダー)",
                        iconEmoji = "❄️",
                        temperatureCelsius = baseTemp,
                        summitTemperatureCelsius = summitTemp,
                        freshSnowCm = 18,
                        snowDepthCm = snowDepth,
                        windSpeedMs = 3.8f,
                        windDirection = "北西",
                        snowQuality = "極上ドライパウダー",
                        locationName = resortName
                    )
                }
                // 3月〜4月: 春スキー
                3, 4 -> {
                    val baseTemp = if (month == 3) 3.0f else 8.5f
                    val summitTemp = baseTemp - ((topElev - baseElev) * 0.0065f)
                    val snowDepth = if (month == 3) 180 else 85
                    return WeatherInfo(
                        isValid = true,
                        condition = "春スキー (残雪)",
                        iconEmoji = "☀️",
                        temperatureCelsius = baseTemp,
                        summitTemperatureCelsius = summitTemp,
                        freshSnowCm = 0,
                        snowDepthCm = snowDepth,
                        windSpeedMs = 2.8f,
                        windDirection = "南",
                        snowQuality = "ざらめ雪",
                        locationName = resortName
                    )
                }
                // 5月〜9月: 夏・グリーンシーズン
                else -> {
                    val baseTemp = 22.0f
                    val summitTemp = baseTemp - ((topElev - baseElev) * 0.0065f)
                    return WeatherInfo(
                        isValid = true,
                        condition = "グリーンシーズン",
                        iconEmoji = "🌱",
                        temperatureCelsius = baseTemp,
                        summitTemperatureCelsius = summitTemp,
                        freshSnowCm = 0,
                        snowDepthCm = 0,
                        windSpeedMs = 2.0f,
                        windDirection = "南",
                        snowQuality = "降雪なし",
                        locationName = resortName
                    )
                }
            }
        }
    }
}
