package com.monaflow.app.data.ai

import kotlinx.serialization.Serializable

/** 错误分类：区分 URL、DNS/网络、TLS、401/403、404、429、5xx、超时、响应格式错误与取消。 */
enum class AiErrorType(val label: String) {
    URL_INVALID("地址格式错误"),
    NETWORK("网络连接失败（DNS/连接）"),
    TLS("TLS/证书错误"),
    AUTH("鉴权失败（401/403，请检查 API Key）"),
    NOT_FOUND("接口不存在（404，请检查 Base URL 与模型名）"),
    RATE_LIMITED("限流（429，请稍后再试）"),
    SERVER("服务端错误（5xx）"),
    TIMEOUT("请求超时"),
    BAD_RESPONSE("响应格式错误"),
    CANCELLED("已取消"),
}

class AiException(
    val type: AiErrorType,
    message: String,
    val httpCode: Int? = null,
) : Exception(message)

@Serializable
data class AiMessage(
    val role: String, // system / user / assistant
    val content: String,
)

data class AiChatRequest(
    val messages: List<AiMessage>,
    val temperature: Double = 0.4,
    val maxTokens: Int? = null,
)

data class AiChatResult(
    val content: String,
    val promptTokens: Long? = null,
    val completionTokens: Long? = null,
    val latencyMs: Long,
)

/** 服务商运行时配置（解密后内存态，永不序列化落库）。 */
data class AiRuntimeConfig(
    val providerId: Long,
    val displayName: String,
    val baseUrl: String,
    val modelName: String,
    val apiKey: String?,
    val protocolType: String,
    val customHeaders: Map<String, String>,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
)

interface AiProtocolAdapter {
    /** 执行对话补全。支持协程取消（取消后不产生写入）。 */
    suspend fun chat(config: AiRuntimeConfig, request: AiChatRequest): AiChatResult
}
