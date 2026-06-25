package com.wifimanager.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.wifimanager.data.models.ScheduleRule

@Dao
interface ScheduleRuleDao {
    @Query("SELECT * FROM schedule_rules ORDER BY createdAt DESC")
    fun getAllRules(): LiveData<List<ScheduleRule>>

    @Query("SELECT * FROM schedule_rules WHERE isEnabled = 1")
    suspend fun getActiveRules(): List<ScheduleRule>

    @Query("SELECT * FROM schedule_rules WHERE id = :id")
    suspend fun getRuleById(id: Int): ScheduleRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: ScheduleRule): Long

    @Update
    suspend fun update(rule: ScheduleRule)

    @Delete
    suspend fun delete(rule: ScheduleRule)

    @Query("UPDATE schedule_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Int, enabled: Boolean)
}
