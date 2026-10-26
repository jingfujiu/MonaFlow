package com.monaflow.app.feature.items

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monaflow.app.data.local.AttachmentStore
import com.monaflow.app.data.local.MonaFlowDatabase
import com.monaflow.app.data.local.entity.ItemEntity
import com.monaflow.app.data.repository.ItemRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(FlowPreview::class)
class ItemListViewModel(private val repo: ItemRepository) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    val query: StateFlow<String> = queryFlow

    val category = MutableStateFlow<String?>(null)

    val items: StateFlow<List<ItemEntity>> = queryFlow
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) repo.observeActive() else repo.search(q)
        }
        .combine(category) { list, cat ->
            if (cat.isNullOrBlank()) list else list.filter { it.category == cat }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<String>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) {
        queryFlow.value = q
    }

    fun setCategory(c: String?) {
        category.value = c
    }
}

data class ItemEditState(
    val id: Long = 0,
    val name: String = "",
    val category: String = "",
    val brand: String = "",
    val model: String = "",
    val quantity: Int = 1,
    val priceText: String = "",
    val currency: String = "CNY",
    val hasPurchaseDate: Boolean = false,
    val purchaseDate: LocalDate = LocalDate.now(),
    val storageLocation: String = "",
    val hasWarranty: Boolean = false,
    val warrantyEndDate: LocalDate = LocalDate.now(),
    val imageUri: String? = null,
    val note: String = "",
    val loaded: Boolean = false,
)

class ItemEditViewModel(
    application: Application,
    private val repo: ItemRepository,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val attachmentStore = AttachmentStore(application)
    private val id: Long = savedStateHandle.get<Long>("itemId") ?: 0L

    private val _state = MutableStateFlow(ItemEditState(id = id))
    val state: StateFlow<ItemEditState> = _state

    val categories: StateFlow<List<String>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (id > 0) {
            viewModelScope.launch {
                repo.byId(id)?.let { item ->
                    val price = item.purchasePriceMinor?.let {
                        com.monaflow.app.core.common.MoneyUtils.format(it, item.currency ?: "CNY")
                    } ?: ""
                    _state.value = ItemEditState(
                        id = item.id,
                        name = item.name,
                        category = item.category,
                        brand = item.brand,
                        model = item.model,
                        quantity = item.quantity,
                        priceText = price,
                        currency = item.currency ?: "CNY",
                        hasPurchaseDate = item.purchaseDate != null,
                        purchaseDate = item.purchaseDate?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now(),
                        storageLocation = item.storageLocation,
                        hasWarranty = item.warrantyEndDate != null,
                        warrantyEndDate = item.warrantyEndDate?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now(),
                        imageUri = item.imageUri,
                        note = item.note,
                        loaded = true,
                    )
                }
            }
        } else {
            _state.value = _state.value.copy(loaded = true)
        }
    }

    fun update(transform: (ItemEditState) -> ItemEditState) {
        _state.value = transform(_state.value)
    }

    fun importImage(uri: Uri, onFail: () -> Unit) {
        viewModelScope.launch {
            val path = attachmentStore.importFrom(uri, "image/jpeg")
            if (path == null) onFail() else _state.value = _state.value.copy(imageUri = path)
        }
    }

    fun removeImage() {
        _state.value.imageUri?.let { attachmentStore.delete(it) }
        _state.value = _state.value.copy(imageUri = null)
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.name.isBlank()) return
        viewModelScope.launch {
            val priceMinor = com.monaflow.app.core.common.MoneyUtils.parseToMinor(s.priceText, s.currency)
            val entity = ItemEntity(
                id = s.id,
                name = s.name.trim(),
                category = s.category.trim(),
                brand = s.brand.trim(),
                model = s.model.trim(),
                quantity = s.quantity.coerceAtLeast(1),
                purchasePriceMinor = priceMinor,
                currency = if (priceMinor != null || s.priceText.isNotBlank()) s.currency else null,
                purchaseDate = if (s.hasPurchaseDate) s.purchaseDate.toEpochDay() else null,
                storageLocation = s.storageLocation.trim(),
                warrantyEndDate = if (s.hasWarranty) s.warrantyEndDate.toEpochDay() else null,
                imageUri = s.imageUri,
                note = s.note.trim(),
                createdAt = 0,
                updatedAt = 0,
            )
            repo.upsert(entity)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        if (id == 0L) return
        viewModelScope.launch {
            repo.delete(id)
            onDone()
        }
    }
}
