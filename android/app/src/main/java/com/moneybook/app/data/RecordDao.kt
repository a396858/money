package com.moneybook.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {

    @Insert
    suspend fun insert(record: Record): Long

    @Delete
    suspend fun delete(record: Record)

    @Query("SELECT * FROM records ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<Record>>

    @Query("SELECT COUNT(*) FROM records")
    suspend fun count(): Int
}
