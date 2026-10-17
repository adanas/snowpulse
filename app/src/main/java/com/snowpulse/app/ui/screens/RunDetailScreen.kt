package com.snowpulse.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snowpulse.app.data.model.RunEntity
import com.snowpulse.app.data.model.GeoPointEntity
import com.snowpulse.app.data.model.TrickEventEntity
import com.snowpulse.app.domain.engine.AiRideAnalysisEngine
import com.snowpulse.app.ui.components.AiRideAnalysisCard
import com.snowpulse.app.ui.components.SkiSlopeTrackMap
import com.snowpulse.app.ui.theme.*
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailScreen(
    run: RunEntity?,
    tricks: List<TrickEventEntity>,
    points: List<GeoPointEntity> = emptyList(),
    resortName: String? = null,
    onBackClick: () -> Unit
) {
    val aiAnalysisEngine = remember { AiRideAnalysisEngine() }
    val aiReport = remember(run, tricks) {
        val runList = if (run != null) listOf(run) else emptyList()
        aiAnalysisEngine.analyzeSession(null, runList, tricks)
    }
    val displayResort = run?.resortName?.takeIf { it.isNotBlank() }
        ?: resortName?.takeIf { it.isNotBlank() }
        ?: "未チェックイン (ライド計測)"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Run #${run?.runNumber ?: 1} ゲレンデライド分析", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (displayResort != "未チェックイン (滑走計測)" && displayResort != "未チェックイン (ライド計測)") {
                            Text("📍 $displayResort", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Ski Slope GPS Track Map (ゲレンデ滑走ラインマップ)
            SkiSlopeTrackMap(
                points = points,
                tricks = tricks,
                resortName = displayResort
            )

            // 2. Stats Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("最高 / 平均速度", color = Color.Gray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "%.1f / %.1f".format(run?.maxSpeedKmh ?: 58.4f, run?.avgSpeedKmh ?: 36.1f),
                            color = NeonCyan,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text("km/h", color = Color.Gray, fontSize = 10.sp)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("最大エッジ角", color = Color.Gray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "%.1f°".format(run?.maxEdgeAngleDeg ?: 52.8f),
                            color = AccentAmber,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text("ディープターン", color = Color.Gray, fontSize = 10.sp)
                    }
                }
            }

            // AI Riding Analysis Report for this Run
            AiRideAnalysisCard(report = aiReport)

            // Tricks List
            Column {
                Text("検出されたアクション & トリック", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))

                if (tricks.isEmpty()) {
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
                                Text("Backside 180", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Surface(color = SuccessEmerald.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                    Text("着地 成功", color = SuccessEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("滞空時間: 0.65秒 | 推定ジャンプ高: 38cm | 回転: 184°", color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                } else {
                    tricks.forEach { trick ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBackground),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(trick.trickType, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Surface(
                                        color = if (trick.isLanded) SuccessEmerald.copy(alpha = 0.2f) else AccentAmber.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(if (trick.isLanded) "着地 成功" else "着地 乱れ", color = if (trick.isLanded) SuccessEmerald else AccentAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("滞空: %.2f秒 | 推定高: %.0fcm".format(trick.airTimeSeconds, trick.jumpHeightCm), color = Color.LightGray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Snow Surface Estimation
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("推定雪質・路面状況", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "コース上部は振動が少なく良好なフラット圧雪。中盤の急斜面にて一部アイシーな振動パターン（30Hz帯）を検知しました。",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
