package com.monaflow.app.feature.notes

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monaflow.app.core.theme.LocalMonaColors
import com.monaflow.app.core.ui.EmptyState
import com.monaflow.app.core.ui.MonaCard
import com.monaflow.app.data.local.entity.NoteEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val noteTimeFormat = DateTimeFormatter.ofPattern("M月d日 HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: NoteListViewModel,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("笔记") },
                actions = {
                    IconButton(onClick = onOpenTrash) {
                        Icon(Icons.Outlined.Delete, contentDescription = "回收站")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewNote,
                containerColor = Color(mona.accent),
                contentColor = Color(mona.onPrimary),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "新建笔记")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("搜索标题与正文") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Outlined.Close, "清空")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    EmptyState(
                        icon = "🪷",
                        title = if (query.isBlank()) "还没有笔记" else "没有找到匹配的笔记",
                        hint = if (query.isBlank()) "记录灵感、清单与日常，全部离线可用" else "换个关键词试试",
                        actionText = if (query.isBlank()) "写第一篇笔记" else null,
                        onAction = if (query.isBlank()) onNewNote else null,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 88.dp),
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { onOpenNote(note.id) },
                            onPin = { viewModel.setPinned(note, !note.isPinned) },
                            onFavorite = { viewModel.setFavorite(note, !note.isFavorite) },
                            onArchive = { viewModel.setArchived(note, true) },
                            onDelete = { viewModel.moveToTrash(note) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onFavorite: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val mona = LocalMonaColors.current
    var menuOpen by remember { mutableStateOf(false) }
    val time = remember(note.updatedAt) {
        Instant.ofEpochMilli(note.updatedAt).atZone(ZoneId.systemDefault()).format(noteTimeFormat)
    }
    MonaCard(onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    note.title.ifBlank { "(无标题)" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(mona.textPrimary),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (note.isPinned) {
                    Icon(Icons.Outlined.PushPin, contentDescription = "已置顶", tint = Color(mona.primary), modifier = Modifier.height(16.dp))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.height(28.dp)) {
                        Text("⋯", color = Color(mona.textSecondary))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (note.isPinned) "取消置顶" else "置顶") },
                            onClick = { menuOpen = false; onPin() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (note.isFavorite) "取消收藏" else "收藏") },
                            onClick = { menuOpen = false; onFavorite() },
                        )
                        DropdownMenuItem(
                            text = { Text("归档") },
                            onClick = { menuOpen = false; onArchive() },
                        )
                        DropdownMenuItem(
                            text = { Text("移入回收站", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
            if (note.content.isNotBlank()) {
                Text(
                    note.content,
                    fontSize = 13.sp,
                    color = Color(mona.textSecondary),
                    maxLines = 3,
                )
            }
            Text(time, fontSize = 11.sp, color = Color(mona.textSecondary))
        }
    }
}

/** 回收站：恢复 / 彻底删除 / 清空（均需确认）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteTrashScreen(
    viewModel: NoteListViewModel,
    onBack: () -> Unit,
) {
    val trash by viewModel.trash.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current
    var confirmEmpty by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<NoteEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("回收站") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
                actions = {
                    if (trash.isNotEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) {
                            Text("清空", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (trash.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(icon = "🗑️", title = "回收站是空的", hint = "删除的笔记会先到这里")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(trash, key = { it.id }) { note ->
                    MonaCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(note.title.ifBlank { "(无标题)" }, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(mona.textPrimary), maxLines = 1)
                            Text(note.content, fontSize = 12.sp, color = Color(mona.textSecondary), maxLines = 2)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { viewModel.restore(note) }) { Text("恢复") }
                                TextButton(onClick = { pendingDelete = note }) {
                                    Text("彻底删除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        com.monaflow.app.core.ui.ConfirmDialog(
            title = "清空回收站",
            text = "回收站中 ${trash.size} 篇笔记将被彻底删除，无法恢复。",
            confirmText = "清空",
            onConfirm = { confirmEmpty = false; viewModel.emptyTrash() },
            onDismiss = { confirmEmpty = false },
        )
    }
    pendingDelete?.let { note ->
        com.monaflow.app.core.ui.ConfirmDialog(
            title = "彻底删除",
            text = "“${note.title.ifBlank { "(无标题)" }}”及其附件将被彻底删除，无法恢复。",
            onConfirm = { viewModel.hardDelete(note); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}
