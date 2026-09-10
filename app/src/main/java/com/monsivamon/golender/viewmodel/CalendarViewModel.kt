package com.monsivamon.golender.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.dataStore
import com.monsivamon.golender.notification.NotificationScheduler
import com.monsivamon.golender.widget.DayWidget
import com.monsivamon.golender.widget.MonthWidget
import com.monsivamon.golender.widget.WeekWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import androidx.glance.appwidget.GlanceAppWidgetManager
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneOffset

// テーマモード（システム/ライト/ダーク）
enum class ThemeMode { SYSTEM, LIGHT, DARK }
// カレンダーデータソース（アプリ内/Google）
enum class CalendarMode { GOLENDAR, GOOGLE }

// UI状態とビジネスロジックを管理するViewModel
class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CalendarRepository(application)
    private val context = application.applicationContext
    private val dataStore = context.dataStore

    // 現在表示中の月
    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    // 現在選択中の日付
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // 表示中の予定リスト
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    // 検索クエリ
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // 表示テーマ
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // 週の始まり
    private val _weekStartDay = MutableStateFlow(DayOfWeek.SUNDAY)
    val weekStartDay: StateFlow<DayOfWeek> = _weekStartDay.asStateFlow()

    // カレンダー背景色
    private val _calendarBgColor = MutableStateFlow(Color.Unspecified)
    val calendarBgColor: StateFlow<Color> = _calendarBgColor.asStateFlow()

    // 曜日ごとの色
    private val _dayColors = MutableStateFlow<Map<DayOfWeek, Color>>(
        mapOf(DayOfWeek.SUNDAY to Color(0xFFE53935), DayOfWeek.SATURDAY to Color(0xFF1E88E5))
    )
    val dayColors: StateFlow<Map<DayOfWeek, Color>> = _dayColors.asStateFlow()

    // カレンダーモード
    private val _calendarMode = MutableStateFlow(CalendarMode.GOLENDAR)
    val calendarMode: StateFlow<CalendarMode> = _calendarMode.asStateFlow()

    // 利用可能なGoogleアカウント一覧
    private val _availableAccounts = MutableStateFlow<List<String>>(emptyList())
    val availableAccounts: StateFlow<List<String>> = _availableAccounts.asStateFlow()

    // 選択中のGoogleアカウント
    private val _selectedAccount = MutableStateFlow<String?>(null)
    val selectedAccount: StateFlow<String?> = _selectedAccount.asStateFlow()

    // ステータスメッセージ（トースト表示用）
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // 開始時刻の通知ON/OFF
    private val _notifyAtStart = MutableStateFlow(true)
    val notifyAtStart: StateFlow<Boolean> = _notifyAtStart.asStateFlow()

    // 10分前の通知ON/OFF
    private val _notify10MinBefore = MutableStateFlow(true)
    val notify10MinBefore: StateFlow<Boolean> = _notify10MinBefore.asStateFlow()

    fun clearStatusMessage() { _statusMessage.value = null }

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme_mode")
        private val WEEK_START_KEY = stringPreferencesKey("week_start_day")
        private val MODE_KEY = stringPreferencesKey("calendar_mode")
        private val ACCOUNT_KEY = stringPreferencesKey("selected_account")
        private val BG_COLOR_KEY = stringPreferencesKey("calendar_bg_color")
        private val NOTIFY_AT_START_KEY = booleanPreferencesKey("notify_at_start")
        private val NOTIFY_10MIN_KEY = booleanPreferencesKey("notify_10min_before")
        // 祝日取得の最終実行時刻を保存するキー
        val LAST_HOLIDAY_FETCH_KEY = longPreferencesKey("last_holiday_fetch_time")
    }

    // 起動時：設定読込、アカウント取得、祝日チェック
    init {
        loadSettingsFromDataStore()
        loadAccounts()
        checkAndFetchHolidays(force = false)
    }

    // 30日ごとに外部APIから日本の祝日を取得してローカルDBに保存
    private fun checkAndFetchHolidays(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = dataStore.data.first()
                val lastFetch = prefs[LAST_HOLIDAY_FETCH_KEY] ?: 0L
                val now = System.currentTimeMillis()
                val thirtyDays = 30L * 24 * 60 * 60 * 1000L

                if (force || now - lastFetch > thirtyDays) {
                    repository.fetchAndSaveHolidays()
                    dataStore.edit { it[LAST_HOLIDAY_FETCH_KEY] = now }
                    loadEvents()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // DataStoreから全設定を読み込む
    private fun loadSettingsFromDataStore() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val preferences = dataStore.data.first()
                preferences[THEME_KEY]?.let { _themeMode.value = ThemeMode.valueOf(it) }
                preferences[WEEK_START_KEY]?.let { _weekStartDay.value = DayOfWeek.valueOf(it) }
                preferences[MODE_KEY]?.let { _calendarMode.value = CalendarMode.valueOf(it) }
                preferences[ACCOUNT_KEY]?.let { _selectedAccount.value = it }

                preferences[NOTIFY_AT_START_KEY]?.let { _notifyAtStart.value = it }
                preferences[NOTIFY_10MIN_KEY]?.let { _notify10MinBefore.value = it }

                preferences[BG_COLOR_KEY]?.let { colorStr ->
                    _calendarBgColor.value = if (colorStr == "UNSPECIFIED") Color.Unspecified else Color(colorStr.toInt())
                }

                val savedColors = mutableMapOf(DayOfWeek.SUNDAY to Color(0xFFE53935), DayOfWeek.SATURDAY to Color(0xFF1E88E5))
                DayOfWeek.values().forEach { day ->
                    preferences[stringPreferencesKey("day_color_${day.name}")]?.let { colorStr ->
                        savedColors[day] = if (colorStr == "UNSPECIFIED") Color.Unspecified else Color(colorStr.toInt())
                    }
                }
                _dayColors.value = savedColors
            } catch (_: Exception) { }
            loadEvents()
        }
    }

    // 設定変更をDataStoreに保存
    private suspend fun saveSettings(theme: ThemeMode? = null, weekStart: DayOfWeek? = null, mode: CalendarMode? = null, account: String? = null) {
        try {
            dataStore.edit { preferences ->
                theme?.let { preferences[THEME_KEY] = it.name }
                weekStart?.let { preferences[WEEK_START_KEY] = it.name }
                mode?.let { preferences[MODE_KEY] = it.name }
                if (account != null) preferences[ACCOUNT_KEY] = account else preferences.remove(ACCOUNT_KEY)
            }
        } catch (_: Exception) { }
    }

    // 通知設定を更新しアラームを再スケジュール
    fun setNotifyOptions(atStart: Boolean, before10: Boolean) {
        _notifyAtStart.value = atStart
        _notify10MinBefore.value = before10
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dataStore.edit { preferences ->
                    preferences[NOTIFY_AT_START_KEY] = atStart
                    preferences[NOTIFY_10MIN_KEY] = before10
                }
            } catch (_: Exception) {}
            NotificationScheduler.updateAlarms(context)
        }
    }

    // カレンダー背景色を設定
    fun setCalendarBgColor(color: Color) {
        _calendarBgColor.value = color
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dataStore.edit { preferences ->
                    preferences[BG_COLOR_KEY] = if (color == Color.Unspecified) "UNSPECIFIED" else color.toArgb().toString()
                }
            } catch (_: Exception) { }
            updateWidgets()
        }
    }

    // 指定曜日の色を設定
    fun setDayColor(day: DayOfWeek, color: Color) {
        val newMap = _dayColors.value.toMutableMap()
        newMap[day] = color
        _dayColors.value = newMap
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dataStore.edit { preferences ->
                    preferences[stringPreferencesKey("day_color_${day.name}")] = if (color == Color.Unspecified) "UNSPECIFIED" else color.toArgb().toString()
                }
            } catch (_: Exception) { }
        }
    }

    // 利用可能なGoogleアカウント一覧を読み込む
    fun loadAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val accounts = repository.getAccountNames()
                _availableAccounts.value = accounts
                if (_selectedAccount.value == null && accounts.isNotEmpty()) _selectedAccount.value = accounts.first()
            } catch (_: SecurityException) {
                _availableAccounts.value = emptyList()
            }
        }
    }

    // カレンダーモードに応じて予定を読み込む
    fun loadEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            val yearMonth = _currentMonth.value
            val start = yearMonth.minusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = yearMonth.plusMonths(1).atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            try {
                val fetchedEvents = if (_calendarMode.value == CalendarMode.GOOGLE) {
                    // Googleモード：選択中アカウントのカレンダーID＋祝日/誕生日カレンダーのIDを統合
                    val calendarIds = _selectedAccount.value?.let {
                        (repository.getCalendarIdsForAccount(it) + repository.getSpecialCalendarIds()).distinct()
                    }
                    val googleEvents = repository.getEventsForMonth(start, end, calendarIds)

                    // ローカルDBの公式祝日データを日付セットとして取得
                    val officialHolidays = repository.getLocalEventsForMonth(start, end).filter { it.description == "system_holiday" }
                    val officialDates = officialHolidays.map {
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(it.startTime), ZoneOffset.UTC).toLocalDate()
                    }.toSet()

                    // Googleの各イベントに祝日・文化イベント・誕生日フラグを付与
                    val mappedGoogleEvents = googleEvents.map { event ->
                        // 誕生日判定（「天皇誕生日」は除外）
                        val isBirthday = event.isBirthdayCalendar ||
                                (event.title.contains("誕生日") && !event.title.contains("天皇誕生日")) ||
                                event.title.contains("Birthday", ignoreCase = true)
                        var updatedEvent = event.copy(isBirthdayCalendar = isBirthday)

                        // 祝日カレンダー、または終日かつ読み取り専用の予定を祝日/文化イベント候補として判定
                        if (!isBirthday && (updatedEvent.isHolidayCalendar || (updatedEvent.isAllDay && updatedEvent.isReadOnly))) {
                            val zone = if (updatedEvent.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
                            val eventDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(updatedEvent.startTime), zone).toLocalDate()

                            if (officialDates.isNotEmpty()) {
                                // 公式祝日一覧に含まれていれば祝日、そうでなければ文化イベント
                                val isOfficial = officialDates.contains(eventDate)
                                updatedEvent = updatedEvent.copy(isHolidayCalendar = isOfficial, isCulturalEvent = isOfficial)
                            } else {
                                // 公式祝日がない場合はタイトルで文化イベントを判定
                                val isCultural = listOf("七夕", "バレンタイン", "節分", "ひな祭り", "母の日", "父の日", "ハロウィン", "クリスマス", "大晦日", "元日").any { updatedEvent.title.contains(it) }
                                if (isCultural || updatedEvent.isHolidayCalendar) updatedEvent = updatedEvent.copy(isCulturalEvent = true)
                            }
                        }
                        updatedEvent
                    }

                    // Google側に存在しない公式祝日を抽出（重複表示を防ぐため差分のみ取得）
                    val existingHolidayDates = mappedGoogleEvents.filter { it.isHolidayCalendar || it.isCulturalEvent }.map {
                        val zone = if (it.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(it.startTime), zone).toLocalDate()
                    }.toSet()

                    // 欠落している祝日を擬似イベントとして注入（IDはマイナス値で衝突回避）
                    val missingHolidays = officialHolidays.filter { localHoliday ->
                        val localDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(localHoliday.startTime), ZoneOffset.UTC).toLocalDate()
                        !existingHolidayDates.contains(localDate)
                    }.map { localHoliday ->
                        Event(
                            id = -(localHoliday.id + 1000L),
                            title = localHoliday.title,
                            startTime = localHoliday.startTime,
                            endTime = localHoliday.endTime,
                            isAllDay = true,
                            calendarId = -1L,
                            location = "",
                            description = "",
                            isReadOnly = true,
                            isHolidayCalendar = true,
                            isCulturalEvent = true
                        )
                    }

                    (mappedGoogleEvents + missingHolidays).sortedBy { it.startTime }
                } else {
                    // Golendarモード：システム祝日は閲覧専用として扱い、誕生日フラグを付与
                    repository.getLocalEventsForMonth(start, end).map { event ->
                        val isBirthday = (event.title.contains("誕生日") && !event.title.contains("天皇誕生日")) ||
                                event.title.contains("Birthday", ignoreCase = true)

                        // descriptionが「system_holiday」なら祝日データとみなして閲覧専用にする
                        val isSystemHoliday = event.description == "system_holiday"

                        event.copy(
                            isBirthdayCalendar = isBirthday,
                            isReadOnly = isSystemHoliday,
                            isHolidayCalendar = isSystemHoliday,
                            isCulturalEvent = isSystemHoliday,
                            description = if (isSystemHoliday) "" else event.description // 内部識別子はUIに出さない
                        )
                    }
                }

                _events.value = fetchedEvents

                // 予定読込後に通知アラームを更新
                viewModelScope.launch(Dispatchers.IO) {
                    NotificationScheduler.updateAlarms(context)
                }
                updateWidgets()
            } catch (_: SecurityException) {
                _events.value = emptyList()
            }
        }
    }

    // 日付を選択（月が変われば再読込）
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        if (YearMonth.from(date) != _currentMonth.value) {
            _currentMonth.value = YearMonth.from(date)
            loadEvents()
        }
    }

    // 今日にリセット
    fun resetToToday() { selectDate(LocalDate.now()) }
    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun setThemeMode(mode: ThemeMode) { _themeMode.value = mode; viewModelScope.launch(Dispatchers.IO) { saveSettings(theme = mode); updateWidgets() } }
    fun setWeekStartDay(day: DayOfWeek) { _weekStartDay.value = day; viewModelScope.launch(Dispatchers.IO) { saveSettings(weekStart = day); updateWidgets() } }

    // カレンダーモードを切り替え（Golendarモード時は強制的に祝日を取得）
    fun setCalendarMode(mode: CalendarMode) {
        _calendarMode.value = mode
        viewModelScope.launch(Dispatchers.IO) {
            saveSettings(mode = mode)
            if (mode == CalendarMode.GOLENDAR) {
                checkAndFetchHolidays(force = true)
            }
        }
        loadEvents()
    }

    fun setSelectedAccount(accountName: String?) { _selectedAccount.value = accountName; viewModelScope.launch(Dispatchers.IO) { saveSettings(account = accountName) }; loadEvents() }

    // ホーム画面ウィジェットを更新
    private fun updateWidgets() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                listOf(DayWidget(), WeekWidget(), MonthWidget()).forEach { widget ->
                    GlanceAppWidgetManager(context).getGlanceIds(widget::class.java).forEach { id -> widget.update(context, id) }
                }
            } catch (_: Exception) { }
        }
    }

    // 予定を追加（終日予定の時刻をUTC日付境界に補正）
    fun addEvent(title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean, location: String, description: String, rrule: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            var finalStart = startMillis
            var finalEnd = endMillis

            if (isAllDay) {
                // 開始時刻をシステムタイムゾーンの日付のUTC 00:00に補正
                if (startMillis % 86400000L != 0L) {
                    finalStart = Instant.ofEpochMilli(startMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli()
                }
                // 終了時刻をシステムタイムゾーンの日付のUTC 00:00（または翌日00:00）に補正
                if (endMillis % 86400000L != 0L) {
                    val endZoned = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault())
                    finalEnd = if (endZoned.hour >= 23) {
                        endZoned.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    } else {
                        endZoned.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    }
                }
                // 終了が開始より前にならないように最低1日分確保
                if (finalEnd <= finalStart) {
                    finalEnd = finalStart + 86400000L
                }
            }

            if (_calendarMode.value == CalendarMode.GOOGLE) {
                repository.insertEvent(title, finalStart, finalEnd, isAllDay, location, description, rrule, _selectedAccount.value)
            } else {
                repository.insertLocalEvent(title, finalStart, finalEnd, isAllDay, location, description, rrule)
            }
            loadEvents()
            updateWidgets()
        }
    }

    // 予定を更新（終日予定の時刻をUTC日付境界に補正）
    fun updateEvent(eventId: Long, title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean, location: String, description: String, rrule: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            var finalStart = startMillis
            var finalEnd = endMillis

            if (isAllDay) {
                if (startMillis % 86400000L != 0L) {
                    finalStart = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                }
                if (endMillis % 86400000L != 0L) {
                    val endZoned = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault())
                    finalEnd = if (endZoned.hour >= 23) {
                        endZoned.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    } else {
                        endZoned.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    }
                }
                if (finalEnd <= finalStart) finalEnd = finalStart + 86400000L
            }

            if (_calendarMode.value == CalendarMode.GOOGLE) repository.updateEvent(eventId, title, finalStart, finalEnd, isAllDay, location, description, rrule)
            else repository.updateLocalEvent(eventId, title, finalStart, finalEnd, isAllDay, location, description, rrule)
            loadEvents()
            updateWidgets()
        }
    }

    // 予定を削除
    fun deleteEvent(eventId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_calendarMode.value == CalendarMode.GOOGLE) repository.deleteEvent(eventId)
            else repository.deleteLocalEvent(eventId)
            loadEvents()
            updateWidgets()
        }
    }

    // 複数日予定の指定日のみを削除（前後に分割、終日予定にも対応）
    fun splitAndDeleteDay(event: Event, dateToRemove: LocalDate) {
        if (event.rrule != null || event.isReadOnly) return

        val isGoogle = _calendarMode.value == CalendarMode.GOOGLE
        val targetAccount = _selectedAccount.value
        viewModelScope.launch(Dispatchers.IO) {
            val zone = if (event.isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
            val eventStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), zone).toLocalDate()
            val adjustedEndTime = if (event.endTime > event.startTime) event.endTime - 1 else event.endTime
            val eventEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(adjustedEndTime), zone).toLocalDate()

            if (eventStart == eventEnd) return@launch

            // 削除日の翌日開始時刻を計算
            val newStart2 = dateToRemove.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            // 削除日の前日終了時刻を計算（終日予定の場合は当日00:00）
            val newEnd1 = if (event.isAllDay) {
                dateToRemove.atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                dateToRemove.minusDays(1).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
            }

            if (dateToRemove == eventStart) {
                // 先頭日を削除 → 開始日を翌日に更新
                if (isGoogle) repository.updateEvent(event.id, event.title, newStart2, event.endTime, event.isAllDay, event.location, event.description, null)
                else repository.updateLocalEvent(event.id, event.title, newStart2, event.endTime, event.isAllDay, event.location, event.description, null)
            } else if (dateToRemove == eventEnd) {
                // 最終日を削除 → 終了日を前日に更新
                if (isGoogle) repository.updateEvent(event.id, event.title, event.startTime, newEnd1, event.isAllDay, event.location, event.description, null)
                else repository.updateLocalEvent(event.id, event.title, event.startTime, newEnd1, event.isAllDay, event.location, event.description, null)
            } else {
                // 中間日を削除 → 前後で2つの予定に分割
                if (isGoogle) {
                    repository.updateEvent(event.id, event.title, event.startTime, newEnd1, event.isAllDay, event.location, event.description, null)
                    repository.insertEvent(event.title, newStart2, event.endTime, event.isAllDay, event.location, event.description, null, targetAccount)
                } else {
                    repository.updateLocalEvent(event.id, event.title, event.startTime, newEnd1, event.isAllDay, event.location, event.description, null)
                    repository.insertLocalEvent(event.title, newStart2, event.endTime, event.isAllDay, event.location, event.description, null)
                }
            }
            loadEvents()
            updateWidgets()
        }
    }

    // JSONファイルに設定と予定をバックアップ出力
    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = dataStore.data.first()
                val root = JSONObject()

                val settingsObj = JSONObject()
                settingsObj.put("theme_mode", prefs[THEME_KEY] ?: "SYSTEM")
                settingsObj.put("week_start_day", prefs[WEEK_START_KEY] ?: "SUNDAY")
                settingsObj.put("calendar_mode", prefs[MODE_KEY] ?: "GOLENDAR")
                settingsObj.put("calendar_bg_color", prefs[BG_COLOR_KEY] ?: "UNSPECIFIED")

                val dayColorsObj = JSONObject()
                DayOfWeek.values().forEach { day ->
                    dayColorsObj.put(day.name, prefs[stringPreferencesKey("day_color_${day.name}")] ?: "UNSPECIFIED")
                }
                settingsObj.put("day_colors", dayColorsObj)
                root.put("settings", settingsObj)

                val eventsToExport = if (_calendarMode.value == CalendarMode.GOOGLE) {
                    repository.getAllGoogleEvents(_selectedAccount.value)
                } else {
                    repository.getAllLocalEvents().map {
                        Event(it.id, it.title, it.startTime, it.endTime, it.isAllDay, -1L, it.location, it.description, it.rrule)
                    }
                }

                val eventsArray = JSONArray()
                for (event in eventsToExport) {
                    val evObj = JSONObject()
                    evObj.put("title", event.title)
                    evObj.put("startTime", event.startTime)
                    evObj.put("endTime", event.endTime)
                    evObj.put("isAllDay", event.isAllDay)
                    evObj.put("location", event.location)
                    evObj.put("description", event.description)
                    evObj.put("rrule", event.rrule ?: JSONObject.NULL)
                    eventsArray.put(evObj)
                }
                root.put("events", eventsArray)

                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(root.toString().toByteArray())
                }
                _statusMessage.value = "バックアップが完了しました"
            } catch (e: Exception) {
                _statusMessage.value = "バックアップに失敗しました: ${e.message}"
            }
        }
    }

    // JSONファイルから設定と予定を復元（追記または上書き）
    fun importBackup(uri: Uri, isAppend: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (_calendarMode.value == CalendarMode.GOOGLE && !isAppend) {
                    _statusMessage.value = "Googleモードでは誤削除防止のため、追記のみ可能です"
                    return@launch
                }

                val jsonString = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().use { reader -> reader.readText() }
                } ?: throw Exception("ファイルが開けませんでした")

                val root = JSONObject(jsonString)

                if (root.has("settings")) {
                    val settingsObj = root.getJSONObject("settings")
                    dataStore.edit { preferences ->
                        if (settingsObj.has("theme_mode")) preferences[THEME_KEY] = settingsObj.getString("theme_mode")
                        if (settingsObj.has("week_start_day")) preferences[WEEK_START_KEY] = settingsObj.getString("week_start_day")
                        if (settingsObj.has("calendar_mode")) preferences[MODE_KEY] = settingsObj.getString("calendar_mode")
                        if (settingsObj.has("calendar_bg_color")) preferences[BG_COLOR_KEY] = settingsObj.getString("calendar_bg_color")

                        if (settingsObj.has("day_colors")) {
                            val dayColorsObj = settingsObj.getJSONObject("day_colors")
                            DayOfWeek.values().forEach { day ->
                                if (dayColorsObj.has(day.name)) {
                                    preferences[stringPreferencesKey("day_color_${day.name}")] = dayColorsObj.getString(day.name)
                                }
                            }
                        }
                    }
                }

                if (root.has("events")) {
                    val eventsArray = root.getJSONArray("events")
                    val importedEvents = mutableListOf<LocalEvent>()
                    for (i in 0 until eventsArray.length()) {
                        val evObj = eventsArray.getJSONObject(i)
                        importedEvents.add(
                            LocalEvent(
                                id = 0,
                                title = evObj.getString("title"),
                                startTime = evObj.getLong("startTime"),
                                endTime = evObj.getLong("endTime"),
                                isAllDay = evObj.getBoolean("isAllDay"),
                                location = evObj.optString("location", ""),
                                description = evObj.optString("description", ""),
                                rrule = if (evObj.isNull("rrule")) null else evObj.getString("rrule")
                            )
                        )
                    }

                    if (_calendarMode.value == CalendarMode.GOOGLE) {
                        val targetAccount = _selectedAccount.value
                        for (ev in importedEvents) {
                            repository.insertEvent(ev.title, ev.startTime, ev.endTime, ev.isAllDay, ev.location, ev.description, ev.rrule, targetAccount)
                        }
                    } else {
                        if (isAppend) {
                            repository.appendLocalEvents(importedEvents)
                        } else {
                            repository.restoreLocalEvents(importedEvents)
                        }
                    }
                }

                loadSettingsFromDataStore()
                _statusMessage.value = if (isAppend) "追記が完了しました" else "復元が完了しました"
            } catch (e: Exception) {
                _statusMessage.value = "復元に失敗しました: 不正なファイルです"
            }
        }
    }
}