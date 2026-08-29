package com.monsivamon.golender.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.Instant
import androidx.glance.appwidget.GlanceAppWidgetManager

// テーマモード（システム/ライト/ダーク）
enum class ThemeMode { SYSTEM, LIGHT, DARK }
// カレンダーデータソース（アプリ内/Google）
enum class CalendarMode { GOLENDAR, GOOGLE }

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
    }

    // 起動時に設定を読み込み、アカウント一覧を取得
    init {
        loadSettingsFromDataStore()
        loadAccounts()
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
                    val calendarIds = _selectedAccount.value?.let { repository.getCalendarIdsForAccount(it) }
                    repository.getEventsForMonth(start, end, calendarIds)
                } else {
                    repository.getLocalEventsForMonth(start, end)
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
    fun setCalendarMode(mode: CalendarMode) { _calendarMode.value = mode; viewModelScope.launch(Dispatchers.IO) { saveSettings(mode = mode) }; loadEvents() }
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

    // 予定を追加
    fun addEvent(title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean, location: String, description: String, rrule: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_calendarMode.value == CalendarMode.GOOGLE) {
                repository.insertEvent(title, startMillis, endMillis, isAllDay, location, description, rrule, _selectedAccount.value)
            } else {
                repository.insertLocalEvent(title, startMillis, endMillis, isAllDay, location, description, rrule)
            }
            loadEvents()
            updateWidgets()
        }
    }

    // 予定を更新
    fun updateEvent(eventId: Long, title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean, location: String, description: String, rrule: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_calendarMode.value == CalendarMode.GOOGLE) repository.updateEvent(eventId, title, startMillis, endMillis, isAllDay, location, description, rrule)
            else repository.updateLocalEvent(eventId, title, startMillis, endMillis, isAllDay, location, description, rrule)
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

    // 複数日予定の指定日のみを削除（前後に分割）
    fun splitAndDeleteDay(event: Event, dateToRemove: LocalDate) {
        if (event.rrule != null || event.isReadOnly) return

        val isGoogle = _calendarMode.value == CalendarMode.GOOGLE
        val targetAccount = _selectedAccount.value
        viewModelScope.launch(Dispatchers.IO) {
            val eventStart = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), ZoneId.systemDefault()).toLocalDate()
            val eventEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.endTime), ZoneId.systemDefault()).toLocalDate()

            if (eventStart == eventEnd) return@launch

            if (dateToRemove == eventStart) {
                val newStart = dateToRemove.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (isGoogle) repository.updateEvent(event.id, event.title, newStart, event.endTime, event.isAllDay, event.location, event.description, null)
                else repository.updateLocalEvent(event.id, event.title, newStart, event.endTime, event.isAllDay, event.location, event.description, null)
            } else if (dateToRemove == eventEnd) {
                val newEnd = dateToRemove.minusDays(1).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (isGoogle) repository.updateEvent(event.id, event.title, event.startTime, newEnd, event.isAllDay, event.location, event.description, null)
                else repository.updateLocalEvent(event.id, event.title, event.startTime, newEnd, event.isAllDay, event.location, event.description, null)
            } else {
                val newEnd1 = dateToRemove.minusDays(1).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val newStart2 = dateToRemove.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
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