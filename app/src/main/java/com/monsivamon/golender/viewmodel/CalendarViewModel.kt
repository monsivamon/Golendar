package com.monsivamon.golender.viewmodel

import android.app.Application
import android.net.Uri
import com.monsivamon.golender.data.EventPhoto
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.SelectedCalendar
import com.monsivamon.golender.data.SelectedCalendarJson
import com.monsivamon.golender.data.dataStore
import com.monsivamon.golender.data.prefs.SettingsKeys
import com.monsivamon.golender.data.source.CalendarMeta
import com.monsivamon.golender.notification.NotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import androidx.compose.ui.graphics.toArgb

// UI 状態とビジネスロジックを集約する ViewModel。
// DataStore / セットアップ / 検索キャッシュ / 予定 CRUD / カレンダー選択 /
// バックアップ / 祝日更新 / アカウント / ウィジェットは外部 Manager に委譲する。
class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    // ---------- 依存コンポーネント ----------
    private val context = application.applicationContext
    private val repository = CalendarRepository(application)
    private val eventLoader = CalendarEventLoader(repository)
    private val settingsStore = SettingsDataStore(context.dataStore)
    private val setupFlow = SetupFlowManager(settingsStore)
    private val searchCacheMgr = SearchCacheManager()
    private val eventCrud = EventCrudManager(repository)
    private val selectionMgr = CalendarSelectionManager(repository, settingsStore)
    private val holidayMgr = HolidayRefreshManager(repository, settingsStore)
    private val accountMgr = AccountManager(repository)
    private val widgetMgr = WidgetCoordinator(context)
    private val backupMgr = BackupCoordinator(
        BackupManager(context, repository), repository, settingsStore
    )
    private val SEARCH_YEARS_PAST = 2L
    private val SEARCH_YEARS_FUTURE = 2L

    // ---------- 表示状態 ----------
    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _searchResults = MutableStateFlow<List<Event>>(emptyList())
    val searchResults: StateFlow<List<Event>> = _searchResults.asStateFlow()
    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()
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
    private val _selectedCalendars = MutableStateFlow<List<SelectedCalendar>>(emptyList())
    val selectedCalendars: StateFlow<List<SelectedCalendar>> = _selectedCalendars.asStateFlow()
    private val _showHolidays = MutableStateFlow(true)
    val showHolidays: StateFlow<Boolean> = _showHolidays.asStateFlow()
    private val _showCalendarSelection = MutableStateFlow(false)
    val showCalendarSelection: StateFlow<Boolean> = _showCalendarSelection.asStateFlow()
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // バックアップ進捗
    data class BackupProgress(val phase: String, val current: Int, val total: Int)
    private val _backupProgress = MutableStateFlow<BackupProgress?>(null)
    val backupProgress: StateFlow<BackupProgress?> = _backupProgress.asStateFlow()

    private val _notifyAtStart = MutableStateFlow(true)
    val notifyAtStart: StateFlow<Boolean> = _notifyAtStart.asStateFlow()
    private val _notify10MinBefore = MutableStateFlow(true)
    val notify10MinBefore: StateFlow<Boolean> = _notify10MinBefore.asStateFlow()
    private val _showBottomList = MutableStateFlow(true)
    val showBottomList: StateFlow<Boolean> = _showBottomList.asStateFlow()
    private val _backupPhotos = MutableStateFlow(false)
    val backupPhotos: StateFlow<Boolean> = _backupPhotos.asStateFlow()

    // 遷移・ショートカット
    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()
    private val _pendingShortcut = MutableStateFlow<String?>(null)
    val pendingShortcut: StateFlow<String?> = _pendingShortcut.asStateFlow()
    private val _requestAddEvent = MutableStateFlow(false)
    val requestAddEvent: StateFlow<Boolean> = _requestAddEvent.asStateFlow()

    // セットアップダイアログ表示フラグ
    // 現在のバージョン名（Welcome の判定に使用）
    private val currentVersionName: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }

    // Welcome 画面の表示情報（null なら非表示）
    private val _welcomeInfo = MutableStateFlow<WelcomeInfo?>(null)
    val welcomeInfo: StateFlow<WelcomeInfo?> = _welcomeInfo.asStateFlow()
    private val _showNotificationSetup = MutableStateFlow(false)
    val showNotificationSetup: StateFlow<Boolean> = _showNotificationSetup.asStateFlow()
    private val _showCalendarSetup = MutableStateFlow(false)
    val showCalendarSetup: StateFlow<Boolean> = _showCalendarSetup.asStateFlow()
    private val _showGestureSetup = MutableStateFlow(false)
    val showGestureSetup: StateFlow<Boolean> = _showGestureSetup.asStateFlow()
    private val _showCalendarTutorial = MutableStateFlow(false)
    val showCalendarTutorial: StateFlow<Boolean> = _showCalendarTutorial.asStateFlow()
    private val _showGestureTutorial = MutableStateFlow(false)
    val showGestureTutorial: StateFlow<Boolean> = _showGestureTutorial.asStateFlow()
    private val _showMapTutorial = MutableStateFlow(false)
    val showMapTutorial: StateFlow<Boolean> = _showMapTutorial.asStateFlow()
    private val _showMonthViewToggleTutorial = MutableStateFlow(false)
    val showMonthViewToggleTutorial: StateFlow<Boolean> = _showMonthViewToggleTutorial.asStateFlow()
    private val _showScheduleAddTutorial = MutableStateFlow(false)
    val showScheduleAddTutorial: StateFlow<Boolean> = _showScheduleAddTutorial.asStateFlow()
    private val _showScheduleEditTutorial = MutableStateFlow(false)
    val showScheduleEditTutorial: StateFlow<Boolean> = _showScheduleEditTutorial.asStateFlow()
    private val _showRecurringTutorial = MutableStateFlow(false)
    val showRecurringTutorial: StateFlow<Boolean> = _showRecurringTutorial.asStateFlow()
    private val _showPhotoTutorial = MutableStateFlow(false)
    val showPhotoTutorial: StateFlow<Boolean> = _showPhotoTutorial.asStateFlow()
    private val _showSearchTutorial = MutableStateFlow(false)
    val showSearchTutorial: StateFlow<Boolean> = _showSearchTutorial.asStateFlow()
    private val _showNotificationTutorial = MutableStateFlow(false)
    val showNotificationTutorial: StateFlow<Boolean> = _showNotificationTutorial.asStateFlow()
    private val _showBackupTutorial = MutableStateFlow(false)
    val showBackupTutorial: StateFlow<Boolean> = _showBackupTutorial.asStateFlow()
    private val _showWidgetTutorial = MutableStateFlow(false)
    val showWidgetTutorial: StateFlow<Boolean> = _showWidgetTutorial.asStateFlow()
    private val _aiSetupDone = MutableStateFlow(true)
    val aiSetupDone: StateFlow<Boolean> = _aiSetupDone.asStateFlow()
    private val _showAiSetup = MutableStateFlow(false)
    val showAiSetup: StateFlow<Boolean> = _showAiSetup.asStateFlow()

    // ---------- チュートリアル表示要求 ----------
    fun requestShowCalendarTutorial() { _showCalendarTutorial.value = true }
    fun dismissCalendarTutorial() { _showCalendarTutorial.value = false }
    fun requestShowGestureTutorial() { _showGestureTutorial.value = true }
    fun dismissGestureTutorial() { _showGestureTutorial.value = false }
    fun requestShowMapTutorial() { _showMapTutorial.value = true }
    fun dismissMapTutorial() { _showMapTutorial.value = false }
    fun requestShowMonthViewToggleTutorial() { _showMonthViewToggleTutorial.value = true }
    fun dismissMonthViewToggleTutorial() { _showMonthViewToggleTutorial.value = false }
    fun requestShowScheduleAddTutorial() { _showScheduleAddTutorial.value = true }
    fun dismissScheduleAddTutorial() { _showScheduleAddTutorial.value = false }
    fun requestShowScheduleEditTutorial() { _showScheduleEditTutorial.value = true }
    fun dismissScheduleEditTutorial() { _showScheduleEditTutorial.value = false }
    fun requestShowRecurringTutorial() { _showRecurringTutorial.value = true }
    fun dismissRecurringTutorial() { _showRecurringTutorial.value = false }
    fun requestShowPhotoTutorial() { _showPhotoTutorial.value = true }
    fun dismissPhotoTutorial() { _showPhotoTutorial.value = false }
    fun requestShowSearchTutorial() { _showSearchTutorial.value = true }
    fun dismissSearchTutorial() { _showSearchTutorial.value = false }
    fun requestShowNotificationTutorial() { _showNotificationTutorial.value = true }
    fun dismissNotificationTutorial() { _showNotificationTutorial.value = false }
    fun requestShowBackupTutorial() { _showBackupTutorial.value = true }
    fun dismissBackupTutorial() { _showBackupTutorial.value = false }
    fun requestShowWidgetTutorial() { _showWidgetTutorial.value = true }
    fun dismissWidgetTutorial() { _showWidgetTutorial.value = false }
    fun requestNavigation(route: String) { _pendingRoute.value = route }
    fun consumeNavigation() { _pendingRoute.value = null }
    fun clearStatusMessage() { _statusMessage.value = null }
    fun requestShortcut(action: String) { _pendingShortcut.value = action }
    fun consumeShortcut() { _pendingShortcut.value = null }
    fun requestAddEvent() { _requestAddEvent.value = true }
    fun consumeAddEventRequest() { _requestAddEvent.value = false }
    fun requestShowAiSetup() { _showAiSetup.value = true }
    fun dismissAiSetup() { _showAiSetup.value = false }

    // ---------- 初期化 ----------
    init {
        loadSettingsFromDataStore()
        loadAccounts()
        checkAndFetchHolidays(force = false)
    }

    // ---------- 検索 ----------
    fun enterSearchMode() {
        viewModelScope.launch(Dispatchers.IO) {
            val cache = searchCacheMgr.get()
            if (cache != null) {
                applySearchFilter(_searchQuery.value, cache)
                return@launch
            }
            _isSearchLoading.value = true
            try {
                val loaded = loadEventsForSearch()
                searchCacheMgr.set(loaded)
                applySearchFilter(_searchQuery.value, loaded)
            } catch (_: Exception) {
                searchCacheMgr.set(emptyList())
                _searchResults.value = emptyList()
            } finally {
                _isSearchLoading.value = false
            }
        }
    }

    fun exitSearchMode() {
        _searchResults.value = emptyList()
        _isSearchLoading.value = false
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        val cache = searchCacheMgr.get() ?: return
        applySearchFilter(query, cache)
    }

    private fun invalidateSearchCache() {
        searchCacheMgr.invalidate()
        _searchResults.value = emptyList()
    }

    private fun applySearchFilter(query: String, source: List<Event>) {
        _searchResults.value = searchCacheMgr.filter(source, query)
    }

    private suspend fun loadEventsForSearch(): List<Event> {
        val now = LocalDate.now()
        val startDate = now.minusYears(SEARCH_YEARS_PAST)
        val endDate = now.plusYears(SEARCH_YEARS_FUTURE)
        val start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = endDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val visibleIds = _selectedCalendars.value.filter { it.isVisible }.map { it.calendarId }
        val colorMap = _selectedCalendars.value.filter { it.colorArgb != 0 }.associate { it.calendarId to it.colorArgb }
        val displayNameMap = _selectedCalendars.value.associate { it.calendarId to it.displayName }
        return eventLoader.loadEventsForSearch(start, end, _calendarMode.value, visibleIds, colorMap, displayNameMap, _showHolidays.value)
    }

    // ---------- 祝日 ----------
    private fun checkAndFetchHolidays(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (holidayMgr.refreshIfNeeded(force)) loadEvents()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ---------- 設定読み込み ----------
    private fun loadSettingsFromDataStore() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val preferences = settingsStore.all()
                preferences[SettingsKeys.THEME]?.let { _themeMode.value = ThemeMode.valueOf(it) }
                preferences[SettingsKeys.WEEK_START]?.let { _weekStartDay.value = DayOfWeek.valueOf(it) }
                preferences[SettingsKeys.MODE]?.let { _calendarMode.value = CalendarMode.valueOf(it) }
                preferences[SettingsKeys.NOTIFY_AT_START]?.let { _notifyAtStart.value = it }
                preferences[SettingsKeys.NOTIFY_10MIN]?.let { _notify10MinBefore.value = it }
                preferences[SettingsKeys.SHOW_BOTTOM_LIST]?.let { _showBottomList.value = it }
                preferences[SettingsKeys.BACKUP_PHOTOS]?.let { _backupPhotos.value = it }

                val welcome = setupFlow.decideWelcome(currentVersionName)
                _welcomeInfo.value = welcome
                val isWelcomeShowing = welcome != null
                val setup = setupFlow.compute()
                // Welcome 表示中は他のセットアップダイアログを抑止する
                _showNotificationSetup.value = !isWelcomeShowing && setup.showNotification
                _showCalendarSetup.value = !isWelcomeShowing && setup.showCalendar
                _showGestureSetup.value = !isWelcomeShowing && setup.showGesture
                _aiSetupDone.value = setup.aiDone

                preferences[SettingsKeys.BG_COLOR]?.let { colorStr ->
                    _calendarBgColor.value = if (colorStr == SettingsKeys.COLOR_UNSPECIFIED) Color.Unspecified
                    else Color(colorStr.toInt())
                }
                _showHolidays.value = preferences[SettingsKeys.SHOW_HOLIDAYS] ?: true

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

                val rawJson = preferences[SettingsKeys.SELECTED_CALENDARS]
                if (!rawJson.isNullOrBlank()) {
                    val loaded = SelectedCalendarJson.fromJson(rawJson)
                        .filter { it.accountName.contains("@") }
                    _selectedCalendars.value = loaded
                    if (loaded.size != SelectedCalendarJson.fromJson(rawJson).size) {
                        selectionMgr.persist(loaded)
                    }
                } else {
                    val oldAccount = preferences[SettingsKeys.ACCOUNT]
                    if (!oldAccount.isNullOrBlank()) {
                        _selectedCalendars.value = selectionMgr.migrateFromOldAccount(oldAccount)
                    }
                }

                val selectionDone = preferences[SettingsKeys.CALENDAR_SELECTION_DONE] ?: false
                if (_calendarMode.value == CalendarMode.GOOGLE && _selectedCalendars.value.isEmpty()) {
                    _showCalendarSelection.value = true
                } else if (!selectionDone && _calendarMode.value == CalendarMode.GOOGLE) {
                    _showCalendarSelection.value = true
                } else {
                    refreshAvailableCalendars()
                }
            } catch (_: Exception) { }
            loadEvents()
        }
    }

    // ---------- カレンダー選択 ----------
    fun refreshAvailableCalendars() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_calendarMode.value != CalendarMode.GOOGLE) return@launch
            val updated = selectionMgr.refresh(_selectedCalendars.value)
            if (updated != null) saveSelectedCalendars(updated)
        }
    }

    private fun fallbackToGolendar(message: String) {
        _calendarMode.value = CalendarMode.GOLENDAR
        _selectedCalendars.value = emptyList()
        _statusMessage.value = message
        viewModelScope.launch(Dispatchers.IO) {
            try { selectionMgr.persistFallback() } catch (_: Exception) { }
            invalidateSearchCache()
            checkAndFetchHolidays(force = true)
            loadEvents()
            widgetMgr.updateAll()
        }
    }

    fun saveSelectedCalendars(list: List<SelectedCalendar>) {
        if (list.isEmpty() && _calendarMode.value == CalendarMode.GOOGLE) {
            fallbackToGolendar("何も選択されなかったため Golendar モードに切り替えました")
            return
        }
        _selectedCalendars.value = list
        viewModelScope.launch(Dispatchers.IO) {
            try { selectionMgr.persist(list) } catch (_: Exception) { }
            invalidateSearchCache()
            loadEvents()
            widgetMgr.updateAll()
        }
    }

    fun setCalendarVisibility(calendarId: Long, visible: Boolean) {
        val updated = _selectedCalendars.value.map {
            if (it.calendarId == calendarId) it.copy(isVisible = visible) else it
        }
        if (_calendarMode.value == CalendarMode.GOOGLE && updated.none { it.isVisible }) {
            _statusMessage.value = "すべてのカレンダーが非表示です。予定が表示されません"
        }
        saveSelectedCalendars(updated)
    }

    fun setCalendarColor(calendarId: Long, colorArgb: Int) {
        val updated = _selectedCalendars.value.map {
            if (it.calendarId == calendarId) it.copy(colorArgb = colorArgb) else it
        }
        saveSelectedCalendars(updated)
    }

    fun setAllCalendarsVisible(visible: Boolean) {
        val updated = _selectedCalendars.value.map { it.copy(isVisible = visible) }
        if (_calendarMode.value == CalendarMode.GOOGLE && updated.none { it.isVisible }) {
            _statusMessage.value = "すべてのカレンダーが非表示です。予定が表示されません"
        }
        saveSelectedCalendars(updated)
    }

    fun setShowHolidays(show: Boolean) {
        _showHolidays.value = show
        viewModelScope.launch(Dispatchers.IO) {
            try { settingsStore.saveShowHolidays(show) } catch (_: Exception) { }
            invalidateSearchCache()
            loadEvents()
            widgetMgr.updateAll()
        }
    }

    fun markCalendarSelectionDone() {
        _showCalendarSelection.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try { settingsStore.setCalendarSelectionDone(true) } catch (_: Exception) { }
        }
    }

    fun setShowBottomList(show: Boolean) {
        _showBottomList.value = show
        viewModelScope.launch(Dispatchers.IO) {
            try { settingsStore.saveShowBottomList(show) } catch (_: Exception) { }
        }
    }

    // ---------- セットアップ ----------
    fun markWelcomeDone() {
        _welcomeInfo.value = null
        val v = currentVersionName
        viewModelScope.launch(Dispatchers.IO) {
            try {
                setupFlow.markWelcomeShown(v)
                // Welcome 完了後に次のセットアップを表示する
                val state = setupFlow.compute()
                _showNotificationSetup.value = state.showNotification
                _showCalendarSetup.value = state.showCalendar
                _showGestureSetup.value = state.showGesture
            } catch (_: Exception) { }
        }
    }

    fun markNotificationSetupDone() {
        _showNotificationSetup.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = setupFlow.markNotificationDone()
                if (state.showCalendar) _showCalendarSetup.value = true
                else if (state.showGesture) _showGestureSetup.value = true
            } catch (_: Exception) { }
        }
    }

    fun markCalendarSetupDone() {
        _showCalendarSetup.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = setupFlow.markCalendarDone()
                if (_calendarMode.value == CalendarMode.GOOGLE) {
                    if (setupFlow.isCalendarSelectionPending()) {
                        _showCalendarSelection.value = true
                    } else if (state.showGesture) {
                        _showGestureSetup.value = true
                    }
                } else {
                    if (state.showGesture) _showGestureSetup.value = true
                }
            } catch (_: Exception) { }
        }
    }

    fun markGestureSetupDone() {
        _showGestureSetup.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try { setupFlow.markGestureDone() } catch (_: Exception) { }
        }
    }

    fun markAiSetupDone() {
        _showAiSetup.value = false
        _aiSetupDone.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try { setupFlow.markAiDone() } catch (_: Exception) { }
        }
    }

    // ---------- 通知 ----------
    fun setNotifyOptions(atStart: Boolean, before10: Boolean) {
        _notifyAtStart.value = atStart
        _notify10MinBefore.value = before10
        viewModelScope.launch(Dispatchers.IO) {
            try { settingsStore.saveNotify(atStart, before10) } catch (_: Exception) { }
            NotificationScheduler.updateAlarms(context)
        }
    }

    // ---------- カスタム設定 ----------
    fun setCalendarBgColor(color: Color) {
        _calendarBgColor.value = color
        viewModelScope.launch(Dispatchers.IO) {
            val value = if (color == Color.Unspecified) SettingsKeys.COLOR_UNSPECIFIED else color.toArgb().toString()
            try { settingsStore.saveBgColor(value) } catch (_: Exception) { }
            widgetMgr.updateAll()
        }
    }

    fun setDayColor(day: DayOfWeek, color: Color) {
        val newMap = _dayColors.value.toMutableMap()
        newMap[day] = color
        _dayColors.value = newMap
        viewModelScope.launch(Dispatchers.IO) {
            val value = if (color == Color.Unspecified) SettingsKeys.COLOR_UNSPECIFIED else color.toArgb().toString()
            try { settingsStore.saveDayColor(day, value) } catch (_: Exception) { }
        }
    }

    // ---------- アカウント ----------
    fun loadAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            _availableAccounts.value = accountMgr.load()
        }
    }

    suspend fun fetchAccountNames(): List<String> = withContext(Dispatchers.IO) {
        val accounts = accountMgr.load()
        _availableAccounts.value = accounts
        accounts
    }

    // ---------- 予定読み込み ----------
    fun loadEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            val yearMonth = _currentMonth.value
            val start = yearMonth.minusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = yearMonth.plusMonths(1).atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            try {
                val fetchedEvents = if (_calendarMode.value == CalendarMode.GOOGLE) {
                    val visibleIds = _selectedCalendars.value.filter { it.isVisible }.map { it.calendarId }
                    val colorMap = _selectedCalendars.value.filter { it.colorArgb != 0 }.associate { it.calendarId to it.colorArgb }
                    val displayNameMap = _selectedCalendars.value.associate { it.calendarId to it.displayName }
                    eventLoader.loadGoogleEvents(start, end, visibleIds, colorMap, displayNameMap, _showHolidays.value)
                } else {
                    eventLoader.loadLocalEvents(start, end, _showHolidays.value)
                }
                _events.value = fetchedEvents
                viewModelScope.launch(Dispatchers.IO) { NotificationScheduler.updateAlarms(context) }
                widgetMgr.updateAll()
            } catch (_: SecurityException) {
                _events.value = emptyList()
            }
        }
    }

    // ---------- 日付選択 ----------
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        if (YearMonth.from(date) != _currentMonth.value) {
            _currentMonth.value = YearMonth.from(date)
            loadEvents()
        }
    }

    fun resetToToday() { selectDate(LocalDate.now()) }

    // ---------- テーマ・週開始・モード ----------
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch(Dispatchers.IO) {
            try { settingsStore.saveTheme(mode) } catch (_: Exception) { }
            widgetMgr.updateAll()
        }
    }

    fun setWeekStartDay(day: DayOfWeek) {
        _weekStartDay.value = day
        viewModelScope.launch(Dispatchers.IO) {
            try { settingsStore.saveWeekStart(day) } catch (_: Exception) { }
            widgetMgr.updateAll()
        }
    }

    fun setCalendarMode(mode: CalendarMode) {
        _calendarMode.value = mode
        invalidateSearchCache()
        viewModelScope.launch(Dispatchers.IO) {
            try { settingsStore.saveMode(mode) } catch (_: Exception) { }
            if (mode == CalendarMode.GOLENDAR) {
                checkAndFetchHolidays(force = true)
            } else {
                if (_selectedCalendars.value.isEmpty()) {
                    _showCalendarSelection.value = true
                } else {
                    refreshAvailableCalendars()
                }
            }
        }
        loadEvents()
    }

    // ---------- 予定 CRUD ----------
    fun addEvent(
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?,
        newPhotoUris: List<Uri> = emptyList(),
        targetCalendarId: Long? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            eventCrud.add(
                mode = _calendarMode.value,
                selectedCalendars = _selectedCalendars.value,
                title = title, startMillis = startMillis, endMillis = endMillis,
                isAllDay = isAllDay, location = location, description = description,
                rrule = rrule, newPhotoUris = newPhotoUris, targetCalendarId = targetCalendarId,
            )
            invalidateSearchCache()
            loadEvents()
            widgetMgr.updateAll()
        }
    }

    fun updateEvent(
        eventId: Long, title: String, startMillis: Long, endMillis: Long,
        isAllDay: Boolean, location: String, description: String, rrule: String?,
        newPhotoUris: List<Uri> = emptyList(),
        keptPhotoIds: List<Long> = emptyList(),
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            eventCrud.update(
                mode = _calendarMode.value,
                eventId = eventId, title = title,
                startMillis = startMillis, endMillis = endMillis,
                isAllDay = isAllDay, location = location, description = description,
                rrule = rrule, newPhotoUris = newPhotoUris, keptPhotoIds = keptPhotoIds,
            )
            invalidateSearchCache()
            loadEvents()
            widgetMgr.updateAll()
        }
    }

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            eventCrud.delete(eventId, _calendarMode.value)
            invalidateSearchCache()
            loadEvents()
            widgetMgr.updateAll()
        }
    }

    fun splitAndDeleteDay(event: Event, dateToRemove: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            eventCrud.splitAndDeleteDay(
                event = event, dateToRemove = dateToRemove,
                mode = _calendarMode.value, selectedCalendars = _selectedCalendars.value,
            )
            invalidateSearchCache()
            loadEvents()
            widgetMgr.updateAll()
        }
    }

    fun addEventsFromAi(events: List<LocalEvent>) {
        viewModelScope.launch(Dispatchers.IO) {
            eventCrud.addFromAi(events)
            invalidateSearchCache()
            loadEvents()
            widgetMgr.updateAll()
        }
    }

    fun setBackupPhotos(enabled: Boolean) {
        _backupPhotos.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            try { settingsStore.saveBackupPhotos(enabled) } catch (_: Exception) { }
        }
    }

    suspend fun getPhotosForEvent(eventId: Long): List<EventPhoto> =
        eventCrud.photos(eventId)

    // ---------- バックアップ ----------
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _backupProgress.value = BackupProgress("バックアップを準備中", 0, 0)
            val result = backupMgr.export(
                uri, _calendarMode.value, _selectedCalendars.value,
                includePhotos = _backupPhotos.value,
                onProgress = { phase, cur, tot ->
                    _backupProgress.value = BackupProgress(phase, cur, tot)
                },
            )
            _backupProgress.value = null
            _statusMessage.value = when (result) {
                is BackupManager.Result.Success -> result.message
                is BackupManager.Result.Failure -> result.message
            }
        }
    }

    fun importBackup(uri: Uri, isAppend: Boolean, targetCalendarId: Long? = null) {
        viewModelScope.launch {
            _backupProgress.value = BackupProgress("読み込みを準備中", 0, 0)
            val result = backupMgr.import(
                uri, isAppend, _calendarMode.value, _selectedCalendars.value,
                targetCalendarId = targetCalendarId,
                onProgress = { phase, cur, tot ->
                    _backupProgress.value = BackupProgress(phase, cur, tot)
                },
            )
            _backupProgress.value = null
            if (result is BackupManager.Result.Success) {
                invalidateSearchCache()
                loadSettingsFromDataStore()
            }
            _statusMessage.value = when (result) {
                is BackupManager.Result.Success -> result.message
                is BackupManager.Result.Failure -> result.message
            }
        }
    }

    // ---------- カレンダーメタ ----------
    suspend fun loadAllCalendarMetas(): List<CalendarMeta> =
        selectionMgr.allCalendarMetas()

    suspend fun loadGoogleCalendarMetas(): List<CalendarMeta> =
        selectionMgr.googleCalendarMetas()
}