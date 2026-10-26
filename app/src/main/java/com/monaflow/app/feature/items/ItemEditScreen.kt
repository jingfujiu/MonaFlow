package com.monaflow.app.feature.items

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.monaflow.app.core.common.MonaDate
import com.monaflow.app.core.common.MoneyUtils
import com.monaflow.app.core.theme.LocalMonaColors
import com.monaflow.app.core.ui.ConfirmDialog
import com.monaflow.app.core.ui.MonaCard
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(
    viewModel: ItemEditViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current
    var confirmDelete by remember { mutableStateOf(false) }
    var dateTarget by remember { mutableStateOf<String?>(null) }
    var categoryMenu by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importImage(uri) {}
    }

    if (!state.loaded) return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == 0L) "新增物品" else "编辑物品") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
                actions = {
                    if (state.id > 0) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, "删除", tint = Color(0xFFB3564E))
                        }
                    }
                    IconButton(onClick = { viewModel.save(onBack) }) {
                        Icon(Icons.Outlined.Check, "保存", tint = Color(mona.primary))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box {
                        if (state.imageUri != null) {
                            AsyncImage(
                                model = File(state.imageUri),
                                contentDescription = "物品图片",
                                modifier = Modifier.size(84.dp),
                                contentScale = ContentScale.Crop,
                            )
                            IconButton(
                                onClick = { viewModel.removeImage() },
                                modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                            ) {
                                Icon(Icons.Outlined.Close, "移除图片", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        } else {
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
                        }
                    }
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                        label = { Text("物品名称") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }
            // 分类 + 品牌/型号
            item {
                val categories by viewModel.categories.collectAsStateWithLifecycle()
                ExposedDropdownMenuBox(
                    expanded = categoryMenu,
                    onExpandedChange = { categoryMenu = it },
                ) {
                    OutlinedTextField(
                        value = state.category,
                        onValueChange = { v -> viewModel.update { it.copy(category = v) } },
                        label = { Text("分类（可选下拉，也可直接输入）") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        singleLine = true,
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    )
                    if (categories.isNotEmpty()) {
                        ExposedDropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            categories.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c) },
                                    onClick = {
                                        viewModel.update { it.copy(category = c) }
                                        categoryMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.brand,
                        onValueChange = { v -> viewModel.update { it.copy(brand = v) } },
                        label = { Text("品牌") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.model,
                        onValueChange = { v -> viewModel.update { it.copy(model = v) } },
                        label = { Text("型号") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }
            // 价格
            item {
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("价格", fontSize = 14.sp, color = Color(mona.textPrimary), modifier = Modifier.width(40.dp))
                            OutlinedTextField(
                                value = state.priceText,
                                onValueChange = { v -> viewModel.update { it.copy(priceText = v) } },
                                label = { Text("未填则留空") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                supportingText = {
                                    Text(
                                        if (state.priceText.isNotBlank())
                                            MoneyUtils.formatWithSymbol(
                                                MoneyUtils.parseToMinor(state.priceText, state.currency) ?: 0,
                                                state.currency,
                                            )
                                        else "金额以最小货币单位存储，不做浮点运算",
                                        fontSize = 10.sp,
                                    )
                                },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("CNY", "USD", "JPY").forEach { cur ->
                                    FilterChip(
                                        selected = state.currency == cur,
                                        onClick = { viewModel.update { it.copy(currency = cur) } },
                                        label = { Text(cur, fontSize = 11.sp) },
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("数量", fontSize = 14.sp, color = Color(mona.textPrimary), modifier = Modifier.width(40.dp))
                            IconButton(onClick = { viewModel.update { it.copy(quantity = (it.quantity - 1).coerceAtLeast(1)) } }) {
                                Text("－")
                            }
                            Text("${state.quantity}", fontSize = 15.sp)
                            IconButton(onClick = { viewModel.update { it.copy(quantity = it.quantity + 1) } }) {
                                Text("＋")
                            }
                        }
                    }
                }
            }
            // 日期
            item {
                MonaCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("购买日期", fontSize = 14.sp, color = Color(mona.textPrimary))
                            Switch(
                                checked = state.hasPurchaseDate,
                                onCheckedChange = { v -> viewModel.update { it.copy(hasPurchaseDate = v) } },
                            )
                        }
                        if (state.hasPurchaseDate) {
                            TextButton(onClick = { dateTarget = "purchase" }) {
                                Text(MonaDate.formatFull(state.purchaseDate), color = Color(mona.primary))
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("保修截止", fontSize = 14.sp, color = Color(mona.textPrimary))
                            Switch(
                                checked = state.hasWarranty,
                                onCheckedChange = { v -> viewModel.update { it.copy(hasWarranty = v) } },
                            )
                        }
                        if (state.hasWarranty) {
                            TextButton(onClick = { dateTarget = "warranty" }) {
                                Text(MonaDate.formatFull(state.warrantyEndDate), color = Color(mona.primary))
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.storageLocation,
                    onValueChange = { v -> viewModel.update { it.copy(storageLocation = v) } },
                    label = { Text("存放位置（如：书房抽屉第二层）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { v -> viewModel.update { it.copy(note = v) } },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    dateTarget?.let { target ->
        val current = if (target == "purchase") state.purchaseDate else state.warrantyEndDate
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = current.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { dateTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        viewModel.update {
                            if (target == "purchase") it.copy(purchaseDate = picked) else it.copy(warrantyEndDate = picked)
                        }
                    }
                    dateTarget = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { dateTarget = null }) { Text("取消") } },
        ) {
            androidx.compose.material3.DatePicker(state = pickerState)
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除物品",
            text = "“${state.name}”将被删除，无法恢复。",
            onConfirm = { confirmDelete = false; viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}
