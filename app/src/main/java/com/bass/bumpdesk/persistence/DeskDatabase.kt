package com.bass.bumpdesk.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DeskItem::class, DeskPile::class], version = 2)
abstract class DeskDatabase : RoomDatabase() {
    abstract fun deskItemDao(): DeskItemDao

    companion object {
        @Volatile
        private var INSTANCE: DeskDatabase? = null

        fun getDatabase(context: Context): DeskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DeskDatabase::class.java,
                    "desk_database"
                )
                .fallbackToDestructiveMigration() // Simple for now, can be replaced with migrations later
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
