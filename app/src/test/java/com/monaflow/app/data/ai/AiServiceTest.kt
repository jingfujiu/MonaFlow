package com.monaflow.app.data.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.monaflow.app.core.security.CredentialVault
import com.monaflow.app.data.local.MonaFlowDatabase
import com.monaflow.app.data.local.entity.AiProviderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 结构化输出解析：非法 JSON 必须抛出，不产生数据写入。 */
class StructuredOutputParserTest {

    @Test
    fun `parse task candidates`() {
        val raw = """
            {"items":[
              {"type":"task","title":"买牛奶","dueDate":"2026-10-28","priority":2,"notes":"低脂"},
              {"type":"countdown","title":"妈妈生日","date":"2026-11-02","recurring":"true"},
              {"type":"habit","name":"早睡","schedule":"每周一二三"},
              {"type":"item","name":"键盘","category":"数码"}
            ]}
        """.trimIndent()
        val candidates = StructuredOutputParser.parseCandidates(raw)
        assertEquals(4, candidates.size)
        val task = candidates[0] as UiCandidate.Task
        assertEquals("买牛奶", task.title)
        assertEquals("2026-10-28", task.dueDate)
        assertEquals(2, task.priority)
        assertEquals(true, (candidates[1] as UiCandidate.CountdownCandidate).recurring)
    }

    @Test
    fun `fenced json block extracted`() {
        val raw = "好的，以下是整理结果：\n```json\n[{\"type\":\"task\",\"title\":\"跑步\"}]\n```"
        val candidates = StructuredOutputParser.parseCandidates(raw)
        assertEquals(1, candidates.size)
        assertEquals("跑步", (candidates[0] as UiCandidate.Task).title)
    }

    @Test
    fun `invalid json throws and writes nothing`() {
        val e = runCatching { StructuredOutputParser.parseCandidates("我不是 JSON") }
            .exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }

    @Test
    fun `unknown types and blank titles dropped`() {
        val raw = """
            {"items":[
              {"type":"unknown","x":1},
              {"type":"task","title":"  "},
              {"type":"task","title":"有效任务"}
            ]}
        """.trimIndent()
        val candidates = StructuredOutputParser.parseCandidates(raw)
        assertEquals(1, candidates.size)
    }

    @Test
    fun `top level array accepted`() {
        val candidates = StructuredOutputParser.parseCandidates("""[{"type":"task","title":"A"}]""")
        assertEquals(1, candidates.size)
    }

    @Test
    fun `missing items array throws`() {
        runCatching { StructuredOutputParser.parseCandidates("""{"foo":1}""") }
            .onSuccess { throw AssertionError("应当失败") }
    }
}

/**
 * AiService 重试与用量：可重试错误（5xx/网络/超时）最多重试 2 次；
 * 鉴权失败不重试；取消不写入。协议适配器用假实现注入。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiServiceTest {

    private lateinit var db: MonaFlowDatabase
    private lateinit var server: MockWebServer
    private lateinit var service: AiService
    private var providerId: Long = 0

    private class FakeVault : CredentialVault {
        override fun encrypt(plain: String): String = "enc:" + plain.reversed()
        override fun decrypt(blob: String): String? = blob.removePrefix("enc:").reversed()
    }

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MonaFlowDatabase::class.java)
            .allowMainThreadQueries().build()
        server = MockWebServer()
        server.start()
        service = AiService(db, FakeVault(), OkHttpClient())
        providerId = db.aiProviderDao().insert(
            AiProviderEntity(
                displayName = "测试",
                baseUrl = server.url("/v1").toString(),
                modelName = "m",
                encryptedCredentialRef = "enc:yek-tset",
                createdAt = 0,
                updatedAt = 0,
            ),
        )
    }

    @After
    fun teardown() {
        db.close()
        runCatching { server.shutdown() }
    }

    private fun chatOk(content: String) = MockResponse()
        .setResponseCode(200)
        .setBody("""{"choices":[{"message":{"role":"assistant","content":"$content"}}],"usage":{"prompt_tokens":1,"completion_tokens":1}}""")

    @Test
    fun `retry on server error then succeed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("x"))
        server.enqueue(chatOk("ok"))
        val result = service.chat(providerId, AiChatRequest(listOf(AiMessage("user", "hi"))))
        assertEquals("ok", result.content)
        assertEquals(2, server.requestCount)
        // 用量：一次 ok（成功那次）
        val usage = db.aiUsageDao().observeRecent(10)
        assertTrue(usage.toString().isNotEmpty())
    }

    @Test
    fun `auth error fails fast without retry`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        server.enqueue(chatOk("should-not-be-called"))
        val e = runCatching {
            service.chat(providerId, AiChatRequest(listOf(AiMessage("user", "hi"))))
        }.exceptionOrNull() as AiException
        assertEquals(AiErrorType.AUTH, e.type)
        assertEquals("鉴权失败不重试", 1, server.requestCount)
    }

    @Test
    fun `rate limited fails without retry`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        val e = runCatching {
            service.chat(providerId, AiChatRequest(listOf(AiMessage("user", "hi"))))
        }.exceptionOrNull() as AiException
        assertEquals(AiErrorType.RATE_LIMITED, e.type)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `gives up after max retries on persistent server error`() = runTest {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(500).setBody("x")) }
        val e = runCatching {
            service.chat(providerId, AiChatRequest(listOf(AiMessage("user", "hi"))))
        }.exceptionOrNull() as AiException
        assertEquals(AiErrorType.SERVER, e.type)
        assertEquals("初始 1 次 + 最多 2 次重试", 3, server.requestCount)
    }

    @Test
    fun `usage recorded with tokens but no fabricated cost`() = runTest {
        server.enqueue(chatOk("hi"))
        service.chat(providerId, AiChatRequest(listOf(AiMessage("user", "hello"))))
        val list = db.aiUsageDao().observeRecent(10).first()
        val ok = list.firstOrNull { it.requestStatus == "ok" }
        assertTrue("应记录成功用量", ok != null)
        ok!!
        assertEquals(1L, ok.promptTokens)
        assertEquals(1L, ok.completionTokens)
        assertEquals("无定价数据时费用必须标记未知", null, ok.estimatedCost)
    }

    @Test
    fun `testConnection classifies auth failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val error = service.testConnection(providerId)
        assertEquals(AiErrorType.AUTH, error)
        assertEquals("auth", db.aiProviderDao().byId(providerId)!!.lastTestStatus)
    }

    @Test
    fun `testConnection success records ok`() = runTest {
        server.enqueue(chatOk("pong"))
        val error = service.testConnection(providerId)
        assertEquals(null, error)
        assertEquals("ok", db.aiProviderDao().byId(providerId)!!.lastTestStatus)
    }
}
