package com.bass.bumpdesk.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DeskItem::class], version = 1)
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
