package com.snowpulse.app.domain.engine

import com.snowpulse.app.data.model.TrackingState
import kotlin.math.abs

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val speedKmh: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class StateTransitionResult(
    val newState: TrackingState,
    val hasRunEnded: Boolean,
    val hasNewRunStarted: Boolean,
    val reason: String
)

/**
 * 滑走・リフト搭乗・一時停止を自動判定するステートマシンエンジン
 */
class StateDetectionEngine {

    var currentState: TrackingState = TrackingState.IDLE
        private set

    private var stateEnterTime: Long = System.currentTimeMillis()
    private var lastSample: LocationSample? = null

    // 判定閾値定数
    companion object {
        const val MIN_SKIING_SPEED_KMH = 12.0f     // 滑走開始とみなす最低速度
        const val STOPPED_SPEED_KMH = 3.0f          // 停止とみなす速度上限
        const val LIFT_MIN_ASCENT_RATE_MPS = 0.4    // リフト上昇とみなす最低高度上昇率 (m/s)
        const val RUN_DESCENT_RATE_MPS = 0.6        // 滑走下降とみなす最低高度降下率 (m/s)
        const val LIFT_CONFIRMATION_DURATION_MS = 25_000L // リフト確定に必要な継続時間 (25秒)
        const val PAUSE_CONFIRMATION_DURATION_MS = 20_000L // 停止確定に必要な継続時間 (20秒)
    }

    fun reset() {
        currentState = TrackingState.IDLE
        lastSample = null
        stateEnterTime = System.currentTimeMillis()
    }

    /**
     * 新しいGPS・高度サンプルを受信した際にステートを更新
     */
    fun processSample(sample: LocationSample): StateTransitionResult {
        val prevSample = lastSample
        lastSample = sample

        if (prevSample == null) {
            return StateTransitionResult(currentState, false, false, "Initial sample")
        }

        val dtSeconds = (sample.timestamp - prevSample.timestamp) / 1000.0
        if (dtSeconds <= 0) {
            return StateTransitionResult(currentState, false, false, "Invalid dt")
        }

        val altitudeDiff = sample.altitudeMeters - prevSample.altitudeMeters
        val ascentRate = altitudeDiff / dtSeconds // 正なら上昇、負なら下降 (m/s)
        val speed = sample.speedKmh
        val timeInCurrentState = sample.timestamp - stateEnterTime

        var newState = currentState
        var hasRunEnded = false
        var hasNewRunStarted = false
        var reason = "Maintain state"

        when (currentState) {
            TrackingState.IDLE -> {
                if (speed > MIN_SKIING_SPEED_KMH && ascentRate < -RUN_DESCENT_RATE_MPS) {
                    newState = TrackingState.SKIING
                    hasNewRunStarted = true
                    reason = "高速下降を検知 -> 滑走開始"
                } else if (ascentRate > LIFT_MIN_ASCENT_RATE_MPS && speed in 4f..25f) {
                    if (timeInCurrentState > LIFT_CONFIRMATION_DURATION_MS) {
                        newState = TrackingState.RIDING_LIFT
                        reason = "持続的な高度上昇を検知 -> リフト搭乗"
                    }
                }
            }

            TrackingState.SKIING -> {
                if (speed < STOPPED_SPEED_KMH) {
                    if (timeInCurrentState > PAUSE_CONFIRMATION_DURATION_MS) {
                        newState = TrackingState.PAUSED_RUN
                        reason = "20秒以上停止 -> 滑走一時停止"
                    }
                } else if (ascentRate > LIFT_MIN_ASCENT_RATE_MPS) {
                    // 滑走から急にリフトに乗った場合
                    newState = TrackingState.RIDING_LIFT
                    hasRunEnded = true
                    reason = "滑走後の高度上昇 -> 前のラン終了 & リフト搭乗"
                }
            }

            TrackingState.PAUSED_RUN -> {
                if (speed > 8.0f && ascentRate < -RUN_DESCENT_RATE_MPS) {
                    newState = TrackingState.SKIING
                    reason = "再加速 -> 滑走再開"
                } else if (ascentRate > LIFT_MIN_ASCENT_RATE_MPS) {
                    newState = TrackingState.RIDING_LIFT
                    hasRunEnded = true
                    reason = "リフト乗り場へ移動し上昇開始 -> ラン確定終了"
                }
            }

            TrackingState.RIDING_LIFT -> {
                if (speed > MIN_SKIING_SPEED_KMH && ascentRate < -RUN_DESCENT_RATE_MPS) {
                    newState = TrackingState.SKIING
                    hasNewRunStarted = true
                    reason = "リフト降車後の下降滑走 -> 新しいラン開始"
                } else if (speed < STOPPED_SPEED_KMH && abs(ascentRate) < 0.2) {
                    if (timeInCurrentState > 15_000L) {
                        newState = TrackingState.IDLE
                        reason = "リフト降り場付近で待機中"
                    }
                }
            }
        }

        if (newState != currentState) {
            currentState = newState
            stateEnterTime = sample.timestamp
        }

        return StateTransitionResult(
            newState = currentState,
            hasRunEnded = hasRunEnded,
            hasNewRunStarted = hasNewRunStarted,
            reason = reason
        )
    }
}
