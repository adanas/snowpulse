package com.snowpulse.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snowpulse.app.domain.engine.AiRideAnalysisReport
import com.snowpulse.app.ui.theme.*

@Composable
fun AiRideAnalysisCard(
    report: AiRideAnalysisReport,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131127)),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            Brush.linearGradient(
                colors = listOf(TrickPurple.copy(alpha = 0.8f), NeonCyan.copy(alpha = 0.5f))
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: AI Coach Badge & Title (2段組レイアウトで画面幅超過・崩れを根本防止)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = TrickPurple.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(9999.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TrickPurple.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "CHECKOUT AI COACH",
                            color = TrickPurple,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = TrickPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "チェックアウト総合 AIライディング分析レポート",
                        color = Color(0xFFE9D5FF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Style Title & Overall Score Banner
            Surface(
                color = CardBackground.copy(alpha = 0.8f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E234E))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "チェックアウト総合ライディングスタイル",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = report.styleTitle,
                            color = NeonCyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = report.styleSubtitle,
                            color = TrickPurple,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "総合AIスコア",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${report.overallScore}",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = TrickPurple,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 28.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "(${report.grade})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessEmerald,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4-Axis Skill Radar Chart (レーダーチャート)
            Surface(
                color = CardBackground.copy(alpha = 0.7f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TrickPurple.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("4軸スキルバランス (セッション全体)", color = Color.Gray, fontSize = 10.sp)
                        Text("RADAR ANALYSIS", color = TrickPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    RideRadarChart(
                        carving = report.carvingScore,
                        speed = report.speedControlScore,
                        stamina = report.staminaScore,
                        trick = report.trickScore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Coach Feedback Note
            Surface(
                color = CardBackground.copy(alpha = 0.9f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TrickPurple.copy(alpha = 0.35f))
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(54.dp)
                            .background(TrickPurple, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "💬 AI専属コーチのチェックアウト総括分析",
                            color = Color(0xFFD8B4FE),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = report.coachFeedback.replace("**", ""),
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // MVP Run & Next Step Tip Badges (2 columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // MVP Run
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF1E1B2E),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("セッション内ベストラン", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(report.mvpRunTitle, color = AccentAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(report.mvpRunDetail, color = Color.LightGray, fontSize = 9.sp)
                    }
                }

                // Next Step Tip
                Surface(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF10232E),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Flag, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("次回への課題", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(report.nextStepTipTitle, color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(report.nextStepTipDetail, color = Color.LightGray, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RideRadarChart(
    carving: Int,
    speed: Int,
    stamina: Int,
    trick: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = (size.minDimension / 2f) * 0.65f

            // Concentric Diamond Grids (25%, 50%, 75%, 100%)
            val gridLevels = listOf(0.25f, 0.50f, 0.75f, 1.0f)
            for (level in gridLevels) {
                val r = maxR * level
                val gridPath = Path().apply {
                    moveTo(center.x, center.y - r)     // Top
                    lineTo(center.x + r, center.y)     // Right
                    lineTo(center.x, center.y + r)     // Bottom
                    lineTo(center.x - r, center.y)     // Left
                    close()
                }
                drawPath(
                    path = gridPath,
                    color = if (level == 1.0f || level == 0.5f) Color(0xFF334155) else Color(0xFF1E293B),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = if (level == 1.0f || level == 0.5f) PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) else null
                    )
                )
            }

            // Cross Axis Lines
            drawLine(
                color = Color(0xFF334155),
                start = Offset(center.x, center.y - maxR),
                end = Offset(center.x, center.y + maxR),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0xFF334155),
                start = Offset(center.x - maxR, center.y),
                end = Offset(center.x + maxR, center.y),
                strokeWidth = 1.dp.toPx()
            )

            // Calculate Data Polygon Points
            val topP = center.y - maxR * (carving.coerceIn(0, 100) / 100f)
            val rightP = center.x + maxR * (speed.coerceIn(0, 100) / 100f)
            val bottomP = center.y + maxR * (stamina.coerceIn(0, 100) / 100f)
            val leftP = center.x - maxR * (trick.coerceIn(0, 100) / 100f)

            val dataPath = Path().apply {
                moveTo(center.x, topP)
                lineTo(rightP, center.y)
                lineTo(center.x, bottomP)
                lineTo(leftP, center.y)
                close()
            }

            // Fill Polygon with translucent purple-cyan gradient
            drawPath(
                path = dataPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        TrickPurple.copy(alpha = 0.55f),
                        NeonCyan.copy(alpha = 0.25f)
                    ),
                    center = center,
                    radius = maxR
                )
            )

            // Stroke Polygon
            drawPath(
                path = dataPath,
                color = TrickPurple,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Draw Vertex Points
            val points = listOf(
                Pair(Offset(center.x, topP), AccentAmber),
                Pair(Offset(rightP, center.y), NeonCyan),
                Pair(Offset(center.x, bottomP), SuccessEmerald),
                Pair(Offset(leftP, center.y), TrickPurple)
            )

            for ((pos, color) in points) {
                drawCircle(color = Color.White, radius = 5.dp.toPx(), center = pos)
                drawCircle(color = color, radius = 3.5.dp.toPx(), center = pos)
            }
        }

        // Top Label (Carving)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("カービング $carving", color = AccentAmber, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("エッジ角・キレ", color = Color.Gray, fontSize = 9.sp)
        }

        // Right Label (Speed)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text("速度 $speed", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("コントロール", color = Color.Gray, fontSize = 9.sp)
        }

        // Bottom Label (Stamina)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("スタミナ $stamina", color = SuccessEmerald, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("ペース配分", color = Color.Gray, fontSize = 9.sp)
        }

        // Left Label (Trick)
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text("トリック $trick", color = TrickPurple, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Text("アグレッシブ", color = Color.Gray, fontSize = 9.sp)
        }
    }
}
