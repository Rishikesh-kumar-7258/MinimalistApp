package com.minimalist.launcher.core.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageDao {

    @Query("SELECT * FROM app_usage WHERE date = :date ORDER BY usageMs DESC")
    fun getUsageForDate(date: String): Flow<List<AppUsageEntity>>

    @Query("SELECT * FROM app_usage WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    suspend fun getUsageForRange(startDate: String, endDate: String): List<AppUsageEntity>

    @Query("SELECT COALESCE(SUM(usageMs), 0) FROM app_usage WHERE date = :date")
    suspend fun getTotalUsageMs(date: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<AppUsageEntity>)
}
