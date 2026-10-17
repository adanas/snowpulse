package com.snowpulse.app.domain.engine

import com.snowpulse.app.data.model.PhonePlacement
import com.snowpulse.app.data.model.Stance
import com.snowpulse.app.data.model.TrackingState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EngineUnitTest {

    private lateinit var stateEngine: StateDetectionEngine
    private lateinit var motionEngine: MotionAnalysisEngine

    @Before
    fun setup() {
        stateEngine = StateDetectionEngine()
        motionEngine = MotionAnalysisEngine(stance = Stance.REGULAR, placement = PhonePlacement.PANTS_FRONT)
    }

    @Test
    fun testSkiingStartDetection() {
        // 山頂待機
        val initial = LocationSample(36.920, 138.440, 1600.0, 0f, 1000L)
        val res1 = stateEngine.processSample(initial)
        assertEquals(TrackingState.IDLE, res1.newState)

        // 急加速 & 高度下降 (滑走開始)
        val movingDown = LocationSample(36.921, 138.441, 1590.0, 35.0f, 2000L)
        val res2 = stateEngine.processSample(movingDown)

        assertEquals(TrackingState.SKIING, res2.newState)
        assertTrue(res2.hasNewRunStarted)
    }

    @Test
    fun testTrickOllieDetection() {
        // 通常滑走 (1G)
        val normalSample = ImuSample(0f, 0f, 9.8f, 0f, 0f, 0f, 1000L)
        val (_, trick1) = motionEngine.processImuSample(normalSample, currentSpeedKmh = 35f)
        assertNull(trick1)

        // ジャンプ踏切 (低G: 0.1G)
        val airborneSample = ImuSample(0.1f, 0.2f, 1.2f, 0.1f, 0.1f, 0.2f, 1100L)
        val (_, trick2) = motionEngine.processImuSample(airborneSample, currentSpeedKmh = 35f)
        assertNull(trick2) // 滞空中

        // 着地衝撃 (高G: 2.6G) 500ms後
        val landingSample = ImuSample(0.5f, 1.0f, 26.0f, 0f, 0f, 0f, 1600L)
        val (_, trick3) = motionEngine.processImuSample(landingSample, currentSpeedKmh = 32f)

        assertNotNull(trick3)
        assertEquals(0.5f, trick3!!.airTimeSec, 0.05f)
        assertTrue(trick3.isLanded)
        assertTrue(trick3.estimatedHeightCm > 20f)
    }

    @Test
    fun test180SpinDetection() {
        // 踏切 (低G)
        motionEngine.processImuSample(ImuSample(0f, 0f, 1.0f, 0f, 0f, 0f, 2000L), currentSpeedKmh = 30f)

        // 滞空中 (約500ms、100msごとに角速度 5.5 rad/s でスピン回転)
        for (t in 2100L until 2600L step 100L) {
            motionEngine.processImuSample(ImuSample(0f, 0f, 1.2f, 0f, 0f, 5.5f, t), currentSpeedKmh = 30f)
        }

        // 着地衝撃
        val landingSample = ImuSample(0f, 0f, 25.0f, 0f, 0f, 0f, 2600L)
        val (_, detectedTrick) = motionEngine.processImuSample(landingSample, currentSpeedKmh = 28f)

        assertNotNull(detectedTrick)
        assertEquals("180 Spin", detectedTrick!!.trickType)
        assertTrue(detectedTrick.rotationDeg in 140f..220f)
    }
}
