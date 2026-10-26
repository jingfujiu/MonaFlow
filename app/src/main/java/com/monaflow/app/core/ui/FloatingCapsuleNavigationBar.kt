package com.monaflow.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monaflow.app.core.theme.ColorUtils
import com.monaflow.app.core.theme.LocalMonaColors

/**
 * 悬浮胶囊导航栏的统一度量。
 * 页面内容区与 FAB 的底部预留 = Height + BottomGap + navigationBars inset，
 * 全部从这里取值，避免多处硬编码导致错位。
 */
object NavBarMetrics {
    val Height: Dp = 80.dp
    val HorizontalMargin: Dp = 16.dp
    val BottomGap: Dp = 12.dp
    val Corner: Dp = 40.dp

    /** 胶囊与内容区之间额外留出的呼吸间距。 */
    val Clearance: Dp = 8.dp

    /** 内容区（含滚动容器与 FAB）应让出的底部高度（不含手势区，调用方叠加）。 */
    fun contentReserve(navigationBarsBottom: Dp): Dp =
        Height + BottomGap + Clearance + navigationBarsBottom
}

data class CapsuleTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * 悬浮式胶囊导航栏：独立于屏幕底边之上，莫奈风格半透明材质 + 轻描边 + 柔和阴影。
 * - 系统底部安全区（手势条/三键）由 navigationBarsPadding 统一处理，仅此一处；
 * - 选中项为独立圆角胶囊背景（低饱和莫奈蓝绿）；
 * - 无实时模糊，浅色雾面/深色玻璃仅靠 alpha，保证 HyperOS 流畅。
 */
@Composable
fun FloatingCapsuleNavigationBar(
    tabs: List<CapsuleTab>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mona = LocalMonaColors.current
    val dark = ColorUtils.luminance(mona.background.toInt()) < 0.3f
    // 浅色：奶油白/雾蓝雾面；深色：深色玻璃质感
    val capsuleBg = Color(mona.surface).copy(alpha = if (dark) 0.93f else 0.96f)
    val capsuleBorder = Color(mona.outline).copy(alpha = if (dark) 0.55f else 0.7f)
    val shape = RoundedCornerShape(NavBarMetrics.Corner)
    val itemShape = RoundedCornerShape(28.dp)

    Column(
        modifier = modifier
            .navigationBarsPadding() // 手势条/三键安全区仅由胶囊负责一次
            .padding(horizontal = NavBarMetrics.HorizontalMargin)
            .padding(bottom = NavBarMetrics.BottomGap),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NavBarMetrics.Height)
                .shadow(
                    elevation = 16.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color(0x33505A62),
                    spotColor = Color(0x33505A62),
                )
                .background(capsuleBg, shape)
                .border(0.6.dp, capsuleBorder, shape)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                val interaction = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(itemShape)
                        .background(
                            if (selected) Color(mona.primary).copy(alpha = 0.16f) else Color.Transparent,
                            itemShape,
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { onSelect(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = Color(if (selected) mona.primary else mona.textSecondary),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = Color(if (selected) mona.primary else mona.textSecondary),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 胶囊导航栏的底部预留（内容区让位），供页面内容容器统一使用。 */
@Composable
fun navBarContentPadding(): androidx.compose.ui.unit.Dp =
    NavBarMetrics.contentReserve(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    )
