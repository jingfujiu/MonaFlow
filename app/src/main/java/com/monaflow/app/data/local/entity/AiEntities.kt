package com.monaflow.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 服务商配置。protocolType: "openai_compat"（OpenAI Chat Completions 兼容）。
 * encryptedCredentialRef 为经 Android Keystore AES-GCM 加密后的 Base64 API Key，
 * 明文永不落库、不打日志。
 */
@Entity(tableName = "ai_providers", indices = [Index("isEnabled")])
data class AiProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val baseUrl: String,
    val protocolType: String = "openai_compat",
    val modelName: String,
    val encryptedCredentialRef: String? = null,
    val customHeadersJson: String? = null,
    val connectTimeoutMs: Int = 15_000,
    val readTimeoutMs: Int = 60_000,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val lastTestStatus: String? = null, // ok / auth_failed / rate_limited / not_found / server_error / timeout / network / bad_response
    val lastTestAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** AI 用量记录。不默认记录完整私人内容；estimatedCost 为 null 表示无法估算。 */
@Entity(
    tableName = "ai_usage",
    indices = [Index("timestamp"), Index("providerId")],
)
data class AiUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: Long,
    val modelName: String,
    val timestamp: Long,
    val requestStatus: String, // ok / error / cancelled
    val latencyMs: Long,
    val promptTokens: Long? = null,
    val completionTokens: Long? = null,
    val estimatedCost: String? = null,
)
