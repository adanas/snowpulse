package com.snowpulse.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snowpulse.app.data.model.TrackingState
import com.snowpulse.app.service.TrackingLiveStats
import com.snowpulse.app.ui.theme.*

@Composable
fun TrackingScreen(
    stats: TrackingLiveStats,
    onPauseClick: () -> Unit,
    onCheckOutClick: () -> Unit,
    onBackClick: () -> Unit,
    onToggleAudioCoach: () -> Unit = {}
) {
    var showCheckOutDialog by remember { mutableStateOf(false) }
    val isPaused = stats.isPaused || stats.state == TrackingState.PAUSED_RUN

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Status & Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                colors = IconButtonDefaults.iconButtonColors(containerColor = CardBackground)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            val stateText = when {
                isPaused -> "休憩中"
                stats.state == TrackingState.SKIING -> "ライド中 (RUN #${stats.runNumber})"
                stats.state == TrackingState.RIDING_LIFT -> "リフト搭乗中"
                else -> "スタンバイ待機中"
            }
            val stateIcon = when {
                isPaused -> Icons.Default.Coffee
                stats.state == TrackingState.SKIING -> Icons.Default.DownhillSkiing
                stats.state == TrackingState.RIDING_LIFT -> Icons.Default.AirlineSeatReclineExtra
                else -> Icons.Default.PauseCircle
            }
            val stateColor = when {
                isPaused -> AccentAmber
                stats.state == TrackingState.SKIING -> SuccessEmerald
                stats.state == TrackingState.RIDING_LIFT -> NeonCyan
                else -> Color.LightGray
            }

            Surface(
                color = stateColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, stateColor.copy(alpha = 0.5f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = stateIcon,
                        contentDescription = null,
                        tint = stateColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stateText,
                        color = stateColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            IconButton(
                onClick = onToggleAudioCoach,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (stats.isAudioCoachEnabled) TrickPurple.copy(alpha = 0.25f) else CardBackground
                )
            ) {
                Icon(
                    imageVector = if (stats.isAudioCoachEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = "Audio Coach Toggle",
                    tint = if (stats.isAudioCoachEnabled) NeonCyan else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Giant Speed Display (滑走中以外はハイフン表示)
        val isSkiing = !isPaused && stats.state == TrackingState.SKIING
        val displaySpeedText = if (isSkiing) "%.1f".format(stats.currentSpeedKmh) else "-"

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "CURRENT SPEED",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = displaySpeedText,
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = if (isSkiing) NeonCyan else Color.Gray
                )
                Text(
                    text = " km/h",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSkiing) Color.LightGray else Color.DarkGray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Text(
                text = "本日の最高速: %.1f km/h".format(stats.maxSpeedKmh),
                color = SuccessEmerald,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Motion & Trick Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Edge Angle Meter Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (isPaused) "休憩中 (停止)" else "推定エッジ角",
                        color = if (isPaused) AccentAmber else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = if (isPaused) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPaused) "--°" else "%.0f°".format(stats.currentEdgeAngle),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaused) Color.Gray else AccentAmber,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (isPaused) 0f else (stats.currentEdgeAngle / 60f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = if (isPaused) Color.DarkGray else AccentAmber,
                        trackColor = Color.DarkGray,
                    )
                }
            }

            // Latest Trick Detected Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("直近トリック", color = Color.Gray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPaused) "検出停止中" else (stats.latestTrick ?: "検出待ち"),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaused) Color.Gray else TrickPurple
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPaused) "センサー停止中" else "AIモーション自動判定",
                        color = if (isPaused) Color.Gray else SuccessEmerald,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Secondary Stats (Elevation & Distance)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBackground.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("現在標高", color = Color.Gray, fontSize = 10.sp)
                    Text("%.0fm".format(stats.altitudeMeters), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("総ライド距離", color = Color.Gray, fontSize = 10.sp)
                    Text("%.1fkm".format(stats.distanceMeters / 1000f), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Glove Friendly Giant Buttons
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPauseClick,
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaused) SuccessEmerald else AccentAmber
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Coffee,
                        contentDescription = null,
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPaused) "ライド再開" else "休憩する",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                Button(
                    onClick = { showCheckOutDialog = true },
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("チェックアウト", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "※お食事・ロッジ滞在時は「休憩する」で無駄な記録をストップできます",
                color = Color.DarkGray,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

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
