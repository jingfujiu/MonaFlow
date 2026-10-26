package com.monaflow.app.data.repository

import com.monaflow.app.core.common.MonaDate
import com.monaflow.app.data.local.MonaFlowDatabase
import com.monaflow.app.data.local.entity.CountdownEntity
import kotlinx.coroutines.flow.Flow

class CountdownRepository(private val db: MonaFlowDatabase) {
    private val dao = db.countdownDao()

    fun observeAll(): Flow<List<CountdownEntity>> = dao.observeAll()

    suspend fun byId(id: Long): CountdownEntity? = dao.byId(id)
    suspend fun all(): List<CountdownEntity> = dao.all()

    suspend fun upsert(countdown: CountdownEntity): Long {
        return if (countdown.id == 0L) {
            dao.insert(countdown.copy(createdAt = System.currentTimeMillis()))
        } else {
            dao.update(countdown)
            countdown.id
        }
    }

    suspend fun delete(id: Long) {
        db.attachmentDao().deleteByOwner(OWNER_COUNTDOWN, id)
        dao.delete(id)
    }

    fun daysOf(c: CountdownEntity, today: java.time.LocalDate = MonaDate.today()) =
        MonaDate.countdownDays(c.targetEpochMillis, c.timeZoneId, c.isRecurring, c.recurrenceRule, today)

    companion object {
        const val OWNER_COUNTDOWN = "countdown"
    }
}
