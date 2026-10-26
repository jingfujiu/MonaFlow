package com.monaflow.app.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monaflow.app.MonaFlowApp
import com.monaflow.app.core.security.CredentialVault
import com.monaflow.app.core.security.KeystoreVault
import com.monaflow.app.data.ai.AiErrorType
import com.monaflow.app.data.ai.AiService
import com.monaflow.app.data.local.entity.AiProviderEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProviderForm(
    val id: Long = 0,
    val displayName: String = "",
    val baseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = "", // 编辑已有且未输入时保持原密钥不变
    val hasExistingKey: Boolean = false,
    val customHeadersJson: String = "",
    val connectTimeoutMs: Int = 15_000,
    val readTimeoutMs: Int = 60_000,
    val isEnabled: Boolean = true,
)

class AiSettingsViewModel(private val app: MonaFlowApp) : ViewModel() {

    private val vault: CredentialVault = KeystoreVault(app)
    private val aiService = AiService(app.database, vault)

    val providers: StateFlow<List<AiProviderEntity>> = app.database.aiProviderDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val usageRecent = app.database.aiUsageDao().observeRecent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val testResult = MutableStateFlow<Pair<Long, String>?>(null) // providerId → 结果文本
    val testingId = MutableStateFlow<Long?>(null)

    fun saveProvider(form: ProviderForm, onDone: () -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = if (form.id > 0) app.database.aiProviderDao().byId(form.id) else null
            val encrypted = when {
                form.apiKey.isNotBlank() -> vault.encrypt(form.apiKey)
                existing != null -> existing.encryptedCredentialRef // 保留原密钥
                else -> null
            }
            val entity = AiProviderEntity(
                id = form.id,
                displayName = form.displayName.trim().ifBlank { "未命名服务" },
                baseUrl = form.baseUrl.trim(),
                protocolType = AiService.PROTOCOL_OPENAI_COMPAT,
                modelName = form.modelName.trim(),
                encryptedCredentialRef = encrypted,
                customHeadersJson = form.customHeadersJson.trim().ifBlank { null },
                connectTimeoutMs = form.connectTimeoutMs.coerceIn(1_000, 120_000),
                readTimeoutMs = form.readTimeoutMs.coerceIn(1_000, 600_000),
                isEnabled = form.isEnabled,
                isDefault = existing?.isDefault ?: false,
                lastTestStatus = existing?.lastTestStatus,
                lastTestAt = existing?.lastTestAt,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            if (form.id == 0L) {
                val id = app.database.aiProviderDao().insert(entity)
                if (app.database.aiProviderDao().all().size == 1) {
                    app.database.aiProviderDao().setDefault(id)
                }
            } else {
                app.database.aiProviderDao().update(entity)
            }
            onDone()
        }
    }

    fun deleteProvider(id: Long) {
        viewModelScope.launch { app.database.aiProviderDao().delete(id) }
    }

    fun setDefault(id: Long) {
        viewModelScope.launch { app.database.aiProviderDao().setDefault(id) }
    }

    fun testProvider(id: Long) {
        viewModelScope.launch {
            testingId.value = id
            val error = runCatching { aiService.testConnection(id) }
                .getOrElse { AiErrorType.BAD_RESPONSE }
            testResult.value = id to (error?.label ?: "连接成功")
            testingId.value = null
        }
    }

    fun providerById(id: Long): AiProviderEntity? = providers.value.firstOrNull { it.id == id }
}
