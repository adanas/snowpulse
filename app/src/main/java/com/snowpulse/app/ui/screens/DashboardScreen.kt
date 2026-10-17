package com.snowpulse.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snowpulse.app.R
import com.snowpulse.app.data.model.RunEntity
import com.snowpulse.app.data.model.SessionEntity
import com.snowpulse.app.data.model.TrackingState
import com.snowpulse.app.service.TrackingLiveStats
import com.snowpulse.app.ui.components.ResortWeatherCard
import com.snowpulse.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    currentSession: SessionEntity?,
    runs: List<RunEntity>,
    sessions: List<SessionEntity> = emptyList(),
    liveStats: TrackingLiveStats,
    sessionMap: Map<String, SessionEntity> = emptyMap(),
    mockResortName: String? = null,
    isGpsMockDisabled: Boolean = false,
    currentLat: Double? = null,
    currentLng: Double? = null,
    onCheckInClick: () -> Unit,
    onCheckInMockClick: () -> Unit = {},
    onTogglePauseClick: () -> Unit,
    onCheckOutClick: () -> Unit,
    onOpenTrackingMeterClick: () -> Unit,
    onOpenSeasonStatsClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onRunClick: (String) -> Unit,
    onSessionClick: (String) -> Unit = onRunClick
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showCheckOutDialog by remember { mutableStateOf(false) }
    val currentDateText = remember {
        SimpleDateFormat("yyyy.MM.dd (E)", Locale.ENGLISH).format(Date())
    }

    // スキー場名表示: チェックイン中優先、未チェックイン時は疑似GPSテレポート先、GPS無効時はGPSオフ表示
    val displayResortName = when {
        liveStats.isCheckedIn && liveStats.resortName.isNotBlank() -> liveStats.resortName
        isGpsMockDisabled -> "- (GPSオフ)"
        !mockResortName.isNullOrBlank() -> mockResortName
        else -> "-"
    }

    // 天気情報: チェックイン中または疑似GPSテレポート先がある場合、リアルタイム気象を算出・表示
    val weatherInfo = remember(liveStats.isCheckedIn, liveStats.resortName, mockResortName, isGpsMockDisabled) {
        val targetResortName = when {
            liveStats.isCheckedIn && liveStats.resortName.isNotBlank() -> liveStats.resortName
            !isGpsMockDisabled && !mockResortName.isNullOrBlank() -> mockResortName
            else -> null
        }

        if (targetResortName != null) {
            val cleanName = targetResortName.removeSuffix(" (デモ)").trim()
            val masterResorts = com.snowpulse.app.data.model.SkiResortData.getMasterResorts(context)
            val matched = masterResorts.find { it.name == cleanName || cleanName.contains(it.name) || it.name.contains(cleanName) }
            val lat = matched?.latitude ?: currentLat ?: 36.8
            val lng = matched?.longitude ?: currentLng ?: 138.8
            val elev = matched?.elevationTopM ?: 1600
            com.snowpulse.app.data.model.WeatherInfo.createRealisticWeather(
                resortName = targetResortName,
                lat = lat,
                lng = lng,
                elevationTopM = elev
            )
        } else {
            com.snowpulse.app.data.model.WeatherInfo.unassigned()
        }
    }

    // 天気情報連動の雪山背景画像（5パターン: 快晴、曇り、粉雪、吹雪、雨/春雪）
    // Fallback: R.drawable.snow_mountain_header
    val weatherHeaderResId = remember(weatherInfo) {
        val cond = weatherInfo?.condition?.lowercase() ?: ""
        val wind = weatherInfo?.windSpeedMs ?: 0f
        val temp = weatherInfo?.temperatureCelsius ?: 0f
        val isHighWind = wind >= 8.0f
        when {
            // 1. 吹雪・荒天
            cond.contains("吹雪") || cond.contains("暴風雪") || (isHighWind && cond.contains("雪")) -> {
                R.drawable.header_weather_blizzard
            }
            // 2. 降雪・粉雪
            cond.contains("雪") || cond.contains("粉雪") || cond.contains("新雪") || cond.contains("パウダー") -> {
                R.drawable.header_weather_snow
            }
            // 3. 雨・みぞれ・春の雪解け / 気温10度以上のグリーンシーズン
            cond.contains("雨") || cond.contains("みぞれ") || cond.contains("グリーン") || temp >= 12.0f -> {
                R.drawable.header_weather_rain
            }
            // 4. 曇り・薄曇り・霧・準備中
            cond.contains("曇") || cond.contains("くもり") || cond.contains("霧") || cond.contains("準備") -> {
                R.drawable.header_weather_cloudy
            }
            // 5. 晴れ・快晴・晴天（デフォルト）
            else -> {
                R.drawable.header_weather_clear
            }
        }
    }

    val displayCheckIns = remember(sessions, runs, sessionMap, liveStats.isCheckedIn, currentSession?.id) {
        val completedSessions = sessions.filter { it.endTime != null && (!liveStats.isCheckedIn || it.id != currentSession?.id) }
        if (completedSessions.isNotEmpty()) {
            completedSessions.sortedByDescending { it.startTime }
        } else if (runs.isNotEmpty()) {
            val grouped = runs.groupBy { it.sessionId }
            grouped.mapNotNull { (sId, runList) ->
                val s = sessionMap[sId]
                // チェックイン中（未チェックアウト）のセッションは除外
                if (s != null && s.endTime == null) return@mapNotNull null
                if (currentSession?.id == sId && liveStats.isCheckedIn) return@mapNotNull null
                val sEndTime = s?.endTime ?: runList.maxOfOrNull { it.endTime ?: it.startTime }
                if (sEndTime == null) return@mapNotNull null

                val sStartTime = runList.minOfOrNull { it.startTime } ?: System.currentTimeMillis()
                val sResort = s?.resortName?.takeIf { it.isNotBlank() }
                    ?: runList.firstOrNull { it.resortName.isNotBlank() }?.resortName
                    ?: "ライドゲレンデ"
                val sDist = s?.totalDistanceMeters?.takeIf { it > 0f }
                    ?: runList.sumOf { it.distanceMeters.toDouble() }.toFloat()
                val sSpeed = s?.maxSpeedKmh?.takeIf { it > 0f }
                    ?: (runList.maxOfOrNull { it.maxSpeedKmh } ?: 0f)
                val sDrop = s?.totalElevationDropMeters?.takeIf { it > 0f }
                    ?: runList.sumOf { it.elevationDropMeters.toDouble() }.toFloat()
                SessionEntity(
                    id = sId,
                    startTime = sStartTime,
                    endTime = sEndTime,
                    resortName = sResort,
                    totalDistanceMeters = sDist,
                    maxSpeedKmh = sSpeed,
                    totalElevationDropMeters = sDrop,
                    totalRunsCount = runList.size
                )
            }.sortedByDescending { it.startTime }
        } else {
            emptyList()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            // App Header Area with Snow Mountain Background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBackground)
            ) {
                // Snow Mountain Background Image (Weather Adaptive)
                Image(
                    painter = painterResource(id = weatherHeaderResId),
                    contentDescription = "Snow Mountain Header",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark Gradient Scrim Overlay for legibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    Color(0xFF0F172A).copy(alpha = 0.88f)
                                )
                            )
                        )
                )

                // Header Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top row: App Title & Settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SnowPulse",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // シーズン記録専用画面遷移ボタン
                            IconButton(
                                onClick = onOpenSeasonStatsClick,
                                modifier = Modifier.size(34.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color(0xFF0F172A).copy(alpha = 0.7f)
                                )
                            ) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = "シーズン記録",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = onSettingsClick,
                                modifier = Modifier.size(34.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color(0xFF0F172A).copy(alpha = 0.7f)
                                )
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Bottom: Resort Name (alone) & Date status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = displayResortName,
                            color = if (displayResortName != "-") NeonCyan else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = currentDateText,
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Mock GPS / Disabled GPS Status Banner for Debugging
        if (isGpsMockDisabled) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF450A0A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🔴", fontSize = 14.sp)
                        Column {
                            Text("疑似GPS無効化中 (位置情報未測位)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("デバッグ画面でGPSが無効化されています。スキー場距離計算はハイフン表示になります。", color = Color(0xFFFCA5A5), fontSize = 10.sp)
                        }
                    }
                }
            }
        } else if (!mockResortName.isNullOrBlank() && !liveStats.isCheckedIn) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0C4A6E),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("📍", fontSize = 14.sp)
                            Column {
                                Text("疑似GPS連動中: $mockResortName", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                val coordText = if (currentLat != null && currentLng != null) {
                                    String.format(Locale.US, "%.4f, %.4f", currentLat, currentLng)
                                } else "デバッグテレポート"
                                Text("テレポート中 ($coordText) • ゲレンデ気象情報を連動表示中", color = NeonCyan, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // Live Resort Weather Card
        item {
            ResortWeatherCard(weather = weatherInfo)
        }

        // Active Check-In Banner (When user is checked in at the resort)
        if (liveStats.isCheckedIn) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (liveStats.isPaused) CardBackground else Color(0xFF0F2327)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (liveStats.isPaused) AccentAmber else SuccessEmerald
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (liveStats.isPaused) AccentAmber.copy(alpha = 0.2f) else SuccessEmerald.copy(alpha = 0.2f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (liveStats.isPaused) Icons.Default.Coffee else Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = if (liveStats.isPaused) AccentAmber else SuccessEmerald,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (liveStats.isPaused) "休憩中" else "チェックイン中",
                                            color = if (liveStats.isPaused) AccentAmber else SuccessEmerald,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (liveStats.state) {
                                        TrackingState.PAUSED_RUN -> Icons.Default.Coffee
                                        TrackingState.SKIING -> Icons.Default.DownhillSkiing
                                        TrackingState.RIDING_LIFT -> Icons.Default.AirlineSeatReclineExtra
                                        TrackingState.IDLE -> Icons.Default.PauseCircle
                                        else -> Icons.Default.Sensors
                                    },
                                    contentDescription = null,
                                    tint = when (liveStats.state) {
                                        TrackingState.SKIING -> SuccessEmerald
                                        TrackingState.RIDING_LIFT -> NeonCyan
                                        TrackingState.PAUSED_RUN -> AccentAmber
                                        else -> Color.LightGray
                                    },
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = when (liveStats.state) {
                                        TrackingState.PAUSED_RUN -> "計測停止中"
                                        TrackingState.SKIING -> "ライド中"
                                        TrackingState.RIDING_LIFT -> "リフト搭乗中"
                                        TrackingState.IDLE -> "スタンバイ"
                                        else -> "自動検知中"
                                    },
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Live Quick Metrics (総ライド距離 & 最高速度 & 本日のライド本数: 均等等間隔配置)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text("総ライド距離", color = Color.Gray, fontSize = 11.sp)
                                Text(
                                    text = "%.1f km".format(liveStats.distanceMeters / 1000f),
                                    color = NeonCyan,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("最高速度", color = Color.Gray, fontSize = 11.sp)
                                Text(
                                    text = "%.1f km/h".format(liveStats.maxSpeedKmh),
                                    color = AccentAmber,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text("本日のライド", color = Color.Gray, fontSize = 11.sp)
                                Text(
                                    text = "${runs.size} Runs",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // View Live Meter Button
                        Button(
                            onClick = onOpenTrackingMeterClick,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("リアルタイムライドメーターを開く", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Break / Checkout Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Pause / Resume (Break button for food & lodge)
                            Button(
                                onClick = onTogglePauseClick,
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (liveStats.isPaused) SuccessEmerald else AccentAmber
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (liveStats.isPaused) Icons.Default.PlayArrow else Icons.Default.Coffee,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (liveStats.isPaused) "ライドを再開" else "休憩する",
                                    color = Color.Black,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Check-out Button (when leaving ski resort)
                            Button(
                                onClick = { showCheckOutDialog = true },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("チェックアウト", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Today's Summary Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("TODAY'S SUMMARY", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("総ライド距離", color = Color.Gray, fontSize = 11.sp)
                            val dist = if ((liveStats.isCheckedIn) && (liveStats.distanceMeters > 0f)) {
                                liveStats.distanceMeters / 1000f
                            } else {
                                (currentSession?.totalDistanceMeters ?: 0f) / 1000f
                            }
                            Text(
                                text = "%.1f km".format(dist),
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("最高速度", color = Color.Gray, fontSize = 11.sp)
                            val maxSpd = if (liveStats.isCheckedIn && liveStats.maxSpeedKmh > 0f) {
                                liveStats.maxSpeedKmh
                            } else {
                                currentSession?.maxSpeedKmh ?: 0f
                            }
                            Text(
                                text = "%.1f km/h".format(maxSpd),
                                color = AccentAmber,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("獲得標高", color = Color.Gray, fontSize = 11.sp)
                            Text(
                                text = "%.0f m".format(currentSession?.totalElevationDropMeters ?: 0f),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ライド本数", color = Color.Gray, fontSize = 11.sp)
                            Text(
                                text = "${runs.size} Runs",
                                color = SuccessEmerald,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Check-in Buttons (Only shown when NOT checked in)
        if (!liveStats.isCheckedIn) {
            item {
                Button(
                    onClick = onCheckInClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Snowboarding, contentDescription = null, tint = Color.Black, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("check in", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Check-in History Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("チェックイン履歴", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${displayCheckIns.size}回 記録済み", color = Color.Gray, fontSize = 12.sp)
            }
        }

        if (displayCheckIns.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("まだチェックイン履歴がありません", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "スキー場に到着したら「check in」を押してください。チェックインごとのライドログやAI分析が自動記録されます。",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        } else {
            // Check-in List Items
            itemsIndexed(displayCheckIns, key = { _, s -> s.id }) { index, session ->
                val displayNumber = displayCheckIns.size - index
                val relatedRun = runs.firstOrNull { it.sessionId == session.id }
                CheckInItemCard(
                    session = session,
                    displayNumber = displayNumber,
                    onClick = {
                        if (relatedRun != null) {
                            onRunClick(relatedRun.id)
                        } else {
                            onSessionClick(session.id)
                        }
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Checkout Confirmation Dialog
    if (showCheckOutDialog) {
        AlertDialog(
            onDismissRequest = { showCheckOutDialog = false },
            title = { Text("チェックアウトしますか？", fontWeight = FontWeight.Bold) },
            text = {
                Text("本日のライド計測を終了し、今日の全データを保存してチェックアウトします。\n\nチェックアウト後、本日の総合結果ページを表示します。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCheckOutDialog = false
                        onCheckOutClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("チェックアウトする", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCheckOutDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
fun RunItemCard(
    run: RunEntity,
    resortName: String = "",
    displayRunNumber: Int = run.runNumber,
    onClick: () -> Unit
) {
    val dateText = remember(run.startTime) {
        val t = if (run.startTime > 0) run.startTime else System.currentTimeMillis()
        SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN).format(Date(t))
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Run #${displayRunNumber}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (resortName.isNotBlank() && resortName != "未チェックイン (滑走計測)" && resortName != "未チェックイン (ライド計測)") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NeonCyan.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = resortName,
                                color = NeonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = dateText,
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "%.1f km | 最高 %.1f km/h".format(run.distanceMeters / 1000f, run.maxSpeedKmh),
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Max %.0f°".format(run.maxEdgeAngleDeg), color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("エッジ角", color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun CheckInItemCard(
    session: SessionEntity,
    displayNumber: Int,
    onClick: () -> Unit
) {
    val dateText = remember(session.startTime) {
        val t = if (session.startTime > 0) session.startTime else System.currentTimeMillis()
        SimpleDateFormat("yyyy/M/d", Locale.JAPAN).format(Date(t))
    }
    val runsCount = if (session.totalRunsCount > 0) session.totalRunsCount else 1
    val rName = session.resortName.takeIf { it.isNotBlank() && it != "未設定ゲレンデ" && it != "未チェックイン (滑走計測)" && it != "未チェックイン (ライド計測)" } ?: "ライドゲレンデ"

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "#$displayNumber $dateText $rName",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${runsCount}本ライド • %.1f km • 最高 %.1f km/h".format(session.totalDistanceMeters / 1000f, session.maxSpeedKmh),
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (session.totalElevationDropMeters > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("%.0fm".format(session.totalElevationDropMeters), color = SuccessEmerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("獲得標高", color = Color.Gray, fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(">", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

