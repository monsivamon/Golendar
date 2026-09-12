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

// UI状態とビジネスロジックを管理するViewModel
class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CalendarRepository(application)
    private val context = application.applicationContext
    private val dataStore = context.dataStore

    private val backupManager = BackupManager(context, repository)

    // 祝日取得の並行実行を防ぐためのミューテックス
    private val holidayFetchMutex = Mutex()

    // ─── State ───

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

    // 定刻通知のON/OFF
    private val _notifyAtStart = MutableStateFlow(true)
    val notifyAtStart: StateFlow<Boolean> = _notifyAtStart.asStateFlow()

    // 10分前通知のON/OFF
    private val _notify10MinBefore = MutableStateFlow(true)
    val notify10MinBefore: StateFlow<Boolean> = _notify10MinBefore.asStateFlow()

    // ウィジェットタップ時の遷移先ルート
    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()

    fun requestNavigation(route: String) { _pendingRoute.value = route }
    fun consumeNavigation() { _pendingRoute.value = null }
    fun clearStatusMessage() { _statusMessage.value = null }

    // 起動時：設定読込、アカウント取得、祝日チェック
    init {
        loadSettingsFromDataStore()
        loadAccounts()
        checkAndFetchHolidays(force = false)
    }

    // 30日ごとに祝日データを取得（ミューテックスで並行実行を防止）
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

    // DataStoreから全設定を読み込む
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

                // 曜日ごとの色を読み込み（未設定は既定色）
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

    // テーマ・週開始・モード・アカウントの設定をDataStoreに保存
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

    // 通知設定を更新しアラームを再スケジュール
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

    // カレンダー背景色を設定
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

    // 指定曜日の色を設定
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

    // 利用可能なGoogleアカウント一覧を読み込む
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

    // カレンダーモードに応じて予定を読み込む
    fun loadEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            val yearMonth = _currentMonth.value
            val start = yearMonth.minusMonths(1).atDay(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = yearMonth.plusMonths(1).atEndOfMonth()
                .atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            try {
                val fetchedEvents = if (_calendarMode.value == CalendarMode.GOOGLE) {
                    // Googleモード：選択中アカウントのカレンダーID＋特殊カレンダーIDを統合
                    val calendarIds = _selectedAccount.value?.let {
                        (repository.getCalendarIdsForAccount(it) + repository.getSpecialCalendarIds()).distinct()
                    }
                    val googleEvents = repository.getEventsForMonth(start, end, calendarIds)

                    // 公式祝日データ（ローカルDB）を日付セットとして取得
                    val officialHolidays = repository.getLocalEventsForMonth(start, end)
                        .filter { it.description == LocalEvent.DESCRIPTION_HOLIDAY }
                    val officialDates = officialHolidays.map {
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(it.startTime), ZoneOffset.UTC).toLocalDate()
                    }.toSet()

                    // Google予定に祝日・文化イベント・誕生日のフラグを付与
                    val mappedGoogleEvents = googleEvents.map { event ->
                        val isBirthday = event.isBirthdayCalendar ||
                                (event.title.contains("誕生日") && !event.title.contains("天皇誕生日")) ||
                                event.title.contains("Birthday", ignoreCase = true)
                        var updatedEvent = event.copy(isBirthdayCalendar = isBirthday)

                        if (!isBirthday && (updatedEvent.isHolidayCalendar || (updatedEvent.isAllDay && updatedEvent.isReadOnly))) {
                            val date = updatedEvent.localStartDate()
                            if (officialDates.isNotEmpty()) {
                                // 公式祝日一覧と一致すれば祝日、そうでなければ文化イベント
                                val isOfficial = officialDates.contains(date)
                                updatedEvent = updatedEvent.copy(isHolidayCalendar = isOfficial, isCulturalEvent = isOfficial)
                            } else {
                                // 公式祝日がない場合はタイトルで文化イベントを判定
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

                    // Google側に存在しない祝日を抽出
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
                        // 疑似イベント（IDはマイナス値で衝突回避）
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

                    // 祝日は日付＋タイトルで重複除去（複数カレンダー購読対策）
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
                    // Golendarモード：システム祝日は閲覧専用、誕生日フラグを付与
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
                        // 祝日は日付＋タイトルで重複除去
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

    fun resetToToday() { selectDate(LocalDate.now()) }
    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    // テーマモードを変更しDataStoreに保存
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch(Dispatchers.IO) { saveSettings(theme = mode); updateWidgets() }
    }

    // 週の開始曜日を変更しDataStoreに保存
    fun setWeekStartDay(day: DayOfWeek) {
        _weekStartDay.value = day
        viewModelScope.launch(Dispatchers.IO) { saveSettings(weekStart = day); updateWidgets() }
    }

    // カレンダーモードを切り替え（Golendarモード時は祝日を強制取得）
    fun setCalendarMode(mode: CalendarMode) {
        _calendarMode.value = mode
        viewModelScope.launch(Dispatchers.IO) {
            saveSettings(mode = mode)
            if (mode == CalendarMode.GOLENDAR) checkAndFetchHolidays(force = true)
        }
        loadEvents()
    }

    // Googleアカウントを切り替えて予定を再読込
    fun setSelectedAccount(accountName: String?) {
        _selectedAccount.value = accountName
        viewModelScope.launch(Dispatchers.IO) { saveSettings(account = accountName) }
        loadEvents()
    }

    // ホーム画面ウィジェットを更新
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

    // 予定を追加（終日予定は時刻をUTC日付境界に補正）
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

    // 予定を更新（終日予定は時刻をUTC日付境界に補正）
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
        // 繰り返し予定・読み取り専用予定は対象外
        if (event.rrule != null || event.isReadOnly) return

        val isGoogle = _calendarMode.value == CalendarMode.GOOGLE
        val targetAccount = _selectedAccount.value
        viewModelScope.launch(Dispatchers.IO) {
            val zone = event.zone()
            val eventStart = event.localStartDate()
            val adjustedEndTime = if (event.endTime > event.startTime) event.endTime - 1 else event.endTime
            val eventEnd = LocalDateTime.ofInstant(Instant.ofEpochMilli(adjustedEndTime), zone).toLocalDate()

            if (eventStart == eventEnd) return@launch

            // 削除日の翌日開始時刻と前日終了時刻を計算
            val newStart2 = dateToRemove.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
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

    // バックアップをエクスポート（結果をステータスメッセージに反映）
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val result = backupManager.export(uri, _calendarMode.value, _selectedAccount.value)
            _statusMessage.value = when (result) {
                is BackupManager.Result.Success -> result.message
                is BackupManager.Result.Failure -> result.message
            }
        }
    }

    // バックアップをインポート（成功時は設定を再読込）
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