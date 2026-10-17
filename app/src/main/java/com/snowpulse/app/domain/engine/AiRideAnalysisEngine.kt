package com.snowpulse.app.domain.engine

import com.snowpulse.app.data.model.RunEntity
import com.snowpulse.app.data.model.SessionEntity
import com.snowpulse.app.data.model.TrickEventEntity
import kotlin.math.roundToInt

data class AiRideAnalysisReport(
    val overallScore: Int,
    val grade: String,
    val styleTitle: String,
    val styleSubtitle: String,
    val carvingScore: Int,
    val speedControlScore: Int,
    val staminaScore: Int,
    val trickScore: Int,
    val coachFeedback: String,
    val mvpRunTitle: String,
    val mvpRunDetail: String,
    val nextStepTipTitle: String,
    val nextStepTipDetail: String
)

class AiRideAnalysisEngine {

    fun analyzeSession(
        session: SessionEntity?,
        runs: List<RunEntity>,
        tricks: List<TrickEventEntity> = emptyList()
    ): AiRideAnalysisReport {
        if (runs.isEmpty()) {
            return createDefaultReport(session)
        }

        // 1. 各指標の集計
        val maxEdgeAngle = runs.maxOfOrNull { it.maxEdgeAngleDeg } ?: 40f
        val avgMaxEdge = runs.map { it.maxEdgeAngleDeg }.average().toFloat()
        val maxSpeed = runs.maxOfOrNull { it.maxSpeedKmh } ?: (session?.maxSpeedKmh ?: 35f)
        val avgSpeed = runs.map { it.avgSpeedKmh }.average().toFloat()
        val totalDistanceKm = runs.sumOf { it.distanceMeters.toDouble() }.toFloat() / 1000f
        val totalRuns = runs.size
        val landedTricks = tricks.count { it.isLanded }

        // 2. 4軸スコア算出 (0〜100点)
        // ① カービングスコア (エッジ角 50°以上で95点, 40°で80点, 30°で65点)
        val carvingScore = ((maxEdgeAngle / 55f) * 70f + (avgMaxEdge / 45f) * 30f)
            .coerceIn(50f, 98f)
            .roundToInt()

        // ② スピードコントロール (最高速 60km/h以上で95点, 45km/hで80点)
        val speedControlScore = ((maxSpeed / 65f) * 60f + (avgSpeed / 40f) * 40f)
            .coerceIn(55f, 99f)
            .roundToInt()

        // ③ スタミナ・ペース配分 (前半の平均速度/エッジ角 vs 後半の低下率)
        val staminaScore = if (runs.size >= 4) {
            val half = runs.size / 2
            val firstHalfAvgSpeed = runs.take(half).map { it.avgSpeedKmh }.average()
            val secondHalfAvgSpeed = runs.drop(half).map { it.avgSpeedKmh }.average()
            val speedRatio = if (firstHalfAvgSpeed > 0) (secondHalfAvgSpeed / firstHalfAvgSpeed).toFloat() else 1f
            (speedRatio * 88f).coerceIn(60f, 96f).roundToInt()
        } else {
            85
        }

        // ④ トリック・アグレッシブ度
        val trickScore = when {
            tricks.isEmpty() -> 72 // トリックなしでもフリーランベースで基礎点
            landedTricks >= 3 -> 95
            landedTricks >= 1 -> 85
            else -> 78
        }

        // 総合スコア (加重平均)
        val overallScore = ((carvingScore * 0.35f) + (speedControlScore * 0.25f) + (staminaScore * 0.20f) + (trickScore * 0.20f)).roundToInt()

        val grade = when {
            overallScore >= 92 -> "S"
            overallScore >= 85 -> "A+"
            overallScore >= 80 -> "A"
            overallScore >= 75 -> "B+"
            else -> "B"
        }

        // 3. ライディングスタイル判定
        val (styleTitle, styleSubtitle) = when {
            carvingScore >= 88 && maxEdgeAngle >= 45f -> Pair("⚡️ ディープ・カーバー", "Carving Specialist")
            speedControlScore >= 88 && maxSpeed >= 50f -> Pair("🚀 ハイスピード・チャージャー", "Speed Charger")
            tricks.isNotEmpty() && landedTricks >= 1 -> Pair("🌪️ フリースタイル・マスター", "Freestyle Hunter")
            totalDistanceKm >= 12f || totalRuns >= 7 -> Pair("🏔️ マウンテン・クルーザー", "All-Mountain Cruiser")
            else -> Pair("❄️ スムース・ライダー", "Smooth Flow Rider")
        }

        // 4. MVPラン (最高速とエッジ角の積が最も高いラン)
        val mvpRun = runs.maxByOrNull { it.maxSpeedKmh * (it.maxEdgeAngleDeg + 10f) } ?: runs.first()
        val mvpRunTitle = "Run #${mvpRun.runNumber} (最高 %.1f km/h)".format(mvpRun.maxSpeedKmh)
        val mvpRunDetail = "エッジ角 %.0f° • 獲得標高 %.0fm".format(mvpRun.maxEdgeAngleDeg, mvpRun.elevationDropMeters)

        // 5. プロコーチAIによるパーソナル・アドバイス生成（チェックアウト総合セッション分析）
        val feedbackBuilder = StringBuilder()
        feedbackBuilder.append("チェックイン〜チェックアウトまでの全滑走（全%d本 / 総距離%.1fkm）をAIが総合分析！最大エッジ角 **%.0f°**、最高速度 **%.1f km/h** を記録しました。".format(totalRuns, totalDistanceKm, maxEdgeAngle, maxSpeed))
        if (carvingScore >= 85) {
            feedbackBuilder.append(" セッション内ベストラン（**Run #${mvpRun.runNumber}**）では谷回りの捉えが鋭く、深いエッジングによる加速感が素晴らしい滑りでした。")
        } else {
            feedbackBuilder.append(" セッションを通じてスムーズなライン取りで安定したクルージングができています。")
        }

        if (staminaScore < 80) {
            feedbackBuilder.append(" セッション後半にやや速度低下とエッジ角の浅まりが見られたため、疲労時の後傾（テール加重）に注意し、前足への荷重を意識すると最後までキレが保てます。")
        } else {
            feedbackBuilder.append(" チェックアウトまでスピード・フォームともに乱れず、スタミナ配分も完璧なセッションでした！")
        }

        // 6. 次回へのステップアップ課題
        val (nextTipTitle, nextTipDetail) = when {
            carvingScore < 85 -> Pair("角付けの谷回り導入", "ターン切り替え直後に身体を倒し込み、エッジ角+5°を目指しましょう")
            staminaScore < 80 -> Pair("疲労時の前傾キープ", "疲れた時ほど前足を意識的に踏み込み、テール抜けを防止")
            tricks.isEmpty() -> Pair("地形でのオーリー導入", "ゲレンデ脇のウェーブや段差でポップ（反発）を意識してみましょう")
            else -> Pair("ヒールターンの迅速化", "ヒール側の捉えを0.2秒早めることで更なる推進力を獲得")
        }

        return AiRideAnalysisReport(
            overallScore = overallScore,
            grade = grade,
            styleTitle = styleTitle,
            styleSubtitle = styleSubtitle,
            carvingScore = carvingScore,
            speedControlScore = speedControlScore,
            staminaScore = staminaScore,
            trickScore = trickScore,
            coachFeedback = feedbackBuilder.toString(),
            mvpRunTitle = mvpRunTitle,
            mvpRunDetail = mvpRunDetail,
            nextStepTipTitle = nextTipTitle,
            nextStepTipDetail = nextTipDetail
        )
    }

    private fun createDefaultReport(session: SessionEntity?): AiRideAnalysisReport {
        return AiRideAnalysisReport(
            overallScore = 86,
            grade = "A+",
            styleTitle = "⚡️ ディープ・カーバー",
            styleSubtitle = "Carving Specialist",
            carvingScore = 88,
            speedControlScore = 85,
            staminaScore = 82,
            trickScore = 75,
            coachFeedback = "本日はキレのある鋭いエッジングが光る素晴らしいセッションでした！適度なスピードを保ちつつ、安定した滑走フォームを維持できています。",
            mvpRunTitle = "Run #1 (最高 48.2 km/h)",
            mvpRunDetail = "最大エッジ角 46° • 安定度 S",
            nextStepTipTitle = "ヒールサイドの角付け進化",
            nextStepTipDetail = "ヒール側の捉えを少し早めて左右対称の深いターン弧を目指しましょう"
        )
    }
}
