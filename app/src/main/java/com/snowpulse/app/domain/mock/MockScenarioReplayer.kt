package com.snowpulse.app.domain.mock

import com.snowpulse.app.domain.engine.ImuSample
import com.snowpulse.app.domain.engine.LocationSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.InputStream

data class MockFrame(
    val location: LocationSample,
    val imu: ImuSample
)

/**
 * 机上検証用：CSVシナリオを再生してGPS & IMUストリームを生成するモックエンジン
 */
class MockScenarioReplayer {

    var playbackSpeed: Float = 1.0f

    /**
     * CSV行形式:
     * timeOffsetMs,lat,lng,alt,speedKmh,accX,accY,accZ,gyroX,gyroY,gyroZ
     */
    fun replayFromStream(inputStream: InputStream): Flow<MockFrame> = flow {
        val lines = inputStream.bufferedReader().readLines()
        if (lines.isEmpty()) return@flow

        val startTime = System.currentTimeMillis()
        var prevOffset = 0L

        for (line in lines.drop(1)) { // skip header
            val parts = line.split(",")
            if (parts.size < 11) continue

            val offsetMs = parts[0].toLongOrNull() ?: 0L
            val delayMs = ((offsetMs - prevOffset) / playbackSpeed).toLong().coerceAtLeast(10L)
            prevOffset = offsetMs

            delay(delayMs)

            val now = startTime + offsetMs
            val location = LocationSample(
                latitude = parts[1].toDouble(),
                longitude = parts[2].toDouble(),
                altitudeMeters = parts[3].toDouble(),
                speedKmh = parts[4].toFloat(),
                timestamp = now
            )
            val imu = ImuSample(
                accX = parts[5].toFloat(),
                accY = parts[6].toFloat(),
                accZ = parts[7].toFloat(),
                gyroX = parts[8].toFloat(),
                gyroY = parts[9].toFloat(),
                gyroZ = parts[10].toFloat(),
                timestampMs = now
            )

            emit(MockFrame(location, imu))
        }
    }
}
