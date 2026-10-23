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
    version = 11,
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

        // v1/v2 → v3 : DB バージョン 1/2 の超旧バージョンからのアップグレード救済。
        //              当時のスキーマは管理外のため、local_events を欠損カラム補完付きの
        //              v3 互換テーブルへ再構築し、マイグレーションパス欠落による
        //              クラッシュ (IllegalStateException) を防ぎつつ既存データを保持する。
        private fun migrateLegacyLocalEventsToV3(db: SupportSQLiteDatabase) {
            // 既存カラム名を収集する。
            // Cursor.getString() は String? を返すため、?.let でアンラップして
            // から MutableSet<String> へ追加する（型不一致のコンパイルエラー対策）。
            val cols = mutableSetOf<String>()
            db.query("PRAGMA table_info(`local_events`)").use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0) cursor.getString(nameIdx)?.let { cols.add(it) }
                }
            }
            if (cols.isEmpty()) {
                // テーブル自体が無い（さらに古い / 破損 DB）場合は新規作成のみで済ませる
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `local_events` (" +
                        "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "`title` TEXT NOT NULL, " +
                        "`startTime` INTEGER NOT NULL, " +
                        "`endTime` INTEGER NOT NULL, " +
                        "`isAllDay` INTEGER NOT NULL, " +
                        "`location` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`rrule` TEXT)"
                )
                return
            }
            // 存在するカラムは COALESCE 補完、欠損カラムはフォールバック値で SELECT する
            fun src(col: String, fallback: String): String =
                if (col in cols) "COALESCE(`$col`, $fallback)" else fallback
            val idExpr = if ("id" in cols) "`id`" else "rowid"
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `local_events_migration_tmp` (" +
                    "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                    "`title` TEXT NOT NULL, " +
                    "`startTime` INTEGER NOT NULL, " +
                    "`endTime` INTEGER NOT NULL, " +
                    "`isAllDay` INTEGER NOT NULL, " +
                    "`location` TEXT NOT NULL, " +
                    "`description` TEXT NOT NULL, " +
                    "`rrule` TEXT)"
            )
            db.execSQL(
                "INSERT INTO `local_events_migration_tmp` " +
                    "(`id`, `title`, `startTime`, `endTime`, `isAllDay`, `location`, `description`, `rrule`) " +
                    "SELECT $idExpr, ${src("title", "''")}, ${src("startTime", "0")}, " +
                    "${src("endTime", "0")}, ${src("isAllDay", "0")}, ${src("location", "''")}, " +
                    "${src("description", "''")}, ${src("rrule", "NULL")} FROM `local_events`"
            )
            db.execSQL("DROP TABLE `local_events`")
            db.execSQL("ALTER TABLE `local_events_migration_tmp` RENAME TO `local_events`")
        }

        private val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateLegacyLocalEventsToV3(db)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateLegacyLocalEventsToV3(db)
            }
        }
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
                // 名前は空文字センチネルとし、表示時に locale の文字列リソースへ解決する。
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO `local_calendars` (`name`, `colorArgb`, `isVisible`, `sortOrder`, `isDefault`, `createdAt`) " +
                    "VALUES ('', 0, 1, 0, 1, $now)"
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

        // v8 → v9 : 既定カレンダー名「マイカレンダー」を空文字センチネルへ移行する。
        //           表示時に locale の文字列リソース（default_calendar_name）へ動的に
        //           解決するため、DB 上では名前を持たない。ユーザーが既定カレンダーの
        //           名前を変更している場合は触らない（名前変更APIで空文字は入らない想定）。
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE `local_calendars` SET `name` = '' " +
                    "WHERE `isDefault` = 1 AND `name` = 'マイカレンダー'"
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
                        "VALUES ('', 0, 1, 0, 1, $now)"
                )
            }
        }

        // v9 → v10 : 予定ごとの任意色を保持するカラムを追加するマイグレーション
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `local_events` ADD COLUMN `colorArgb` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // v10 → v11 : 祝日の対象国を識別するカラムを追加するマイグレーション。
        //              既存の祝日は日本分のみのため JP として引き継ぐ
        //              （再取得に失敗しても前回データを表示できる）。
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `local_events` ADD COLUMN `holidayCountry` TEXT")
                db.execSQL(
                    "UPDATE `local_events` SET `holidayCountry` = 'JP' " +
                        "WHERE `description` = 'system_holiday'"
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
                    .addMigrations(MIGRATION_1_3, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .addCallback(SEED_DEFAULT_CALENDAR_CALLBACK)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}