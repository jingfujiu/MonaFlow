package com.monaflow.app.feature.notes

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.monaflow.app.data.local.AttachmentStore
import com.monaflow.app.data.local.MonaFlowDatabase
import com.monaflow.app.data.local.entity.AttachmentEntity
import com.monaflow.app.data.local.entity.NoteEntity
import com.monaflow.app.data.repository.NoteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class NoteEditState(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val attachments: List<AttachmentEntity> = emptyList(),
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val lastSavedAt: Long? = null,
)

/**
 * 笔记编辑：输入即入内存态，800ms 防抖落库保护未保存内容；
 * 返回前再强制保存一次。附件复制进私有目录，外部 URI 失效不影响。
 */
class NoteEditViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val repo = NoteRepository(MonaFlowDatabase.get(application))
    private val attachmentDao = MonaFlowDatabase.get(application).attachmentDao()
    private val attachmentStore = AttachmentStore(application)
    private val noteId: Long = savedStateHandle.get<Long>("noteId") ?: 0L

    private val _state = MutableStateFlow(NoteEditState(id = noteId))
    val state: StateFlow<NoteEditState> = _state

    private var autoSaveJob: Job? = null
    private var dirty = false

    init {
        if (noteId > 0) {
            viewModelScope.launch {
                val note = repo.byId(noteId)
                if (note != null) {
                    val tags = repo.tagsOf(noteId).map { it.name }
                    val atts = attachmentDao.byOwner(OWNER_TYPE, noteId)
                    _state.value = NoteEditState(
                        id = note.id,
                        title = note.title,
                        content = note.content,
                        tags = tags,
                        isPinned = note.isPinned,
                        isFavorite = note.isFavorite,
                        isArchived = note.isArchived,
                        attachments = atts,
                        loaded = true,
                    )
                } else {
                    _state.value = _state.value.copy(loaded = true)
                }
            }
        } else {
            _state.value = _state.value.copy(loaded = true)
        }
    }

    fun edit(transform: (NoteEditState) -> NoteEditState) {
        dirty = true
        _state.value = transform(_state.value)
        scheduleAutoSave()
    }

    fun setTags(tags: List<String>) {
        edit { it.copy(tags = tags) }
    }

    fun togglePin() = edit { it.copy(isPinned = !it.isPinned) }
    fun toggleFavorite() = edit { it.copy(isFavorite = !it.isFavorite) }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(800)
            saveNow()
        }
    }

    /** 返回键 / 顶栏保存时调用；把当前内容落库。 */
    fun saveNow(onDone: () -> Unit = {}) {
        val s = _state.value
        if (!s.loaded) {
            onDone()
            return
        }
        dirty = false
        viewModelScope.launch {
            _state.value = s.copy(saving = true)
            val id = repo.upsert(
                NoteEntity(
                    id = s.id,
                    title = s.title.trim(),
                    content = s.content,
                    createdAt = 0, // repository 更新时保留原创建时间
                    updatedAt = 0,
                    isPinned = s.isPinned,
                    isFavorite = s.isFavorite,
                    isArchived = s.isArchived,
                ),
                tagNames = s.tags,
            )
            if (s.id == 0L && _state.value.id == 0L) {
                _state.value = _state.value.copy(id = id)
            }
            _state.value = _state.value.copy(saving = false, lastSavedAt = System.currentTimeMillis())
            onDone()
        }
    }

    /** 从相册选择后导入：复制进私有目录再登记；新笔记先保存拿到 id。 */
    fun importAttachment(uri: Uri, mimeType: String, onFail: () -> Unit) {
        viewModelScope.launch {
            val path = attachmentStore.importFrom(uri, mimeType)
            if (path == null) {
                onFail()
                return@launch
            }
            if (_state.value.id == 0L) {
                saveNow {
                    viewModelScope.launch {
                        val saved = AttachmentEntity(
                            ownerType = OWNER_TYPE,
                            ownerId = _state.value.id,
                            uri = path,
                            mimeType = mimeType,
                            fileName = path.substringAfterLast('/'),
                            createdAt = System.currentTimeMillis(),
                        )
                        attachmentDao.insert(saved)
                        _state.value = _state.value.copy(attachments = _state.value.attachments + saved)
                    }
                }
            } else {
                val entity = AttachmentEntity(
                    ownerType = OWNER_TYPE,
                    ownerId = _state.value.id,
                    uri = path,
                    mimeType = mimeType,
                    fileName = path.substringAfterLast('/'),
                    createdAt = System.currentTimeMillis(),
                )
                attachmentDao.insert(entity)
                _state.value = _state.value.copy(attachments = _state.value.attachments + entity)
            }
        }
    }

    fun removeAttachment(attachment: AttachmentEntity) {
        viewModelScope.launch {
            attachmentDao.delete(attachment.id)
            attachmentStore.delete(attachment.uri)
            _state.value = _state.value.copy(attachments = _state.value.attachments - attachment)
        }
    }

    companion object {
        const val OWNER_TYPE = "note"
    }
}
