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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.monaflow.app.core.ui.ConfirmDialog
import com.monaflow.app.core.ui.EmptyState
import com.monaflow.app.core.ui.MonaCard
import com.monaflow.app.data.local.entity.AiProviderEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val testTimeFormat = DateTimeFormatter.ofPattern("M月d日 HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    viewModel: AiSettingsViewModel,
    onBack: () -> Unit,
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val testingId by viewModel.testingId.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current
    var editing by remember { mutableStateOf<ProviderForm?>(null) }
    var pendingDelete by remember { mutableStateOf<AiProviderEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 服务管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = ProviderForm() },
                containerColor = Color(mona.accent),
                contentColor = Color(mona.onPrimary),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "添加服务商")
            }
        },
    ) { padding ->
        if (providers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = "🤖",
                    title = "还没有 AI 服务商",
                    hint = "支持 DeepSeek、Kimi、智谱 GLM、通义千问及任何 OpenAI 兼容接口。\nAPI Key 使用 Android Keystore 加密存储，不随备份导出。",
                    actionText = "添加服务商",
                    onAction = { editing = ProviderForm() },
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(providers, key = { it.id }) { p ->
                    val statusText = when {
                        testingId == p.id -> "测试中…"
                        testResult?.first == p.id -> testResult!!.second
                        p.lastTestStatus != null && p.lastTestAt != null ->
                            describeStatus(p.lastTestStatus!!) + " " +
                                Instant.ofEpochMilli(p.lastTestAt!!).atZone(ZoneId.systemDefault()).format(testTimeFormat)
                        else -> "未测试"
                    }
                    MonaCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(p.displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(mona.textPrimary))
                                        if (p.isDefault) {
                                            Text(
                                                "默认",
                                                fontSize = 10.sp,
                                                color = Color(mona.onPrimary),
                                                modifier = Modifier
                                                    .background(Color(mona.primary), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                            )
                                        }
                                        if (!p.isEnabled) {
                                            Text("已停用", fontSize = 10.sp, color = Color(mona.textSecondary))
                                        }
                                    }
                                    Text(p.modelName, fontSize = 12.sp, color = Color(mona.textSecondary))
                                    Text(p.baseUrl, fontSize = 11.sp, color = Color(mona.textSecondary), maxLines = 1)
                                }
                                Switch(checked = p.isEnabled, onCheckedChange = { enabled ->
                                    viewModel.saveProvider(
                                        ProviderForm(
                                            id = p.id,
                                            displayName = p.displayName,
                                            baseUrl = p.baseUrl,
                                            modelName = p.modelName,
                                            hasExistingKey = p.encryptedCredentialRef != null,
                                            customHeadersJson = p.customHeadersJson ?: "",
                                            connectTimeoutMs = p.connectTimeoutMs,
                                            readTimeoutMs = p.readTimeoutMs,
                                            isEnabled = enabled,
                                        ),
                                    ) {}
                                })
                            }
                            Text(
                                statusText,
                                fontSize = 11.sp,
                                color = if (statusText.startsWith("连接成功") || statusText.startsWith("测试中")) Color(mona.primary) else Color(0xFFB3564E),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { viewModel.testProvider(p.id) }, enabled = p.isEnabled) {
                                    Text("测试连接", fontSize = 12.sp)
                                }
                                TextButton(onClick = { viewModel.setDefault(p.id) }) {
                                    Text("设为默认", fontSize = 12.sp)
                                }
                                TextButton(onClick = {
                                    editing = ProviderForm(
                                        id = p.id,
                                        displayName = p.displayName,
                                        baseUrl = p.baseUrl,
                                        modelName = p.modelName,
                                        hasExistingKey = p.encryptedCredentialRef != null,
                                        customHeadersJson = p.customHeadersJson ?: "",
                                        connectTimeoutMs = p.connectTimeoutMs,
                                        readTimeoutMs = p.readTimeoutMs,
                                        isEnabled = p.isEnabled,
                                    )
                                }) { Text("编辑", fontSize = 12.sp) }
                                IconButton(onClick = { pendingDelete = p }) {
                                    Icon(Icons.Outlined.Delete, "删除", tint = Color(mona.textSecondary), modifier = Modifier.height(18.dp))
                                }
                            }
                        }
                    }
                }
                item {
                    Text(
                        "密钥仅保存在本机（Keystore 加密），请求仅在功能使用时发送；用量记录不含正文内容。",
                        fontSize = 11.sp,
                        color = Color(mona.textSecondary),
                    )
                }
            }
        }
    }

    editing?.let { form ->
        ProviderEditDialog(
            form = form,
            onSave = { viewModel.saveProvider(it) { editing = null } },
            onDismiss = { editing = null },
        )
    }

    pendingDelete?.let { p ->
        ConfirmDialog(
            title = "删除服务商",
            text = "“${p.displayName}”将被删除（其 API Key 一并清除）。",
            onConfirm = { viewModel.deleteProvider(p.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

private fun describeStatus(status: String): String = when (status) {
    "ok" -> "连接成功"
    "auth" -> "鉴权失败"
    "rate_limited" -> "限流"
    "not_found" -> "接口不存在"
    "server_error" -> "服务端错误"
    "timeout" -> "超时"
    "network" -> "网络错误"
    "tls" -> "TLS 错误"
    "url_invalid" -> "地址错误"
    "bad_response" -> "响应格式错误"
    "cancelled" -> "已取消"
    else -> status
}

@Composable
private fun ProviderEditDialog(
    form: ProviderForm,
    onSave: (ProviderForm) -> Unit,
    onDismiss: () -> Unit,
) {
    var f by remember { mutableStateOf(form) }
    var keyVisible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (form.id == 0L) "添加 AI 服务商" else "编辑服务商") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        value = f.displayName,
                        onValueChange = { v -> f = f.copy(displayName = v) },
                        label = { Text("显示名称（如：DeepSeek 官方）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = f.baseUrl,
                        onValueChange = { v -> f = f.copy(baseUrl = v) },
                        label = { Text("API Base URL") },
                        placeholder = { Text("https://api.deepseek.com/v1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = f.modelName,
                        onValueChange = { v -> f = f.copy(modelName = v) },
                        label = { Text("模型名称") },
                        placeholder = { Text("deepseek-chat / moonshot-v1-8k / glm-4-flash …") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = f.apiKey,
                        onValueChange = { v -> f = f.copy(apiKey = v) },
                        label = { Text(if (f.hasExistingKey) "API Key（留空保持不变）" else "API Key") },
                        visualTransformation = if (keyVisible) androidx.compose.ui.text.input.VisualTransformation.None
                        else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Text(if (keyVisible) "隐藏" else "显示", fontSize = 11.sp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = f.customHeadersJson,
                        onValueChange = { v -> f = f.copy(customHeadersJson = v) },
                        label = { Text("自定义请求头 JSON（可选）") },
                        placeholder = { Text("{\"X-Title\":\"MonaFlow\"}") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = f.connectTimeoutMs.toString(),
                            onValueChange = { v -> f = f.copy(connectTimeoutMs = v.toIntOrNull() ?: 15_000) },
                            label = { Text("连接超时(ms)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = f.readTimeoutMs.toString(),
                            onValueChange = { v -> f = f.copy(readTimeoutMs = v.toIntOrNull() ?: 60_000) },
                            label = { Text("读取超时(ms)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("启用该服务", fontSize = 14.sp)
                        Switch(checked = f.isEnabled, onCheckedChange = { v -> f = f.copy(isEnabled = v) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(f) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
