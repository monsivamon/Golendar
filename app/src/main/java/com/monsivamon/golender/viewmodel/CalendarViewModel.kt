package com.monsivamon.golender.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.dataStore
import com.monsivamon.golender.data.prefs.SettingsKeys
import com.monsivamon.golender.data.util.correctAllDayMillis
import com.monsivamon.golender.data.util.localStartDate
import com.monsivamon.golender.data.util.zone
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import androidx.compose.ui.graphics.toArgb

// UI状態とビジネスロジックを管理するViewModel。
class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CalendarRepository(application)
    private val context = application.applicationContext
    private val dataStore = context.dataStore

    private val backupManager = BackupManager(context, repository)

    private val holidayFetchMutex = Mutex()

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _weekStartDay = MutableStateFlow(DayOfWeek.SUNDAY)
    val weekStartDay: StateFlow<DayOfWeek> = _weekStartDay.asStateFlow()

    private val _calendarBgColor = MutableStateFlow(Color.Unspecified)
    val calendarBgColor: StateFlow<Color> = _calendarBgColor.asStateFlow()

    private val _dayColors = MutableStateFlow<Map<DayOfWeek, Color>>(
        mapOf(DayOfWeek.SUNDAY to Color(0xFFE53935), DayOfWeek.SATURDAY to Color(0xFF1E88E5))
    )
    val dayColors: StateFlow<Map<DayOfWeek, Color>> = _dayColors.asStateFlow()

    private val _calendarMode = MutableStateFlow(CalendarMode.GOLENDAR)
    val calendarMode: StateFlow<CalendarMode> = _calendarMode.asStateFlow()

    private val _availableAccounts = MutableStateFlow<List<String>>(emptyList())
    val availableAccounts: StateFlow<List<String>> = _availableAccounts.asStateFlow()

    private val _selectedAccount = MutableStateFlow<String?>(null)
    val selectedAccount: StateFlow<String?> = _selectedAccount.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _notifyAtStart = MutableStateFlow(true)
    val notifyAtStart: StateFlow<Boolean> = _notifyAtStart.asStateFlow()

    private val _notify10MinBefore = MutableStateFlow(true)
    val notify10MinBefore: StateFlow<Boolean> = _notify10MinBefore.asStateFlow()

    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()

    private val _pendingShortcut = MutableStateFlow<String?>(null)
    val pendingShortcut: StateFlow<String?> = _pendingShortcut.asStateFlow()

    private val _requestAddEvent = MutableStateFlow(false)
    val requestAddEvent: StateFlow<Boolean> = _requestAddEvent.asStateFlow()

    // ウィジェット等からの画面遷移要求を設定する。
    fun requestNavigation(route: String) { _pendingRoute.value = route }

    // 画面遷移要求を消費済みにする。
    fun consumeNavigation() { _pendingRoute.value = null }

    // ステータスメッセージをクリアする。
    fun clearStatusMessage() { _statusMessage.value = null }

    // ショートカットからのアクション要求を設定する。
    fun requestShortcut(action: String) { _pendingShortcut.value = action }

    // ショートカット要求を消費済みにする。
    fun consumeShortcut() { _pendingShortcut.value = null }

    // 予定追加ダイアログの表示要求を設定する。
    fun requestAddEvent() { _requestAddEvent.value = true }

    // 予定追加要求を消費済みにする。
    fun consumeAddEventRequest() { _requestAddEvent.value = false }

    // 起動時に設定・アカウント・祝日を読み込む。
    init {
        loadSettingsFromDataStore()
        loadAccounts()
        checkAndFetchHolidays(force = false)
    }

    // 30日ごとに祝日データを取得する（並行実行をミューテックスで防止）。
    private fun checkAndFetchHolidays(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            holidayFetchMutex.withLock {
                try {
                    val prefs = dataStore.data.first()
                    val lastFetch = prefs[SettingsKeys.LAST_HOLIDAY_FETCH] ?: 0L
                    val now = System.currentTimeMillis()
                    val thirtyDays = 30L * 24 * 60 * 60 * 1000L

                    if (force || now - lastFetch > thirtyDays) {
                        repository.fetchAndSaveHolidays()
                        dataStore.edit { it[SettingsKeys.LAST_HOLIDAY_FETCH] = now }
                        loadEvents()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // DataStoreから全設定を読み込む。
    private fun loadSettingsFromDataStore() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val preferences = dataStore.data.first()
                preferences[SettingsKeys.THEME]?.let { _themeMode.value = ThemeMode.valueOf(it) }
                preferences[SettingsKeys.WEEK_START]?.let { _weekStartDay.value = DayOfWeek.valueOf(it) }
                preferences[SettingsKeys.MODE]?.let { _calendarMode.value = CalendarMode.valueOf(it) }
                preferences[SettingsKeys.ACCOUNT]?.let { _selectedAccount.value = it }

                preferences[SettingsKeys.NOTIFY_AT_START]?.let { _notifyAtStart.value = it }
                preferences[SettingsKeys.NOTIFY_10MIN]?.let { _notify10MinBefore.value = it }

                preferences[SettingsKeys.BG_COLOR]?.let { colorStr ->
                    _calendarBgColor.value = if (colorStr == SettingsKeys.COLOR_UNSPECIFIED) Color.Unspecified
                    else Color(colorStr.toInt())
                }

                val savedColors = mutableMapOf(
                    DayOfWeek.SUNDAY to Color(0xFFE53935),
                    DayOfWeek.SATURDAY to Color(0xFF1E88E5),
                )
                DayOfWeek.values().forEach { day ->
                    preferences[SettingsKeys.dayColor(day)]?.let { colorStr ->
                        savedColors[day] = if (colorStr == SettingsKeys.COLOR_UNSPECIFIED) Color.Unspecified
                        else Color(colorStr.toInt())
                    }
                }
                _dayColors.value = savedColors
            } catch (_: Exception) { }
            loadEvents()
        }
    }

    // テーマ・週開始・モード・アカウントの設定をDataStoreに保存する。
    private suspend fun saveSettings(
        theme: ThemeMode? = null, weekStart: DayOfWeek? = null,
        mode: CalendarMode? = null, account: String? = null,
    ) {
        try {
            dataStore.edit { prefs ->
                theme?.let { prefs[SettingsKeys.THEME] = it.name }
                weekStart?.let { prefs[SettingsKeys.WEEK_START] = it.name }
                mode?.let { prefs[SettingsKeys.MODE] = it.name }
                if (account != null) prefs[SettingsKeys.ACCOUNT] = account
                else prefs.remove(SettingsKeys.ACCOUNT)
            }
        } catch (_: Exception) { }
    }

    // 通知設定を更新しアラームを再スケジュールする。
    fun setNotifyOptions(atStart: Boolean, before10: Boolean) {
        _notifyAtStart.value = atStart
        _notify10MinBefore.value = before10
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dataStore.edit { prefs ->
                    prefs[SettingsKeys.NOTIFY_AT_START] = atStart
                    prefs[SettingsKeys.NOTIFY_10MIN] = before10
                }
            } catch (_: Exception) {}
            NotificationScheduler.updateAlarms(context)
        }
    }

    // カレンダー背景色を設定して保存する。
    fun setCalendarBgColor(color: Color) {
        _calendarBgColor.value = color
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dataStore.edit { prefs ->
                    prefs[SettingsKeys.BG_COLOR] =
                        if (color == Color.Unspecified) SettingsKeys.COLOR_UNSPECIFIED
                        else color.toArgb().toString()
                }
            } catch (_: Exception) { }
            updateWidgets()
        }
    }

    // 指定曜日の色を設定して保存する。
    fun setDayColor(day: DayOfWeek, color: Color) {
        val newMap = _dayColors.value.toMutableMap()
        newMap[day] = color
        _dayColors.value = newMap
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dataStore.edit { prefs ->
                    prefs[SettingsKeys.dayColor(day)] =
                        if (color == Color.Unspecified) SettingsKeys.COLOR_UNSPECIFIED
                        else color.toArgb().toString()
                }
            } catch (_: Exception) { }
        }
    }

    // 利用可能なGoogleアカウント一覧を読み込む。
    fun loadAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val accounts = repository.getAccountNames()
                _availableAccounts.value = accounts
                if (_selectedAccount.value == null && accounts.isNotEmpty()) {
                    _selectedAccount.value = accounts.first()
                }
            } catch (_: SecurityException) {
                _availableAccounts.value = emptyList()
            }
        }
    }

    // カレンダーモードに応じて予定を読み込む。
    fun loadEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            val yearMonth = _currentMonth.value
            val start = yearMonth.minusMonths(1).atDay(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = yearMonth.plusMonths(1).atEndOfMonth()
                .atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            try {
                val fetchedEvents = if (_calendarMode.value == CalendarMode.GOOGLE) {
                    val calendarIds = _selectedAccount.value?.let {
                        (repository.getCalendarIdsForAccount(it) + repository.getSpecialCalendarIds()).distinct()
                    }
                    val googleEvents = repository.getEventsForMonth(start, end, calendarIds)

                    val officialHolidays = repository.getLocalEventsForMonth(start, end)
                        .filter { it.description == LocalEvent.DESCRIPTION_HOLIDAY }
                    val officialDates = officialHolidays.map {
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(it.startTime), ZoneOffset.UTC).toLocalDate()
                    }.toSet()

                    val mappedGoogleEvents = googleEvents.map { event ->
                        val isBirthday = event.isBirthdayCalendar ||
                                (event.title.contains("誕生日") && !event.title.contains("天皇誕生日")) ||
                                event.title.contains("Birthday", ignoreCase = true)
                        var updatedEvent = event.copy(isBirthdayCalendar = isBirthday)

                        if (!isBirthday && (updatedEvent.isHolidayCalendar || (updatedEvent.isAllDay && updatedEvent.isReadOnly))) {
                            val date = updatedEvent.localStartDate()
                            if (officialDates.isNotEmpty()) {
                                val isOfficial = officialDates.contains(date)
                                updatedEvent = updatedEvent.copy(isHolidayCalendar = isOfficial, isCulturalEvent = isOfficial)
                            } else {
                                val isCultural = listOf(
                                    "七夕", "バレンタイン", "節分", "ひな祭り", "母の日",
                                    "父の日", "ハロウィン", "クリスマス", "大晦日", "元日",
                                ).any { updatedEvent.title.contains(it) }
                                if (isCultural || updatedEvent.isHolidayCalendar) {
                                    updatedEvent = updatedEvent.copy(isCulturalEvent = true)
                                }
                            }
                        }
                        updatedEvent
                    }

                    val existingHolidayDates = mappedGoogleEvents
                        .filter { it.isHolidayCalendar || it.isCulturalEvent }
                        .map { it.localStartDate() }
                        .toSet()

                    val missingHolidays = officialHolidays.filter { localHoliday ->
                        val localDate = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(localHoliday.startTime), ZoneOffset.UTC
                        ).toLocalDate()
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
                            isCulturalEvent = true,
                        )
                    }

                    (mappedGoogleEvents + missingHolidays)
                        .distinctBy { ev ->
                            if (ev.isHolidayCalendar || ev.isCulturalEvent) {
                                "holiday_${ev.localStartDate()}_${ev.title}"
                            } else {
                                "event_${ev.id}_${ev.startTime}"
                            }
                        }
                        .sortedBy { it.startTime }
                } else {
                    repository.getLocalEventsForMonth(start, end).map { event ->
                        val isBirthday = (event.title.contains("誕生日") && !event.title.contains("天皇誕生日")) ||
                                event.title.contains("Birthday", ignoreCase = true)
                        val isSystemHoliday = event.description == LocalEvent.DESCRIPTION_HOLIDAY

                        event.copy(
                            isBirthdayCalendar = isBirthday,
                            isReadOnly = isSystemHoliday,
                            isHolidayCalendar = isSystemHoliday,
                            isCulturalEvent = isSystemHoliday,
                            description = if (isSystemHoliday) "" else event.description,
                        )
                    }
                        .distinctBy { ev ->
                            if (ev.isHolidayCalendar || ev.isCulturalEvent) {
                                "holiday_${ev.localStartDate()}_${ev.title}"
                            } else {
                                "event_${ev.id}_${ev.startTime}"
                            }
                        }
                        .sortedBy { it.startTime }
                }

                _events.value = fetchedEvents

                viewModelScope.launch(Dispatchers.IO) {
                    NotificationScheduler.updateAlarms(context)
                }
                updateWidgets()
            } catch (_: SecurityException) {
                _events.value = emptyList()
            }
        }
    }

    // 日付を選択し、月が変われば予定を再読込する。
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        if (YearMonth.from(date) != _currentMonth.value) {
            _currentMonth.value = YearMonth.from(date)
            loadEvents()
        }
    }

    // 選択日を今日にリセットする。
    fun resetToToday() { selectDate(LocalDate.now()) }

    // 検索クエリを更新する。
    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    // テーマモードを設定して保存する。
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch(Dispatchers.IO) { saveSettings(theme = mode); updateWidgets() }
    }

    // 週の開始曜日を設定して保存する。
    fun setWeekStartDay(day: DayOfWeek) {
        _weekStartDay.value = day
        viewModelScope.launch(Dispatchers.IO) { saveSettings(weekStart = day); updateWidgets() }
    }

    // カレンダーモードを設定し、必要に応じて祝日を再取得する。
    fun setCalendarMode(mode: CalendarMode) {
        _calendarMode.value = mode
        viewModelScope.launch(Dispatchers.IO) {
            saveSettings(mode = mode)
            if (mode == CalendarMode.GOLENDAR) checkAndFetchHolidays(force = true)
        }
        loadEvents()
    }

    // 表示対象のGoogleアカウントを設定して予定を再読込する。
    fun setSelectedAccount(accountName: String?) {
        _selectedAccount.value = accountName
        viewModelScope.launch(Dispatchers.IO) { saveSettings(account = accountName) }
        loadEvents()
    }

    // 全ウィジェットを更新する。
    private fun updateWidgets() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>().applicationContext
                listOf(DayWidget(), WeekWidget(), MonthWidget()).forEach { widget ->
                    GlanceAppWidgetManager(ctx)
                        .getGlanceIds(widget::class.java)
                        .forEach { id -> widget.update(ctx, id) }
                }
            } catch (_: Exception) { }
        }
    }

    // 予定を新規作成する（終日予定はミリ秒補正）。
    fun addEvent(
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val (finalStart, finalEnd) = if (isAllDay) {
                val c = correctAllDayMillis(startMillis, endMillis); c.start to c.end
            } else startMillis to endMillis

            if (_calendarMode.value == CalendarMode.GOOGLE) {
                repository.insertEvent(title, finalStart, finalEnd, isAllDay,
                    location, description, rrule, _selectedAccount.value)
            } else {
                repository.insertLocalEvent(title, finalStart, finalEnd, isAllDay,
                    location, description, rrule)
            }
            loadEvents()
            updateWidgets()
        }
    }

    // 予定を更新する（終日予定はミリ秒補正）。
    fun updateEvent(
        eventId: Long, title: String, startMillis: Long, endMillis: Long,
        isAllDay: Boolean, location: String, description: String, rrule: String?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val (finalStart, finalEnd) = if (isAllDay) {
                val c = correctAllDayMillis(startMillis, endMillis); c.start to c.end
            } else startMillis to endMillis

            if (_calendarMode.value == CalendarMode.GOOGLE) {
                repository.updateEvent(eventId, title, finalStart, finalEnd, isAllDay,
                    location, description, rrule)
            } else {
                repository.updateLocalEvent(eventId, title, finalStart, finalEnd, isAllDay,
                    location, description, rrule)
            }
            loadEvents()
            updateWidgets()
        }
    }

    // 予定を削除する。
    fun deleteEvent(eventId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_calendarMode.value == CalendarMode.GOOGLE) repository.deleteEvent(eventId)
            else repository.deleteLocalEvent(eventId)
            loadEvents()
            updateWidgets()
        }
    }

    // 複数日予定から指定日だけを削除する。
    fun splitAndDeleteDay(event: Event, dateToRemove: LocalDate) {
        if (event.rrule != null || event.isReadOnly) return

        val isGoogle = _calendarMode.value == CalendarMode.GOOGLE
        val targetAccount = _selectedAccount.value
        viewModelScope.launch(Dispatchers.IO) {
            val zone = event.zone()
            val eventStart = event.localStartDate()
            val adjustedEndTime = if (event.endTime > event.startTime) event.endTime - 1 else event.endTime
            val eventEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(adjustedEndTime), zone).toLocalDate()

            if (eventStart == eventEnd) return@launch

            val newStart2 = dateToRemove.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val newEnd1 = if (event.isAllDay) {
                dateToRemove.atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                dateToRemove.minusDays(1).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
            }

            if (dateToRemove == eventStart) {
                if (isGoogle) repository.updateEvent(event.id, event.title, newStart2, event.endTime, event.isAllDay, event.location, event.description, null)
                else repository.updateLocalEvent(event.id, event.title, newStart2, event.endTime, event.isAllDay, event.location, event.description, null)
            } else if (dateToRemove == eventEnd) {
                if (isGoogle) repository.updateEvent(event.id, event.title, event.startTime, newEnd1, event.isAllDay, event.location, event.description, null)
                else repository.updateLocalEvent(event.id, event.title, event.startTime, newEnd1, event.isAllDay, event.location, event.description, null)
            } else {
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

    // 予定と設定をJSONファイルへエクスポートする。
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val result = backupManager.export(uri, _calendarMode.value, _selectedAccount.value)
            _statusMessage.value = when (result) {
                is BackupManager.Result.Success -> result.message
                is BackupManager.Result.Failure -> result.message
            }
        }
    }

    // JSONファイルから予定をインポートする（追記または上書き）。
    fun importBackup(uri: Uri, isAppend: Boolean) {
        viewModelScope.launch {
            val result = backupManager.import(uri, isAppend, _calendarMode.value, _selectedAccount.value)
            if (result is BackupManager.Result.Success) loadSettingsFromDataStore()
            _statusMessage.value = when (result) {
                is BackupManager.Result.Success -> result.message
                is BackupManager.Result.Failure -> result.message
            }
        }
    }
}