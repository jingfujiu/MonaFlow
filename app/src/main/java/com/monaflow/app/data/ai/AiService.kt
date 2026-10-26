package com.monaflow.app.data.ai

import com.monaflow.app.core.security.CredentialVault
import com.monaflow.app.data.local.MonaFlowDatabase
import com.monaflow.app.data.local.entity.AiProviderEntity
import com.monaflow.app.data.local.entity.AiUsageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient

/**
 * AI 服务门面：服务商解析 → 凭证解密 → 协议适配 → 有限重试 → 用量落库。
 * 用量记录只含服务商/模型/时间/状态/延迟/Token，不记录完整私人内容。
 */
class AiService(
    private val db: MonaFlowDatabase,
    private val vault: CredentialVault,
    client: OkHttpClient = OkHttpClient(),
) {

    private val adapterFor: Map<String, AiProtocolAdapter> = mapOf(
        PROTOCOL_OPENAI_COMPAT to OpenAiCompatAdapter(client),
    )

    private val usageDao get() = db.aiUsageDao()
    private val providerDao get() = db.aiProviderDao()

    companion object {
        const val PROTOCOL_OPENAI_COMPAT = "openai_compat"

        /** 鉴权失败/限流/格式错误不重试；仅网络/超时/5xx 有限重试。 */
        private val RETRYABLE = setOf(AiErrorType.NETWORK, AiErrorType.TIMEOUT, AiErrorType.SERVER)
        private const val MAX_RETRIES = 2
    }

    fun supportsProtocol(protocolType: String): Boolean = adapterFor.containsKey(protocolType)

    /** 运行时配置：从实体解密凭证；协议不支持时抛异常而非静默换协议。 */
    fun toRuntimeConfig(entity: AiProviderEntity): AiRuntimeConfig {
        if (!adapterFor.containsKey(entity.protocolType)) {
            throw AiException(AiErrorType.BAD_RESPONSE, "暂不支持的协议类型：${entity.protocolType}")
        }
        return AiRuntimeConfig(
            providerId = entity.id,
            displayName = entity.displayName,
            baseUrl = entity.baseUrl,
            modelName = entity.modelName,
            apiKey = entity.encryptedCredentialRef?.let { vault.decrypt(it) },
            protocolType = entity.protocolType,
            customHeaders = parseHeaders(entity.customHeadersJson),
            connectTimeoutMs = entity.connectTimeoutMs,
            readTimeoutMs = entity.readTimeoutMs,
        )
    }

    private fun parseHeaders(json_: String?): Map<String, String> {
        if (json_.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = Json.parseToJsonElement(json_)
            buildMap {
                (obj as? JsonObject)?.forEach { (k, v) ->
                    if (v is kotlinx.serialization.json.JsonPrimitive && v.isString) put(k, v.content)
                }
            }
        }.getOrDefault(emptyMap())
    }

    /**
     * 对话补全（带有限重试与用量记录）。请求取消时抛 CancellationException 且不写用量之外的数据。
     */
    suspend fun chat(
        providerId: Long,
        request: AiChatRequest,
    ): AiChatResult = withContext(Dispatchers.IO) {
        val entity = providerDao.byId(providerId)
            ?: throw AiException(AiErrorType.URL_INVALID, "服务商不存在")
        if (!entity.isEnabled) throw AiException(AiErrorType.NETWORK, "该服务商已停用")
        val config = toRuntimeConfig(entity)
        val adapter = adapterFor.getValue(entity.protocolType)

        var attempt = 0
        var lastError: AiException? = null
        while (attempt <= MAX_RETRIES) {
            attempt++
            try {
                val result = adapter.chat(config, request)
                logUsage(entity.id, entity.modelName, "ok", result.latencyMs, result.promptTokens, result.completionTokens)
                return@withContext result
            } catch (e: AiException) {
                lastError = e
                val cancellable = e.type == AiErrorType.CANCELLED
                if (cancellable || e.type !in RETRYABLE || attempt > MAX_RETRIES) {
                    logUsage(entity.id, entity.modelName, if (cancellable) "cancelled" else "error", 0, null, null)
                    throw e
                }
                // 指数退避
                kotlinx.coroutines.delay(500L * attempt)
            }
        }
        throw lastError ?: AiException(AiErrorType.NETWORK, "未知错误")
    }

    /** 连接测试：返回错误类型或 null（成功）。并把结果写回 lastTestStatus。 */
    suspend fun testConnection(providerId: Long): AiErrorType? {
        return try {
            chat(
                providerId,
                AiChatRequest(
                    messages = listOf(
                        AiMessage("user", "ping，请只回复 pong"),
                    ),
                    maxTokens = 16,
                ),
            )
            providerDao.recordTestResult(providerId, "ok", System.currentTimeMillis())
            null
        } catch (e: AiException) {
            providerDao.recordTestResult(providerId, e.type.name.lowercase(), System.currentTimeMillis())
            e.type
        }
    }

    private suspend fun logUsage(
        providerId: Long,
        modelName: String,
        status: String,
        latencyMs: Long,
        promptTokens: Long?,
        completionTokens: Long?,
    ) {
        runCatching {
            usageDao.insert(
                AiUsageEntity(
                    providerId = providerId,
                    modelName = modelName,
                    timestamp = System.currentTimeMillis(),
                    requestStatus = status,
                    latencyMs = latencyMs,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    estimatedCost = null, // 无可信定价数据时明确为"未知"，不虚构
                ),
            )
        }
    }
}

/**
 * 结构化输出解析：模型返回的 JSON 必须通过验证才进入预览；
 * 非法时抛 IllegalArgumentException，由调用方提示手动处理（不写入数据库）。
 */
object StructuredOutputParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    /** 从模型输出中提取 JSON（容忍 ```json 代码块包裹）。 */
    fun extractJson(raw: String): JsonElement {
        val cleaned = raw
            .replace("```json", "```")
            .substringAfter("```", raw)
            .substringBefore("```")
            .trim()
            .ifBlank { raw.trim() }
        return runCatching { json.parseToJsonElement(cleaned) }
            .getOrElse { throw IllegalArgumentException("AI 返回的内容不是合法 JSON") }
    }

    /** 解析"候选条目"列表（待办/习惯/倒计时/物品）。 */
    fun parseCandidates(raw: String): List<UiCandidate> {
        val element = extractJson(raw)
        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> element["items"] as? JsonArray
                ?: element["candidates"] as? JsonArray
                ?: throw IllegalArgumentException("缺少 items 数组")
            else -> throw IllegalArgumentException("顶层必须是 JSON 数组或对象")
        }
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val type = (obj["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.lowercase()
                ?: return@mapNotNull null
            fun str(k: String): String = (obj[k] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
            when (type) {
                "task" -> UiCandidate.Task(
                    title = str("title").trim(),
                    dueDate = str("dueDate").takeIf { it.isNotBlank() },
                    priority = str("priority").toIntOrNull()?.coerceIn(0, 3) ?: 0,
                    notes = str("notes"),
                ).takeIf { it.title.isNotBlank() }
                "countdown" -> UiCandidate.CountdownCandidate(
                    title = str("title").trim(),
                    date = str("date"),
                    recurring = (obj["recurring"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true",
                ).takeIf { it.title.isNotBlank() && it.date.isNotBlank() }
                "habit" -> UiCandidate.HabitCandidate(
                    name = str("name").trim(),
                    schedule = str("schedule"),
                ).takeIf { it.name.isNotBlank() }
                "item" -> UiCandidate.ItemCandidate(
                    name = str("name").trim(),
                    category = str("category"),
                ).takeIf { it.name.isNotBlank() }
                else -> null // 未知类型丢弃，不让脏数据入库
            }
        }
    }
}

/** 预览用候选条目：用户确认后才写库。 */
sealed class UiCandidate {
    abstract fun display(): String

    data class Task(val title: String, val dueDate: String?, val priority: Int, val notes: String) : UiCandidate() {
        override fun display(): String = "待办：$title" + (dueDate?.let { "（$it）" } ?: "")
    }

    data class CountdownCandidate(val title: String, val date: String, val recurring: Boolean) : UiCandidate() {
        override fun display(): String = "倒计时：$title（$date${if (recurring) "，每年" else ""}）"
    }

    data class HabitCandidate(val name: String, val schedule: String) : UiCandidate() {
        override fun display(): String = "习惯：$name（$schedule）"
    }

    data class ItemCandidate(val name: String, val category: String) : UiCandidate() {
        override fun display(): String = "物品：$name" + (category.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: "")
    }
}
