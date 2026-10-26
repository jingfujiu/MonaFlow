package com.monaflow.app.feature.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AssistChip
import androidx.compose.material3.InputChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.monaflow.app.core.theme.LocalMonaColors
import com.monaflow.app.core.ui.MonaCard
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val savedTimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    viewModel: NoteEditViewModel,
    onBack: () -> Unit,
    onConvertToTask: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current
    val snackbar = remember { SnackbarHostState() }
    var tagDialog by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.importAttachment(uri, "image/jpeg") {
            }
        }
    }

    if (!state.loaded) return

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            state.saving -> "保存中…"
                            state.lastSavedAt != null -> "已保存 " +
                                Instant.ofEpochMilli(state.lastSavedAt!!).atZone(ZoneId.systemDefault())
                                    .format(savedTimeFormat)
                            else -> if (state.id == 0L) "新建笔记" else "编辑笔记"
                        },
                        fontSize = 14.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.saveNow(onBack) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回并保存")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.togglePin() }) {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = if (state.isPinned) "取消置顶" else "置顶",
                            tint = if (state.isPinned) Color(mona.primary) else Color(mona.textSecondary),
                        )
                    }
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (state.isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (state.isFavorite) "取消收藏" else "收藏",
                            tint = if (state.isFavorite) Color(0xFFC97B85) else Color(mona.textSecondary),
                        )
                    }
                    IconButton(onClick = { viewModel.saveNow(onBack) }) {
                        Icon(Icons.Outlined.Check, "保存", tint = Color(mona.primary))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { v -> viewModel.edit { it.copy(title = v) } },
                    placeholder = { Text("标题", fontSize = 20.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(mona.textPrimary),
                    ),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = state.content,
                    onValueChange = { v -> viewModel.edit { it.copy(content = v) } },
                    placeholder = { Text("写点什么…", fontSize = 15.sp) },
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = Color(mona.textPrimary),
                    ),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
            }
            // 标签
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { tagDialog = true },
                        label = { Text("＋ 标签", fontSize = 12.sp) },
                    )
                    state.tags.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.setTags(state.tags - tag) },
                            label = { Text(tag, fontSize = 12.sp) },
                            trailingIcon = { Icon(Icons.Outlined.Close, "移除", modifier = Modifier.size(14.dp)) },
                        )
                    }
                }
            }
            // 附件
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonaCard(
                        Modifier.width(84.dp).height(84.dp),
                        background = Color(mona.surfaceHigh),
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.AddPhotoAlternate, "添加图片", tint = Color(mona.textSecondary))
                        }
                    }
                    state.attachments.forEach { att ->
                        Box {
                            AsyncImage(
                                model = File(att.uri),
                                contentDescription = att.fileName,
                                modifier = Modifier.size(84.dp).clickable { },
                                contentScale = ContentScale.Crop,
                            )
                            IconButton(
                                onClick = { viewModel.removeAttachment(att) },
                                modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                            ) {
                                Icon(Icons.Outlined.Close, "删除附件", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            viewModel.saveNow {
                                onConvertToTask(viewModel.state.value.id)
                            }
                        },
                        label = { Text("转为待办", fontSize = 12.sp) },
                    )
                    // 阶段2接入：AI 总结 / 标题建议 / 标签建议 / 行动项提取
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    if (tagDialog) {
        TagInputDialog(
            existing = state.tags,
            onConfirm = { tags ->
                viewModel.setTags(tags)
                tagDialog = false
            },
            onDismiss = { tagDialog = false },
        )
    }
}

@Composable
private fun TagInputDialog(
    existing: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(existing.joinToString(" ")) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("用空格分隔多个标签", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("工作 生活 灵感") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onConfirm(text.split(Regex("\\s+")).filter { it.isNotBlank() }.distinct())
            }) { Text("确定") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
