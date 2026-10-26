package com.monaflow.app.data.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OpenAI Chat Completions 兼容协议适配器。
 * 适用于 DeepSeek / Kimi(Moonshot) / 智谱 GLM / 通义千问兼容模式 / OpenAI 及其他兼容端点；
 * 其他协议必须另写适配器，不得假设只换 URL 就兼容。
 */
class OpenAiCompatAdapter(
    private val baseClient: OkHttpClient,
) : AiProtocolAdapter {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Serializable
    private data class WireRequest(
        val model: String,
        val messages: List<AiMessage>,
        val temperature: Double,
        val stream: Boolean = false,
        val max_tokens: Int? = null,
    )

    @Serializable
    private data class WireResponse(
        val choices: List<Choice> = emptyList(),
        val usage: Usage? = null,
        val error: WireError? = null,
    ) {
        @Serializable
        data class Choice(val index: Int = 0, val message: Msg? = null) {
            @Serializable
            data class Msg(val role: String = "assistant", val content: String? = null)
        }

        @Serializable
        data class Usage(
            val prompt_tokens: Long? = null,
            val completion_tokens: Long? = null,
        )

        @Serializable
        data class WireError(val message: String? = null, val type: String? = null)
    }

    override suspend fun chat(config: AiRuntimeConfig, request: AiChatRequest): AiChatResult {
        val url = buildUrl(config.baseUrl)
        val client = baseClient.newBuilder()
            .connectTimeout(config.connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()

        val bodyObj = WireRequest(
            model = config.modelName,
            messages = request.messages,
            temperature = request.temperature,
            max_tokens = request.maxTokens,
        )
        val body = json.encodeToString(WireRequest.serializer(), bodyObj)
            .toRequestBody("application/json".toMediaType())

        val builder = Request.Builder()
            .url(url)
            .post(body)
            .header("Authorization", "Bearer ${config.apiKey.orEmpty()}")
            .header("Content-Type", "application/json")
        config.customHeaders.forEach { (k, v) ->
            if (k.isNotBlank() && !k.equals("authorization", ignoreCase = true)) builder.header(k, v)
        }

        val call = client.newCall(builder.build())
        val started = System.currentTimeMillis()
        val raw = try {
            call.awaitSuspend()
        } catch (e: IOException) {
            throw classify(e)
        } finally {
            // 取消场景由 awaitSuspend 的 invokeOnCancellation 处理
        }
        val latency = System.currentTimeMillis() - started

        return parseResponse(raw, latency)
    }

    internal fun parseResponse(raw: String, latencyMs: Long): AiChatResult {
        val parsed = runCatching { json.decodeFromString(WireResponse.serializer(), raw) }
            .getOrElse {
                throw AiException(AiErrorType.BAD_RESPONSE, "响应不是合法的 OpenAI 兼容 JSON：${it.message}")
            }
        parsed.error?.let { err ->
            throw AiException(
                AiErrorType.BAD_RESPONSE,
                "服务返回错误：${err.message ?: err.type ?: "unknown"}",
            )
        }
        val content = parsed.choices.firstOrNull()?.message?.content
            ?: throw AiException(AiErrorType.BAD_RESPONSE, "响应缺少 choices[0].message.content")
        return AiChatResult(
            content = content,
            promptTokens = parsed.usage?.prompt_tokens,
            completionTokens = parsed.usage?.completion_tokens,
            latencyMs = latencyMs,
        )
    }

    companion object {
        /** 拼接 /chat/completions；容忍末尾斜杠与已带 v1 的写法。 */
        internal fun buildUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            if (trimmed.isEmpty()) throw AiException(AiErrorType.URL_INVALID, "Base URL 为空")
            val withScheme = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                "https://$trimmed"
            } else {
                trimmed
            }
            return try {
                val parsed = java.net.URI(withScheme)
                if (parsed.scheme != "http" && parsed.scheme != "https") {
                    throw AiException(AiErrorType.URL_INVALID, "仅支持 http/https：$baseUrl")
                }
                if (withScheme.endsWith("/chat/completions")) withScheme else "$withScheme/chat/completions"
            } catch (e: AiException) {
                throw e
            } catch (e: Exception) {
                throw AiException(AiErrorType.URL_INVALID, "Base URL 无法解析：$baseUrl")
            }
        }

        /** 把底层 IOException 归类为业务错误类型。 */
        internal fun classify(e: IOException): AiException = when (e) {
            is AiException -> e
            is SocketTimeoutException -> AiException(AiErrorType.TIMEOUT, "请求超时：${e.message}")
            is SSLException -> AiException(AiErrorType.TLS, "TLS/证书错误：${e.message}")
            is UnknownHostException -> AiException(AiErrorType.NETWORK, "域名解析失败（DNS）：${e.message}")
            is MalformedURLException -> AiException(AiErrorType.URL_INVALID, "URL 格式错误：${e.message}")
            else -> if (e.message?.contains("Canceled", ignoreCase = true) == true || e is java.io.InterruptedIOException) {
                AiException(AiErrorType.CANCELLED, "请求已取消")
            } else {
                AiException(AiErrorType.NETWORK, "网络错误：${e.message ?: e.javaClass.simpleName}")
            }
        }

        /** 非 2xx 状态码 → 分类（不重试的类型在此确定）。 */
        internal fun httpError(code: Int, bodySnippet: String): AiException = when (code) {
            401, 403 -> AiException(AiErrorType.AUTH, "鉴权失败($code)：$bodySnippet", code)
            404 -> AiException(AiErrorType.NOT_FOUND, "接口不存在(404)：$bodySnippet", code)
            429 -> AiException(AiErrorType.RATE_LIMITED, "限流(429)：$bodySnippet", code)
            in 500..599 -> AiException(AiErrorType.SERVER, "服务端错误($code)：$bodySnippet", code)
            else -> AiException(AiErrorType.BAD_RESPONSE, "HTTP $code：$bodySnippet", code)
        }
    }
}

/** OkHttp Call 的协程封装：协程取消时调用 call.cancel()。 */
private suspend fun Call.awaitSuspend(): String =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = runCatching { it.body?.string() }.getOrNull()
                    if (cont.isActive) {
                        if (it.isSuccessful && body != null) {
                            cont.resume(body)
                        } else {
                            val snippet = (body ?: "").take(300)
                            cont.resumeWithException(
                                OpenAiCompatAdapter.httpError(it.code, snippet),
                            )
                        }
                    }
                }
            }
        })
    }
