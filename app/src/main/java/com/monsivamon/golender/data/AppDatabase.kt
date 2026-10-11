package com.monsivamon.golender.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// ローカル予定と添付写真とローカルカレンダーを管理する Room データベース
@Database(
    entities = [LocalEvent::class, EventPhoto::class, LocalCalendar::class],
    version = 8,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    // ローカル予定の DAO を取得する
    abstract fun localEventDao(): LocalEventDao

    // 添付写真の DAO を取得する
    abstract fun eventPhotoDao(): EventPhotoDao

    // ローカルカレンダーの DAO を取得する
    abstract fun localCalendarDao(): LocalCalendarDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v3 → v4 : event_photos テーブルを追加するマイグレーション
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

        // v4 → v5 : local_calendars テーブル追加 + local_events.calendarId 追加
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `local_calendars` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `colorArgb` INTEGER NOT NULL,
                        `isVisible` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                // 既定カレンダーを 1 件投入する。
                // 色は 0（自動割り当て）とし、テーマに応じたパレット色を実行時に解決する。
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO `local_calendars` (`name`, `colorArgb`, `isVisible`, `sortOrder`, `isDefault`, `createdAt`) " +
                    "VALUES ('マイカレンダー', 0, 1, 0, 1, $now)"
                )
                // 既存予定に calendarId 列を追加する
                db.execSQL("ALTER TABLE `local_events` ADD COLUMN `calendarId` INTEGER NOT NULL DEFAULT 0")
                // 既存のユーザー予定（祝日以外）を既定カレンダー(id=1)へ紐付ける
                db.execSQL("UPDATE `local_events` SET `calendarId` = 1 WHERE `description` != 'system_holiday'")
            }
        }

        // v5 → v6 : 既定カレンダーの色を「アプリ固定の紫(-9823334)」から
        //            「自動割り当て(0)」へ移行する。
        //            ユーザーが既に別の色に変更していた場合は触らない。
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE `local_calendars` SET `colorArgb` = 0 " +
                    "WHERE `isDefault` = 1 AND `colorArgb` = -9823334"
                )
            }
        }

        // v6 → v7 : LocalEvent.calendarId に @ColumnInfo(defaultValue = "0") を付けた
        //            ことによるスキーマハッシュ変更を反映する。
        //            実際のカラムは v4→v5 の ALTER TABLE で既に DEFAULT 0 付きなので、
        //            ここではスキーマ変更は不要（identity hash の更新のみ）。
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // no-op: Room が identity hash を更新するために必要
            }
        }

        // v7 → v8 : AI 解析（addFromAi）で追加された予定は calendarId=0 のまま
        //           保存されており、可視カレンダーフィルタ（calendarId IN (...)）に
        //           弾かれて画面に表示されない状態だった。既定カレンダー
        //           （isDefault = 1）へ一括で付け替えて救済する。
        //           祝日（description = 'system_holiday'）は対象外。
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 既定カレンダーが存在する場合のみ実行する
                db.execSQL(
                    "UPDATE `local_events` SET `calendarId` = " +
                        "(SELECT `id` FROM `local_calendars` WHERE `isDefault` = 1 LIMIT 1) " +
                        "WHERE `calendarId` = 0 " +
                        "AND `description` != 'system_holiday' " +
                        "AND EXISTS (SELECT 1 FROM `local_calendars` WHERE `isDefault` = 1)"
                )
            }
        }

        // 初回インストール時は Room が新規 DB を作るだけでマイグレーションは走らない。
        // その結果 local_calendars が空のままになり、既定カレンダー（マイカレンダー）が
        // 存在しない状態になる。onCreate / onOpen のタイミングで 1 件だけ投入し、
        // 既存 DB でも既定行が何らかの理由で消えていた場合は自己修復する。
        private val SEED_DEFAULT_CALENDAR_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedDefaultCalendarIfMissing(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                seedDefaultCalendarIfMissing(db)
            }

            private fun seedDefaultCalendarIfMissing(db: SupportSQLiteDatabase) {
                val count = db.query(
                    "SELECT COUNT(*) FROM local_calendars WHERE isDefault = 1"
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
                if (count > 0) return
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO `local_calendars` " +
                        "(`name`, `colorArgb`, `isVisible`, `sortOrder`, `isDefault`, `createdAt`) " +
                        "VALUES ('マイカレンダー', 0, 1, 0, 1, $now)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "golendar_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .addCallback(SEED_DEFAULT_CALENDAR_CALLBACK)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}