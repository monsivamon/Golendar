package com.monsivamon.golender.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import com.monsivamon.golender.R
import com.monsivamon.golender.data.EventPhoto
import com.monsivamon.golender.data.LocalCalendar
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.SelectedCalendar
import com.monsivamon.golender.data.SelectedCalendarJson
import com.monsivamon.golender.data.dataStore
import com.monsivamon.golender.data.prefs.LanguageSetting
import com.monsivamon.golender.data.prefs.SettingsKeys
import com.monsivamon.golender.data.source.CalendarMeta
import com.monsivamon.golender.notification.NotificationScheduler
import com.monsivamon.golender.data.prefs.AppTimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.ui.graphics.toArgb

// UI 状態とビジネスロジックを集約する ViewModel
class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val displayZone get() = AppTimeZone.effective()
    private val repository = CalendarRepository(application)
    private val eventLoader = CalendarEventLoader(context, repository)
    private val settingsStore = SettingsDataStore(context.dataStore)
    private val setupFlow = SetupFlowManager(settingsStore)
    private val searchCacheMgr = SearchCacheManager()
    private val eventCrud = EventCrudManager(repository)
    private val selectionMgr = CalendarSelectionManager(repository, settingsStore)
    private val holidayMgr = HolidayRefreshManager(repository, settingsStore)
    private val accountMgr = AccountManager(repository)
    private val widgetMgr = WidgetCoordinator(context)
    private val backupMgr = BackupCoordinator(
        context, BackupManager(LanguageSetting.wrap(context), repository), repository, settingsStore
    )
    private val SEARCH_YEARS_PAST = 2L
    private val SEARCH_YEARS_FUTURE = 2L

    // 表示タイムゾーン設定の UI 変数。settings と picker ダイアログの双方から使う
    data class TimezoneSetting(
        val pinned: Boolean,      // 表示タイムゾーンを固定するか
        val zoneId: String,       // 固定する IANA ゾーン ID（例: "Asia/Tokyo"）
        val notifyPinned: Boolean, // 通知時刻も現地の時計に合わせるか
    )

    // ---------- 表示状態 ----------
    private val _currentMonth = MutableStateFlow(YearMonth.now(AppTimeZone.effective()))
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()
    private val _selectedDate = MutableStateFlow(LocalDate.now(AppTimeZone.effective()))
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
        mapOf(
            DayOfWeek.SUNDAY to Color(SettingsKeys.DEFAULT_SUNDAY_COLOR_ARGB),
            DayOfWeek.SATURDAY to Color(SettingsKeys.DEFAULT_SATURDAY_COLOR_ARGB),
        )
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

    // ---------- 表示タイムゾーン ----------
    // 表示タイムゾーン設定。固定 ON なら zoneId の時刻で全画面を表示する
    private val _timezoneSetting = MutableStateFlow(
        TimezoneSetting(pinned = false, zoneId = AppTimeZone.effective().id, notifyPinned = false)
    )
    val timezoneSetting: StateFlow<TimezoneSetting> = _timezoneSetting.asStateFlow()
    private val _showCalendarSelection = MutableStateFlow(false)
    val showCalendarSelection: StateFlow<Boolean> = _showCalendarSelection.asStateFlow()
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // 予定・写真の更新ごとにインクリメントする版数（イベントカードの📎再読込トリガ）
    private val _dataVersion = MutableStateFlow(0L)
    val dataVersion: StateFlow<Long> = _dataVersion.asStateFlow()

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

    // Golendar モードのローカルカレンダー一覧
    private val _localCalendars = MutableStateFlow<List<LocalCalendar>>(emptyList())
    val localCalendars: StateFlow<List<LocalCalendar>> = _localCalendars.asStateFlow()

    // 遷移・ショートカット
    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()
    private val _pendingShortcut = MutableStateFlow<String?>(null)
    val pendingShortcut: StateFlow<String?> = _pendingShortcut.asStateFlow()
    private val _requestAddEvent = MutableStateFlow(false)
    val requestAddEvent: StateFlow<Boolean> = _requestAddEvent.asStateFlow()

    private val currentVersionName: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }

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
    private val _showCalendarModeTutorial = MutableStateFlow(false)
    val showCalendarModeTutorial: StateFlow<Boolean> = _showCalendarModeTutorial.asStateFlow()

    // ---------- チュートリアル表示要求 ----------
    // カレンダー権限のチュートリアル表示を要求する
    fun requestShowCalendarTutorial() { _showCalendarTutorial.value = true }
    // カレンダー権限のチュートリアルを閉じる
    fun dismissCalendarTutorial() { _showCalendarTutorial.value = false }
    // ジェスチャー操作のチュートリアル表示を要求する
    fun requestShowGestureTutorial() { _showGestureTutorial.value = true }
    // ジェスチャー操作のチュートリアルを閉じる
    fun dismissGestureTutorial() { _showGestureTutorial.value = false }
    // 地図の使い方のチュートリアル表示を要求する
    fun requestShowMapTutorial() { _showMapTutorial.value = true }
    // 地図の使い方のチュートリアルを閉じる
    fun dismissMapTutorial() { _showMapTutorial.value = false }
    // 月表示の下部リストのチュートリアル表示を要求する
    fun requestShowMonthViewToggleTutorial() { _showMonthViewToggleTutorial.value = true }
    // 月表示の下部リストのチュートリアルを閉じる
    fun dismissMonthViewToggleTutorial() { _showMonthViewToggleTutorial.value = false }
    // 予定の追加のチュートリアル表示を要求する
    fun requestShowScheduleAddTutorial() { _showScheduleAddTutorial.value = true }
    // 予定の追加のチュートリアルを閉じる
    fun dismissScheduleAddTutorial() { _showScheduleAddTutorial.value = false }
    // 予定の編集と削除のチュートリアル表示を要求する
    fun requestShowScheduleEditTutorial() { _showScheduleEditTutorial.value = true }
    // 予定の編集と削除のチュートリアルを閉じる
    fun dismissScheduleEditTutorial() { _showScheduleEditTutorial.value = false }
    // 繰り返し予定のチュートリアル表示を要求する
    fun requestShowRecurringTutorial() { _showRecurringTutorial.value = true }
    // 繰り返し予定のチュートリアルを閉じる
    fun dismissRecurringTutorial() { _showRecurringTutorial.value = false }
    // 写真の活用のチュートリアル表示を要求する
    fun requestShowPhotoTutorial() { _showPhotoTutorial.value = true }
    // 写真の活用のチュートリアルを閉じる
    fun dismissPhotoTutorial() { _showPhotoTutorial.value = false }
    // 検索の使い方のチュートリアル表示を要求する
    fun requestShowSearchTutorial() { _showSearchTutorial.value = true }
    // 検索の使い方のチュートリアルを閉じる
    fun dismissSearchTutorial() { _showSearchTutorial.value = false }
    // 通知の設定のチュートリアル表示を要求する
    fun requestShowNotificationTutorial() { _showNotificationTutorial.value = true }
    // 通知の設定のチュートリアルを閉じる
    fun dismissNotificationTutorial() { _showNotificationTutorial.value = false }
    // バックアップと復元のチュートリアル表示を要求する
    fun requestShowBackupTutorial() { _showBackupTutorial.value = true }
    // バックアップと復元のチュートリアルを閉じる
    fun dismissBackupTutorial() { _showBackupTutorial.value = false }
    // ウィジェットとショートカットのチュートリアル表示を要求する
    fun requestShowWidgetTutorial() { _showWidgetTutorial.value = true }
    // ウィジェットとショートカットのチュートリアルを閉じる
    fun dismissWidgetTutorial() { _showWidgetTutorial.value = false }
    // 外部からの画面遷移要求を記録する
    fun requestNavigation(route: String) { _pendingRoute.value = route }
    // 遷移要求を消費する
    fun consumeNavigation() { _pendingRoute.value = null }

    // 起動時の1度きり読み出し用。UI 側で pendingRoute.value を直読みしないためのラッパー
    // 起動時の初期ルートを 1 度だけ取得する
    fun consumeInitialRoute(): String? {
        val r = _pendingRoute.value
        _pendingRoute.value = null
        return r
    }

    // 起動時の1度きり参照用（消費は pendingShortcut の LaunchedEffect が担当）
    // 起動時のショートカットを参照する
    fun peekPendingShortcut(): String? = _pendingShortcut.value
    // ステータスメッセージをクリアする
    fun clearStatusMessage() { _statusMessage.value = null }
    // ショートカットアクションを記録する
    fun requestShortcut(action: String) { _pendingShortcut.value = action }
    // ショートカットアクションを消費する
    fun consumeShortcut() { _pendingShortcut.value = null }
    // 予定追加要求を立てる
    fun requestAddEvent() { _requestAddEvent.value = true }
    // 予定追加要求を消費する
    fun consumeAddEventRequest() { _requestAddEvent.value = false }
    // AI 解析の初回説明表示を要求する
    fun requestShowAiSetup() { _showAiSetup.value = true }
    // AI 解析の初回説明を閉じる
    fun dismissAiSetup() { _showAiSetup.value = false }
    // カレンダーモードのチュートリアル表示を要求する
    fun requestShowCalendarModeTutorial() { _showCalendarModeTutorial.value = true }
    // カレンダーモードのチュートリアルを閉じる
    fun dismissCalendarModeTutorial() { _showCalendarModeTutorial.value = false }

    // 「カレンダーモード」セクションが展開されたとき、未表示ならチュートリアルを自動表示する
    // カレンダーモード節の展開時に初回チュートリアルを自動表示する
    fun onCalendarModeSectionExpanded() {
        if (_showCalendarModeTutorial.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val shown = try { settingsStore.isCalendarModeTutorialShown() } catch (_: Exception) { false }
            if (!shown) {
                try { settingsStore.markCalendarModeTutorialShown() } catch (_: Exception) { }
                _showCalendarModeTutorial.value = true
            }
        }
    }

    // ---------- 初期化 ----------
    // 初期化: 設定読み込み、アカウント取得、ローカルカレンダー読込、祝日チェックを実行する
    init {
        loadSettingsFromDataStore()
        loadAccounts()
        loadLocalCalendars()
        checkAndFetchHolidays(force = false)
    }

    // ---------- ローカルカレンダー ----------
    // ローカルカレンダー一覧を再読込する
    fun loadLocalCalendars() {
        viewModelScope.launch(Dispatchers.IO) {
            _localCalendars.value = repository.getAllLocalCalendars()
        }
    }

    // ローカルカレンダーを新規追加する
    fun addLocalCalendar(name: String, colorArgb: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val maxSort = _localCalendars.value.maxOfOrNull { it.sortOrder } ?: 0
            val newSort = maxSort + 1
            // 未設定(0) はそのまま保存する。
            // 表示時に AppColors.resolveCalendarColor が primaryAccent に
            // フォールバックするので、UI 側で勝手に色を割り当てない。
            repository.insertLocalCalendar(
                LocalCalendar(name = name, colorArgb = colorArgb, sortOrder = newSort)
            )
            _localCalendars.value = repository.getAllLocalCalendars()
            loadEvents()
            widgetMgr.updateAll()
        }
    }

    // ローカルカレンダーを更新する（可視 0 件は拒否）
    fun updateLocalCalendar(calendar: LocalCalendar) {
        // 「最低1つのカレンダーは常に表示」ルール:
        // 可視カレンダーが 0 になる操作は拒否する
        if (!calendar.isVisible) {
            val others = _localCalendars.value.filter { it.id != calendar.id && it.isVisible }
            if (others.isEmpty()) {
                _statusMessage.value = LanguageSetting.wrap(context).getString(R.string.vm_keep_one_calendar_visible)
                return
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateLocalCalendar(calendar)
            _localCalendars.value = repository.getAllLocalCalendars()
            loadEvents()
            widgetMgr.updateAll()
        }
    }

    // 全てのローカルカレンダーの可視/非表示を一括で切り替える。
    // 「最低1つは可視」ルールのため、呼び出し側は visible=true のみを使用する。
    // 全ローカルカレンダーの可視/非表示を一括切替する
    fun setAllLocalCalendarsVisible(visible: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = _localCalendars.value.map { it.copy(isVisible = visible) }
            for (cal in updated) {
                repository.updateLocalCalendar(cal)
            }
            _localCalendars.value = repository.getAllLocalCalendars()
            loadEvents()
            widgetMgr.updateAll()
        }
    }
    // ローカルカレンダーを削除する（所属予定も削除）
    fun deleteLocalCalendar(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteLocalCalendar(id)
            _localCalendars.value = repository.getAllLocalCalendars()
            loadEvents()
            widgetMgr.updateAll()
        }
    }

    // 指定カレンダーに属する予定の件数を返す（削除確認ダイアログ用）
    // 指定カレンダーの予定件数を返す
    suspend fun countEventsInLocalCalendar(id: Long): Int =
        withContext(Dispatchers.IO) { repository.countEventsInLocalCalendar(id) }

    // ---------- 検索 ----------
    // 検索モードに入り、検索対象を遅延ロードする
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

    // 検索モードを終了する
    fun exitSearchMode() {
        _searchResults.value = emptyList()
        _isSearchLoading.value = false
    }

    // 検索クエリを更新して結果を再フィルタする
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        val cache = searchCacheMgr.get() ?: return
        applySearchFilter(query, cache)
    }

    // 検索キャッシュを無効化する
    private fun invalidateSearchCache() {
        searchCacheMgr.invalidate()
        _searchResults.value = emptyList()
    }

    // クエリで検索結果をフィルタする
    private fun applySearchFilter(query: String, source: List<Event>) {
        _searchResults.value = searchCacheMgr.filter(source, query)
    }

    // 検索範囲（過去2年〜未来2年）の予定を読み込む
    private suspend fun loadEventsForSearch(): List<Event> {
        val now = LocalDate.now(displayZone)
        val startDate = now.minusYears(SEARCH_YEARS_PAST)
        val endDate = now.plusYears(SEARCH_YEARS_FUTURE)
        val start = startDate.atStartOfDay(displayZone).toInstant().toEpochMilli()
        val end = endDate.atTime(23, 59, 59).atZone(displayZone).toInstant().toEpochMilli()
        val visibleIds = _selectedCalendars.value.filter { it.isVisible }.map { it.calendarId }
        val colorMap = _selectedCalendars.value.filter { it.colorArgb != 0 }.associate { it.calendarId to it.colorArgb }
        val displayNameMap = _selectedCalendars.value.associate { it.calendarId to it.displayName }
        return eventLoader.loadEventsForSearch(start, end, _calendarMode.value, visibleIds, colorMap, displayNameMap, _showHolidays.value)
    }

    // ---------- 祝日 ----------
    // 祝日データを必要に応じて更新する
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
    // 起動時に DataStore から全設定を読み込む
    private fun loadSettingsFromDataStore() {
        viewModelScope.launch(Dispatchers.IO) {
            // 起動直後に loadEvents() が正しく動くよう、先にローカルカレンダーを
            // 読み込んで _localCalendars を埋めておく。
            // これが空のままだと Golendar モードで visibleLocalIds が空になり、
            // loadEvents() が予定 0 件を返してしまう（起動直後に予定が表示されない）。
            try {
                _localCalendars.value = repository.getAllLocalCalendars()
            } catch (_: Exception) { }
            // 旧Ver(1.1.6)で「全て非表示」状態だった場合の救済。
            // 新Verでは可視0件のUIを廃止したため、既存の全非表示状態をそのまま
            // 読み込むとカレンダーが何も表示されなくなる。先頭1件を可視に戻す。
            try {
                val locals = _localCalendars.value
                if (locals.isNotEmpty() && locals.none { it.isVisible }) {
                    val fixed = locals.mapIndexed { i, c -> if (i == 0) c.copy(isVisible = true) else c }
                    for (c in fixed) repository.updateLocalCalendar(c)
                    _localCalendars.value = repository.getAllLocalCalendars()
                }
            } catch (_: Exception) { }
            try {
                val preferences = settingsStore.all()
                preferences[SettingsKeys.THEME]?.let { _themeMode.value = ThemeMode.valueOf(it) }
                preferences[SettingsKeys.WEEK_START]?.let { _weekStartDay.value = DayOfWeek.valueOf(it) }
                preferences[SettingsKeys.MODE]?.let { _calendarMode.value = CalendarMode.valueOf(it) }
                preferences[SettingsKeys.NOTIFY_AT_START]?.let { _notifyAtStart.value = it }
                preferences[SettingsKeys.NOTIFY_10MIN]?.let { _notify10MinBefore.value = it }
                preferences[SettingsKeys.SHOW_BOTTOM_LIST]?.let { _showBottomList.value = it }
                preferences[SettingsKeys.BACKUP_PHOTOS]?.let { _backupPhotos.value = it }

                val welcome = setupFlow.decideWelcome(currentVersionName, preferences)
                _welcomeInfo.value = welcome
                val isWelcomeShowing = welcome != null
                val setup = setupFlow.compute(preferences)
                _showNotificationSetup.value = !isWelcomeShowing && setup.showNotification
                _showCalendarSetup.value = !isWelcomeShowing && setup.showCalendar
                _showGestureSetup.value = !isWelcomeShowing && setup.showGesture
                _aiSetupDone.value = setup.aiDone

                preferences[SettingsKeys.BG_COLOR]?.let { colorStr ->
                    _calendarBgColor.value = if (colorStr == SettingsKeys.COLOR_UNSPECIFIED) Color.Unspecified
                    else Color(colorStr.toInt())
                }
                _showHolidays.value = preferences[SettingsKeys.SHOW_HOLIDAYS] ?: true

                // 表示タイムゾーン設定を読み込んで UI 状態とプロセス内キャッシュへ反映する
                _timezoneSetting.value = TimezoneSetting(
                    pinned = preferences[SettingsKeys.TIMEZONE_PIN] ?: false,
                    zoneId = preferences[SettingsKeys.TIMEZONE_ID] ?: displayZone.id,
                    notifyPinned = preferences[SettingsKeys.TIMEZONE_NOTIFY_PIN] ?: false,
                )
                AppTimeZone.load(context)

                val savedColors = mutableMapOf(
                    DayOfWeek.SUNDAY to Color(SettingsKeys.DEFAULT_SUNDAY_COLOR_ARGB),
                    DayOfWeek.SATURDAY to Color(SettingsKeys.DEFAULT_SATURDAY_COLOR_ARGB),
                )
                DayOfWeek.values().forEach { day ->
                    preferences[SettingsKeys.dayColor(day)]?.let { colorStr ->
                        savedColors[day] = if (colorStr == SettingsKeys.COLOR_UNSPECIFIED) {
                            // 「標準」を明示選択した日は Unspecified を維持する
                            Color.Unspecified
                        } else {
                            try { Color(colorStr.toInt()) } catch (_: Exception) { Color.Unspecified }
                        }
                    }
                }
                _dayColors.value = savedColors

                val rawJson = preferences[SettingsKeys.SELECTED_CALENDARS]
                if (!rawJson.isNullOrBlank()) {
                    val loaded = SelectedCalendarJson.fromJson(rawJson)
                        .filter { it.accountName.contains("@") }
                    // 旧Verで「全て非表示」状態だった場合の救済。
                    // 先頭1件を可視に戻して永続化する。
                    if (loaded.isNotEmpty() && loaded.none { it.isVisible }) {
                        val fixed = loaded.mapIndexed { i, c -> if (i == 0) c.copy(isVisible = true) else c }
                        _selectedCalendars.value = fixed
                        selectionMgr.persist(fixed)
                    } else {
                        _selectedCalendars.value = loaded
                        if (loaded.size != SelectedCalendarJson.fromJson(rawJson).size) {
                            selectionMgr.persist(loaded)
                        }
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
    // 端末の実カレンダーと選択リストを突き合わせて更新する
    fun refreshAvailableCalendars() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_calendarMode.value != CalendarMode.GOOGLE) return@launch
            val updated = selectionMgr.refresh(_selectedCalendars.value)
            if (updated != null) saveSelectedCalendars(updated)
        }
    }

    // Google モードから Golendar モードへフォールバックする
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

    // 選択カレンダーを永続化し、予定を再読込する
    fun saveSelectedCalendars(list: List<SelectedCalendar>) {
        if (list.isEmpty() && _calendarMode.value == CalendarMode.GOOGLE) {
            fallbackToGolendar(LanguageSetting.wrap(context).getString(R.string.vm_nothing_selected_fallback_golendar))
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

    // Google カレンダーの表示 ON/OFF を切替する（可視 0 件は拒否）
    fun setCalendarVisibility(calendarId: Long, visible: Boolean) {
        // 「最低1つのカレンダーは常に表示」ルール:
        // 可視カレンダーが 0 になる操作は拒否する
        if (!visible) {
            val others = _selectedCalendars.value.filter { it.calendarId != calendarId && it.isVisible }
            if (others.isEmpty()) {
                _statusMessage.value = LanguageSetting.wrap(context).getString(R.string.vm_keep_one_calendar_visible)
                return
            }
        }
        val updated = _selectedCalendars.value.map {
            if (it.calendarId == calendarId) it.copy(isVisible = visible) else it
        }
        saveSelectedCalendars(updated)
    }

    // Google カレンダーの表示色を変更する
    fun setCalendarColor(calendarId: Long, colorArgb: Int) {
        val updated = _selectedCalendars.value.map {
            if (it.calendarId == calendarId) it.copy(colorArgb = colorArgb) else it
        }
        saveSelectedCalendars(updated)
    }

    // Google カレンダーの可視/非表示を一括で切り替える。
    // 「最低1つは可視」ルールのため、呼び出し側は visible=true のみを使用する。
    // 全 Google カレンダーの可視/非表示を一括切替する
    fun setAllCalendarsVisible(visible: Boolean) {
        val updated = _selectedCalendars.value.map { it.copy(isVisible = visible) }
        saveSelectedCalendars(updated)
    }
    // 祝日表示 ON/OFF を保存し、再読込する
    fun setShowHolidays(show: Boolean) {
        _showHolidays.value = show
        viewModelScope.launch(Dispatchers.IO) {
            try { settingsStore.saveShowHolidays(show) } catch (_: Exception) { }
            invalidateSearchCache()
            loadEvents()
            widgetMgr.updateAll()
        }
    }

    // カレンダー選択ダイアログの完了を記録する
    fun markCalendarSelectionDone() {
        _showCalendarSelection.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try { settingsStore.setCalendarSelectionDone(true) } catch (_: Exception) { }
        }
    }

    // 月表示の下部リスト ON/OFF を保存する
    fun setShowBottomList(show: Boolean) {
        _showBottomList.value = show
        viewModelScope.launch(Dispatchers.IO) {
            try { settingsStore.saveShowBottomList(show) } catch (_: Exception) { }
        }
    }

    // ---------- セットアップ ----------
    // Welcome 画面の表示完了を記録し、次ステップを判定する
    fun markWelcomeDone() {
        _welcomeInfo.value = null
        val v = currentVersionName
        viewModelScope.launch(Dispatchers.IO) {
            try {
                setupFlow.markWelcomeShown(v)
                val state = setupFlow.compute()
                _showNotificationSetup.value = state.showNotification
                _showCalendarSetup.value = state.showCalendar
                _showGestureSetup.value = state.showGesture
            } catch (_: Exception) { }
        }
    }

    // 通知セットアップ完了を記録し、次ステップを判定する
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

    // カレンダーセットアップ完了を記録し、次ステップを判定する
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

    // ジェスチャー案内の完了を記録する
    fun markGestureSetupDone() {
        _showGestureSetup.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try { setupFlow.markGestureDone() } catch (_: Exception) { }
        }
    }

    // AI 解析説明の完了を記録する
    fun markAiSetupDone() {
        _showAiSetup.value = false
        _aiSetupDone.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try { setupFlow.markAiDone() } catch (_: Exception) { }
        }
    }

    // ---------- 通知 ----------
    // 通知 ON/OFF を保存し、アラームを再登録する
    fun setNotifyOptions(atStart: Boolean, before10: Boolean) {
        _notifyAtStart.value = atStart
        _notify10MinBefore.value = before10
        viewModelScope.launch(Dispatchers.IO) {
            try { settingsStore.saveNotify(atStart, before10) } catch (_: Exception) { }
            NotificationScheduler.updateAlarms(context)
        }
    }

    // ---------- 表示タイムゾーン ----------
    // 表示タイムゾーン設定（固定 ON/OFF・固定ゾーン・通知固定）を保存し、
    // 通知・ウィジェット・予定一覧を新しい表示基準で更新する
    fun updateTimezoneSetting(pinned: Boolean, zoneId: String, notifyPinned: Boolean) {
        _timezoneSetting.value = TimezoneSetting(pinned, zoneId, notifyPinned)
        viewModelScope.launch(Dispatchers.IO) {
            try { AppTimeZone.save(context, pinned, zoneId, notifyPinned) } catch (_: Exception) { }
            // 「今日」の解釈が変わるため選択日を新基準の今日へ合わせ直す
            val today = LocalDate.now(AppTimeZone.effective())
            _selectedDate.value = today
            _currentMonth.value = YearMonth.from(today)
            try { NotificationScheduler.updateAlarms(context) } catch (_: Exception) { }
            try { widgetMgr.updateAll() } catch (_: Exception) { }
            loadEvents()
        }
    }

    // ---------- カスタム設定 ----------
    // アプリ背景色を保存し、ウィジェットを更新する
    fun setCalendarBgColor(color: Color) {
        _calendarBgColor.value = color
        viewModelScope.launch(Dispatchers.IO) {
            val value = if (color == Color.Unspecified) SettingsKeys.COLOR_UNSPECIFIED else color.toArgb().toString()
            try { settingsStore.saveBgColor(value) } catch (_: Exception) { }
            widgetMgr.updateAll()
        }
    }

    // 曜日ごとの文字色を保存する
    fun setDayColor(day: DayOfWeek, color: Color) {
        val newMap = _dayColors.value.toMutableMap()
        newMap[day] = color
        _dayColors.value = newMap
        viewModelScope.launch(Dispatchers.IO) {
            val value = if (color == Color.Unspecified) SettingsKeys.COLOR_UNSPECIFIED else color.toArgb().toString()
            try { settingsStore.saveDayColor(day, value) } catch (_: Exception) { }
            // 曜日色設定をウィジェットにも即時反映する
            try { widgetMgr.updateAll() } catch (_: Exception) { }
        }
    }

    // ---------- アカウント ----------
    // 端末のアカウント一覧を読み込む
    fun loadAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            _availableAccounts.value = accountMgr.load()
        }
    }

    // アカウント一覧を取得し、状態にも反映する
    suspend fun fetchAccountNames(): List<String> = withContext(Dispatchers.IO) {
        val accounts = accountMgr.load()
        _availableAccounts.value = accounts
        accounts
    }

    // ---------- 予定読み込み ----------
    // 現在月の予定をモードに応じて読み込む
    fun loadEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            val yearMonth = _currentMonth.value
            val start = yearMonth.minusMonths(1).atDay(1).atStartOfDay(displayZone).toInstant().toEpochMilli()
            val end = yearMonth.plusMonths(1).atEndOfMonth().atTime(23, 59, 59).atZone(displayZone).toInstant().toEpochMilli()
            try {
                val fetchedEvents = if (_calendarMode.value == CalendarMode.GOOGLE) {
                    val visibleIds = _selectedCalendars.value.filter { it.isVisible }.map { it.calendarId }
                    val colorMap = _selectedCalendars.value.filter { it.colorArgb != 0 }.associate { it.calendarId to it.colorArgb }
                    val displayNameMap = _selectedCalendars.value.associate { it.calendarId to it.displayName }
                    eventLoader.loadGoogleEvents(start, end, visibleIds, colorMap, displayNameMap, _showHolidays.value)
                } else {
                    val visibleLocalIds = _localCalendars.value.filter { it.isVisible }.map { it.id }
                    val localColorMap = _localCalendars.value.associate { it.id to it.colorArgb }
                    // 既定カレンダーの空文字センチネルは locale 名に展開してカード表示に使う
                    val defaultName = LanguageSetting.wrap(context).getString(R.string.default_calendar_name)
                    val localNameMap = _localCalendars.value.associate {
                        it.id to if (it.isDefault && it.name.isEmpty()) defaultName else it.name
                    }
                    eventLoader.loadLocalEvents(
                        start, end, _showHolidays.value, visibleLocalIds,
                        calendarColorMap = localColorMap,
                        calendarNameMap = localNameMap,
                    )
                }
                _events.value = fetchedEvents
                _dataVersion.value = _dataVersion.value + 1
                viewModelScope.launch(Dispatchers.IO) { NotificationScheduler.updateAlarms(context) }
                widgetMgr.updateAll()
            } catch (_: SecurityException) {
                _events.value = emptyList()
            }
        }
    }

    // ---------- 日付選択 ----------
    // 選択日を更新し、必要なら月も移動して再読込する
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        if (YearMonth.from(date) != _currentMonth.value) {
            _currentMonth.value = YearMonth.from(date)
            loadEvents()
        }
    }

    // 選択日を今日に戻す。「今日」は表示タイムゾーン基準
    fun resetToToday() { selectDate(LocalDate.now(displayZone)) }

    // ---------- テーマ・週開始・モード ----------
    // テーマモードを保存し、ウィジェットを更新する
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch(Dispatchers.IO) {
            try { settingsStore.saveTheme(mode) } catch (_: Exception) { }
            widgetMgr.updateAll()
        }
    }

    // 週の開始曜日を保存し、ウィジェットを更新する
    fun setWeekStartDay(day: DayOfWeek) {
        _weekStartDay.value = day
        viewModelScope.launch(Dispatchers.IO) {
            try { settingsStore.saveWeekStart(day) } catch (_: Exception) { }
            widgetMgr.updateAll()
        }
    }

    // カレンダーモードを切替える
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
    // 予定を新規追加する
    fun addEvent(
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?,
        newPhotoUris: List<Uri> = emptyList(),
        targetCalendarId: Long? = null,
        localCalendarId: Long? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            eventCrud.add(
                mode = _calendarMode.value,
                selectedCalendars = _selectedCalendars.value,
                title = title, startMillis = startMillis, endMillis = endMillis,
                isAllDay = isAllDay, location = location, description = description,
                rrule = rrule, newPhotoUris = newPhotoUris, targetCalendarId = targetCalendarId,
                localCalendarId = localCalendarId,
            )
            invalidateSearchCache()
            loadEvents()
            widgetMgr.updateAll()
        }
    }

    // 予定を更新する
    fun updateEvent(
        eventId: Long, title: String, startMillis: Long, endMillis: Long,
        isAllDay: Boolean, location: String, description: String, rrule: String?,
        newPhotoUris: List<Uri> = emptyList(),
        keptPhotoIds: List<Long> = emptyList(),
        localCalendarId: Long? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            eventCrud.update(
                mode = _calendarMode.value,
                eventId = eventId, title = title,
                startMillis = startMillis, endMillis = endMillis,
                isAllDay = isAllDay, location = location, description = description,
                rrule = rrule, newPhotoUris = newPhotoUris, keptPhotoIds = keptPhotoIds,
                localCalendarId = localCalendarId,
            )
            invalidateSearchCache()
            loadEvents()
            widgetMgr.updateAll()
        }
    }

    // 予定を削除する
    fun deleteEvent(eventId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            eventCrud.delete(eventId, _calendarMode.value)
            invalidateSearchCache()
            loadEvents()
            widgetMgr.updateAll()
        }
    }

    // 複数日予定から指定日だけを削除する
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

    // AI 解析結果をローカル DB に一括登録する
    fun addEventsFromAi(events: List<LocalEvent>, targetCalendarId: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            eventCrud.addFromAi(events, targetCalendarId)
            invalidateSearchCache()
            loadEvents()
            widgetMgr.updateAll()
        }
    }

    // 写真もバックアップ ON/OFF を保存する
    fun setBackupPhotos(enabled: Boolean) {
        _backupPhotos.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            try { settingsStore.saveBackupPhotos(enabled) } catch (_: Exception) { }
        }
    }

    // 指定予定の添付写真を取得する
    suspend fun getPhotosForEvent(eventId: Long): List<EventPhoto> =
        eventCrud.photos(eventId)

    // 複数予定の写真枚数を一括取得する（イベントカードの📎表示用）
    // 複数予定の写真枚数を一括取得する
    suspend fun getPhotoCountsForEvents(eventIds: List<Long>): Map<Long, Int> =
        withContext(Dispatchers.IO) {
            if (eventIds.isEmpty()) return@withContext emptyMap()
            repository.getPhotosForEvents(eventIds).mapValues { it.value.size }
        }

    // ---------- バックアップ ----------
    // バックアップをエクスポートする
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _backupProgress.value = BackupProgress(LanguageSetting.wrap(context).getString(R.string.vm_progress_preparing_backup), 0, 0)
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

    // バックアップをインポート（追記/復元）する
    fun importBackup(
        uri: Uri,
        isAppend: Boolean,
        targetGoogleCalendarId: Long? = null,
        targetLocalCalendarId: Long? = null,
        selectedSourceIds: Set<Long>? = null,
        autoCreateMissingCalendars: Boolean = false,
    ) {
        viewModelScope.launch {
            _backupProgress.value = BackupProgress(LanguageSetting.wrap(context).getString(R.string.vm_progress_preparing_import), 0, 0)
            val result = backupMgr.import(
                uri, isAppend, _calendarMode.value, _selectedCalendars.value,
                targetGoogleCalendarId = targetGoogleCalendarId,
                targetLocalCalendarId = targetLocalCalendarId,
                selectedSourceIds = selectedSourceIds,
                autoCreateMissingCalendars = autoCreateMissingCalendars,
                onProgress = { phase, cur, tot ->
                    _backupProgress.value = BackupProgress(phase, cur, tot)
                },
            )
            _backupProgress.value = null
            if (result is BackupManager.Result.Success) {
                invalidateSearchCache()
                loadLocalCalendars()
                loadSettingsFromDataStore()
            }
            _statusMessage.value = when (result) {
                is BackupManager.Result.Success -> result.message
                is BackupManager.Result.Failure -> result.message
            }
        }
    }

    // 復元前のプレビュー情報を取得する
    suspend fun peekBackup(uri: Uri): BackupManager.Metadata? =
        withContext(Dispatchers.IO) { backupMgr.peek(uri) }

    // ---------- カレンダーメタ ----------
    // 端末上の全カレンダーメタを取得する
    suspend fun loadAllCalendarMetas(): List<CalendarMeta> =
        selectionMgr.allCalendarMetas()

    // Google アカウントのカレンダーメタを取得する
    suspend fun loadGoogleCalendarMetas(): List<CalendarMeta> =
        selectionMgr.googleCalendarMetas()
}