package com.wifimanager.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.wifimanager.data.models.RouterConfig

@Dao
interface RouterConfigDao {
    @Query("SELECT * FROM router_configs ORDER BY isActive DESC, lastConnected DESC")
    fun getAllRouters(): LiveData<List<RouterConfig>>

    @Query("SELECT * FROM router_configs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveRouter(): RouterConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: RouterConfig): Long

    @Update
    suspend fun update(config: RouterConfig)

    @Delete
    suspend fun delete(config: RouterConfig)

    @Query("UPDATE router_configs SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE router_configs SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Int)

    @Query("UPDATE router_configs SET lastConnected = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: Int, timestamp: Long)
}
