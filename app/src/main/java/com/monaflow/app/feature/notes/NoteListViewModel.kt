package com.monaflow.app.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monaflow.app.data.local.entity.NoteEntity
import com.monaflow.app.data.local.entity.TagEntity
import com.monaflow.app.data.repository.NoteRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class NoteListViewModel(private val repo: NoteRepository) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    val query: StateFlow<String> = queryFlow

    val notes: StateFlow<List<NoteEntity>> = queryFlow
        .debounce(300) // 搜索防抖
        .flatMapLatest { q ->
            if (q.isBlank()) repo.observeActive() else repo.searchActive(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<TagEntity>> = repo.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trash: StateFlow<List<NoteEntity>> = repo.observeTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) {
        queryFlow.value = q
    }

    fun setPinned(note: NoteEntity, pinned: Boolean) = viewModelScope.launch {
        repo.setPinned(note.id, pinned)
    }

    fun setFavorite(note: NoteEntity, favorite: Boolean) = viewModelScope.launch {
        repo.setFavorite(note.id, favorite)
    }

    fun setArchived(note: NoteEntity, archived: Boolean) = viewModelScope.launch {
        repo.setArchived(note.id, archived)
    }

    fun moveToTrash(note: NoteEntity) = viewModelScope.launch {
        repo.moveToTrash(note.id)
    }

    fun restore(note: NoteEntity) = viewModelScope.launch {
        repo.restore(note.id)
    }

    fun hardDelete(note: NoteEntity) = viewModelScope.launch {
        repo.hardDelete(note.id)
    }

    fun emptyTrash() = viewModelScope.launch {
        repo.emptyTrash()
    }
}
