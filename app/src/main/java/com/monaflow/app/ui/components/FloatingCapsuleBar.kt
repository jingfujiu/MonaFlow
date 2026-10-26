package com.monaflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monaflow.app.ui.theme.*

data class NavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

@Composable
fun FloatingCapsuleBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onQuickAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val leftItems = listOf(
        NavItem("today", "今日", Icons.Default.WbSunny),
        NavItem("notes", "笔记", Icons.Default.EditNote)
    )
    val rightItems = listOf(
        NavItem("tasks", "待办", Icons.Default.CheckCircleOutline),
        NavItem("settings", "我的", Icons.Default.PersonOutline)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // 悬浮毛玻璃胶囊容器
        Row(
            modifier = Modifier
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(36.dp),
                    spotColor = Color(0x2630363B),
                    ambientColor = Color(0x1AA9C7D8)
                )
                .clip(RoundedCornerShape(36.dp))
                .background(Color(0xE6FFFFFF)) // 90% 白底微透
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(36.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左翼导航
            leftItems.forEach { item ->
                CapsuleItem(
                    item = item,
                    isSelected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) }
                )
            }

            // 中间高亮快捷创建大圆钮 (+)
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(46.dp)
                    .shadow(8.dp, CircleShape, spotColor = MonetSageGreen)
                    .clip(CircleShape)
                    .background(MonetSageGreen)
                    .clickable { onQuickAdd() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "快速新建",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 右翼导航
            rightItems.forEach { item ->
                CapsuleItem(
                    item = item,
                    isSelected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) }
                )
            }
        }
    }
}

@Composable
private fun CapsuleItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val activeBg = if (isSelected) MonetMistBlue.copy(alpha = 0.6f) else Color.Transparent
    val tint = if (isSelected) MonetTextDark else MonetTextMuted

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(activeBg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = item.title,
            fontSize = 11.sp,
            color = tint,
            lineHeight = 12.sp
        )
    }
}
