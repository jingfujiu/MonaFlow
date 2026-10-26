package com.monaflow.app.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monaflow.app.ui.theme.*

@Composable
fun TodayScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "MONAFLOW · 莫奈时序",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MonetSageGreen,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "今日聚焦与日常节奏",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MonetTextDark
                )
            }
        }

        // 艺术画意晨间卡片
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MonetMistBlue)
                    .padding(20.dp)
            ) {
                Column {
                    Text("🌿 莫奈晨光整理", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MonetTextDark)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "“色彩是我永恒的执念、喜悦与折磨。” 莫奈睡莲色调已应用，今日有 3 项重点任务与 2 项习惯打卡进行中。",
                        fontSize = 13.sp,
                        color = MonetTextDark.copy(alpha = 0.8f),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 今日打卡看板
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("日常习惯流", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💧 晨起温水润喉", color = MonetTextDark, fontSize = 14.sp)
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(containerColor = MonetWaterLilyGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("已打卡", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
