package com.snowpulse.app.domain.engine

import com.snowpulse.app.data.model.RunEntity
import com.snowpulse.app.data.model.SeasonStats
import com.snowpulse.app.data.model.SessionEntity
import com.snowpulse.app.data.model.VisitedResortStat
import java.util.Calendar

object SeasonStatsCalculator {

    /**
     * 指定タイムスタンプ（ミリ秒）が属するシーズンキー（例: "2026-27"）を返します。
     * シーズン期間: 10/1 〜 翌年5/31
     * オフシーズン (6/1〜9/30) は直前シーズンに帰属。
     */
    fun getSeasonKey(timestampMs: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1 // 1-12
        return if (month >= 10) {
            val nextYearShort = (year + 1) % 100
            "%d-%02d".format(year, nextYearShort)
        } else {
            val yearShort = year % 100
            "%d-%02d".format(year - 1, yearShort)
        }
    }

    /**
     * 現在日時のデフォルトシーズンキーを返します。
     */
    fun getCurrentSeasonKey(): String = getSeasonKey(System.currentTimeMillis())

    /**
     * セッション一覧およびラン一覧から、指定シーズンの集計データを算出します。
     */
    fun calculateSeasonStats(
        seasonKey: String,
        sessions: List<SessionEntity>,
        runs: List<RunEntity>
    ): SeasonStats {
        // 完了済み（endTime != null）かつ対象シーズンのセッション
        val seasonSessions = sessions.filter { s ->
            s.endTime != null && getSeasonKey(s.startTime) == seasonKey
        }
        val seasonSessionIds = seasonSessions.map { it.id }.toSet()

        // 対象シーズンのラン（セッションIDまたは開始日時で判定）
        val seasonRuns = runs.filter { r ->
            seasonSessionIds.contains(r.sessionId) || getSeasonKey(r.startTime) == seasonKey
        }

        // 1. 総距離 (m)
        val totalDistM = seasonSessions.sumOf { it.totalDistanceMeters.toDouble() }.toFloat()
            .takeIf { it > 0f } ?: seasonRuns.sumOf { it.distanceMeters.toDouble() }.toFloat()

        // 2. 獲得標高 (m)
        val totalDropM = seasonSessions.sumOf { it.totalElevationDropMeters.toDouble() }.toFloat()
            .takeIf { it > 0f } ?: seasonRuns.sumOf { it.elevationDropMeters.toDouble() }.toFloat()

        // 3. チェックイン回数 (完了セッション数)
        val totalCheckIns = seasonSessions.size

        // 4. 最高速度 (km/h)
        val maxSpeedKmh = (seasonSessions.map { it.maxSpeedKmh } + seasonRuns.map { it.maxSpeedKmh })
            .maxOrNull() ?: 0f

        // 5. 総ライド本数
        val totalRunsCount = if (seasonSessions.isNotEmpty()) {
            val sumCounts = seasonSessions.sumOf { it.totalRunsCount }
            if (sumCounts > 0) sumCounts else seasonRuns.size
        } else {
            seasonRuns.size
        }

        // 6. 訪問スキー場ごとの集計 & 最多訪問（ホームゲレンデ）判定
        val resortGroups = seasonSessions.groupBy { s ->
            s.resortName.takeIf { it.isNotBlank() } ?: "ゲレンデ"
        }
        val sortedResorts = resortGroups.map { (resortName, sList) ->
            val dist = sList.sumOf { it.totalDistanceMeters.toDouble() }.toFloat()
            val speed = sList.maxOfOrNull { it.maxSpeedKmh } ?: 0f
            VisitedResortStat(
                resortName = resortName,
                checkInCount = sList.size,
                totalDistanceMeters = dist,
                maxSpeedKmh = speed
            )
        }.sortedByDescending { it.checkInCount }

        val homeResort = sortedResorts.firstOrNull()?.resortName
        val visitedResortsWithHome = sortedResorts.map { stat ->
            stat.copy(isHomeResort = (stat.resortName == homeResort && stat.checkInCount > 0))
        }

        return SeasonStats(
            seasonKey = seasonKey,
            seasonDisplayName = "$seasonKey Season",
            totalDistanceMeters = totalDistM,
            totalElevationDropMeters = totalDropM,
            totalCheckIns = totalCheckIns,
            maxSpeedKmh = maxSpeedKmh,
            totalRunsCount = totalRunsCount,
            uniqueResortsCount = resortGroups.size,
            visitedResorts = visitedResortsWithHome,
            homeResortName = homeResort
        )
    }

    /**
     * データ内に存在する利用可能なシーズンキー一覧（降順）を返します。
     * 現在シーズンは必ず含まれます。
     */
    fun getAvailableSeasonKeys(sessions: List<SessionEntity>): List<String> {
        val keys = sessions.map { getSeasonKey(it.startTime) }.toMutableSet()
        keys.add(getCurrentSeasonKey())
        return keys.toList().sortedDescending()
    }
}
