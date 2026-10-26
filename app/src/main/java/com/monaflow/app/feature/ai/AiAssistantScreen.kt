package com.monaflow.app.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monaflow.app.core.theme.LocalMonaColors
import com.monaflow.app.core.ui.MonaCard
import com.monaflow.app.data.ai.UiCandidate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: AiAssistantViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI 助手", fontSize = 17.sp)
                        Text(
                            if (state.hasProvider) state.providerName else "未配置服务商",
                            fontSize = 11.sp,
                            color = Color(mona.textSecondary),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("服务管理") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding(),
        ) {
            LazyColumn(
                Modifier.weight(1f),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!state.hasProvider && state.messages.isEmpty()) {
                    item {
                        MonaCard {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("开始使用 AI", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(mona.textPrimary))
                                Text(
                                    "1. 点击右上角“服务管理”\n2. 添加 DeepSeek / Kimi / GLM / 兼容接口\n3. 填入 API Key 并测试连接\n\n密钥加密存于本机，发送内容前都会先展示。",
                                    fontSize = 13.sp,
                                    color = Color(mona.textSecondary),
                                    lineHeight = 19.sp,
                                )
                            }
                        }
                    }
                }
                items(state.messages) { entry ->
                    val bg = when (entry.role) {
                        "user" -> Color(mona.primary)
                        "error" -> Color(0xFFB3564E).copy(alpha = 0.12f)
                        else -> Color(mona.surface)
                    }
                    val fg = if (entry.role == "user") Color(mona.onPrimary) else Color(mona.textPrimary)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (entry.role == "user") Arrangement.End else Arrangement.Start,
                    ) {
                        Box(
                            Modifier
                                .widthIn(max = 300.dp)
                                .background(bg, RoundedCornerShape(14.dp))
                                .padding(12.dp),
                        ) {
                            Text(entry.text, fontSize = 13.sp, color = fg, lineHeight = 19.sp)
                        }
                    }
                }
                if (state.busy) {
                    item {
                        Text("思考中…", fontSize = 12.sp, color = Color(mona.textSecondary))
                    }
                }
            }

            // 快捷操作
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = { viewModel.suggestTodayPlan() }, label = { Text("今日计划建议", fontSize = 12.sp) })
                AssistChip(onClick = { input = "" ; viewModel.send("帮我整理一下思路：") }, label = { Text("自由提问", fontSize = 12.sp) })
            }

            // 输入区
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("自然语言创建待办/倒计时，或直接提问", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )
                Column {
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                viewModel.organize(input)
                                input = ""
                            }
                        },
                        enabled = !state.busy && input.isNotBlank(),
                    ) {
                        Text("✂️ 整理", fontSize = 12.sp)
                    }
                    IconButton(
                        onClick = {
                            if (input.isNotBlank()) {
                                viewModel.send(input)
                                input = ""
                            }
                        },
                        enabled = !state.busy && input.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Send, "发送", tint = Color(mona.primary))
                    }
                }
            }
            if (state.busy) {
                TextButton(onClick = { viewModel.cancel() }, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)) {
                    Text("取消请求", fontSize = 12.sp, color = Color(0xFFB3564E))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    // 候选预览确认（写入前必须确认）
    if (state.pendingCandidates.isNotEmpty()) {
        val selected = remember(state.pendingCandidates) {
            mutableStateOf(state.pendingCandidates.toSet())
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.dismissCandidates() },
            title = { Text("确认创建") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.pendingCandidates.size) { i ->
                        val c = state.pendingCandidates[i]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = c in selected.value,
                                onCheckedChange = { checked ->
                                    selected.value =
                                        if (checked) selected.value + c else selected.value - c
                                },
                            )
                            Text(c.display(), fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmCandidates(selected.value.toList()) {}
                }) { Text("创建所选（${selected.value.size}）") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCandidates() }) { Text("全部取消") }
            },
        )
    }
}
