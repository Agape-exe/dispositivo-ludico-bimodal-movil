package com.taller.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.taller.app.data.local.entity.ActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: ActivityEntity): Long

    @Update
    suspend fun update(activity: ActivityEntity)

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: Long): ActivityEntity?

    @Query("SELECT * FROM activities WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getAllOrderedByCreated(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE isActive = 1 ORDER BY updatedAt DESC")
    fun getAllOrderedByUpdated(): Flow<List<ActivityEntity>>

    @Query("UPDATE activities SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}
