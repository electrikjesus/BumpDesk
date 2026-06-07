package com.bass.bumpdesk.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DeskItem::class, DeskPile::class], version = 3)
abstract class DeskDatabase : RoomDatabase() {
    abstract fun deskItemDao(): DeskItemDao

    companion object {
        @Volatile
        private var INSTANCE: DeskDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS desk_items_new (
                        id TEXT NOT NULL,
                        layoutProfileKey TEXT NOT NULL,
                        type TEXT NOT NULL,
                        packageName TEXT,
                        appWidgetId INTEGER,
                        text TEXT,
                        posX REAL NOT NULL,
                        posY REAL NOT NULL,
                        posZ REAL NOT NULL,
                        sizeX REAL NOT NULL,
                        sizeZ REAL NOT NULL,
                        surface TEXT NOT NULL,
                        isPinned INTEGER NOT NULL,
                        scale REAL NOT NULL,
                        pileId TEXT,
                        PRIMARY KEY(id, layoutProfileKey)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO desk_items_new (
                        id, layoutProfileKey, type, packageName, appWidgetId, text,
                        posX, posY, posZ, sizeX, sizeZ, surface, isPinned, scale, pileId
                    )
                    SELECT
                        id, '${LayoutProfileKeys.LEGACY}', type, packageName, appWidgetId, text,
                        posX, posY, posZ, sizeX, sizeZ, surface, isPinned, scale, pileId
                    FROM desk_items
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE desk_items")
                db.execSQL("ALTER TABLE desk_items_new RENAME TO desk_items")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS desk_piles_new (
                        name TEXT NOT NULL,
                        layoutProfileKey TEXT NOT NULL,
                        posX REAL NOT NULL,
                        posY REAL NOT NULL,
                        posZ REAL NOT NULL,
                        layoutMode TEXT NOT NULL,
                        surface TEXT NOT NULL,
                        scale REAL NOT NULL,
                        isSystem INTEGER NOT NULL,
                        isFannedOut INTEGER NOT NULL,
                        PRIMARY KEY(name, layoutProfileKey)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO desk_piles_new (
                        name, layoutProfileKey, posX, posY, posZ, layoutMode, surface,
                        scale, isSystem, isFannedOut
                    )
                    SELECT
                        name, '${LayoutProfileKeys.LEGACY}', posX, posY, posZ, layoutMode, surface,
                        scale, isSystem, isFannedOut
                    FROM desk_piles
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE desk_piles")
                db.execSQL("ALTER TABLE desk_piles_new RENAME TO desk_piles")
            }
        }

        fun getDatabase(context: Context): DeskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DeskDatabase::class.java,
                    "desk_database",
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
