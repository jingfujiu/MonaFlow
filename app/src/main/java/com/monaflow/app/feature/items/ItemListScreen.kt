package com.monaflow.app.feature.items

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.monaflow.app.core.common.MoneyUtils
import com.monaflow.app.core.common.MonaDate
import com.monaflow.app.core.theme.LocalMonaColors
import com.monaflow.app.core.ui.EmptyState
import com.monaflow.app.core.ui.MonaCard
import com.monaflow.app.data.repository.ItemRepository
import com.monaflow.app.data.repository.WarrantyState
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    viewModel: ItemListViewModel,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val mona = LocalMonaColors.current

    val totalCount = items.size
    val totalPrice = items.mapNotNull { it.purchasePriceMinor }.sum()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("物品记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNew,
                containerColor = Color(mona.accent),
                contentColor = Color(mona.onPrimary),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "新增物品")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("搜索名称/分类/品牌/位置") },
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
            if (categories.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    item {
                        FilterChip(
                            selected = category == null,
                            onClick = { viewModel.setCategory(null) },
                            label = { Text("全部", fontSize = 12.sp) },
                        )
                    }
                    items(categories, key = { it }) { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { viewModel.setCategory(if (category == c) null else c) },
                            label = { Text(c, fontSize = 12.sp) },
                        )
                    }
                }
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    EmptyState(
                        icon = "📦",
                        title = if (query.isBlank() && category == null) "还没有物品记录" else "没有匹配的物品",
                        hint = "记录物品的位置、价格与保修期，找东西不再翻箱倒柜",
                        actionText = if (query.isBlank() && category == null) "记录第一件物品" else null,
                        onAction = if (query.isBlank() && category == null) onNew else null,
                    )
                }
            } else {
                Text(
                    "$totalCount 件" + if (totalPrice > 0) " · 已登记总价约 ${MoneyUtils.formatWithSymbol(totalPrice, "CNY")}（仅统计 CNY）" else "",
                    fontSize = 12.sp,
                    color = Color(mona.textSecondary),
                    modifier = Modifier.padding(top = 10.dp),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 10.dp, bottom = 88.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        ItemCard(item = item, onClick = { onEdit(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCard(item: com.monaflow.app.data.local.entity.ItemEntity, onClick: () -> Unit) {
    val mona = LocalMonaColors.current
    val warranty = ItemWarranty(item)
    MonaCard(onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (item.imageUri != null) {
                AsyncImage(
                    model = File(item.imageUri),
                    contentDescription = item.name,
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier.size(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Inventory2, null, tint = Color(mona.textSecondary))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(mona.textPrimary),
                    maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.category.isNotBlank()) Text(item.category, fontSize = 11.sp, color = Color(mona.textSecondary))
                    if (item.storageLocation.isNotBlank()) Text("📍${item.storageLocation}", fontSize = 11.sp, color = Color(mona.textSecondary))
                }
                if (item.purchasePriceMinor != null && item.currency != null) {
                    Text(
                        MoneyUtils.formatWithSymbol(item.purchasePriceMinor, item.currency) + " × ${item.quantity}",
                        fontSize = 12.sp,
                        color = Color(mona.primary),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                when (warranty) {
                    is WarrantyState.EXPIRING_SOON -> Badge("保修 ${MonaDate.formatShort(warranty.endDate)} 到期", 0xFFD9A441)
                    is WarrantyState.EXPIRED -> Badge("保修已过期", 0xFFB3564E)
                    else -> {}
                }
            }
        }
    }
}

private fun ItemWarranty(item: com.monaflow.app.data.local.entity.ItemEntity): WarrantyState? {
    val end = item.warrantyEndDate ?: return null
    val endDate = LocalDate.ofEpochDay(end)
    val today = LocalDate.now()
    return when {
        endDate.isBefore(today) -> WarrantyState.EXPIRED(endDate)
        endDate.isBefore(today.plusDays(30)) -> WarrantyState.EXPIRING_SOON(endDate)
        else -> WarrantyState.VALID(endDate)
    }
}

@Composable
private fun Badge(text: String, color: Long) {
    Box(
        Modifier
            .background(Color(color).copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 10.sp, color = Color(color))
    }
}
