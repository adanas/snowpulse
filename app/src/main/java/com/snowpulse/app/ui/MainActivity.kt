package com.snowpulse.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.app.Activity
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.snowpulse.app.data.local.AppDatabase
import com.snowpulse.app.data.local.PreferencesManager
import com.snowpulse.app.data.model.PhonePlacement
import com.snowpulse.app.data.model.RunEntity
import com.snowpulse.app.data.model.SessionEntity
import com.snowpulse.app.data.model.SkiResortData
import com.snowpulse.app.data.model.Stance
import com.snowpulse.app.domain.engine.AudioCoachEngine
import com.snowpulse.app.service.TrackingForegroundService
import com.snowpulse.app.service.TrackingLiveStats
import com.snowpulse.app.ui.screens.CheckInScreen
import com.snowpulse.app.ui.screens.DashboardScreen
import com.snowpulse.app.ui.screens.RunDetailScreen
import com.snowpulse.app.ui.screens.SessionResultScreen
import com.snowpulse.app.ui.screens.SettingsScreen
import com.snowpulse.app.ui.screens.TrackingScreen
import com.snowpulse.app.ui.screens.DebugScreen
import com.snowpulse.app.ui.screens.SeasonStatsScreen
import com.snowpulse.app.domain.engine.SeasonStatsCalculator
import com.snowpulse.app.util.AppLogger
import com.snowpulse.app.ui.theme.DarkBackground
import com.snowpulse.app.ui.theme.SnowPulseTheme
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.UUID

enum class Screen {
    DASHBOARD,
    CHECK_IN,
    TRACKING,
    RUN_DETAIL,
    SESSION_RESULT,
    SETTINGS,
    DEBUG,
    SEASON_STATS
}

class MainActivity : ComponentActivity() {

    private var trackingService: TrackingForegroundService? = null
    private var isServiceBound = false
    private var debugAudioCoach: AudioCoachEngine? = null

    private val liveStats = mutableStateOf(TrackingLiveStats())
    private val currentScreen = mutableStateOf(Screen.DASHBOARD)
    private val screenBackStack = mutableStateListOf(Screen.DASHBOARD)
    private val selectedRunId = mutableStateOf<String?>(null)
    private val showPermissionDialog = mutableStateOf(false)
    private val isGpsEnabled = mutableStateOf(false)
    private val isGpsMockDisabled = mutableStateOf(false)
    private val mockResortName = mutableStateOf<String?>(null)
    private val isLocating = mutableStateOf(false)
    private val currentLat = mutableStateOf<Double?>(null)
    private val currentLng = mutableStateOf<Double?>(null)
    private val selectedSeasonKey = mutableStateOf("")

    private fun navigateTo(screen: Screen) {
        if (screen == Screen.DASHBOARD) {
            screenBackStack.clear()
            screenBackStack.add(Screen.DASHBOARD)
        } else {
            if (screenBackStack.isEmpty() || screenBackStack.last() != screen) {
                screenBackStack.add(screen)
            }
        }
        currentScreen.value = screen
    }

    private fun navigateBack() {
        if (screenBackStack.size > 1) {
            screenBackStack.removeAt(screenBackStack.size - 1)
            currentScreen.value = screenBackStack.last()
        } else {
            screenBackStack.clear()
            screenBackStack.add(Screen.DASHBOARD)
            currentScreen.value = Screen.DASHBOARD
        }
    }

    private lateinit var db: AppDatabase
    private lateinit var prefs: PreferencesManager

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TrackingForegroundService.LocalBinder
            trackingService = binder.getService()
            isServiceBound = true

            lifecycleScope.launch {
                trackingService?.liveStats?.collectLatest { stats ->
                    liveStats.value = stats
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            trackingService = null
            isServiceBound = false
        }
    }

    private val gpsResolutionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // GPSが有効化されたので現在地を取得
            fetchCurrentLocation()
        }
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineGranted) {
            checkGpsSettingsAndPrompt {
                fetchCurrentLocation {
                    navigateTo(Screen.CHECK_IN)
                }
                return@checkGpsSettingsAndPrompt
            }
            fetchCurrentLocation {
                navigateTo(Screen.CHECK_IN)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.getInstance(this)
        prefs = PreferencesManager(this)
        debugAudioCoach = AudioCoachEngine(applicationContext)

        // 全国マスターデータを初期化
        SkiResortData.initialize(applicationContext)

        // アプリ起動時: GPSがオフの場合は最新Androidポリシーに従いシステムダイアログを表示
        checkGpsSettingsAndPrompt {
            fetchCurrentLocation()
        }

        setContent {
            SnowPulseTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
                    val currentStance by prefs.stanceFlow.collectAsState(initial = Stance.REGULAR)
                    val currentPlacement by prefs.phonePlacementFlow.collectAsState(initial = PhonePlacement.PANTS_FRONT)

                    val latestSession by db.trackingDao().getLatestSession().collectAsState(initial = null)
                    val allSessions by db.trackingDao().getAllSessions().collectAsState(initial = emptyList())
                    val sessionMap = remember(allSessions) { allSessions.associateBy { it.id } }
                    val runs by db.trackingDao().getAllRuns().collectAsState(initial = emptyList())

                    val selectedTricksFlow = remember(selectedRunId.value) {
                        selectedRunId.value?.let { db.trackingDao().getTricksForRun(it) }
                            ?: kotlinx.coroutines.flow.flowOf(emptyList())
                    }
                    val selectedTricks by selectedTricksFlow.collectAsState(initial = emptyList())

                    val selectedPointsFlow = remember(selectedRunId.value) {
                        selectedRunId.value?.let { db.trackingDao().getPointsForRun(it) }
                            ?: kotlinx.coroutines.flow.flowOf(emptyList())
                    }
                    val selectedPoints by selectedPointsFlow.collectAsState(initial = emptyList())

                    val sessionPointsFlow = remember(latestSession?.id) {
                        latestSession?.id?.let { db.trackingDao().getPointsForSession(it) }
                            ?: kotlinx.coroutines.flow.flowOf(emptyList())
                    }
                    val sessionPoints by sessionPointsFlow.collectAsState(initial = emptyList())

                    val sessionTricksFlow = remember(latestSession?.id) {
                        latestSession?.id?.let { db.trackingDao().getTricksForSession(it) }
                            ?: kotlinx.coroutines.flow.flowOf(emptyList())
                    }
                    val sessionTricks by sessionTricksFlow.collectAsState(initial = emptyList())

                    val latestSessionRuns = remember(runs, latestSession?.id) {
                        if (latestSession != null) {
                            runs.filter { it.sessionId == latestSession?.id }
                        } else {
                            runs.take(1)
                        }
                    }

                    // Android ジェスチャーコントロール（左右端スワイプ戻る）対応
                    // 1. ホーム画面（DASHBOARD）以外ではアプリを終了させず、前画面に戻す
                    // 2. ホーム画面での戻る操作: チェックイン中ならタスク常駐（moveTaskToBack）、未チェックインならアプリ終了（finish）
                    BackHandler(enabled = true) {
                        if (currentScreen.value != Screen.DASHBOARD || screenBackStack.size > 1) {
                            navigateBack()
                        } else {
                            if (liveStats.value.isCheckedIn) {
                                // チェックイン中: アプリを終了させずバックグラウンド退避しタスク常駐
                                moveTaskToBack(true)
                            } else {
                                // 未チェックイン: タスク常駐は不要なので通常終了
                                finish()
                            }
                        }
                    }

                    when (currentScreen.value) {
                        Screen.DASHBOARD -> DashboardScreen(
                            currentSession = latestSession,
                            sessionMap = sessionMap,
                            runs = runs,
                            sessions = allSessions,
                            liveStats = liveStats.value,
                            mockResortName = mockResortName.value,
                            isGpsMockDisabled = isGpsMockDisabled.value,
                            currentLat = currentLat.value,
                            currentLng = currentLng.value,
                            onCheckInClick = { checkPermissionsAndOpenCheckIn() },
                            onCheckInMockClick = {
                                checkInMock(null)
                                navigateTo(Screen.TRACKING)
                            },
                            onTogglePauseClick = { trackingService?.togglePause() },
                            onCheckOutClick = { checkOut() },
                            onOpenTrackingMeterClick = { navigateTo(Screen.TRACKING) },
                            onOpenSeasonStatsClick = { navigateTo(Screen.SEASON_STATS) },
                            onSettingsClick = { navigateTo(Screen.SETTINGS) },
                            onRunClick = { runId ->
                                selectedRunId.value = runId
                                navigateTo(Screen.RUN_DETAIL)
                            },
                            onSessionClick = { sId ->
                                selectedRunId.value = runs.firstOrNull { it.sessionId == sId }?.id
                                navigateTo(Screen.SESSION_RESULT)
                            }
                        )

                        Screen.CHECK_IN -> CheckInScreen(
                            currentLat = currentLat.value,
                            currentLng = currentLng.value,
                            isGpsEnabled = isGpsEnabled.value,
                            isLocating = isLocating.value,
                            onRefreshLocation = { fetchCurrentLocation() },
                            onEnableGpsClick = {
                                checkGpsSettingsAndPrompt {
                                    fetchCurrentLocation()
                                }
                            },
                            onResortSelectAndCheckIn = { resort ->
                                checkIn(resort.name)
                                navigateTo(Screen.TRACKING)
                            },
                            onMockCheckIn = { resortName ->
                                checkInMock(resortName)
                                navigateTo(Screen.TRACKING)
                            },
                            onBackClick = { navigateBack() }
                        )

                        Screen.TRACKING -> TrackingScreen(
                            stats = liveStats.value,
                            onPauseClick = { trackingService?.togglePause() },
                            onCheckOutClick = { checkOut() },
                            onBackClick = { navigateBack() },
                            onToggleAudioCoach = { trackingService?.toggleAudioCoach() }
                        )

                        Screen.RUN_DETAIL -> {
                            val selectedRunIndex = runs.indexOfFirst { it.id == selectedRunId.value }
                            val calculatedRunNumber = if (selectedRunIndex >= 0) runs.size - selectedRunIndex else 1
                            val selectedRun = runs.find { it.id == selectedRunId.value }?.let { r ->
                                r.copy(runNumber = maxOf(r.runNumber, calculatedRunNumber))
                            }
                            val selectedRunResort = selectedRun?.let { r ->
                                r.resortName.takeIf { it.isNotBlank() }
                                    ?: sessionMap[r.sessionId]?.resortName?.takeIf { it.isNotBlank() }
                            } ?: latestSession?.resortName

                            RunDetailScreen(
                                run = selectedRun,
                                tricks = selectedTricks,
                                points = selectedPoints,
                                resortName = selectedRunResort,
                                onBackClick = { navigateBack() }
                            )
                        }

                        Screen.SESSION_RESULT -> SessionResultScreen(
                            session = latestSession,
                            sessionMap = sessionMap,
                            runs = latestSessionRuns.ifEmpty { runs.take(1) },
                            points = if (sessionPoints.isNotEmpty()) sessionPoints else selectedPoints,
                            tricks = if (sessionTricks.isNotEmpty()) sessionTricks else selectedTricks,
                            onHomeClick = { navigateTo(Screen.DASHBOARD) }
                        )

                        Screen.SETTINGS -> SettingsScreen(
                            currentStance = currentStance,
                            currentPlacement = currentPlacement,
                            onStanceChange = { s -> lifecycleScope.launch { prefs.setStance(s) } },
                            onPlacementChange = { p -> lifecycleScope.launch { prefs.setPhonePlacement(p) } },
                            onDebugClick = { navigateTo(Screen.DEBUG) },
                            onBackClick = { navigateBack() }
                        )

                        Screen.DEBUG -> DebugScreen(
                            currentLat = currentLat.value,
                            currentLng = currentLng.value,
                            isGpsMockDisabled = isGpsMockDisabled.value,
                            onToggleMockGpsDisabled = {
                                isGpsMockDisabled.value = !isGpsMockDisabled.value
                                isGpsEnabled.value = isGpsProviderEnabled()
                                if (isGpsMockDisabled.value) {
                                    mockResortName.value = null
                                }
                            },
                            onTeleport = { lat, lng, name ->
                                currentLat.value = lat
                                currentLng.value = lng
                                mockResortName.value = name
                                isGpsMockDisabled.value = false
                                isGpsEnabled.value = true
                                checkIn(name)
                            },
                            onCheckInMock = { name ->
                                mockResortName.value = name
                                checkInMock(name)
                            },
                            onCreateQuickRun = {
                                createDebugQuickRun()
                            },
                            onCreateUnriddenSession = {
                                createDebugUnriddenSession()
                            },
                            onForceState = { state ->
                                trackingService?.forceStateForDebug(state)
                            },
                            onInjectSeedRuns = {
                                injectDebugSeedRuns()
                            },
                            onTestTts = { text ->
                                AppLogger.i("TTS", "TTS発話リクエスト: $text")
                                if (trackingService != null) {
                                    trackingService?.speakDirect(text)
                                } else {
                                    if (debugAudioCoach == null) {
                                        debugAudioCoach = AudioCoachEngine(applicationContext)
                                    }
                                    debugAudioCoach?.speak(text, isHighPriority = true)
                                }
                                android.widget.Toast.makeText(this@MainActivity, "🔊 $text", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onClearDatabase = {
                                clearAllDebugData()
                            },
                            onBackClick = { navigateBack() }
                        )

                        Screen.SEASON_STATS -> {
                            val availableSeasons = remember(allSessions) {
                                SeasonStatsCalculator.getAvailableSeasonKeys(allSessions)
                            }
                            val currentKey = selectedSeasonKey.value.ifBlank {
                                availableSeasons.firstOrNull() ?: SeasonStatsCalculator.getCurrentSeasonKey()
                            }
                            val seasonStats = remember(currentKey, allSessions, runs) {
                                SeasonStatsCalculator.calculateSeasonStats(currentKey, allSessions, runs)
                            }

                            SeasonStatsScreen(
                                stats = seasonStats,
                                availableSeasons = availableSeasons,
                                selectedSeasonKey = currentKey,
                                onSeasonSelect = { newKey -> selectedSeasonKey.value = newKey },
                                onBackClick = { navigateBack() },
                                onShareClick = {
                                    val shareText = "🏂 ${seasonStats.seasonDisplayName} ライド記録\n" +
                                            "・総ライド距離: %.1f km\n".format(seasonStats.totalDistanceMeters / 1000f) +
                                            "・最高速度: %.1f km/h\n".format(seasonStats.maxSpeedKmh) +
                                            "・獲得標高: %,.0f m\n".format(seasonStats.totalElevationDropMeters) +
                                            "・チェックイン: ${seasonStats.totalCheckIns} 回\n" +
                                            "・制覇ゲレンデ: ${seasonStats.uniqueResortsCount} 箇所\n" +
                                            "#SnowPulse #スノーボード #スキー"
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    startActivity(Intent.createChooser(sendIntent, "シーズン実績を共有"))
                                }
                            )
                        }
                    }

                    // Google Play Prominent Disclosure Dialog
                    if (showPermissionDialog.value) {
                        AlertDialog(
                            onDismissRequest = { showPermissionDialog.value = false },
                            title = { Text("位置情報の利用について") },
                            text = {
                                Text("SnowPulseは、スキー場チェックイン後に画面を消灯した状態やアプリをバックグラウンドに置いている間でも、滑走速度・距離・リフト搭乗を正確に記録・自動判定するために位置情報を収集します。")
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showPermissionDialog.value = false
                                    launchPermissionRequest()
                                }) {
                                    Text("許可する")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPermissionDialog.value = false }) {
                                    Text("キャンセル")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun isGpsProviderEnabled(): Boolean {
        if (isGpsMockDisabled.value) return false
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    override fun onResume() {
        super.onResume()
        val gpsActive = isGpsProviderEnabled()
        isGpsEnabled.value = gpsActive
        if (gpsActive && (currentLat.value == null || currentLng.value == null)) {
            fetchCurrentLocation()
        }
    }

    private fun checkGpsSettingsAndPrompt(onGpsEnabled: () -> Unit = {}) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L).build()
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            isGpsEnabled.value = true
            onGpsEnabled()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution).build()
                    gpsResolutionLauncher.launch(intentSenderRequest)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun checkPermissionsAndOpenCheckIn() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine) {
            checkGpsSettingsAndPrompt {
                fetchCurrentLocation {
                    currentScreen.value = Screen.CHECK_IN
                }
                return@checkGpsSettingsAndPrompt
            }
            fetchCurrentLocation {
                currentScreen.value = Screen.CHECK_IN
            }
        } else {
            showPermissionDialog.value = true
        }
    }

    private fun fetchCurrentLocation(onComplete: () -> Unit = {}) {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val gpsActive = isGpsProviderEnabled()
        isGpsEnabled.value = gpsActive

        if (!hasFine || !gpsActive) {
            isLocating.value = false
            onComplete()
            return
        }

        isLocating.value = true
        try {
            val fused = LocationServices.getFusedLocationProviderClient(this)
            // 1. まず高速なキャッシュ位置を取得
            fused.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    currentLat.value = loc.latitude
                    currentLng.value = loc.longitude
                    isLocating.value = false
                    onComplete()
                } else {
                    // キャッシュが無い場合: getCurrentLocation で能動的に高精度現在地を測位
                    fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { freshLoc ->
                            if (freshLoc != null) {
                                currentLat.value = freshLoc.latitude
                                currentLng.value = freshLoc.longitude
                            }
                            isLocating.value = false
                            onComplete()
                        }
                        .addOnFailureListener {
                            isLocating.value = false
                            onComplete()
                        }
                }
            }.addOnFailureListener {
                // lastLocation 失敗時も getCurrentLocation をフォールバック試行
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { freshLoc ->
                        if (freshLoc != null) {
                            currentLat.value = freshLoc.latitude
                            currentLng.value = freshLoc.longitude
                        }
                        isLocating.value = false
                        onComplete()
                    }
                    .addOnFailureListener {
                        isLocating.value = false
                        onComplete()
                    }
            }
        } catch (e: SecurityException) {
            isLocating.value = false
            onComplete()
        }
    }

    private fun launchPermissionRequest() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestLocationPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun checkIn(resortName: String) {
        val intent = Intent(this, TrackingForegroundService::class.java).apply {
            action = TrackingForegroundService.ACTION_CHECK_IN
            putExtra(TrackingForegroundService.EXTRA_RESORT_NAME, resortName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun checkInMock(resortName: String? = null) {
        val intent = Intent(this, TrackingForegroundService::class.java).apply {
            action = TrackingForegroundService.ACTION_CHECK_IN_MOCK
            putExtra(TrackingForegroundService.EXTRA_RESORT_NAME, resortName.orEmpty())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun checkOut() {
        val intent = Intent(this, TrackingForegroundService::class.java).apply {
            action = TrackingForegroundService.ACTION_CHECK_OUT
        }
        startService(intent)
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        navigateTo(Screen.SESSION_RESULT)
    }

    private fun createDebugQuickRun() {
        lifecycleScope.launch(Dispatchers.IO) {
            val sId = UUID.randomUUID().toString()
            val rId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val totalExisting = db.trackingDao().getTotalRunCount()
            val resort = "川場スキー場"
            val session = SessionEntity(
                id = sId,
                resortName = resort,
                startTime = now - 900_000L,
                endTime = now,
                totalDistanceMeters = 2100f,
                maxSpeedKmh = 54.2f,
                totalRunsCount = 1,
                totalElevationDropMeters = 580f
            )
            db.trackingDao().insertSession(session)
            val run = RunEntity(
                id = rId,
                sessionId = sId,
                runNumber = totalExisting + 1,
                resortName = resort,
                startTime = now - 900_000L,
                endTime = now,
                distanceMeters = 2100f,
                maxSpeedKmh = 54.2f,
                avgSpeedKmh = 34.5f,
                elevationDropMeters = 580f,
                maxEdgeAngleDeg = 48.0f
            )
            db.trackingDao().insertRun(run)
            AppLogger.i("DEBUG", "1本滑走完了レコード (2.1km / 54.2km/h) を生成しました")
        }
    }

    private fun createDebugUnriddenSession() {
        lifecycleScope.launch(Dispatchers.IO) {
            val sId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val resort = "ニューグリーンピア津南"
            val session = SessionEntity(
                id = sId,
                resortName = resort,
                startTime = now,
                endTime = now + 5000L,
                totalDistanceMeters = 0.0f,
                maxSpeedKmh = 0.0f,
                totalRunsCount = 0,
                totalElevationDropMeters = 0.0f
            )
            db.trackingDao().insertSession(session)
            AppLogger.i("DEBUG", "未滑走セッション (0.0km / 0本) を作成しました")
        }
    }

    private fun injectDebugSeedRuns() {
        lifecycleScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val oneDay = 86400000L
            val seedData = listOf(
                Triple("ニューグリーンピア津南", now, listOf(
                    Pair(1800f, 58.4f),
                    Pair(2100f, 54.2f)
                )),
                Triple("川場スキー場", now - oneDay, listOf(
                    Pair(2200f, 52.1f),
                    Pair(1900f, 49.3f),
                    Pair(2400f, 55.0f)
                )),
                Triple("白馬八方尾根", now - 2 * oneDay, listOf(
                    Pair(2600f, 61.2f),
                    Pair(2000f, 48.5f),
                    Pair(2300f, 53.0f)
                ))
            )
            var currentRunNum = 1
            seedData.reversed().forEach { (resort, dateBase, runsList) ->
                val sId = UUID.randomUUID().toString()
                var sessionDist = 0f
                var sessionMaxSpeed = 0f
                runsList.forEach { (dist, spd) ->
                    sessionDist += dist
                    if (spd > sessionMaxSpeed) sessionMaxSpeed = spd
                }
                val session = SessionEntity(
                    id = sId,
                    resortName = resort,
                    startTime = dateBase - 18000000L,
                    endTime = dateBase,
                    totalDistanceMeters = sessionDist,
                    maxSpeedKmh = sessionMaxSpeed,
                    totalRunsCount = runsList.size
                )
                db.trackingDao().insertSession(session)

                runsList.forEachIndexed { rIdx, (dist, spd) ->
                    val rId = UUID.randomUUID().toString()
                    val run = RunEntity(
                        id = rId,
                        sessionId = sId,
                        runNumber = currentRunNum++,
                        resortName = resort,
                        startTime = dateBase - (runsList.size - rIdx) * 3600000L,
                        endTime = dateBase - (runsList.size - rIdx) * 3600000L + 900000L,
                        distanceMeters = dist,
                        maxSpeedKmh = spd,
                        avgSpeedKmh = spd * 0.65f,
                        elevationDropMeters = 550f,
                        maxEdgeAngleDeg = 44.0f
                    )
                    db.trackingDao().insertRun(run)
                }
            }
            AppLogger.i("DEBUG", "シードデータ8本 (津南/川場/白馬) を生成完了")
        }
    }

    private fun clearAllDebugData() {
        lifecycleScope.launch(Dispatchers.IO) {
            db.trackingDao().deleteAllGeoPoints()
            db.trackingDao().deleteAllTricks()
            db.trackingDao().deleteAllRuns()
            db.trackingDao().deleteAllSessions()
            AppLogger.w("DEBUG", "全テーブル（GeoPoints, Tricks, Runs, Sessions）を完全消去しました")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        debugAudioCoach?.shutdown()
        debugAudioCoach = null
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}
