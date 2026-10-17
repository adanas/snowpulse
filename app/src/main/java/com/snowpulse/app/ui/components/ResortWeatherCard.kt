package com.snowpulse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snowpulse.app.data.model.WeatherInfo
import com.snowpulse.app.ui.theme.*

/**
 * ゲレンデ天気情報カード（パターンB: コンパクト省スペース型）
 * ヘッダー直下にスマートに配置し、縦スペースを圧迫せずに気温・新雪・風速を即座に把握できます。
 */
@Composable
fun ResortWeatherCard(
    weather: WeatherInfo = WeatherInfo(),
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Weather Icon, Temperature & Condition
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = weather.iconEmoji,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = weather.tempDisplay,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = weather.condition,
                            color = if (weather.isValid) NeonCyan else Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "山頂 ${weather.summitTempDisplay} | 積雪 ${weather.snowDepthDisplay}",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }

            // Right: Fresh Snow & Wind Pill Badges
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // 24h Fresh Snow Pill Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (weather.isValid) NeonCyan.copy(alpha = 0.15f) else Color(0xFF1E293B),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (weather.isValid) NeonCyan.copy(alpha = 0.35f) else Color(0xFF334155)
                    )
                ) {
                    Text(
                        text = "新雪 ${weather.freshSnowDisplay}",
                        color = if (weather.isValid) NeonCyan else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Wind Pill Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (weather.isValid) SuccessEmerald.copy(alpha = 0.15f) else Color(0xFF1E293B)
                ) {
                    Text(
                        text = weather.windSpeedDisplay,
                        color = if (weather.isValid) SuccessEmerald else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
