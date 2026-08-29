package com.monsivamon.golender.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// ローカル予定データを管理するRoomデータベース
@Database(entities = [LocalEvent::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    // 予定操作用のDAOを取得
    abstract fun localEventDao(): LocalEventDao

    companion object {
        // スレッド間の可視性を保証
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
                    // スキーマ変更時にRoom管理テーブルのみ破棄（外部テーブルは保持）
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}