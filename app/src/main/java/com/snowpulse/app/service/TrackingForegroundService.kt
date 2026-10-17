package com.snowpulse.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.snowpulse.app.data.local.AppDatabase
import com.snowpulse.app.data.local.PreferencesManager
import com.snowpulse.app.data.model.GeoPointEntity
import com.snowpulse.app.data.model.RunEntity
import com.snowpulse.app.data.model.SessionEntity
import com.snowpulse.app.data.model.TrackingState
import com.snowpulse.app.data.model.TrickEventEntity
import com.snowpulse.app.domain.engine.AudioCoachEngine
import com.snowpulse.app.domain.engine.ImuSample
import com.snowpulse.app.domain.engine.LocationSample
import com.snowpulse.app.domain.engine.MotionAnalysisEngine
import com.snowpulse.app.domain.engine.StateDetectionEngine
import com.snowpulse.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.snowpulse.app.util.AppLogger
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

data class TrackingLiveStats(
    val isCheckedIn: Boolean = false,
    val isPaused: Boolean = false,
    val state: TrackingState = TrackingState.IDLE,
    val currentSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val currentEdgeAngle: Float = 0f,
    val runNumber: Int = 0,
    val latestTrick: String? = null,
    val altitudeMeters: Double = 0.0,
    val distanceMeters: Float = 0f,
    val resortName: String = "",
    val isAudioCoachEnabled: Boolean = true
)

class TrackingForegroundService : Service(), SensorEventListener {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Engines & Repos
    private lateinit var stateEngine: StateDetectionEngine
    private lateinit var motionEngine: MotionAnalysisEngine
    private lateinit var audioCoach: AudioCoachEngine
    private lateinit var db: AppDatabase
    private lateinit var prefs: PreferencesManager

    // Hardware
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null

    // Session & Run State
    private var currentSessionId: String? = null
    private var currentRunId: String? = null
    private var currentResortName: String = ""
    private var runCount: Int = 0
    private var maxSessionSpeed: Float = 0f
    private var totalSessionDistance: Float = 0f
    private var lastLocation: Location? = null

    // Run-level accumulator
    private var currentRunStartTime: Long = 0L
    private var currentRunDistance: Float = 0f
    private var currentRunMaxSpeed: Float = 0f
    private var currentRunMaxEdge: Float = 0f
    private var currentRunStartAltitude: Double = 0.0

    // Live Stats Flow for UI
    private val _liveStats = MutableStateFlow(TrackingLiveStats())
    val liveStats: StateFlow<TrackingLiveStats> = _liveStats.asStateFlow()

    // Temporary Buffers for Batch Insert
    private val pendingPoints = mutableListOf<GeoPointEntity>()
    private var isManuallyPaused = false

    fun togglePause() {
        if (isManuallyPaused) {
            isManuallyPaused = false
            _liveStats.value = _liveStats.value.copy(
                isPaused = false,
                state = TrackingState.IDLE
            )
            audioCoach.speak("休憩を終了し、滑走計測を再開します")
        } else {
            isManuallyPaused = true
            _liveStats.value = _liveStats.value.copy(
                isPaused = true,
                state = TrackingState.PAUSED_RUN,
                currentEdgeAngle = 0f // 休憩中はエッジ角を0にリセット
            )
            audioCoach.speak("休憩モードに設定しました。ゆっくりお休みください")
        }
    }

    fun toggleAudioCoach(): Boolean {
        val nextState = !_liveStats.value.isAudioCoachEnabled
        audioCoach.isEnabled = nextState
        _liveStats.value = _liveStats.value.copy(isAudioCoachEnabled = nextState)
        AppLogger.i("AUDIO", "音声コーチトグル: ${if (nextState) "ON" else "OFF (ミュート)"}")
        if (nextState) {
            audioCoach.speak("音声コーチを有効にしました")
        }
        return nextState
    }

    inner class LocalBinder : Binder() {
        fun getService(): TrackingForegroundService = this@TrackingForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getInstance(this)
        prefs = PreferencesManager(this)
        stateEngine = StateDetectionEngine()
        motionEngine = MotionAnalysisEngine()
        audioCoach = AudioCoachEngine(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        initLocationCallback()
        createNotificationChannel()

        // Observe Stance & Placement Preferences dynamically (設定変更時に即座にキャリブレーションリセット・更新)
        serviceScope.launch {
            combine(prefs.stanceFlow, prefs.phonePlacementFlow) { stance, placement ->
                Pair(stance, placement)
            }.collect { (stance, placement) ->
                motionEngine.updateSettings(stance, placement)
                AppLogger.d("SETTINGS", "スマホ収納位置またはスタンス変更検知: placement=$placement, stance=$stance -> キャリブレーションリセット")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resortName = intent?.getStringExtra(EXTRA_RESORT_NAME).orEmpty()
        when (intent?.action) {
            ACTION_START, ACTION_CHECK_IN -> startTrackingSession(resortName)
            ACTION_START_MOCK, ACTION_CHECK_IN_MOCK -> startMockTrackingSession(resortName)
            ACTION_STOP, ACTION_CHECK_OUT -> stopTrackingSession()
        }
        return START_STICKY
    }

    private var mockJob: kotlinx.coroutines.Job? = null
    private var isMockTrackingSession = false

    private fun startTrackingSession(resortName: String) {
        isMockTrackingSession = false
        currentSessionId = UUID.randomUUID().toString()
        currentResortName = resortName.ifBlank { "滑走計測" }
        runCount = 0
        maxSessionSpeed = 0f
        totalSessionDistance = 0f
        isManuallyPaused = false
        stateEngine.reset()

        _liveStats.value = TrackingLiveStats(
            isCheckedIn = true,
            isPaused = false,
            resortName = currentResortName,
            state = TrackingState.IDLE
        )

        val notification = buildNotification("$currentResortName にチェックイン中", "自動滑走検知がスタンバイ中です")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Save new session in Room & load historical run count
        serviceScope.launch(Dispatchers.IO) {
            val totalExisting = db.trackingDao().getTotalRunCount()
            runCount = totalExisting
            val session = SessionEntity(
                id = currentSessionId!!,
                resortName = currentResortName,
                stance = motionEngine.stance,
                phonePlacement = motionEngine.placement
            )
            db.trackingDao().insertSession(session)
        }

        startHardwareUpdates()
        audioCoach.speak("${currentResortName}にチェックインしました。自動滑走計測を開始します")
    }

    private fun startMockTrackingSession(resortName: String) {
        isMockTrackingSession = true
        currentSessionId = UUID.randomUUID().toString()
        currentResortName = if (resortName.isNotBlank()) "$resortName (デモ)" else "デモ滑走ゲレンデ"
        runCount = 0
        maxSessionSpeed = 0f
        totalSessionDistance = 0f
        isManuallyPaused = false
        stateEngine.reset()

        _liveStats.value = TrackingLiveStats(
            isCheckedIn = true,
            isPaused = false,
            resortName = currentResortName,
            state = TrackingState.SKIING
        )

        val notification = buildNotification("デモ滑走シミュレーション中", "${currentResortName}で滑走中...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        currentRunId = UUID.randomUUID().toString()
        currentRunStartTime = System.currentTimeMillis()
        currentRunDistance = 0f
        currentRunMaxSpeed = 0f
        currentRunMaxEdge = 0f
        currentRunStartAltitude = 1600.0

        serviceScope.launch(Dispatchers.IO) {
            val totalExisting = db.trackingDao().getTotalRunCount()
            runCount = totalExisting + 1
            _liveStats.value = _liveStats.value.copy(runNumber = runCount)

            val session = SessionEntity(
                id = currentSessionId!!,
                resortName = currentResortName,
                stance = motionEngine.stance,
                phonePlacement = motionEngine.placement
            )
            db.trackingDao().insertSession(session)
            db.trackingDao().insertRun(
                RunEntity(
                    id = currentRunId!!,
                    sessionId = currentSessionId!!,
                    runNumber = runCount,
                    resortName = currentResortName,
                    startTime = currentRunStartTime,
                    distanceMeters = 0f,
                    maxSpeedKmh = 0f,
                    avgSpeedKmh = 0f,
                    maxEdgeAngleDeg = 0f
                )
            )
        }

        audioCoach.speak("${currentResortName}にチェックインしました。デモ滑走を開始します")

        val replayer = com.snowpulse.app.domain.mock.MockScenarioReplayer().apply {
            playbackSpeed = 1.5f // テンポよく1.5倍速で再生
        }

        mockJob?.cancel()
        mockJob = serviceScope.launch(Dispatchers.Default) {
            try {
                assets.open("scenarios/scenario_normal_run.csv").use { inputStream ->
                    replayer.replayFromStream(inputStream).collect { frame ->
                        if (isManuallyPaused) {
                            return@collect
                        }
                        val loc = Location("mock").apply {
                            latitude = frame.location.latitude
                            longitude = frame.location.longitude
                            altitude = frame.location.altitudeMeters
                            speed = frame.location.speedKmh / 3.6f
                            time = frame.location.timestamp
                        }
                        onNewLocation(loc)

                        val (edgeAngle, trick) = motionEngine.processImuSample(
                            frame.imu,
                            _liveStats.value.currentSpeedKmh
                        )
                        _liveStats.value = _liveStats.value.copy(currentEdgeAngle = edgeAngle)

                        trick?.let { t ->
                            _liveStats.value = _liveStats.value.copy(latestTrick = "${t.trickType} (${t.airTimeSec}s)")
                            audioCoach.speak("${t.trickType} 成功！")
                            currentRunId?.let { rId ->
                                launch {
                                    db.trackingDao().insertTrick(
                                        TrickEventEntity(
                                            runId = rId,
                                            trickType = t.trickType,
                                            airTimeSeconds = t.airTimeSec,
                                            jumpHeightCm = t.estimatedHeightCm,
                                            rotationDegrees = t.rotationDeg,
                                            isLanded = t.isLanded,
                                            timestamp = t.timestamp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun finalizeCurrentRun() {
        val sId = currentSessionId ?: return
        val rId = currentRunId ?: UUID.randomUUID().toString()
        val rCount = runCount.coerceAtLeast(1)
        val rStart = if (currentRunStartTime > 0) currentRunStartTime else System.currentTimeMillis()

        // Flush any remaining GeoPoints to database
        if (pendingPoints.isNotEmpty()) {
            val remaining = ArrayList(pendingPoints)
            pendingPoints.clear()
            db.trackingDao().insertGeoPoints(remaining)
        }

        val durationSec = ((System.currentTimeMillis() - rStart) / 1000f).coerceAtLeast(1f)
        val avgSpeed = if (durationSec > 0 && currentRunDistance > 0) {
            (currentRunDistance / durationSec * 3.6f).coerceAtMost(currentRunMaxSpeed)
        } else {
            0f
        }
        val currentAlt = _liveStats.value.altitudeMeters
        val drop = if (currentRunStartAltitude > 0 && currentAlt > 0) {
            (currentRunStartAltitude - currentAlt).toFloat().coerceAtLeast(0f)
        } else {
            0f
        }

        val run = RunEntity(
            id = rId,
            sessionId = sId,
            runNumber = rCount,
            resortName = currentResortName,
            startTime = rStart,
            endTime = System.currentTimeMillis(),
            distanceMeters = currentRunDistance,
            maxSpeedKmh = currentRunMaxSpeed,
            avgSpeedKmh = avgSpeed,
            elevationDropMeters = drop,
            maxEdgeAngleDeg = currentRunMaxEdge,
            avgVibrationIndex = motionEngine.getCurrentVibrationIndex()
        )
        // insertRun は OnConflictStrategy.REPLACE なので、新規・更新を問わず確実に保存される
        db.trackingDao().insertRun(run)
    }

    private suspend fun ensureMockRunAndPointsRecorded() {
        val sId = currentSessionId ?: return
        val rId = currentRunId ?: java.util.UUID.randomUUID().toString().also { currentRunId = it }
        val existingPointCount = db.trackingDao().getPointsCountForRun(rId)
        if (existingPointCount >= 10) {
            return
        }

        AppLogger.i("SERVICE", "デモチェックアウト検出: GPS点群および滑走データを確実に補完・記録します (RunId: $rId)")

        // 基準座標の決定 (選択中スキー場またはデフォルト)
        val (baseLat, baseLng) = when {
            currentResortName.contains("苗場") -> Pair(36.7900, 138.7800)
            currentResortName.contains("白馬") -> Pair(36.7020, 137.8600)
            currentResortName.contains("野沢") -> Pair(36.9200, 138.4400)
            currentResortName.contains("津南") || currentResortName.contains("グリーンピア") -> Pair(36.9850, 138.6850)
            else -> Pair(36.8500, 138.7500)
        }

        val now = System.currentTimeMillis()
        val mockPoints = ArrayList<GeoPointEntity>()
        val totalSteps = 25

        // 山頂(1450m)からベース(930m)へ下るリアルな25点のS字ターン軌跡を生成
        for (i in 0 until totalSteps) {
            val progress = i.toFloat() / (totalSteps - 1)
            val sCurveOffset = kotlin.math.sin(progress * Math.PI.toFloat() * 4f) * 0.0012
            val lat = baseLat + 0.015 - (progress * 0.015) + (sCurveOffset * 0.5)
            val lng = baseLng - 0.008 + (progress * 0.016) + sCurveOffset
            val alt = 1450.0 - (progress * 520.0) // 1450m -> 930m
            val speed = 25f + (kotlin.math.sin(progress * Math.PI.toFloat()) * 29.2f).toFloat() // 最大 54.2 km/h
            val edge = 28f + (kotlin.math.abs(kotlin.math.sin(progress * Math.PI.toFloat() * 4f)) * 20f).toFloat() // 最大 48°
            val ptTime = now - ((totalSteps - i) * 3000L)

            mockPoints.add(
                GeoPointEntity(
                    runId = rId,
                    latitude = lat,
                    longitude = lng,
                    altitudeMeters = alt,
                    speedKmh = speed,
                    heading = 180f,
                    edgeAngleDeg = edge,
                    timestamp = ptTime
                )
            )
        }

        db.trackingDao().insertGeoPoints(mockPoints)

        // トリックイベント（Ollie）の補完
        db.trackingDao().insertTrick(
            TrickEventEntity(
                runId = rId,
                trickType = "Ollie",
                airTimeSeconds = 0.65f,
                jumpHeightCm = 38f,
                rotationDegrees = 0f,
                isLanded = true,
                timestamp = now - 45000L
            )
        )

        // 統計値の補正 (滑走分析画面で充実したレポートが表示されるように設定)
        currentRunDistance = 2100f
        currentRunMaxSpeed = 54.2f
        currentRunMaxEdge = 48.0f
        currentRunStartAltitude = 1450.0
        _liveStats.value = _liveStats.value.copy(
            altitudeMeters = 930.0,
            currentSpeedKmh = 0f,
            currentEdgeAngle = 0f,
            latestTrick = "Ollie (0.65s)"
        )
        totalSessionDistance = 2100f
        maxSessionSpeed = 54.2f
    }

    private fun stopTrackingSession() {
        stopHardwareUpdates()
        serviceScope.launch(Dispatchers.IO) {
            // デモモードの場合、滑走分析に必要なGPS点群・ラン・トリックを確実にDBへ完備
            if (isMockTrackingSession) {
                ensureMockRunAndPointsRecorded()
            }

            // 1. バッファに残っている未保存のGeoPointをDBへフラッシュ
            if (pendingPoints.isNotEmpty()) {
                val remaining = ArrayList(pendingPoints)
                pendingPoints.clear()
                db.trackingDao().insertGeoPoints(remaining)
            }

            // 2. 現在進行中のランを確定して保存
            finalizeCurrentRun()

            // 3. セッションのサマリーを確実にDBへ更新 (未登録の場合は新規挿入して確実にチェックイン履歴へ残す)
            currentSessionId?.let { sId ->
                val session = db.trackingDao().getSessionById(sId)
                if (session != null) {
                    db.trackingDao().updateSession(
                        session.copy(
                            endTime = System.currentTimeMillis(),
                            totalDistanceMeters = totalSessionDistance,
                            maxSpeedKmh = maxSessionSpeed,
                            totalRunsCount = runCount.coerceAtLeast(1)
                        )
                    )
                } else {
                    val newSession = SessionEntity(
                        id = sId,
                        resortName = currentResortName,
                        startTime = currentRunStartTime.takeIf { it > 0 } ?: (System.currentTimeMillis() - 60000L),
                        endTime = System.currentTimeMillis(),
                        totalDistanceMeters = totalSessionDistance,
                        maxSpeedKmh = maxSessionSpeed,
                        totalRunsCount = runCount.coerceAtLeast(1)
                    )
                    db.trackingDao().insertSession(newSession)
                }
            }

            _liveStats.value = TrackingLiveStats(
                isCheckedIn = false,
                isPaused = false,
                state = TrackingState.IDLE
            )

            val finishedCount = runCount.coerceAtLeast(1)
            val speedInt = maxSessionSpeed.toInt()
            audioCoach.speak("チェックアウトしました。お疲れ様でした！本日は${finishedCount}本の滑走、最高速度は時速${speedInt}キロでした。AIライディング分析レポートを作成しました。")
            kotlinx.coroutines.delay(1800L) // TTS発声完了猶予
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startHardwareUpdates() {
        // Location Request: 1Hz (1秒間隔)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Permission missing
        }

        // Sensors: 50Hz (20,000 microseconds)
        accelSensor?.let { sensorManager.registerListener(this, it, 20_000) }
        gyroSensor?.let { sensorManager.registerListener(this, it, 20_000) }
    }

    private fun stopHardwareUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
    }

    private fun initLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                onNewLocation(loc)
            }
        }
    }

    private fun onNewLocation(loc: Location) {
        if (isManuallyPaused) return

        val speedKmh = loc.speed * 3.6f
        if (speedKmh > maxSessionSpeed) {
            maxSessionSpeed = speedKmh
        }

        // Distance accumulation
        lastLocation?.let { prev ->
            totalSessionDistance += prev.distanceTo(loc)
        }
        lastLocation = loc

        // State Machine Update
        val sample = LocationSample(
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitudeMeters = loc.altitude,
            speedKmh = speedKmh,
            timestamp = loc.time
        )
        val transition = stateEngine.processSample(sample)

        // Handle Run Transition
        if (transition.hasNewRunStarted || (currentRunId == null && speedKmh > 5.0f)) {
            if (currentRunId != null) {
                serviceScope.launch(Dispatchers.IO) { finalizeCurrentRun() }
            }
            runCount++
            currentRunId = UUID.randomUUID().toString()
            currentRunStartTime = System.currentTimeMillis()
            currentRunDistance = 0f
            currentRunMaxSpeed = speedKmh
            currentRunMaxEdge = _liveStats.value.currentEdgeAngle
            currentRunStartAltitude = loc.altitude

            val initialRun = RunEntity(
                id = currentRunId!!,
                sessionId = currentSessionId ?: "",
                runNumber = runCount,
                resortName = currentResortName,
                startTime = currentRunStartTime,
                distanceMeters = 0f,
                maxSpeedKmh = speedKmh,
                maxEdgeAngleDeg = currentRunMaxEdge
            )
            serviceScope.launch(Dispatchers.IO) {
                db.trackingDao().insertRun(initialRun)
            }
        } else if (transition.hasRunEnded) {
            audioCoach.speak("${runCount}本目終了。最高速度は時速${maxSessionSpeed.toInt()}キロでした。")
            serviceScope.launch(Dispatchers.IO) {
                finalizeCurrentRun()
                currentRunId = null
            }
        } else if (currentRunId != null) {
            lastLocation?.let { prev ->
                currentRunDistance += prev.distanceTo(loc)
            }
            if (speedKmh > currentRunMaxSpeed) currentRunMaxSpeed = speedKmh
            if (_liveStats.value.currentEdgeAngle > currentRunMaxEdge) {
                currentRunMaxEdge = _liveStats.value.currentEdgeAngle
            }
        }

        // Save GeoPoint
        currentRunId?.let { rId ->
            val point = GeoPointEntity(
                runId = rId,
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitudeMeters = loc.altitude,
                speedKmh = speedKmh,
                heading = loc.bearing,
                edgeAngleDeg = _liveStats.value.currentEdgeAngle,
                timestamp = loc.time
            )
            pendingPoints.add(point)
            if (pendingPoints.size >= 10) {
                val toInsert = ArrayList(pendingPoints)
                pendingPoints.clear()
                serviceScope.launch { db.trackingDao().insertGeoPoints(toInsert) }
            }
        }

        _liveStats.value = _liveStats.value.copy(
            state = transition.newState,
            currentSpeedKmh = speedKmh,
            maxSpeedKmh = maxSessionSpeed,
            runNumber = runCount,
            altitudeMeters = loc.altitude,
            distanceMeters = totalSessionDistance
        )

        updateNotification(speedKmh, runCount)
    }

    // IMU Sensor Listeners
    private var lastAcc = FloatArray(3)
    private var lastGyro = FloatArray(3)

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || isManuallyPaused) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> System.arraycopy(event.values, 0, lastAcc, 0, 3)
            Sensor.TYPE_GYROSCOPE -> System.arraycopy(event.values, 0, lastGyro, 0, 3)
        }

        val imuSample = ImuSample(
            accX = lastAcc[0],
            accY = lastAcc[1],
            accZ = lastAcc[2],
            gyroX = lastGyro[0],
            gyroY = lastGyro[1],
            gyroZ = lastGyro[2],
            timestampMs = System.currentTimeMillis()
        )

        val (edgeAngle, trick) = motionEngine.processImuSample(imuSample, _liveStats.value.currentSpeedKmh)

        _liveStats.value = _liveStats.value.copy(currentEdgeAngle = edgeAngle)

        trick?.let { t ->
            _liveStats.value = _liveStats.value.copy(latestTrick = "${t.trickType} (${t.airTimeSec}s)")
            audioCoach.speak("${t.trickType} 成功！")
            currentRunId?.let { rId ->
                serviceScope.launch {
                    db.trackingDao().insertTrick(
                        TrickEventEntity(
                            runId = rId,
                            trickType = t.trickType,
                            airTimeSeconds = t.airTimeSec,
                            jumpHeightCm = t.estimatedHeightCm,
                            rotationDegrees = t.rotationDeg,
                            isLanded = t.isLanded,
                            timestamp = t.timestamp
                        )
                    )
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "滑走トラッキング",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ゲレンデ滑走中のリアルタイム計測ステータス"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(speedKmh: Float, runs: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        val text = "速度: %.1f km/h | 滑走: %d本 | 距離: %.1f km".format(
            speedKmh, runs, totalSessionDistance / 1000f
        )
        val title = if (currentResortName.isNotBlank()) "${currentResortName}にチェックイン中" else "SnowPulse 計測中"
        manager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    fun forceStateForDebug(state: TrackingState) {
        _liveStats.value = _liveStats.value.copy(state = state)
        AppLogger.i("SERVICE", "デバッグ強制ステート変更: $state")
    }

    fun speakDirect(text: String) {
        audioCoach.speak(text)
        AppLogger.i("TTS", "デバッグ音声発声: $text")
    }

    override fun onDestroy() {
        super.onDestroy()
        mockJob?.cancel()
        serviceScope.cancel()
        audioCoach.shutdown()
        stopHardwareUpdates()
    }

    companion object {
        const val CHANNEL_ID = "snowpulse_tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_START_MOCK = "ACTION_START_MOCK"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"

        // Semantic Check-in / Check-out Aliases
        const val ACTION_CHECK_IN = ACTION_START
        const val ACTION_CHECK_IN_MOCK = ACTION_START_MOCK
        const val ACTION_CHECK_OUT = ACTION_STOP

        const val EXTRA_RESORT_NAME = "EXTRA_RESORT_NAME"
    }
}
