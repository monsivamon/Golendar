package com.monsivamon.golender.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// ローカル予定データと添付写真を管理するRoomデータベース
@Database(
    entities = [LocalEvent::class, EventPhoto::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    // 予定操作用のDAOを取得
    abstract fun localEventDao(): LocalEventDao
    // 添付写真操作用のDAOを取得
    abstract fun eventPhotoDao(): EventPhotoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // スレッドセーフなシングルトンインスタンスを取得
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "golendar_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}