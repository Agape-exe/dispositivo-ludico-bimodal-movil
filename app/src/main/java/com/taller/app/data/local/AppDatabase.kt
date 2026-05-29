package com.taller.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.taller.app.data.local.dao.ActivityDao
import com.taller.app.data.local.dao.AttemptDao
import com.taller.app.data.local.dao.QuestionDao
import com.taller.app.data.local.dao.SessionDao
import com.taller.app.data.local.dao.TechnicalEventDao
import com.taller.app.data.local.entity.ActivityEntity
import com.taller.app.data.local.entity.AttemptEntity
import com.taller.app.data.local.entity.QuestionEntity
import com.taller.app.data.local.entity.SessionEntity
import com.taller.app.data.local.entity.TechnicalEventEntity

@Database(
    entities = [
        ActivityEntity::class,
        QuestionEntity::class,
        SessionEntity::class,
        AttemptEntity::class,
        TechnicalEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun activityDao(): ActivityDao
    abstract fun questionDao(): QuestionDao
    abstract fun sessionDao(): SessionDao
    abstract fun attemptDao(): AttemptDao
    abstract fun technicalEventDao(): TechnicalEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "taller_app_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
