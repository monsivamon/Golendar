package com.monsivamon.golender.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// ローカル予定データと添付写真を管理するRoomデータベース。
@Database(
    entities = [LocalEvent::class, EventPhoto::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    // 予定操作用のDAOを取得する。
    abstract fun localEventDao(): LocalEventDao

    // 写真操作用のDAOを取得する。
    abstract fun eventPhotoDao(): EventPhotoDao

    // シングルトンインスタンスとマイグレーションを管理する。
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v3 → v4：event_photos テーブルを追加するマイグレーション。
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `event_photos` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `eventId` INTEGER NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_event_photos_eventId` " +
                            "ON `event_photos` (`eventId`)"
                )
            }
        }

        // スレッドセーフなシングルトンインスタンスを取得する。
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "golendar_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}