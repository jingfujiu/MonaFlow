package com.monaflow.app.data.ai

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiAdapterTest {

    private lateinit var server: MockWebServer
    private lateinit var adapter: OpenAiCompatAdapter

    private val config get() = AiRuntimeConfig(
        providerId = 1,
        displayName = "test",
        baseUrl = server.url("/v1").toString(),
        modelName = "test-model",
        apiKey = "sk-test",
        protocolType = "openai_compat",
        customHeaders = emptyMap(),
        connectTimeoutMs = 5_000,
        readTimeoutMs = 5_000,
    )

    private val request = AiChatRequest(messages = listOf(AiMessage("user", "hi")))

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        adapter = OpenAiCompatAdapter(OkHttpClient())
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun okResponse(content: String = "pong", prompt: Long = 10, completion: Long = 5) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(
            """{"id":"1","object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant","content":"$content"}}],
               "usage":{"prompt_tokens":$prompt,"completion_tokens":$completion}}""",
        )

    @Test
    fun `successful chat parses content and usage`() = runTest {
        server.enqueue(okResponse("你好", 12, 34))
        val result = adapter.chat(config, request)
        assertEquals("你好", result.content)
        assertEquals(12L, result.promptTokens)
        assertEquals(34L, result.completionTokens)
        assertTrue(result.latencyMs >= 0)
        // 请求携带鉴权头与模型名
        val recorded = server.takeRequest()
        assertEquals("Bearer sk-test", recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains("\"model\":\"test-model\""))
        assertTrue(recorded.path!!.endsWith("/chat/completions"))
    }

    @Test
    fun `baseUrl with trailing slash and bare host builds correctly`() {
        assertEquals("https://api.x.com/v1/chat/completions", OpenAiCompatAdapter.buildUrl("https://api.x.com/v1/"))
        assertEquals("https://api.x.com/v1/chat/completions", OpenAiCompatAdapter.buildUrl("api.x.com/v1"))
        assertEquals("http://127.0.0.1:8080/v1/chat/completions", OpenAiCompatAdapter.buildUrl("http://127.0.0.1:8080/v1"))
    }

    @Test
    fun `invalid url classified`() {
        val e = runCatching { OpenAiCompatAdapter.buildUrl("   ") }
            .exceptionOrNull() as AiException
        assertEquals(AiErrorType.URL_INVALID, e.type)
    }

    @Test
    fun `401 classified as auth without retry semantics baked in`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        val e = runCatching { adapter.chat(config, request) }.exceptionOrNull() as AiException
        assertEquals(AiErrorType.AUTH, e.type)
        assertEquals(401, e.httpCode)
    }

    @Test
    fun `429 classified as rate limited`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        val e = runCatching { adapter.chat(config, request) }.exceptionOrNull() as AiException
        assertEquals(AiErrorType.RATE_LIMITED, e.type)
    }

    @Test
    fun `404 classified as not found`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("no route"))
        val e = runCatching { adapter.chat(config, request) }.exceptionOrNull() as AiException
        assertEquals(AiErrorType.NOT_FOUND, e.type)
    }

    @Test
    fun `500 classified as server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        val e = runCatching { adapter.chat(config, request) }.exceptionOrNull() as AiException
        assertEquals(AiErrorType.SERVER, e.type)
    }

    @Test
    fun `malformed json classified as bad response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("this is not json"))
        val e = runCatching { adapter.chat(config, request) }.exceptionOrNull() as AiException
        assertEquals(AiErrorType.BAD_RESPONSE, e.type)
    }

    @Test
    fun `json without choices classified as bad response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"object":"chat.completion"}"""))
        val e = runCatching { adapter.chat(config, request) }.exceptionOrNull() as AiException
        assertEquals(AiErrorType.BAD_RESPONSE, e.type)
    }

    @Test
    fun `dns failure classified as network`() = runTest {
        val badConfig = config.copy(baseUrl = "https://nonexistent-host-monaflow-test.invalid")
        val e = runCatching { adapter.chat(badConfig, request) }.exceptionOrNull() as AiException
        assertEquals(AiErrorType.NETWORK, e.type)
    }

    @Test
    fun `response body error field surfaces as bad response`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"error":{"message":"model not loaded","type":"server_error"}}"""),
        )
        val e = runCatching { adapter.chat(config, request) }.exceptionOrNull() as AiException
        assertEquals(AiErrorType.BAD_RESPONSE, e.type)
        assertTrue(e.message!!.contains("model not loaded"))
    }
}
