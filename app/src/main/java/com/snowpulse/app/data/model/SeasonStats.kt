package com.snowpulse.app.data.model

data class VisitedResortStat(
    val resortName: String,
    val checkInCount: Int,
    val totalDistanceMeters: Float,
    val maxSpeedKmh: Float,
    val isHomeResort: Boolean = false
)

data class SeasonStats(
    val seasonKey: String,                 // 例: "2026-27"
    val seasonDisplayName: String,         // 例: "2026-27 Season"
    val totalDistanceMeters: Float,        // 必須: 総距離 (m)
    val totalElevationDropMeters: Float,   // 必須: 獲得標高 (m)
    val totalCheckIns: Int,                // 必須: チェックイン回数 (回)
    val maxSpeedKmh: Float,                // 必須: 最高速度 (km/h)
    val totalRunsCount: Int,               // 総ライド本数 (Runs)
    val uniqueResortsCount: Int,           // 制覇ゲレンデ数 (箇所)
    val visitedResorts: List<VisitedResortStat>, // 訪問スキー場リスト
    val homeResortName: String? = null     // 最多訪問ゲレンデ (ホームゲレンデ)
)
