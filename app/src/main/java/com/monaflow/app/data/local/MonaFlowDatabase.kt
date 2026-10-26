package com.monaflow.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.monaflow.app.data.local.dao.AiProviderDao
import com.monaflow.app.data.local.dao.AiUsageDao
import com.monaflow.app.data.local.dao.AttachmentDao
import com.monaflow.app.data.local.dao.BackupDao
import com.monaflow.app.data.local.dao.CountdownDao
import com.monaflow.app.data.local.dao.HabitDao
import com.monaflow.app.data.local.dao.ItemDao
import com.monaflow.app.data.local.dao.NoteDao
import com.monaflow.app.data.local.dao.TaskDao
import com.monaflow.app.data.local.entity.AiProviderEntity
import com.monaflow.app.data.local.entity.AiUsageEntity
import com.monaflow.app.data.local.entity.AttachmentEntity
import com.monaflow.app.data.local.entity.CategoryEntity
import com.monaflow.app.data.local.entity.CountdownEntity
import com.monaflow.app.data.local.entity.HabitCheckInEntity
import com.monaflow.app.data.local.entity.HabitEntity
import com.monaflow.app.data.local.entity.ItemEntity
import com.monaflow.app.data.local.entity.NoteEntity
import com.monaflow.app.data.local.entity.NoteTagCrossRef
import com.monaflow.app.data.local.entity.TagEntity
import com.monaflow.app.data.local.entity.TaskEntity

@Database(
    entities = [
        NoteEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        CategoryEntity::class,
        HabitEntity::class,
        HabitCheckInEntity::class,
        ItemEntity::class,
        TaskEntity::class,
        CountdownEntity::class,
        AttachmentEntity::class,
        AiProviderEntity::class,
        AiUsageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MonaFlowDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun habitDao(): HabitDao
    abstract fun itemDao(): ItemDao
    abstract fun taskDao(): TaskDao
    abstract fun countdownDao(): CountdownDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun aiProviderDao(): AiProviderDao
    abstract fun aiUsageDao(): AiUsageDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val DB_NAME = "monaflow.db"

        @Volatile
        private var instance: MonaFlowDatabase? = null

        fun get(context: Context): MonaFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MonaFlowDatabase::class.java,
                    DB_NAME,
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
    }
}

/** 数据库升级只允许 Migration，禁止破坏性重建。 */
val ALL_MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf()
