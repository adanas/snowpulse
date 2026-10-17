package com.snowpulse.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snowpulse.app.data.model.PhonePlacement
import com.snowpulse.app.data.model.Stance
import com.snowpulse.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentStance: Stance,
    currentPlacement: PhonePlacement,
    onStanceChange: (Stance) -> Unit,
    onPlacementChange: (PhonePlacement) -> Unit,
    onDebugClick: () -> Unit = {},
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ライディング & ギア設定", color = Color.White, fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section: Stance
            Column {
                Text("スタンス (前足の向き)", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StanceButton(
                        title = "Regular",
                        subtitle = "左足が前",
                        isSelected = currentStance == Stance.REGULAR,
                        modifier = Modifier.weight(1f),
                        onClick = { onStanceChange(Stance.REGULAR) }
                    )
                    StanceButton(
                        title = "Goofy",
                        subtitle = "右足が前",
                        isSelected = currentStance == Stance.GOOFY,
                        modifier = Modifier.weight(1f),
                        onClick = { onStanceChange(Stance.GOOFY) }
                    )
                }
            }

            // Section: Phone Placement
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("スマホの収納位置", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("エッジ角精度に影響", color = AccentAmber, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))

                PlacementOptionCard(
                    title = "パンツのポケット (前足側)",
                    description = "ボードの角付け（エッジ角）と完全に連動し、トリック・ターン解析が最も正確に動作します。",
                    tag = "推奨・最高精度",
                    tagColor = SuccessEmerald,
                    isSelected = currentPlacement == PhonePlacement.PANTS_FRONT,
                    onClick = { onPlacementChange(PhonePlacement.PANTS_FRONT) }
                )
                Spacer(modifier = Modifier.height(8.dp))

                PlacementOptionCard(
                    title = "パンツのポケット (後足側)",
                    description = "エッジ角・ジャンプともに高精度で取得可能です。",
                    isSelected = currentPlacement == PhonePlacement.PANTS_BACK,
                    onClick = { onPlacementChange(PhonePlacement.PANTS_BACK) }
                )
                Spacer(modifier = Modifier.height(8.dp))

                PlacementOptionCard(
                    title = "ジャケットの胸ポケット",
                    description = "上半身の傾き（体幹角）として参考表示。スピン判定は簡易モードになります。",
                    isSelected = currentPlacement == PhonePlacement.CHEST,
                    onClick = { onPlacementChange(PhonePlacement.CHEST) }
                )
                Spacer(modifier = Modifier.height(8.dp))

                PlacementOptionCard(
                    title = "リュック / ヒップバッグ",
                    description = "速度・距離・高度・リフト判定のみ利用（姿勢解析OFF）。",
                    isSelected = currentPlacement == PhonePlacement.BACKPACK,
                    onClick = { onPlacementChange(PhonePlacement.BACKPACK) }
                )
            }

            // Calibration Note
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = SuccessEmerald, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "滑走開始時の直線加速と重力から端末の傾きを自動キャリブレーションするため、ポケット内で斜めに入っていても問題ありません。",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            // 🛠️ デバッグ・開発者メニュー
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDebugClick() },
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🛠️", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("デバッグ・開発者コンソール", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("GPS偽装・即時滑走生成・ログ・DB初期化", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Text("OPEN ›", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Button(
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("設定を保存して戻る", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun StanceButton(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        color = if (isSelected) NeonCyan.copy(alpha = 0.15f) else CardBackground,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isSelected) NeonCyan else Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = if (isSelected) NeonCyan else Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PlacementOptionCard(
    title: String,
    description: String,
    tag: String? = null,
    tagColor: Color = NeonCyan,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                1.5.dp,
                if (isSelected) NeonCyan else Color.Transparent,
                RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                tag?.let {
                    Surface(
                        color = tagColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(it, color = tagColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, color = Color.LightGray, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
