package com.snowpulse.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.snowpulse.app.data.model.GeoPointEntity
import com.snowpulse.app.data.model.SkiResort
import com.snowpulse.app.data.model.SkiResortData
import com.snowpulse.app.data.model.TrickEventEntity
import com.snowpulse.app.ui.theme.*
import kotlin.math.*

@Composable
fun SkiSlopeTrackMap(
    points: List<GeoPointEntity>,
    tricks: List<TrickEventEntity> = emptyList(),
    resortName: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val matchedResort = remember(resortName, context) {
        if (resortName.isNotBlank() && resortName != "未チェックイン (滑走計測)" && resortName != "未チェックイン (ライド計測)") {
            SkiResortData.getMasterResorts(context).find { it.name == resortName || resortName.contains(it.name) }
        } else {
            null
        }
    }

    // 描画用データ：実際にGPSで記録された測位点のみを使用（架空のサンプル軌跡補完を完全撤廃）
    val displayPoints = points

    // インタラクティブ・スライダー位置 (0.0f .. 1.0f)
    var scrubberProgress by remember { mutableStateOf(1.0f) }
    val currentIndex = remember(scrubberProgress, displayPoints.size) {
        if (displayPoints.isNotEmpty()) {
            ((displayPoints.size - 1) * scrubberProgress).toInt().coerceIn(0, displayPoints.size - 1)
        } else {
            -1
        }
    }
    val currentInspectedPoint = if (currentIndex in displayPoints.indices) displayPoints[currentIndex] else null

    // コース名および標高情報の動的算出（「メインゲレンデ」等の不要な文言を排除し、スキー場名のみを表示）
    val courseTitle = remember(resortName, matchedResort) {
        when {
            matchedResort != null -> matchedResort.name
            resortName.isNotBlank() && resortName != "未チェックイン (滑走計測)" && resortName != "未チェックイン (ライド計測)" -> resortName.replace(" (デモ)", "")
            else -> "ライドコース"
        }
    }

    val elevationText = remember(displayPoints, matchedResort) {
        if (displayPoints.size >= 2) {
            val maxAlt = displayPoints.maxOf { it.altitudeMeters }.toInt()
            val minAlt = displayPoints.minOf { it.altitudeMeters }.toInt()
            val drop = (maxAlt - minAlt).coerceAtLeast(0)
            "標高差 ${maxAlt}m → ${minAlt}m (${drop}m)"
        } else if (displayPoints.size == 1) {
            "現在標高 ${displayPoints[0].altitudeMeters.toInt()}m (未ライド)"
        } else if (matchedResort?.elevationTopM != null) {
            "山頂標高 ${matchedResort.elevationTopM}m (未ライド)"
        } else {
            "標高差 0m (未ライド)"
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1526)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Resort Course Badge & Compass
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NeonCyan.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "GPS TRACKING",
                                color = NeonCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = courseTitle,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = elevationText,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                // Compass Indicator (North)
                Surface(
                    shape = CircleShape,
                    color = CardBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray)
                ) {
                    Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "North",
                            tint = AccentAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Map Canvas (Ski Slope & Track Line)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Color(0xFF080E1C), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    val width = size.width
                    val height = size.height

                    // 1. Draw Piste Slope Background & Contours (ゲレンデの斜面・等高線)
                    // 1. Draw Piste Slope Background & Contours (ゲレンデの斜面・等高線)
                    drawSlopeBackground(width, height)

                    // 2. Normalize Points to Canvas Coordinates (実測点が2点以上ある場合のみ滑走軌跡を描画)
                    if (displayPoints.size >= 2) {
                        val minLat = displayPoints.minOf { it.latitude }
                        val maxLat = displayPoints.maxOf { it.latitude }
                        val minLng = displayPoints.minOf { it.longitude }
                        val maxLng = displayPoints.maxOf { it.longitude }

                        val latRange = (maxLat - minLat).coerceAtLeast(0.0001)
                        val lngRange = (maxLng - minLng).coerceAtLeast(0.0001)

                        val mappedOffsets = displayPoints.map { p ->
                            val nx = ((p.longitude - minLng) / lngRange).toFloat()
                            val ny = (1.0f - ((p.latitude - minLat) / latRange).toFloat())
                            Offset(
                                x = 20f + nx * (width - 40f),
                                y = 20f + ny * (height - 40f)
                            )
                        }

                        // 3. Draw Track Line with Speed Gradient Segments
                        drawGpsTrackLine(mappedOffsets, displayPoints)

                        // 4. Draw Start & Goal Markers
                        if (mappedOffsets.isNotEmpty()) {
                            val startPos = mappedOffsets.first()
                            val goalPos = mappedOffsets.last()

                            drawCircle(color = SuccessEmerald.copy(alpha = 0.4f), radius = 14f, center = startPos)
                            drawCircle(color = SuccessEmerald, radius = 7f, center = startPos)

                            drawCircle(color = Color(0xFFEF4444).copy(alpha = 0.4f), radius = 14f, center = goalPos)
                            drawCircle(color = Color(0xFFEF4444), radius = 7f, center = goalPos)
                        }

                        // 5. Draw Trick Pins (if any)
                        tricks.forEachIndexed { i, trick ->
                            val targetIndex = (displayPoints.size / (tricks.size + 1)) * (i + 1)
                            if (targetIndex in mappedOffsets.indices) {
                                val trickPos = mappedOffsets[targetIndex]
                                drawCircle(color = TrickPurple.copy(alpha = 0.5f), radius = 16f, center = trickPos)
                                drawCircle(color = TrickPurple, radius = 8f, center = trickPos)
                                drawCircle(color = Color.White, radius = 3f, center = trickPos)
                            }
                        }

                        // 6. Draw Current Inspected Rider Marker
                        if (currentIndex in mappedOffsets.indices) {
                            val activePos = mappedOffsets[currentIndex]
                            drawCircle(color = NeonCyan.copy(alpha = 0.6f), radius = 18f, center = activePos)
                            drawCircle(color = Color.White, radius = 9f, center = activePos)
                            drawCircle(color = NeonCyan, radius = 5f, center = activePos)
                        }
                    } else if (displayPoints.size == 1) {
                        // 1点（チェックイン位置等）のみの場合
                        val singlePos = Offset(width / 2f, height / 2f)
                        drawCircle(color = NeonCyan.copy(alpha = 0.35f), radius = 20f, center = singlePos)
                        drawCircle(color = NeonCyan, radius = 8f, center = singlePos)
                    }
                }

                // Overlay Labels
                if (displayPoints.size >= 2) {
                    Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Surface(
                            color = Color(0xFF0F172A).copy(alpha = 0.85f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).background(SuccessEmerald, CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("START (山頂)", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            color = Color(0xFF0F172A).copy(alpha = 0.85f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.align(Alignment.BottomEnd)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).background(Color(0xFFEF4444), CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("GOAL (山麓)", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // 未ライド（GPSライドログなし）オーバーレイ
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            color = Color(0xFF0F172A).copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("📍 GPSライドデータなし (未ライド)", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("ライドを開始すると実際の軌跡が描画されます", color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Playback / Point Inspector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ライド軌跡シークバー (地点アナライザー)", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = "進捗 %.0f%%".format(scrubberProgress * 100f),
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Scrubber Slider
                    Slider(
                        value = scrubberProgress,
                        onValueChange = { scrubberProgress = it },
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )

                    // Point Metrics at Current Scrubber Position
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        val inspectedSpeed = currentInspectedPoint?.speedKmh ?: 0f
                        val inspectedEdge = currentInspectedPoint?.edgeAngleDeg ?: 0f
                        val inspectedAltText = currentInspectedPoint?.let { "%.0f m".format(it.altitudeMeters) } ?: "- m"

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("通過速度", color = Color.Gray, fontSize = 10.sp)
                            Text(
                                text = "%.1f km/h".format(inspectedSpeed),
                                color = if (inspectedSpeed > 40f) AccentAmber else NeonCyan,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("推定エッジ角", color = Color.Gray, fontSize = 10.sp)
                            Text(
                                text = "%.0f°".format(inspectedEdge),
                                color = AccentAmber,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("標高", color = Color.Gray, fontSize = 10.sp)
                            Text(
                                text = inspectedAltText,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Legend / Color Scale Indicator
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(NeonCyan, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("低速(〜25km/h)", color = Color.Gray, fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(SuccessEmerald, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("巡航(25〜40km/h)", color = Color.Gray, fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(AccentAmber, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("高速(40km/h〜)", color = Color.Gray, fontSize = 10.sp)
                }
            }
        }
    }
}

// Draw Slope Topography & Contour Lines
private fun DrawScope.drawSlopeBackground(width: Float, height: Float) {
    // ゲレンデコースの緩やかな蛇行境界線（ピスト境界）
    val pistePath = Path().apply {
        moveTo(width * 0.15f, 0f)
        cubicTo(width * 0.05f, height * 0.35f, width * 0.25f, height * 0.7f, width * 0.1f, height)
        lineTo(width * 0.9f, height)
        cubicTo(width * 0.95f, height * 0.65f, width * 0.75f, height * 0.3f, width * 0.85f, 0f)
        close()
    }
    drawPath(pistePath, color = Color(0xFF0F1A30).copy(alpha = 0.7f))

    // Contour Lines (等高線)
    for (i in 1..4) {
        val y = height * (i / 5.0f)
        val contour = Path().apply {
            moveTo(0f, y)
            quadraticBezierTo(width * 0.5f, y + 15f * sin(i.toFloat()), width, y - 10f)
        }
        drawPath(
            contour,
            color = Color(0xFF1E2E4E).copy(alpha = 0.5f),
            style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
        )
    }

    // Chairlift Wire (リフト線)
    val liftLine = Path().apply {
        moveTo(width * 0.88f, 10f)
        lineTo(width * 0.92f, height - 10f)
    }
    drawPath(
        liftLine,
        color = Color(0xFF475569).copy(alpha = 0.6f),
        style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
    )
}

// Draw GPS Track Line with dynamic speed gradient
private fun DrawScope.drawGpsTrackLine(
    points: List<Offset>,
    entities: List<GeoPointEntity>
) {
    if (points.size < 2) return

    for (i in 0 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        val speed = entities[i].speedKmh

        val segmentColor = when {
            speed > 42.0f -> AccentAmber // 赤・オレンジ (高速)
            speed > 25.0f -> SuccessEmerald // グリーン (巡航)
            else -> NeonCyan // シアン (低速・ターン導入)
        }

        // 1. Glow effect line (Outer soft line)
        drawLine(
            color = segmentColor.copy(alpha = 0.3f),
            start = p1,
            end = p2,
            strokeWidth = 10f,
            cap = StrokeCap.Round
        )

        // 2. Core crisp track line
        drawLine(
            color = segmentColor,
            start = p1,
            end = p2,
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
    }
}
