package com.snowpulse.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.snowpulse.app.data.model.GeoPointEntity
import com.snowpulse.app.data.model.RunEntity
import com.snowpulse.app.data.model.SessionEntity
import com.snowpulse.app.data.model.TrickEventEntity
import com.snowpulse.app.domain.engine.AiRideAnalysisEngine
import com.snowpulse.app.ui.components.AiRideAnalysisCard
import com.snowpulse.app.ui.components.SkiSlopeTrackMap
import com.snowpulse.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionResultScreen(
    session: SessionEntity?,
    runs: List<RunEntity>,
    sessionMap: Map<String, SessionEntity> = emptyMap(),
    points: List<GeoPointEntity> = emptyList(),
    tricks: List<TrickEventEntity> = emptyList(),
    onRunClick: (String) -> Unit = {},
    onHomeClick: () -> Unit
) {
    val context = LocalContext.current
    var showShareCardDialog by remember { mutableStateOf(false) }

    val sessionDateText = remember(session?.startTime) {
        val time = session?.startTime ?: System.currentTimeMillis()
        SimpleDateFormat("yyyy.MM.dd", Locale.ENGLISH).format(Date(time))
    }

    // 実測GPSおよびランデータに基づくSession Metricsの動的集計（固定値排除）
    val actualRuns = remember(runs) { runs.filter { it.distanceMeters > 0f || it.maxSpeedKmh > 0f } }

    val totalDistKm = remember(session, actualRuns, points) {
        when {
            session != null && session.totalDistanceMeters > 0f -> session.totalDistanceMeters / 1000f
            actualRuns.isNotEmpty() && actualRuns.sumOf { it.distanceMeters.toDouble() } > 0 -> actualRuns.sumOf { it.distanceMeters.toDouble() }.toFloat() / 1000f
            points.size >= 2 -> {
                var sumDist = 0.0
                for (i in 0 until points.size - 1) {
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
                    sumDist += results[0]
                }
                (sumDist / 1000.0).toFloat()
            }
            else -> 0f
        }
    }

    val maxSpeed = remember(session, actualRuns, points) {
        when {
            session != null && session.maxSpeedKmh > 0f -> session.maxSpeedKmh
            actualRuns.isNotEmpty() -> actualRuns.maxOfOrNull { it.maxSpeedKmh } ?: 0f
            points.isNotEmpty() -> points.maxOfOrNull { it.speedKmh } ?: 0f
            else -> 0f
        }
    }

    val totalRuns = remember(actualRuns, totalDistKm, maxSpeed, session) {
        if (actualRuns.isNotEmpty()) {
            actualRuns.size
        } else if (totalDistKm > 0.05f || maxSpeed > 5.0f) {
            1
        } else {
            session?.totalRunsCount?.takeIf { (session.totalDistanceMeters > 0f) } ?: 0
        }
    }

    val totalDrop = remember(session, actualRuns, points) {
        when {
            session != null && session.totalElevationDropMeters > 0f -> session.totalElevationDropMeters
            actualRuns.isNotEmpty() && actualRuns.sumOf { it.elevationDropMeters.toDouble() } > 0 -> actualRuns.sumOf { it.elevationDropMeters.toDouble() }.toFloat()
            points.size >= 2 -> {
                val maxAlt = points.maxOf { it.altitudeMeters }
                val minAlt = points.minOf { it.altitudeMeters }
                (maxAlt - minAlt).toFloat().coerceAtLeast(0f)
            }
            else -> 0f
        }
    }

    val maxEdge = remember(actualRuns, points) {
        actualRuns.maxOfOrNull { it.maxEdgeAngleDeg }?.takeIf { it > 0 }
            ?: points.maxOfOrNull { it.edgeAngleDeg }?.takeIf { it > 0 }
            ?: 0f
    }

    val avgSpeed = remember(actualRuns, points) {
        when {
            actualRuns.isNotEmpty() -> actualRuns.map { it.avgSpeedKmh }.filter { it > 0 }.average().toFloat().takeIf { !it.isNaN() } ?: 0f
            points.isNotEmpty() -> points.map { it.speedKmh }.filter { it > 0 }.average().toFloat().takeIf { !it.isNaN() } ?: 0f
            else -> 0f
        }
    }

    val estimatedCalories = (totalDistKm * 55).toInt()

    val resortName = session?.resortName?.takeIf { it.isNotBlank() } ?: "未チェックイン (ライド計測)"

    // AI Ride Analysis Report
    val aiAnalysisEngine = remember { AiRideAnalysisEngine() }
    val aiReport = remember(session, runs, tricks) {
        aiAnalysisEngine.analyzeSession(session, runs, tricks)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本日のライド", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onHomeClick) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showShareCardDialog = true }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. Celebration Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2327)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, SuccessEmerald)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(SuccessEmerald.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessEmerald,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CHECK-OUT COMPLETE",
                                color = SuccessEmerald,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "本日のライド完了！お疲れ様でした",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$resortName | $sessionDateText",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // 2. AI Riding Analysis Report Card
            AiRideAnalysisCard(report = aiReport)

            // 3. Today's Grand Total Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "TODAY'S RIDE SUMMARY",
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Primary 4 Metrics
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("総ライド距離", color = Color.Gray, fontSize = 11.sp)
                            Text(
                                text = "%.1f km".format(totalDistKm),
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("最高速度", color = Color.Gray, fontSize = 11.sp)
                            Text(
                                text = "%.1f km/h".format(maxSpeed),
                                color = AccentAmber,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("獲得標高差", color = Color.Gray, fontSize = 11.sp)
                            Text(
                                text = "%.0f m".format(totalDrop),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ライド本数", color = Color.Gray, fontSize = 11.sp)
                            Text(
                                text = "$totalRuns Runs",
                                color = SuccessEmerald,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Divider(
                        color = Color.DarkGray.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // Secondary Insights
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("平均速度", color = Color.Gray, fontSize = 10.sp)
                            Text("%.1f km/h".format(avgSpeed), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("最大エッジ角", color = Color.Gray, fontSize = 10.sp)
                            Text("%.0f°".format(maxEdge), color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("推定消費カロリー", color = Color.Gray, fontSize = 10.sp)
                            Text("$estimatedCalories kcal", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("トリック成功", color = Color.Gray, fontSize = 10.sp)
                            Text("${tricks.size} 回", color = TrickPurple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // 3. Today's GPS Slope Track Map
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("本日のライド軌跡マップ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("GPSトラック描画", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                SkiSlopeTrackMap(
                    points = points,
                    tricks = tricks,
                    resortName = resortName
                )
            }

            // 4. Bottom Navigation Button (右上TopAppBarに共有ボタンがあるため下部はダッシュボード戻るボタンのみ)
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Button(
                    onClick = onHomeClick,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ダッシュボードに戻る", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // 1:1 SQUARE SNS SHARE CARD POPUP DIALOG
    if (showShareCardDialog) {
        Dialog(onDismissRequest = { showShareCardDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF0F172A),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonCyan.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Modal Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ライドを共有",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        IconButton(
                            onClick = { showShareCardDialog = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "閉じる", tint = Color.LightGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 1:1 Square Card Preview (アスペクト比 1:1)
                    ShareCardSquarePreview(
                        resortName = resortName,
                        dateText = sessionDateText,
                        maxSpeed = maxSpeed,
                        maxEdge = maxEdge,
                        totalDistKm = totalDistKm,
                        totalRuns = totalRuns,
                        elevDrop = totalDrop,
                        aiStyle = aiReport.styleTitle,
                        aiScore = aiReport.overallScore
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5 Icon-Only Action Buttons: Instagram, X, TikTok, Other Apps, Save Image (統一スタイル)
                    val shareBtnBg = Color(0xFF1E293B)
                    val shareBtnBorder = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF334155))
                    val shareBtnShape = RoundedCornerShape(12.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Instagram (オリジナルアイコン、統一ボタンスタイル: OS/Instagramデフォルト動作)
                        Button(
                            onClick = {
                                val bitmap = generateShareCardBitmap(
                                    resortName = resortName,
                                    dateText = sessionDateText,
                                    maxSpeed = maxSpeed,
                                    maxEdge = maxEdge,
                                    totalDistKm = totalDistKm,
                                    totalRuns = totalRuns,
                                    elevDrop = totalDrop,
                                    aiStyle = aiReport.styleTitle,
                                    aiScore = aiReport.overallScore
                                )
                                shareCardToInstagram(context, bitmap, resortName, maxSpeed, maxEdge)
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = shareBtnBg),
                            border = shareBtnBorder,
                            shape = shareBtnShape,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            InstagramOriginalIcon()
                        }

                        // 2. 𝕏 (オリジナルアイコン、統一ボタンスタイル: 画像ポスト + タグ付き)
                        Button(
                            onClick = {
                                val bitmap = generateShareCardBitmap(
                                    resortName = resortName,
                                    dateText = sessionDateText,
                                    maxSpeed = maxSpeed,
                                    maxEdge = maxEdge,
                                    totalDistKm = totalDistKm,
                                    totalRuns = totalRuns,
                                    elevDrop = totalDrop,
                                    aiStyle = aiReport.styleTitle,
                                    aiScore = aiReport.overallScore
                                )
                                shareCardToX(context, bitmap, resortName, maxSpeed, maxEdge, aiReport.styleTitle)
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = shareBtnBg),
                            border = shareBtnBorder,
                            shape = shareBtnShape,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            XLogoOriginalIcon()
                        }

                        // 3. TikTok (オリジナルアイコン、統一ボタンスタイル)
                        Button(
                            onClick = {
                                val bitmap = generateShareCardBitmap(
                                    resortName = resortName,
                                    dateText = sessionDateText,
                                    maxSpeed = maxSpeed,
                                    maxEdge = maxEdge,
                                    totalDistKm = totalDistKm,
                                    totalRuns = totalRuns,
                                    elevDrop = totalDrop,
                                    aiStyle = aiReport.styleTitle,
                                    aiScore = aiReport.overallScore
                                )
                                shareCardToTikTok(context, bitmap, resortName, maxSpeed, maxEdge)
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = shareBtnBg),
                            border = shareBtnBorder,
                            shape = shareBtnShape,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            TikTokOriginalIcon()
                        }

                        // 4. その他のアプリ (アイコンのみ、統一ボタンスタイル)
                        Button(
                            onClick = {
                                val bitmap = generateShareCardBitmap(
                                    resortName = resortName,
                                    dateText = sessionDateText,
                                    maxSpeed = maxSpeed,
                                    maxEdge = maxEdge,
                                    totalDistKm = totalDistKm,
                                    totalRuns = totalRuns,
                                    elevDrop = totalDrop,
                                    aiStyle = aiReport.styleTitle,
                                    aiScore = aiReport.overallScore
                                )
                                shareCardImageIntent(context, bitmap, resortName, maxSpeed, maxEdge)
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = shareBtnBg),
                            border = shareBtnBorder,
                            shape = shareBtnShape,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "その他アプリで共有", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        // 5. 画像を保存 (アイコンのみ、統一ボタンスタイル)
                        Button(
                            onClick = {
                                val bitmap = generateShareCardBitmap(
                                    resortName = resortName,
                                    dateText = sessionDateText,
                                    maxSpeed = maxSpeed,
                                    maxEdge = maxEdge,
                                    totalDistKm = totalDistKm,
                                    totalRuns = totalRuns,
                                    elevDrop = totalDrop,
                                    aiStyle = aiReport.styleTitle,
                                    aiScore = aiReport.overallScore
                                )
                                saveCardImageToMediaStore(context, bitmap)
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = shareBtnBg),
                            border = shareBtnBorder,
                            shape = shareBtnShape,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "画像を保存", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstagramOriginalIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val stroke = 2.dp.toPx()
        // 外枠角丸四角
        drawRoundRect(
            color = Color.White,
            size = androidx.compose.ui.geometry.Size(size.width, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.5.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
        // 中央円
        drawCircle(
            color = Color.White,
            radius = size.width * 0.25f,
            center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
        // 右上点
        drawCircle(
            color = Color.White,
            radius = stroke * 0.75f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.22f)
        )
    }
}

@Composable
fun XLogoOriginalIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val s = size.width / 24f
        val p1 = androidx.compose.ui.graphics.Path().apply {
            moveTo(18.244f * s, 2.25f * s)
            lineTo(21.552f * s, 2.25f * s)
            lineTo(14.325f * s, 10.51f * s)
            lineTo(22.827f * s, 21.75f * s)
            lineTo(16.17f * s, 21.75f * s)
            lineTo(10.956f * s, 14.933f * s)
            lineTo(4.99f * s, 21.75f * s)
            lineTo(1.68f * s, 21.75f * s)
            lineTo(9.41f * s, 12.915f * s)
            lineTo(1.254f * s, 2.25f * s)
            lineTo(8.08f * s, 2.25f * s)
            lineTo(12.793f * s, 8.481f * s)
            close()
        }
        drawPath(p1, color = Color.White)
        val p2 = androidx.compose.ui.graphics.Path().apply {
            moveTo(17.083f * s, 19.77f * s)
            lineTo(18.916f * s, 19.77f * s)
            lineTo(7.084f * s, 4.126f * s)
            lineTo(5.117f * s, 4.126f * s)
            close()
        }
        drawPath(p2, color = Color(0xFF000000))
    }
}

@Composable
fun TikTokOriginalIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(19.dp)) {
        val s = size.width / 24f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(19.59f * s, 6.69f * s)
            cubicTo(18.2f * s, 5.2f * s, 16.5f * s, 3.5f * s, 15.82f * s, 2.0f * s)
            lineTo(12.37f * s, 2.0f * s)
            lineTo(12.37f * s, 15.67f * s)
            cubicTo(12.37f * s, 18.2f * s, 9.8f * s, 19.8f * s, 7.5f * s, 18.5f * s)
            cubicTo(5.5f * s, 17.2f * s, 5.2f * s, 14.5f * s, 6.8f * s, 13.0f * s)
            cubicTo(7.8f * s, 12.2f * s, 9.2f * s, 12.2f * s, 10.3f * s, 12.8f * s)
            lineTo(10.3f * s, 9.3f * s)
            cubicTo(5.2f * s, 9.2f * s, 3.2f * s, 14.0f * s, 4.8f * s, 18.0f * s)
            cubicTo(6.2f * s, 21.8f * s, 11.8f * s, 22.2f * s, 14.5f * s, 19.2f * s)
            cubicTo(15.5f * s, 18.0f * s, 15.82f * s, 16.8f * s, 15.82f * s, 15.2f * s)
            lineTo(15.82f * s, 8.5f * s)
            cubicTo(17.5f * s, 9.7f * s, 19.0f * s, 10.0f * s, 20.5f * s, 10.0f * s)
            lineTo(20.5f * s, 6.69f * s)
            close()
        }
        drawPath(path, color = Color.White)
    }
}

@Composable
fun ShareCardSquarePreview(
    resortName: String,
    dateText: String,
    maxSpeed: Float,
    maxEdge: Float,
    totalDistKm: Float,
    totalRuns: Int,
    elevDrop: Float,
    aiStyle: String,
    aiScore: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF090D16),
                        Color(0xFF0F172A),
                        Color(0xFF020617)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = NeonCyan.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "❄️ 極上パウダー",
                                color = NeonCyan,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(dateText, color = Color.Gray, fontSize = 8.sp)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = resortName,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("SNOWPULSE", color = NeonCyan, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.5.sp)
                    Text("AI TRACKER", color = Color.Gray, fontSize = 7.sp)
                }
            }

            // Middle Visual (GPS Track Line & AI badge)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.1f, h * 0.2f)
                        cubicTo(w * 0.4f, h * 0.25f, w * 0.2f, h * 0.5f, w * 0.55f, h * 0.55f)
                        cubicTo(w * 0.9f, h * 0.6f, w * 0.35f, h * 0.75f, w * 0.9f, h * 0.8f)
                    }
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = listOf(NeonCyan, SuccessEmerald, AccentAmber, TrickPurple)
                        ),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 5.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                    drawCircle(color = SuccessEmerald, radius = 3.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.2f))
                    drawCircle(color = TrickPurple, radius = 5.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w * 0.55f, h * 0.55f))
                    drawCircle(color = Color.White, radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w * 0.55f, h * 0.55f))
                    drawCircle(color = Color(0xFFEF4444), radius = 3.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w * 0.9f, h * 0.8f))
                }

                // AI Badge Top-Right
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TrickPurple.copy(alpha = 0.6f)),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), horizontalAlignment = Alignment.End) {
                        Text("⚡️ $aiStyle", color = Color(0xFF38BDF8), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text("Score $aiScore (A+)", color = TrickPurple, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }

                // Trick Badge Bottom-Left
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TrickPurple.copy(alpha = 0.4f)),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        text = "⭐ Ollie 180 (0.65s)",
                        color = Color(0xFFE9D5FF),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            // Bottom 4 Metrics Grid
            Surface(
                color = CardBackground.copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("最大エッジ角", color = Color.Gray, fontSize = 7.sp)
                        Text("%.0f°".format(maxEdge), color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("最高速度", color = Color.Gray, fontSize = 7.sp)
                        Text("%.1f km/h".format(maxSpeed), color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("総距離/本数", color = Color.Gray, fontSize = 7.sp)
                        Text("%.1fkm (%d)".format(totalDistKm, totalRuns), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("獲得標高", color = Color.Gray, fontSize = 7.sp)
                        Text("%.0fm".format(elevDrop), color = SuccessEmerald, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Footer
            Text(
                text = "POWERED BY SNOWPULSE • #SnowPulse",
                color = Color.DarkGray,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )
        }
    }
}

// 1080x1080 Bitmap Generation
private fun generateShareCardBitmap(
    resortName: String,
    dateText: String,
    maxSpeed: Float,
    maxEdge: Float,
    totalDistKm: Float,
    totalRuns: Int,
    elevDrop: Float,
    aiStyle: String,
    aiScore: Int
): android.graphics.Bitmap {
    val size = 1080
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Background Gradient
    val bgPaint = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(
            0f, 0f, 0f, size.toFloat(),
            intArrayOf(
                android.graphics.Color.parseColor("#090D16"),
                android.graphics.Color.parseColor("#0F172A"),
                android.graphics.Color.parseColor("#020617")
            ),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

    // Header
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 44f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    canvas.drawText(resortName, 60f, 110f, textPaint)

    val subPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#94A3B8")
        textSize = 22f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    canvas.drawText("$dateText  •  ❄️ 極上ドライパウダー", 60f, 150f, subPaint)

    // Brand Logo Top-Right
    val brandPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#22D3EE")
        textSize = 32f
        textAlign = android.graphics.Paint.Align.RIGHT
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    canvas.drawText("SNOWPULSE", size - 60f, 105f, brandPaint)
    subPaint.textAlign = android.graphics.Paint.Align.RIGHT
    canvas.drawText("AI TRACKER", size - 60f, 140f, subPaint)

    // Middle GPS Track Curve
    val path = android.graphics.Path().apply {
        moveTo(120f, 320f)
        cubicTo(380f, 340f, 200f, 480f, 540f, 520f)
        cubicTo(880f, 560f, 320f, 680f, 940f, 700f)
    }
    val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#3322D3EE")
        strokeWidth = 32f
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    canvas.drawPath(path, glowPaint)

    val trackPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.LinearGradient(
            120f, 320f, 940f, 700f,
            intArrayOf(
                android.graphics.Color.parseColor("#22D3EE"),
                android.graphics.Color.parseColor("#10B981"),
                android.graphics.Color.parseColor("#F59E0B"),
                android.graphics.Color.parseColor("#C084FC")
            ),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        strokeWidth = 12f
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }
    canvas.drawPath(path, trackPaint)

    // Markers
    val markerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    markerPaint.color = android.graphics.Color.parseColor("#10B981")
    canvas.drawCircle(120f, 320f, 14f, markerPaint)

    markerPaint.color = android.graphics.Color.parseColor("#C084FC")
    canvas.drawCircle(540f, 520f, 20f, markerPaint)
    markerPaint.color = android.graphics.Color.WHITE
    canvas.drawCircle(540f, 520f, 8f, markerPaint)

    markerPaint.color = android.graphics.Color.parseColor("#EF4444")
    canvas.drawCircle(940f, 700f, 14f, markerPaint)

    // Floating AI Badges
    val cardBgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E00F172A")
    }
    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
        color = android.graphics.Color.parseColor("#A855F7")
    }
    canvas.drawRoundRect(size - 380f, 240f, size - 60f, 350f, 16f, 16f, cardBgPaint)
    canvas.drawRoundRect(size - 380f, 240f, size - 60f, 350f, 16f, 16f, strokePaint)

    val badgeTitlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#38BDF8")
        textSize = 22f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    canvas.drawText("⚡️ $aiStyle", size - 355f, 280f, badgeTitlePaint)

    val badgeScorePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#C084FC")
        textSize = 34f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    canvas.drawText("Score $aiScore (A+)", size - 355f, 326f, badgeScorePaint)

    // Trick badge
    strokePaint.color = android.graphics.Color.parseColor("#70C084FC")
    canvas.drawRoundRect(60f, 680f, 380f, 740f, 14f, 14f, cardBgPaint)
    canvas.drawRoundRect(60f, 680f, 380f, 740f, 14f, 14f, strokePaint)
    badgeTitlePaint.color = android.graphics.Color.parseColor("#E9D5FF")
    badgeTitlePaint.textSize = 20f
    canvas.drawText("⭐ Ollie 180 (Air 0.65s)", 85f, 718f, badgeTitlePaint)

    // Bottom 4 Metrics Grid
    val metricsCardPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E60F172A")
    }
    val metricsBorderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
        color = android.graphics.Color.parseColor("#2E334155")
    }
    canvas.drawRoundRect(60f, 780f, size - 60f, 980f, 20f, 20f, metricsCardPaint)
    canvas.drawRoundRect(60f, 780f, size - 60f, 980f, 20f, 20f, metricsBorderPaint)

    val colW = (size - 120f) / 4f
    val startX = 60f

    val colLabelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#94A3B8")
        textSize = 18f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val colValPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 46f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
    }
    val colSubPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#94A3B8")
        textSize = 16f
        textAlign = android.graphics.Paint.Align.CENTER
    }

    // Col 1: Max Edge
    canvas.drawText("最大エッジ角", startX + colW * 0.5f, 830f, colLabelPaint)
    colValPaint.color = android.graphics.Color.parseColor("#F59E0B")
    canvas.drawText("%.0f°".format(maxEdge), startX + colW * 0.5f, 900f, colValPaint)
    canvas.drawText("Deep Turn", startX + colW * 0.5f, 940f, colSubPaint)

    // Col 2: Max Speed
    canvas.drawText("最高速度", startX + colW * 1.5f, 830f, colLabelPaint)
    colValPaint.color = android.graphics.Color.parseColor("#22D3EE")
    canvas.drawText("%.1f".format(maxSpeed), startX + colW * 1.5f, 900f, colValPaint)
    canvas.drawText("km/h", startX + colW * 1.5f, 940f, colSubPaint)

    // Col 3: Dist / Runs
    canvas.drawText("総ライド距離", startX + colW * 2.5f, 830f, colLabelPaint)
    colValPaint.color = android.graphics.Color.WHITE
    canvas.drawText("%.1fkm".format(totalDistKm), startX + colW * 2.5f, 900f, colValPaint)
    colSubPaint.color = android.graphics.Color.parseColor("#10B981")
    canvas.drawText("$totalRuns Runs", startX + colW * 2.5f, 940f, colSubPaint)

    // Col 4: Elev Drop
    canvas.drawText("獲得標高", startX + colW * 3.5f, 830f, colLabelPaint)
    colValPaint.color = android.graphics.Color.parseColor("#10B981")
    canvas.drawText("%.0fm".format(elevDrop), startX + colW * 3.5f, 900f, colValPaint)
    colSubPaint.color = android.graphics.Color.parseColor("#94A3B8")
    canvas.drawText("標高差", startX + colW * 3.5f, 940f, colSubPaint)

    // Footer
    val footerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#64748B")
        textSize = 18f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    canvas.drawText("POWERED BY SNOWPULSE  •  #SnowPulse", size / 2f, 1030f, footerPaint)

    return bitmap
}

private fun getCardImageUri(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap
): android.net.Uri? {
    // 1. キャッシュディレクトリに保存して FileProvider から取得
    try {
        val cachePath = java.io.File(context.cacheDir, "images")
        if (!cachePath.exists()) cachePath.mkdirs()
        val file = java.io.File(cachePath, "snowpulse_card.png")
        java.io.FileOutputStream(file).use { stream ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
        }

        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        if (uri != null) return uri
    } catch (e: Exception) {
        android.util.Log.e("SnowPulse", "FileProvider (cacheDir) failed: ${e.message}", e)
    }

    // 2. externalCacheDir フォールバック
    try {
        val extCache = context.externalCacheDir ?: context.cacheDir
        val imagesDir = java.io.File(extCache, "images")
        if (!imagesDir.exists()) imagesDir.mkdirs()
        val file = java.io.File(imagesDir, "snowpulse_card.png")
        java.io.FileOutputStream(file).use { stream ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
        }
        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        if (uri != null) return uri
    } catch (e: Exception) {
        android.util.Log.e("SnowPulse", "FileProvider (externalCacheDir) failed: ${e.message}", e)
    }

    // 3. MediaStore フォールバック (内部ストレージ共有Uri)
    try {
        val filename = "snowpulse_card_${System.currentTimeMillis()}.png"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                stream.flush()
            }
            return uri
        }
    } catch (e: Exception) {
        android.util.Log.e("SnowPulse", "MediaStore fallback failed: ${e.message}", e)
    }

    return null
}

// 𝕏 (Twitter) 専用共有（カード画像添付 + タグ付きポスト）
private fun shareCardToX(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    resortName: String,
    maxSpeed: Float,
    maxEdge: Float,
    aiStyle: String = "ディープ・カーバー"
) {
    val text = "本日 $resortName でライド完了！最高速 %.1fkm/h、エッジ角 %.0f° 🏂⚡️ AIスタイル: $aiStyle #SnowPulse #スノーボード".format(maxSpeed, maxEdge)
    val uri = getCardImageUri(context, bitmap)

    // クリップボードにもハッシュタグ付きテキストを自動コピー（万一のテキスト欠落対策）
    try {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("SnowPulse", text)
        clipboard?.setPrimaryClip(clip)
    } catch (_: Exception) {}

    if (uri != null) {
        val xPackages = listOf("com.twitter.android", "com.twitter.android.lite")
        for (pkg in xPackages) {
            try {
                context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
        }

        // Xアプリに直接カード画像とテキストを渡して投稿画面起動
        for (pkg in xPackages) {
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    setPackage(pkg)
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, text)
                    clipData = android.content.ClipData.newRawUri("SnowPulse Card", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }

        // Xアプリが明示パッケージで起動できなかった場合、画像添付Chooser起動
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                clipData = android.content.ClipData.newRawUri("SnowPulse Card", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "𝕏 (Twitter) で投稿"))
            return
        } catch (_: Exception) {}
    }

    // フォールバック: カード画像をメディア保存の上、Web版ポスト画面を開く
    try {
        saveCardImageToMediaStore(context, bitmap)
        val tweetUrl = "https://twitter.com/intent/tweet?text=" + java.net.URLEncoder.encode(text, "UTF-8")
        val webIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(tweetUrl))
        context.startActivity(webIntent)
        android.widget.Toast.makeText(context, "カード画像を保存しました。Xの投稿画面で画像を添付してください", android.widget.Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
        shareCardImageIntent(context, bitmap, resortName, maxSpeed, maxEdge)
    }
}

// Instagram 専用共有 (OS/Instagramデフォルトの画像共有インテント: ストーリーズ/フィード/DMの選択はInstagram側で実行)
private fun shareCardToInstagram(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    resortName: String,
    maxSpeed: Float,
    maxEdge: Float
) {
    val uri = getCardImageUri(context, bitmap)
    if (uri == null) {
        android.widget.Toast.makeText(context, "カード画像の生成に失敗しました", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    try {
        context.grantUriPermission("com.instagram.android", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: Exception) {}

    val caption = "本日 $resortName でライド完了！最高速 %.1fkm/h、エッジ角 %.0f° 🏂⚡️ #SnowPulse #スノーボード".format(maxSpeed, maxEdge)

    try {
        // OS/Instagramデフォルト動作: InstagramアプリのACTION_SEND画像インテントを直接起動
        val feedIntent = Intent(Intent.ACTION_SEND).apply {
            setPackage("com.instagram.android")
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            clipData = android.content.ClipData.newRawUri("SnowPulse Card", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(feedIntent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Instagramアプリが開けないため共有メニューを開きます", android.widget.Toast.LENGTH_SHORT).show()
        shareCardImageIntent(context, bitmap, resortName, maxSpeed, maxEdge)
    }
}

// TikTok 専用共有
private fun shareCardToTikTok(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    resortName: String,
    maxSpeed: Float,
    maxEdge: Float
) {
    val uri = getCardImageUri(context, bitmap)
    val packages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
    var launched = false
    if (uri != null) {
        for (pkg in packages) {
            try {
                context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
        }
    }

    for (pkg in packages) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                setPackage(pkg)
                if (uri != null) {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newRawUri("SnowPulse Card", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                }
            }
            context.startActivity(intent)
            launched = true
            break
        } catch (_: Exception) {}
    }
    if (!launched) {
        android.widget.Toast.makeText(context, "TikTokアプリが開けないため共有メニューを開きます", android.widget.Toast.LENGTH_SHORT).show()
        shareCardImageIntent(context, bitmap, resortName, maxSpeed, maxEdge)
    }
}

private fun shareCardImageIntent(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    resortName: String,
    maxSpeed: Float,
    maxEdge: Float
) {
    try {
        val uri = getCardImageUri(context, bitmap)
        if (uri != null) {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newRawUri("SnowPulse Card", uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "本日 $resortName でライド完了！最高速 %.1fkm/h、エッジ角 %.0f° 🏂⚡️ #SnowPulse #スノーボード".format(maxSpeed, maxEdge)
                )
            }
            context.startActivity(Intent.createChooser(shareIntent, "ライドカードを共有"))
        } else {
            throw Exception("Failed to get image uri")
        }
    } catch (e: Exception) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                "本日 $resortName でライド完了！最高速 %.1fkm/h、エッジ角 %.0f° 🏂⚡️ #SnowPulse".format(maxSpeed, maxEdge)
            )
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(shareIntent, "ライドカードを共有"))
    }
}

private fun saveCardImageToMediaStore(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap
) {
    try {
        val filename = "SnowPulse_${System.currentTimeMillis()}.png"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/SnowPulse")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                values.clear()
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            android.widget.Toast.makeText(context, "カード画像を保存しました", android.widget.Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "画像保存に失敗しました", android.widget.Toast.LENGTH_SHORT).show()
    }
}
