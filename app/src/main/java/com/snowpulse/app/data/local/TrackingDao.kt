package com.snowpulse.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.snowpulse.app.data.model.GeoPointEntity
import com.snowpulse.app.data.model.RunEntity
import com.snowpulse.app.data.model.SessionEntity
import com.snowpulse.app.data.model.TrickEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingDao {

    // Sessions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC LIMIT 1")
    fun getLatestSession(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    // Runs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: RunEntity)

    @Update
    suspend fun updateRun(run: RunEntity)

    @Query("SELECT * FROM runs WHERE sessionId = :sessionId ORDER BY runNumber ASC")
    fun getRunsForSession(sessionId: String): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs ORDER BY startTime DESC")
    fun getAllRuns(): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE id = :runId")
    suspend fun getRunById(runId: String): RunEntity?

    @Query("SELECT COUNT(*) FROM runs")
    suspend fun getTotalRunCount(): Int

    // GeoPoints
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeoPoints(points: List<GeoPointEntity>)

    @Query("SELECT * FROM geo_points WHERE runId = :runId ORDER BY timestamp ASC")
    fun getPointsForRun(runId: String): Flow<List<GeoPointEntity>>

    @Query("SELECT COUNT(*) FROM geo_points WHERE runId = :runId")
    suspend fun getPointsCountForRun(runId: String): Int

    @Query("SELECT geo_points.* FROM geo_points INNER JOIN runs ON geo_points.runId = runs.id WHERE runs.sessionId = :sessionId ORDER BY geo_points.timestamp ASC")
    fun getPointsForSession(sessionId: String): Flow<List<GeoPointEntity>>

    // Tricks
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrick(trick: TrickEventEntity)

    @Query("SELECT * FROM trick_events WHERE runId = :runId ORDER BY timestamp ASC")
    fun getTricksForRun(runId: String): Flow<List<TrickEventEntity>>

    @Query("SELECT trick_events.* FROM trick_events INNER JOIN runs ON trick_events.runId = runs.id WHERE runs.sessionId = :sessionId ORDER BY trick_events.timestamp ASC")
    fun getTricksForSession(sessionId: String): Flow<List<TrickEventEntity>>

    // Debug / Cleanup Operations
    @Query("DELETE FROM geo_points")
    suspend fun deleteAllGeoPoints()

    @Query("DELETE FROM trick_events")
    suspend fun deleteAllTricks()

    @Query("DELETE FROM runs")
    suspend fun deleteAllRuns()

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()
}
