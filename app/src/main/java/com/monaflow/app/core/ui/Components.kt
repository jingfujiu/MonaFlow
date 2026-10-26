package com.monaflow.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monaflow.app.core.theme.LocalMonaColors

/** 统一卡片容器：莫奈 Token 圆角/底色/轻阴影。 */
@Composable
fun MonaCard(
    modifier: Modifier = Modifier,
    background: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val mona = LocalMonaColors.current
    val shape = RoundedCornerShape(20.dp)
    val bg = background ?: Color(mona.surface)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val mona = LocalMonaColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(mona.textSecondary),
        )
        trailing?.invoke()
    }
}

/** 空状态：明确创建入口，不留死页面。 */
@Composable
fun EmptyState(
    icon: String,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val mona = LocalMonaColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(icon, fontSize = 44.sp)
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(mona.textPrimary),
        )
        Text(
            hint,
            fontSize = 13.sp,
            color = Color(mona.textSecondary),
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Button(onClick = onAction) { Text(actionText) }
        }
    }
}

/** 高风险操作二次确认（删除/覆盖恢复/清空回收站）。 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "删除",
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmText,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 防止快速重复点击造成重复提交。 */
fun singleClickGuard(currentTimeMillis: () -> Long = { System.currentTimeMillis() }): () -> Unit {
    var last = 0L
    return {
        val now = currentTimeMillis()
        if (now - last > 500) {
            last = now
        }
    }
}

@Composable
fun rememberDebouncedClick(onClick: () -> Unit): () -> Unit {
    var last by remember { mutableLongStateOf(0L) }
    return {
        val now = System.currentTimeMillis()
        if (now - last > 500) {
            last = now
            onClick()
        }
    }
}
