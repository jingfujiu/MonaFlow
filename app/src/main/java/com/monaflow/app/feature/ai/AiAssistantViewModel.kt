package com.monaflow.app.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monaflow.app.MonaFlowApp
import com.monaflow.app.core.security.KeystoreVault
import com.monaflow.app.data.ai.AiChatRequest
import com.monaflow.app.data.ai.AiErrorType
import com.monaflow.app.data.ai.AiException
import com.monaflow.app.data.ai.AiMessage
import com.monaflow.app.data.ai.AiService
import com.monaflow.app.data.ai.StructuredOutputParser
import com.monaflow.app.data.ai.UiCandidate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

data class ChatEntry(
    val role: String, // user / assistant / error
    val text: String,
)

data class AiAssistantState(
    val hasProvider: Boolean = false,
    val providerName: String = "",
    val messages: List<ChatEntry> = emptyList(),
    val busy: Boolean = false,
    val pendingCandidates: List<UiCandidate> = emptyList(), // 非空时弹出预览确认
)

class AiAssistantViewModel(private val app: MonaFlowApp) : ViewModel() {

    private val vault = KeystoreVault(app)
    private val aiService = AiService(app.database, vault)

    private val _state = MutableStateFlow(AiAssistantState())
    val state: StateFlow<AiAssistantState> = _state

    private var chatJob: Job? = null

    init {
        viewModelScope.launch {
            val provider = app.database.aiProviderDao().defaultProvider()
                ?: app.database.aiProviderDao().all().firstOrNull { it.isEnabled }
            _state.value = _state.value.copy(
                hasProvider = provider != null,
                providerName = provider?.displayName ?: "",
            )
        }
    }

    fun cancel() {
        chatJob?.cancel()
        _state.value = _state.value.copy(busy = false)
    }

    /** 自由问答 / 今日建议。 */
    fun send(text: String, todayPlanMode: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.busy) return
        chatJob = viewModelScope.launch {
            val provider = app.database.aiProviderDao().defaultProvider()
                ?: app.database.aiProviderDao().all().firstOrNull { it.isEnabled }
            if (provider == null) {
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatEntry("error", "尚未配置 AI 服务商，请先到设置中添加。"),
                )
                return@launch
            }

            val contextBlock = if (todayPlanMode) buildTodayContext() else ""
            val systemPrompt = buildString {
                append("你是 MonaFlow（个人效率应用）中的助手，回答简洁、可执行，使用简体中文。")
                append("今天是 ${LocalDate.now()}。")
                if (contextBlock.isNotBlank()) append("\n用户今日数据：\n$contextBlock")
            }

            val userEntry = ChatEntry("user", trimmed)
            _state.value = _state.value.copy(
                messages = _state.value.messages + userEntry,
                busy = true,
            )
            try {
                val history = _state.value.messages
                    .filter { it.role == "user" || it.role == "assistant" }
                    .takeLast(12)
                    .map { AiMessage(it.role, it.text) }
                val result = aiService.chat(
                    provider.id,
                    AiChatRequest(
                        messages = listOf(AiMessage("system", systemPrompt)) + history,
                    ),
                )
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatEntry("assistant", result.content),
                    busy = false,
                )
            } catch (e: AiException) {
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatEntry("error", "${e.type.label}：${e.message}"),
                    busy = false,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatEntry("error", "已取消，未写入任何数据。"),
                    busy = false,
                )
                throw e
            }
        }
    }

    /** 自然语言整理 → 解析为候选（仅解析，不写库；确认后写入）。 */
    fun organize(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.busy) return
        chatJob = viewModelScope.launch {
            val provider = app.database.aiProviderDao().defaultProvider()
                ?: app.database.aiProviderDao().all().firstOrNull { it.isEnabled }
            if (provider == null) {
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatEntry("error", "尚未配置 AI 服务商。"),
                )
                return@launch
            }
            _state.value = _state.value.copy(
                messages = _state.value.messages + ChatEntry("user", "✂️ 整理：$trimmed"),
                busy = true,
            )
            try {
                val system = buildString {
                    append("你是个人效率应用的智能整理器。把用户的输入解析为结构化条目。")
                    append("今天是 ${LocalDate.now()}。")
                    append("只输出 JSON，格式：{\"items\":[{\"type\":\"task\",\"title\":\"...\",\"dueDate\":\"yyyy-MM-dd 或空\",\"priority\":0-3,\"notes\":\"...\"}")
                    append(",{\"type\":\"countdown\",\"title\":\"...\",\"date\":\"yyyy-MM-dd\",\"recurring\":\"true|false\"}")
                    append(",{\"type\":\"habit\",\"name\":\"...\",\"schedule\":\"每天|每周一三|隔天\"}")
                    append(",{\"type\":\"item\",\"name\":\"...\",\"category\":\"...\"}]}")
                    append("没有的日期字段留空字符串；不要输出 JSON 以外的任何文字。")
                }
                val result = aiService.chat(
                    provider.id,
                    AiChatRequest(
                        messages = listOf(
                            AiMessage("system", system),
                            AiMessage("user", trimmed),
                        ),
                        temperature = 0.1,
                    ),
                )
                val candidates = StructuredOutputParser.parseCandidates(result.content)
                if (candidates.isEmpty()) {
                    _state.value = _state.value.copy(
                        messages = _state.value.messages + ChatEntry("error", "AI 未识别出可创建的条目，请换个说法。"),
                        busy = false,
                    )
                } else {
                    _state.value = _state.value.copy(pendingCandidates = candidates, busy = false)
                }
            } catch (e: IllegalArgumentException) {
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatEntry("error", "AI 返回格式无效（${e.message}），未创建任何数据。"),
                    busy = false,
                )
            } catch (e: AiException) {
                _state.value = _state.value.copy(
                    messages = _state.value.messages + ChatEntry("error", "${e.type.label}：${e.message}"),
                    busy = false,
                )
            }
        }
    }

    /** 用户确认候选后写入数据库（仅写确认的条目）。 */
    fun confirmCandidates(selected: List<UiCandidate>, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            var written = 0
            val zone = java.time.ZoneId.systemDefault()
            val today = LocalDate.now()
            selected.forEach { c ->
                runCatching {
                    when (c) {
                        is UiCandidate.Task -> {
                            val due = c.dueDate?.let {
                                runCatching {
                                    LocalDate.parse(it).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                                }.getOrNull()
                            }
                            app.taskRepository.upsert(
                                com.monaflow.app.data.local.entity.TaskEntity(
                                    title = c.title,
                                    description = c.notes,
                                    priority = c.priority,
                                    createdAt = System.currentTimeMillis(),
                                    dueAt = due,
                                ),
                            )
                            written++
                        }
                        is UiCandidate.CountdownCandidate -> {
                            val date = runCatching { LocalDate.parse(c.date) }.getOrNull() ?: return@runCatching
                            app.countdownRepository.upsert(
                                com.monaflow.app.data.local.entity.CountdownEntity(
                                    title = c.title,
                                    targetEpochMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
                                    timeZoneId = zone.id,
                                    isRecurring = c.recurring,
                                    recurrenceRule = if (c.recurring) "FREQ=YEARLY;INTERVAL=1" else null,
                                    createdAt = System.currentTimeMillis(),
                                ),
                            )
                            written++
                        }
                        is UiCandidate.HabitCandidate -> {
                            val rule = mapScheduleText(c.schedule)
                            app.habitRepository.upsert(
                                com.monaflow.app.data.local.entity.HabitEntity(
                                    name = c.name,
                                    scheduleRule = rule.encode(),
                                    startDate = today.toEpochDay(),
                                    createdAt = System.currentTimeMillis(),
                                ),
                            )
                            written++
                        }
                        is UiCandidate.ItemCandidate -> {
                            app.itemRepository.upsert(
                                com.monaflow.app.data.local.entity.ItemEntity(
                                    name = c.name,
                                    category = c.category,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis(),
                                ),
                            )
                            written++
                        }
                    }
                }
            }
            _state.value = _state.value.copy(
                pendingCandidates = emptyList(),
                messages = _state.value.messages + ChatEntry("assistant", "已创建 $written 条（按你的确认）。"),
            )
            onDone(written)
        }
    }

    fun dismissCandidates() {
        _state.value = _state.value.copy(pendingCandidates = emptyList())
    }

    /** 今日计划建议入口：把今日数据发给 AI。 */
    fun suggestTodayPlan() {
        send("请基于我今天的任务和习惯，给出简短的今日计划建议。", todayPlanMode = true)
    }

    private suspend fun buildTodayContext(): String {
        val today = LocalDate.now()
        val tasks = app.taskRepository.observeToday().first()
            .joinToString("；") { it.title + (if (it.status == 1) "（已完成）" else "") }
        val habits = app.habitRepository.observeActive().first()
            .joinToString("；") { it.name }
        return buildString {
            append("今日待办：").append(tasks.ifBlank { "无" }).append('\n')
            append("进行中的习惯：").append(habits.ifBlank { "无" })
        }
    }

    private fun mapScheduleText(text: String): com.monaflow.app.core.common.RecurrenceRule {
        val t = text.trim()
        val dayMap = mapOf(
            "一" to java.time.DayOfWeek.MONDAY, "二" to java.time.DayOfWeek.TUESDAY,
            "三" to java.time.DayOfWeek.WEDNESDAY, "四" to java.time.DayOfWeek.THURSDAY,
            "五" to java.time.DayOfWeek.FRIDAY, "六" to java.time.DayOfWeek.SATURDAY,
            "日" to java.time.DayOfWeek.SUNDAY, "天" to java.time.DayOfWeek.SUNDAY,
        )
        if (t.contains("周")) {
            val days = dayMap.entries.filter { t.contains(it.key) }.map { it.value }.toSet()
            if (days.isNotEmpty()) return com.monaflow.app.core.common.RecurrenceRule.weekly(days)
        }
        if (t.contains("隔")) return com.monaflow.app.core.common.RecurrenceRule.daily(2)
        return com.monaflow.app.core.common.RecurrenceRule.daily()
    }
}
