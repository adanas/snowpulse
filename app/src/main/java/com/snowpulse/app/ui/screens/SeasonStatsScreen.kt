package com.snowpulse.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snowpulse.app.data.model.SeasonStats
import com.snowpulse.app.data.model.VisitedResortStat
import com.snowpulse.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonStatsScreen(
    stats: SeasonStats,
    availableSeasons: List<String>,
    selectedSeasonKey: String,
    onSeasonSelect: (String) -> Unit,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit = {}
) {
    var expandedDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "シーズンライド記録",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "戻る",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // シーズンセレクター（ドロップダウン）
                    Box {
                        Surface(
                            onClick = { expandedDropdown = true },
                            shape = RoundedCornerShape(12.dp),
                            color = CardBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "$selectedSeasonKey Season",
                                    color = NeonCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "シーズン選択",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false },
                            modifier = Modifier.background(CardBackground)
                        ) {
                            availableSeasons.forEach { seasonKey ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "$seasonKey Season",
                                            color = if (seasonKey == selectedSeasonKey) NeonCyan else Color.White,
                                            fontWeight = if (seasonKey == selectedSeasonKey) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        expandedDropdown = false
                                        onSeasonSelect(seasonKey)
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 4大必須メトリクス + 総ライド本数 ヒーローカード
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonCyan.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SEASON RIDE SUMMARY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonCyan,
                                letterSpacing = 1.2.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 上段2列: 総ライド距離 & 最高速度 (大きく表示)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("総ライド距離", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "%.1f".format(stats.totalDistanceMeters / 1000f),
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = NeonCyan
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("km", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonCyan, modifier = Modifier.padding(bottom = 3.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("最高速度", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "%.1f".format(stats.maxSpeedKmh),
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = AccentAmber
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("km/h", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentAmber, modifier = Modifier.padding(bottom = 3.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // 下段3列等間隔: 獲得標高 / チェックイン回数 / 総ライド本数
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 獲得標高
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text("獲得標高", color = Color(0xFF94A3B8), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "%,.0f m".format(stats.totalElevationDropMeters),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                            }
                            // チェックイン回数
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("チェックイン", color = Color(0xFF94A3B8), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${stats.totalCheckIns} 回",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = SuccessEmerald
                                )
                            }
                            // 総ライド本数
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("総ライド本数", color = Color(0xFF94A3B8), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${stats.totalRunsCount} Runs",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TrickPurple
                                )
                            }
                        }
                    }
                }
            }

            // 2. 記録 セクション
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Landscape,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "記録 (${stats.uniqueResortsCount})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }

            if (stats.visitedResorts.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CardBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🏂", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "このシーズンのライド記録はまだありません",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "スキー場にチェックインしてライドを開始しましょう！",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                items(stats.visitedResorts) { resortStat ->
                    VisitedResortCard(resortStat = resortStat)
                }
            }

            // 4. シーズン総括 シェアボタン
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onShareClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "シーズンライド実績を共有",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun VisitedResortCard(resortStat: VisitedResortStat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (resortStat.isHomeResort) Color(0xFF132338) else CardBackground
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (resortStat.isHomeResort) 1.5.dp else 1.dp,
            if (resortStat.isHomeResort) AccentAmber else GlassBorderSubtle
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = if (resortStat.isHomeResort) AccentAmber else NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = resortStat.resortName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
                if (resortStat.isHomeResort) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentAmber.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("🏆", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "HOME",
                                color = AccentAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // チェックイン回数
                Column {
                    Text("チェックイン", color = Color.Gray, fontSize = 10.5.sp)
                    Text(
                        text = "${resortStat.checkInCount} 回",
                        color = SuccessEmerald,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // 滑走距離
                Column {
                    Text("滑走距離", color = Color.Gray, fontSize = 10.5.sp)
                    Text(
                        text = "%.1f km".format(resortStat.totalDistanceMeters / 1000f),
                        color = NeonCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                // 最高速度
                Column(horizontalAlignment = Alignment.End) {
                    Text("最高速度", color = Color.Gray, fontSize = 10.5.sp)
                    Text(
                        text = "%.1f km/h".format(resortStat.maxSpeedKmh),
                        color = AccentAmber,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
