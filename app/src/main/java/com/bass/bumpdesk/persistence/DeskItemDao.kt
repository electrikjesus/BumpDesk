package com.bass.bumpdesk.persistence

import androidx.room.*

@Dao
interface DeskItemDao {
    @Query("SELECT * FROM desk_items WHERE layoutProfileKey = :layoutProfileKey")
    suspend fun getItemsForProfile(layoutProfileKey: String): List<DeskItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllItems(items: List<DeskItem>)

    @Query("DELETE FROM desk_items WHERE layoutProfileKey = :layoutProfileKey")
    suspend fun deleteItemsForProfile(layoutProfileKey: String)

    @Query("SELECT * FROM desk_piles WHERE layoutProfileKey = :layoutProfileKey")
    suspend fun getPilesForProfile(layoutProfileKey: String): List<DeskPile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPiles(piles: List<DeskPile>)

    @Query("DELETE FROM desk_piles WHERE layoutProfileKey = :layoutProfileKey")
    suspend fun deletePilesForProfile(layoutProfileKey: String)

    @Query("SELECT EXISTS(SELECT 1 FROM desk_items WHERE layoutProfileKey = :layoutProfileKey)")
    suspend fun hasItemsForProfile(layoutProfileKey: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM desk_piles WHERE layoutProfileKey = :layoutProfileKey)")
    suspend fun hasPilesForProfile(layoutProfileKey: String): Boolean

    @Transaction
    suspend fun replaceProfile(
        layoutProfileKey: String,
        items: List<DeskItem>,
        piles: List<DeskPile>,
    ) {
        deleteItemsForProfile(layoutProfileKey)
        deletePilesForProfile(layoutProfileKey)
        if (items.isNotEmpty()) {
            insertAllItems(items)
        }
        if (piles.isNotEmpty()) {
            insertAllPiles(piles)
        }
    }
}
