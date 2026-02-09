package com.bass.bumpdesk.persistence

import androidx.room.*

@Dao
interface DeskItemDao {
    @Query("SELECT * FROM desk_items")
    suspend fun getAll(): List<DeskItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DeskItem>)

    @Query("DELETE FROM desk_items")
    suspend fun deleteAll()
}
