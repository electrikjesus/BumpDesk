package com.bass.bumpdesk.persistence

import androidx.room.*

@Dao
interface DeskItemDao {
    @Query("SELECT * FROM desk_items")
    suspend fun getAllItems(): List<DeskItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllItems(items: List<DeskItem>)

    @Query("DELETE FROM desk_items")
    suspend fun deleteAllItems()

    @Query("SELECT * FROM desk_piles")
    suspend fun getAllPiles(): List<DeskPile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPiles(piles: List<DeskPile>)

    @Query("DELETE FROM desk_piles")
    suspend fun deleteAllPiles()
}
