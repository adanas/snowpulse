package com.snowpulse.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class Stance {
    REGULAR,
    GOOFY
}

enum class PhonePlacement {
    PANTS_FRONT, // 推奨 (ボード角付け連動・最高精度)
    PANTS_BACK,
    CHEST,       // 体幹角推定
    BACKPACK     // ログ専用
}

enum class TrackingState {
    IDLE,
    SKIING,
    PAUSED_RUN,
    RIDING_LIFT
}

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val resortName: String = "未設定ゲレンデ",
    val totalDistanceMeters: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val totalElevationDropMeters: Float = 0f,
    val totalRunsCount: Int = 0,
    val stance: Stance = Stance.REGULAR,
    val phonePlacement: PhonePlacement = PhonePlacement.PANTS_FRONT
)

@Entity(
    tableName = "runs",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class RunEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val runNumber: Int,
    val resortName: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val distanceMeters: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val elevationDropMeters: Float = 0f,
    val maxEdgeAngleDeg: Float = 0f,
    val avgVibrationIndex: Float = 0f
)

@Entity(
    tableName = "geo_points",
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("runId")]
)
data class GeoPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val runId: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val speedKmh: Float,
    val heading: Float = 0f,
    val edgeAngleDeg: Float,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "trick_events",
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("runId")]
)
data class TrickEventEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val runId: String,
    val trickType: String, // "Ollie", "180 Spin", "360 Spin", "Jump"
    val airTimeSeconds: Float,
    val jumpHeightCm: Float,
    val rotationDegrees: Float,
    val isLanded: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
