package com.snowpulse.app.domain.engine

import com.snowpulse.app.data.model.PhonePlacement
import com.snowpulse.app.data.model.Stance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

private fun Double.toDegrees(): Float = (this * 180.0 / PI).toFloat()

data class ImuSample(
    val accX: Float,
    val accY: Float,
    val accZ: Float,
    val gyroX: Float, // rad/s
    val gyroY: Float,
    val gyroZ: Float,
    val timestampMs: Long = System.currentTimeMillis(),
)

data class TrickDetected(
    val trickType: String,
    val airTimeSec: Float,
    val estimatedHeightCm: Float,
    val rotationDeg: Float,
    val isLanded: Boolean,
    val timestamp: Long,
)

/**
 * 6軸IMUセンサーから姿勢角（エッジ角）、トリック（ジャンプ/スピン）、雪質振動を抽出する解析エンジン
 */
class MotionAnalysisEngine(
    var stance: Stance = Stance.REGULAR,
    var placement: PhonePlacement = PhonePlacement.PANTS_FRONT,
) {

    // 姿勢・エッジ角
    private var calibratedRollOffset: Float = 0f
    private var isCalibrated: Boolean = false

    // ジャンプ・トリック検出用
    private var isAirborne: Boolean = false
    private var jumpStartTimeMs: Long = 0L
    private var accumulatedSpinRad: Float = 0f
    private var lastSampleTimestampMs: Long = 0L

    // 振動（雪質）解析用バッファ
    private val vibrationWindow = mutableListOf<Float>()

    companion object {
        private const val WINDOW_SIZE = 50
        const val LOW_G_AIRBORNE_THRESHOLD = 3.0f   // 無重力（滞空）判定 (m/s^2) < ~0.3G
        const val IMPACT_LANDING_THRESHOLD = 22.0f  // 着地衝撃判定 (m/s^2) > ~2.2G
        const val MIN_AIR_TIME_MS = 250L            // トリックと認める最小滞空時間 (250ms)
        const val MAX_AIR_TIME_MS = 2500L           // 異常値除外 (2.5秒)
    }

    fun updateSettings(newStance: Stance, newPlacement: PhonePlacement) {
        this.stance = newStance
        this.placement = newPlacement
        this.isCalibrated = false
    }

    /**
     * IMUサンプルを1件処理し、現在のエッジ角および検出されたトリックを返す
     */
    fun processImuSample(
        sample: ImuSample,
        currentSpeedKmh: Float,
    ): Pair<Float, TrickDetected?> {
        val totalAcc = sqrt(sample.accX * sample.accX + sample.accY * sample.accY + sample.accZ * sample.accZ)
        val dt = if (lastSampleTimestampMs > 0L) {
            ((sample.timestampMs - lastSampleTimestampMs) / 1000f).coerceIn(0.002f, 0.1f)
        } else {
            0.02f // デフォルト 50Hz 想定
        }
        lastSampleTimestampMs = sample.timestampMs

        // 1. 直進滑走時の自動姿勢キャリブレーション
        if ((!isCalibrated) && (currentSpeedKmh > 20.0f)) {
            val rawRoll = atan2(sample.accX.toDouble(), sample.accY.toDouble()).toDegrees()
            calibratedRollOffset = rawRoll
            isCalibrated = true
        }

        // 2. エッジ角（バンク角）の推定
        var estimatedEdgeAngle = 0f
        if ((placement == PhonePlacement.PANTS_FRONT) || (placement == PhonePlacement.PANTS_BACK)) {
            val rawRoll = atan2(sample.accX.toDouble(), sample.accY.toDouble()).toDegrees()
            var roll = rawRoll - calibratedRollOffset

            // スタンスに応じた左右エッジ補正
            if (stance == Stance.GOOFY) {
                roll = -roll
            }
            estimatedEdgeAngle = abs(roll).coerceIn(0f, 90f)
        } else if (placement == PhonePlacement.CHEST) {
            // 胸ポケットは体幹角として約70%の係数で参考推定
            val rawRoll = atan2(sample.accX.toDouble(), sample.accY.toDouble()).toDegrees()
            estimatedEdgeAngle = (abs(rawRoll - calibratedRollOffset) * 0.7f).coerceIn(0f, 70f)
        }

        // 3. ジャンプ・トリック検出ステートマシン
        var detectedTrick: TrickDetected? = null

        if (placement != PhonePlacement.BACKPACK) {
            if (!isAirborne) {
                // 抜重（落下）による低G検知
                if ((totalAcc < LOW_G_AIRBORNE_THRESHOLD) && (currentSpeedKmh > 10.0f)) {
                    isAirborne = true
                    jumpStartTimeMs = sample.timestampMs
                    accumulatedSpinRad = 0f
                }
            } else {
                // 滞空中のスピン回転（鉛直軸角速度）を動的dtで時間積分
                accumulatedSpinRad += abs(sample.gyroZ) * dt

                // 着地衝撃（高G）検知
                val airTimeMs = sample.timestampMs - jumpStartTimeMs
                if (totalAcc > IMPACT_LANDING_THRESHOLD) {
                    isAirborne = false
                    if (airTimeMs in MIN_AIR_TIME_MS..MAX_AIR_TIME_MS) {
                        val airTimeSec = airTimeMs / 1000f
                        // h = 0.5 * g * (t/2)^2 でジャンプ高さを物理推定
                        val heightMeters = 0.5f * 9.8f * (airTimeSec / 2f) * (airTimeSec / 2f)
                        val rotationDeg = accumulatedSpinRad.toDouble().toDegrees()

                        val trickType = classifyTrick(airTimeSec, rotationDeg)
                        detectedTrick = TrickDetected(
                            trickType = trickType,
                            airTimeSec = airTimeSec,
                            estimatedHeightCm = (heightMeters * 100).coerceAtLeast(15f),
                            rotationDeg = rotationDeg,
                            isLanded = true,
                            timestamp = sample.timestampMs,
                        )
                    }
                } else if (airTimeMs > MAX_AIR_TIME_MS) {
                    // タイムアウト（ジャンプではなく転倒やポケット移動）
                    isAirborne = false
                }
            }
        }

        // 4. 雪質振動インデックスの蓄積
        vibrationWindow.add(abs(totalAcc - 9.8f))
        if (vibrationWindow.size > WINDOW_SIZE) {
            vibrationWindow.removeAt(0)
        }

        return Pair(estimatedEdgeAngle, detectedTrick)
    }

    private fun classifyTrick(airTimeSec: Float, rotationDeg: Float): String {
        return when {
            rotationDeg in 280f..440f -> "360 Spin"
            rotationDeg in 120f..240f -> "180 Spin"
            airTimeSec >= 0.55f -> "Big Air Jump"
            else -> "Ollie / Hop"
        }
    }

    /**
     * 直近の路面振動指数（雪質判定用）を取得
     * 0.0〜1.5: 良好なパウダー/フラット圧雪
     * 1.5〜3.5: 荒れバーン/モコモコ雪
     * 3.5以上: ガタガタのアイスバーン
     */
    fun getCurrentVibrationIndex(): Float {
        if (vibrationWindow.isEmpty()) return 0f
        return vibrationWindow.average().toFloat()
    }
}
