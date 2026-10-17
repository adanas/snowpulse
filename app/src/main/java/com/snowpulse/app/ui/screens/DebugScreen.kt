package com.snowpulse.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snowpulse.app.data.model.TrackingState
import com.snowpulse.app.ui.theme.*
import com.snowpulse.app.util.AppLogger
import com.snowpulse.app.util.LogLevel

data class TeleportPreset(
    val name: String,
    val lat: Double,
    val lng: Double,
    val note: String = ""
)

val DEBUG_TELEPORT_PRESETS = listOf(
    TeleportPreset("津南", 36.95265, 138.68827, "ニューグリーンピア津南"),
    TeleportPreset("川場", 36.7562, 139.1123, "川場スキー場"),
    TeleportPreset("野沢温泉", 36.9234, 138.4456, "野沢温泉スキー場"),
    TeleportPreset("丸沼高原", 36.8123, 139.3456, "丸沼高原スキー場"),
    TeleportPreset("白馬八方", 36.7023, 137.8456, "白馬八方尾根"),
    TeleportPreset("苗場", 36.7912, 138.7845, "苗場スキー場"),
    TeleportPreset("🏙️ 東京駅", 35.6812, 139.7671, "市街地 (150km遠隔)")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    currentLat: Double?,
    currentLng: Double?,
    isGpsMockDisabled: Boolean,
    onToggleMockGpsDisabled: () -> Unit,
    onTeleport: (lat: Double, lng: Double, name: String) -> Unit,
    onCheckInMock: (resortName: String) -> Unit = {},
    onCreateQuickRun: () -> Unit,
    onCreateUnriddenSession: () -> Unit,
    onForceState: (TrackingState) -> Unit,
    onInjectSeedRuns: () -> Unit,
    onTestTts: (String) -> Unit,
    onClearDatabase: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showResetDialog by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf(DEBUG_TELEPORT_PRESETS[0]) }

    val logs by AppLogger.logs.collectAsState()
    val logListState = rememberLazyListState()

    // Auto scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logListState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🛠️ デバッグコンソール", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==========================================
            // モジュール 1: 位置情報 & GPSステートシミュレータ
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("📍 1. 位置情報 & GPSステートシミュレータ", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // GPS Toggle (ON / 疑似OFF)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("疑似GPS状態", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    if (isGpsMockDisabled) "🔴 疑似GPS無効中 (ハイフン表示/ダイアログ検証)" else "🟢 GPS正常有効中",
                                    color = if (isGpsMockDisabled) AccentAmber else SuccessEmerald,
                                    fontSize = 11.sp
                                )
                            }
                            Button(
                                onClick = {
                                    onToggleMockGpsDisabled()
                                    AppLogger.i("DEBUG", "GPSトグル切替: 無効状態=${!isGpsMockDisabled}")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isGpsMockDisabled) SuccessEmerald else Color(0xFFDC2626)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(if (isGpsMockDisabled) "GPS有効化" else "GPS無効化", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Current Coordinates
                        Text(
                            text = "現在地: %s".format(
                                if (currentLat != null && currentLng != null) "%.4f, %.4f".format(currentLat, currentLng) else "未測位"
                            ),
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Presets Chips
                        Text("テレポート先を選択:", color = Color.Gray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DEBUG_TELEPORT_PRESETS.take(4).forEach { preset ->
                                val isSelected = selectedPreset.name == preset.name
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedPreset = preset },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) NeonCyan.copy(alpha = 0.2f) else DarkBackground,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) NeonCyan else Color.Gray.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Text(
                                        text = preset.name,
                                        color = if (isSelected) NeonCyan else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DEBUG_TELEPORT_PRESETS.drop(4).forEach { preset ->
                                val isSelected = selectedPreset.name == preset.name
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedPreset = preset },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) NeonCyan.copy(alpha = 0.2f) else DarkBackground,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) NeonCyan else Color.Gray.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Text(
                                        text = preset.name,
                                        color = if (isSelected) NeonCyan else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                onTeleport(selectedPreset.lat, selectedPreset.lng, selectedPreset.note)
                                AppLogger.i("GPS", "テレポート＆チェックイン実行: ${selectedPreset.note} (${selectedPreset.lat}, ${selectedPreset.lng})")
                                Toast.makeText(context, "🚀 ${selectedPreset.note}にテレポート＆チェックインしました", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("🚀 ${selectedPreset.note}にテレポート＆チェックイン", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                onCheckInMock(selectedPreset.note)
                                AppLogger.i("DEMO", "デモモードチェックイン実行: ${selectedPreset.note}")
                                Toast.makeText(context, "🎿 ${selectedPreset.note}でデモチェックインしました", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonCyan.copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("🎿 デモモードで check in (${selectedPreset.note})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ==========================================
            // モジュール 2: セッション状態・即時シミュレータ
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("⏱️ 2. セッション状態・即時シミュレータ", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Run vs Unridden Session
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onCreateQuickRun()
                                    AppLogger.i("SESSION", "1本滑走完了状態を即時注入 (2.1km / 54.2km/h)")
                                    Toast.makeText(context, "滑走1本完了状態を注入しました", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessEmerald),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("⛷️ 1本滑走完了注入\n(2.1km / 54km/h)", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }

                            Button(
                                onClick = {
                                    onCreateUnriddenSession()
                                    AppLogger.i("SESSION", "未滑走セッション状態を作成 (0.0km / 0本)")
                                    Toast.makeText(context, "未滑走セッションを作成しました", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64748B)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("⚠️ 未滑走状態作成\n(0.0km / 0本)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("ステート強制変更:", color = Color.Gray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "⛷️ 滑走" to TrackingState.SKIING,
                                "🚡 リフト" to TrackingState.RIDING_LIFT,
                                "⏸️ 休憩" to TrackingState.PAUSED_RUN,
                                "⏹️ 待機" to TrackingState.IDLE
                            ).forEach { (label, state) ->
                                OutlinedButton(
                                    onClick = {
                                        onForceState(state)
                                        AppLogger.i("STATE", "ステート強制遷移: $state")
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(label, fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // モジュール 3: テスト用シードデータ一括注入
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("📦 3. テスト用シードデータ投入", color = Color(0xFFA855F7), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("過去3日分・8本のラン履歴（津南・川場・丸沼、GPS軌跡・日付付）を一括生成します。", color = Color.Gray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                onInjectSeedRuns()
                                AppLogger.i("DB", "テスト用シードデータ一括投入完了 (8 Runs)")
                                Toast.makeText(context, "8本のラン履歴を投入しました", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("📥 サンプル滑走履歴を一括生成 (8 Runs)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // ==========================================
            // モジュール 4: 音声アナウンス・TTSテスター
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("🔊 4. 音声アナウンス・TTSテスター", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    val msg = "ニューグリーンピア津南にチェックインしました。自動滑走計測を開始します"
                                    onTestTts(msg)
                                    AppLogger.i("TTS", msg)
                                },
                                modifier = Modifier.weight(1f).height(38.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("📣 チェックイン", fontSize = 11.sp, color = Color.White)
                            }

                            Button(
                                onClick = {
                                    val msg = "1本目終了。最高速度は時速54キロでした"
                                    onTestTts(msg)
                                    AppLogger.i("TTS", msg)
                                },
                                modifier = Modifier.weight(1f).height(38.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("📣 滑走終了", fontSize = 11.sp, color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    val msg = "オーリー 成功！"
                                    onTestTts(msg)
                                    AppLogger.i("TTS", msg)
                                },
                                modifier = Modifier.weight(1f).height(38.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("📣 トリック成功", fontSize = 11.sp, color = Color.White)
                            }

                            Button(
                                onClick = {
                                    val msg = "チェックアウトしました。お疲れ様でした！本日は2本の滑走でした"
                                    onTestTts(msg)
                                    AppLogger.i("TTS", msg)
                                },
                                modifier = Modifier.weight(1f).height(38.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("📣 チェックアウト", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // モジュール 5: アプリ内リアルタイムログ確認
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF020617)),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📜 5. リアルタイムログ確認 (${logs.size}件)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    onClick = {
                                        val text = AppLogger.getAllLogsText()
                                        clipboardManager.setText(AnnotatedString(text))
                                        Toast.makeText(context, "ログをコピーしました", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("📋 コピー", color = NeonCyan, fontSize = 11.sp)
                                }

                                TextButton(
                                    onClick = {
                                        AppLogger.clear()
                                        Toast.makeText(context, "ログを消去しました", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("🗑️ 消去", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Log Console Window (Height 180dp)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F172A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                        ) {
                            if (logs.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("ログはありません", color = Color.Gray, fontSize = 11.sp)
                                }
                            } else {
                                LazyColumn(
                                    state = logListState,
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(logs, key = { it.id }) { log ->
                                        val levelColor = when (log.level) {
                                            LogLevel.DEBUG -> Color.Gray
                                            LogLevel.INFO -> NeonCyan
                                            LogLevel.WARN -> AccentAmber
                                            LogLevel.ERROR -> Color(0xFFEF4444)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = "${log.timestamp} ",
                                                color = Color.DarkGray,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "[${log.tag}] ",
                                                color = levelColor,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = log.message,
                                                color = Color.LightGray,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // モジュール 6: データベース完全初期化 (完全リセット)
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("⚠️ 6. データベース初期化 (完全リセット)", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Room DBの全ラン・セッション・GPS点群を消去し、初回インストール直後のクリーンな状態に復元します。", color = Color.Gray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { showResetDialog = true },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🗑️ データベースを完全初期化", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("データベースを完全初期化しますか？", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold) },
            text = {
                Text("保存されているすべての滑走履歴、セッション、GPS測位点、トリックイベントが永久に削除されます。\nアプリは初回起動状態に戻ります。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        onClearDatabase()
                        AppLogger.w("DB", "データベース完全初期化を実行しました")
                        Toast.makeText(context, "データベースを初期化しました", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("初期化を実行", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}
