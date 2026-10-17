package com.snowpulse.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.snowpulse.app.data.model.SkiResort
import com.snowpulse.app.data.model.SkiResortData
import com.snowpulse.app.ui.theme.*

import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    currentLat: Double?,
    currentLng: Double?,
    isGpsEnabled: Boolean = true,
    isLocating: Boolean = false,
    onRefreshLocation: () -> Unit = {},
    onEnableGpsClick: () -> Unit = {},
    onResortSelectAndCheckIn: (SkiResort) -> Unit,
    onMockCheckIn: (String?) -> Unit,
    onBackClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    // 現在地から近い順に全国マスターからスキー場を取得
    val resorts = remember(context, currentLat, currentLng) {
        SkiResortData.getResortsNear(currentLat, currentLng, context)
    }

    val filteredResorts = remember(resorts, searchQuery) {
        if (searchQuery.isBlank()) {
            resorts
        } else {
            val q = searchQuery.trim()
            resorts.filter {
                it.name.contains(q, ignoreCase = true) ||
                (it.kana.isNotBlank() && it.kana.contains(q, ignoreCase = true)) ||
                (it.area.isNotBlank() && it.area.contains(q, ignoreCase = true)) ||
                (it.prefecture.isNotBlank() && it.prefecture.contains(q, ignoreCase = true))
            }
        }
    }

    val nearestResort = if (currentLat != null && currentLng != null) resorts.firstOrNull() else null
    var selectedResort by remember(nearestResort) { mutableStateOf(nearestResort) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("スキー場に check in", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("全国483ゲレンデ対応 • GPS自動検出", color = Color.Gray, fontSize = 11.sp)
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
        containerColor = DarkBackground,
        bottomBar = {
            Surface(
                color = CardBackground,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    val targetResort = selectedResort ?: nearestResort
                    Button(
                        onClick = {
                            if (targetResort != null) {
                                onResortSelectAndCheckIn(targetResort)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(14.dp),
                        enabled = targetResort != null
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (targetResort != null) "${targetResort.name} に check in" else "スキー場を選択してください",
                            color = Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { onMockCheckIn(targetResort?.name) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🎿 デモ走行データで check in (エミュレータ用)", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Search Input Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("スキー場名・エリアで検索...", color = Color.Gray, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )
            }

            // GPS状態および最寄りゲレンデの表示
            if (!isGpsEnabled) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "位置情報 (GPS) がオフです",
                                    color = AccentAmber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "現在地からの距離を計算するには端末のGPSを有効にしてください（距離は「- km」表示）",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onEnableGpsClick,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("有効化", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (currentLat == null || currentLng == null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E36)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLocating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = NeonCyan,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text("📍", fontSize = 20.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isLocating) "GPS位置情報を測位中..." else "GPS有効 (位置情報を取得中)",
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "高精度GPSで現在地を測位しています。最寄りスキー場を自動検出します",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = onRefreshLocation,
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("再取得", color = NeonCyan, fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else if (searchQuery.isBlank() && nearestResort != null) {
                item {
                    Text("📍 現在地から最も近いゲレンデ", color = SuccessEmerald, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedResort = nearestResort },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C2427)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            if (selectedResort?.id == nearestResort.id) SuccessEmerald else SuccessEmerald.copy(alpha = 0.5f)
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
                                        shape = RoundedCornerShape(8.dp),
                                        color = SuccessEmerald.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "最寄り検出 (GPS連動)",
                                            color = SuccessEmerald,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(nearestResort.area, color = Color.LightGray, fontSize = 11.sp)
                                }
                                Text(
                                    text = "ここから ${nearestResort.distanceDisplay}",
                                    color = NeonCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = nearestResort.name,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (nearestResort.elevationTopM != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = CardBackground
                                    ) {
                                        Text(
                                            text = "標高 ${nearestResort.elevationTopM}m",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (nearestResort.snowboardAllowed) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = NeonCyan.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "🏂 スノボ可",
                                            color = NeonCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (nearestResort.nightSkiing) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = AccentAmber.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "🌙 ナイター",
                                            color = AccentAmber,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) {
                            if (currentLat != null) "周辺のスキー場一覧 (距離順)" else "全国スキー場一覧 (全483件)"
                        } else "検索結果 (${filteredResorts.size}件)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (currentLat != null) "GPS連動中" else "GPS未取得 (- km)",
                        color = if (currentLat != null) SuccessEmerald else Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            if (filteredResorts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBackground)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("該当するスキー場が見つかりませんでした", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                items(filteredResorts, key = { it.id }) { resort ->
                    val isSelected = selectedResort?.id == resort.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedResort = resort },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF132338) else CardBackground
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) NeonCyan else Color(0xFF1E293B)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            if (isSelected) NeonCyan.copy(alpha = 0.2f) else Color(0xFF1E293B),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = resort.weatherIcon,
                                        fontSize = 16.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = resort.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (resort.elevationTopM != null) {
                                            Text(
                                                text = "標高 ${resort.elevationTopM}m",
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                        }
                                        if (resort.snowboardAllowed) {
                                            Text(
                                                text = "🏂 スノボ可",
                                                color = NeonCyan.copy(alpha = 0.85f),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (resort.nightSkiing) {
                                            Text(
                                                text = "🌙 ナイター",
                                                color = AccentAmber.copy(alpha = 0.85f),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = resort.distanceDisplay,
                                    color = if (isSelected) NeonCyan else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "${resort.tempC}°C",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
