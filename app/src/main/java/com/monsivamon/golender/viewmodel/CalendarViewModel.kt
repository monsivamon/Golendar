package com.monsivamon.golender.viewmodel

import android.app.Application
import android.net.Uri
import com.monsivamon.golender.data.EventPhoto
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.monsivamon.golender.data.CalendarRepository
import com.monsivamon.golender.data.Event
import com.monsivamon.golender.data.LocalEvent
import com.monsivamon.golender.data.SelectedCalendar
import com.monsivamon.golender.data.SelectedCalendarJson
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
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import androidx.compose.ui.graphics.toArgb

// UI状態とビジネスロジックを集約する ViewModel
class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    // 依存コンポーネントと作業用フィールド
    private val repository = CalendarRepository(application)
    private val context = application.applicationContext
    private val dataStore = context.dataStore
    private val backupManager = BackupManager(context, repository)
    private val eventLoader = CalendarEventLoader(repository)
    private val holidayFetchMutex = Mutex()
    private val SEARCH_YEARS_PAST = 2L
    private val SEARCH_YEARS_FUTURE = 2L
    private var searchCache: List<Event>? = null

    // 現在表示中の月
    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()
    // 選択中の日付
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()
    // 表示中の予定一覧
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()
    // 検索クエリ
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    // 検索結果一覧
    private val _searchResults = MutableStateFlow<List<Event>>(emptyList())
    val searchResults: StateFlow<List<Event>> = _searchResults.asStateFlow()
    // 検索中ローディング状態
    private val _isSearchLoading = MutableStateFlow(false)
    val isSearchLoading: StateFlow<Boolean> = _isSearchLoading.asStateFlow()
    // テーマモード
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    // 週の開始曜日
    private val _weekStartDay = MutableStateFlow(DayOfWeek.SUNDAY)
    val weekStartDay: StateFlow<DayOfWeek> = _weekStartDay.asStateFlow()
    // カレンダー背景色（未設定は Unspecified）
    private val _calendarBgColor = MutableStateFlow(Color.Unspecified)
    val calendarBgColor: StateFlow<Color> = _calendarBgColor.asStateFlow()
    // 曜日ごとの文字色（初期は日曜=赤、土曜=青）
    private val _dayColors = MutableStateFlow<Map<DayOfWeek, Color>>(
        mapOf(DayOfWeek.SUNDAY to Color(0xFFE53935), DayOfWeek.SATURDAY to Color(0xFF1E88E5))
    )
    val dayColors: StateFlow<Map<DayOfWeek, Color>> = _dayColors.asStateFlow()
    // カレンダーモード（GOLENDAR / GOOGLE）
    private val _calendarMode = MutableStateFlow(CalendarMode.GOLENDAR)
    val calendarMode: StateFlow<CalendarMode> = _calendarMode.asStateFlow()
    // 利用可能な Google アカウント一覧
    private val _availableAccounts = MutableStateFlow<List<String>>(emptyList())
    val availableAccounts: StateFlow<List<String>> = _availableAccounts.asStateFlow()

    // 表示対象として選択中のカレンダー一覧
    private val _selectedCalendars = MutableStateFlow<List<SelectedCalendar>>(emptyList())
    val selectedCalendars: StateFlow<List<SelectedCalendar>> = _selectedCalendars.asStateFlow()

    // 祝日を表示するかどうか
    private val _showHolidays = MutableStateFlow(true)
    val showHolidays: StateFlow<Boolean> = _showHolidays.asStateFlow()

    // カレンダー選択ダイアログの表示フラグ
    private val _showCalendarSelection = MutableStateFlow(false)
    val showCalendarSelection: StateFlow<Boolean> = _showCalendarSelection.asStateFlow()

    // 画面に表示するステータスメッセージ
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()
    // バックアップ／復元の進捗状態
    data class BackupProgress(val phase: String, val current: Int, val total: Int)
    private val _backupProgress = MutableStateFlow<BackupProgress?>(null)
    val backupProgress: StateFlow<BackupProgress?> = _backupProgress.asStateFlow()
    // 定刻通知 ON/OFF
    private val _notifyAtStart = MutableStateFlow(true)
    val notifyAtStart: StateFlow<Boolean> = _notifyAtStart.asStateFlow()
    // 10 分前通知 ON/OFF
    private val _notify10MinBefore = MutableStateFlow(true)
    val notify10MinBefore: StateFlow<Boolean> = _notify10MinBefore.asStateFlow()
    // 月表示の下部予定リスト表示 ON/OFF
    private val _showBottomList = MutableStateFlow(true)
    val showBottomList: StateFlow<Boolean> = _showBottomList.asStateFlow()
    // 外部からの画面遷移要求
    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()
    // ショートカットからのアクション要求
    private val _pendingShortcut = MutableStateFlow<String?>(null)
    val pendingShortcut: StateFlow<String?> = _pendingShortcut.asStateFlow()
    // 予定追加ダイアログの表示要求
    private val _requestAddEvent = MutableStateFlow(false)
    val requestAddEvent: StateFlow<Boolean> = _requestAddEvent.asStateFlow()
    // 初回通知セットアップの表示フラグ
    private val _showNotificationSetup = MutableStateFlow(false)
    val showNotificationSetup: StateFlow<Boolean> = _showNotificationSetup.asStateFlow()
    // 初回カレンダーセットアップの表示フラグ
    private val _showCalendarSetup = MutableStateFlow(false)
    val showCalendarSetup: StateFlow<Boolean> = _showCalendarSetup.asStateFlow()
    // 写真もバックアップするかどうか
    private val _backupPhotos = MutableStateFlow(false)
    val backupPhotos: StateFlow<Boolean> = _backupPhotos.asStateFlow()
    // 初回ジェスチャー案内の表示フラグ
    private val _showGestureSetup = MutableStateFlow(false)
    val showGestureSetup: StateFlow<Boolean> = _showGestureSetup.asStateFlow()
    // カレンダー権限チュートリアルの表示フラグ
    private val _showCalendarTutorial = MutableStateFlow(false)
    val showCalendarTutorial: StateFlow<Boolean> = _showCalendarTutorial.asStateFlow()
    // ジェスチャーチュートリアルの表示フラグ
    private val _showGestureTutorial = MutableStateFlow(false)
    val showGestureTutorial: StateFlow<Boolean> = _showGestureTutorial.asStateFlow()
    // AI 解析の初回説明完了フラグ
    private val _aiSetupDone = MutableStateFlow(true)
    val aiSetupDone: StateFlow<Boolean> = _aiSetupDone.asStateFlow()
    // AI 解析の初回説明表示フラグ
    private val _showAiSetup = MutableStateFlow(false)
    val showAiSetup: StateFlow<Boolean> = _showAiSetup.asStateFlow()

    // カレンダー権限チュートリアルを再表示する
    fun requestShowCalendarTutorial() { _showCalendarTutorial.value = true }
    // カレンダー権限チュートリアルを閉じる
    fun dismissCalendarTutorial() { _showCalendarTutorial.value = false }
    // ジェスチャーチュートリアルを再表示する
    fun requestShowGestureTutorial() { _showGestureTutorial.value = true }
    // ジェスチャーチュートリアルを閉じる
    fun dismissGestureTutorial() { _showGestureTutorial.value = false }
    // 画面遷移要求を設定する
    fun requestNavigation(route: String) { _pendingRoute.value = route }
    // 画面遷移要求を消費済みにする
    fun consumeNavigation() { _pendingRoute.value = null }
    // ステータスメッセージをクリアする
    fun clearStatusMessage() { _statusMessage.value = null }
    // ショートカットアクション要求を設定する
    fun requestShortcut(action: String) { _pendingShortcut.value = action }
    // ショートカット要求を消費済みにする
    fun consumeShortcut() { _pendingShortcut.value = null }
    // 予定追加ダイアログの表示要求を設定する
    fun requestAddEvent() { _requestAddEvent.value = true }
    // 予定追加要求を消費済みにする
    fun consumeAddEventRequest() { _requestAddEvent.value = false }

    // 起動時に設定・アカウント・祝日を読み込む
    init {
        loadSettingsFromDataStore()
        loadAccounts()
        checkAndFetchHolidays(force = false)
    }

    // 検索モードに入るときに呼ぶ（検索対象を遅延ロード）
    fun enterSearchMode() {
        viewModelScope.launch(Dispatchers.IO) {
            val cache = searchCache
            if (cache != null) {
                applySearchFilter(_searchQuery.value, cache)
                return@launch
            }
            _isSearchLoading.value = true
            try {
                val loaded = loadEventsForSearch()
                searchCache = loaded
                applySearchFilter(_searchQuery.value, loaded)
            } catch (_: Exception) {
                searchCache = emptyList()
                _searchResults.value = emptyList()
            } finally {
                _isSearchLoading.value = false
            }
        }
    }

    // 検索モードを抜けるときに呼ぶ（結果をクリア）
    fun exitSearchMode() {
        _searchResults.value = emptyList()
        _isSearchLoading.value = false
    }

    // 検索クエリを更新し、キャッシュからフィルタして結果を更新する
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        val cache = searchCache ?: return
        applySearchFilter(query, cache)
    }

    // 検索キャッシュを無効化する
    private fun invalidateSearchCache() {
        searchCache = null
        _searchResults.value = emptyList()
    }

    // クエリでフィルタして検索結果を更新する
    private fun applySearchFilter(query: String, source: List<Event>) {
        _searchResults.value = if (query.isBlank()) {
            emptyList()
        } else {
            source.filter { it.title.contains(query, ignoreCase = true) }
        }
    }

    // 検索対象期間の予定を EventLoader 経由で取得する
    private suspend fun loadEventsForSearch(): List<Event> {
        val now = LocalDate.now()
        val startDate = now.minusYears(SEARCH_YEARS_PAST)
        val endDate = now.plusYears(SEARCH_YEARS_FUTURE)
        val start = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = endDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 検索に必要なメタ情報（可視 ID・色・表示名）を組み立てる
        val visibleIds = _selectedCalendars.value.filter { it.isVisible }.map { it.calendarId }
        val colorMap = _selectedCalendars.value.filter { it.colorArgb != 0 }.associate { it.calendarId to it.colorArgb }
        val displayNameMap = _selectedCalendars.value.associate { it.calendarId to it.displayName }

        return eventLoader.loadEventsForSearch(start, end, _calendarMode.value, visibleIds, colorMap, displayNameMap, _showHolidays.value)
    }

    // 30 日ごとに祝日データを取得する（並行実行をミューテックスで防止）
    private fun checkAndFetchHolidays(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            holidayFetchMutex.withLock {
                try {
                    val prefs = dataStore.data.first()
                    val lastFetch = prefs[SettingsKeys.LAST_HOLIDAY_FETCH] ?: 0L
                    val now = System.currentTimeMillis()
                    val thirtyDays = 30L * 24 * 60 * 60 * 1000L
                    // force 指定、または前回取得から 30 日以上経過したら再取得する
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

    // DataStore から全設定を読み込む
    private fun loadSettingsFromDataStore() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val preferences = dataStore.data.first()
                // 基本設定（テーマ・週開始・モード）
                preferences[SettingsKeys.THEME]?.let { _themeMode.value = ThemeMode.valueOf(it) }
                preferences[SettingsKeys.WEEK_START]?.let { _weekStartDay.value = DayOfWeek.valueOf(it) }
                preferences[SettingsKeys.MODE]?.let { _calendarMode.value = CalendarMode.valueOf(it) }
                // 通知・下部リスト表示の設定
                preferences[SettingsKeys.NOTIFY_AT_START]?.let { _notifyAtStart.value = it }
                preferences[SettingsKeys.NOTIFY_10MIN]?.let { _notify10MinBefore.value = it }
                preferences[SettingsKeys.SHOW_BOTTOM_LIST]?.let { _showBottomList.value = it }
                // 初回セットアップの進捗（通知→カレンダー→ジェスチャー）
                val notifDone = preferences[SettingsKeys.NOTIFICATION_SETUP_DONE] ?: false
                val calDone = preferences[SettingsKeys.CALENDAR_SETUP_DONE] ?: false
                preferences[SettingsKeys.BACKUP_PHOTOS]?.let { _backupPhotos.value = it }
                val gestureDone = preferences[SettingsKeys.GESTURE_SETUP_DONE] ?: false
                _showNotificationSetup.value = !notifDone
                _showCalendarSetup.value = notifDone && !calDone
                _showGestureSetup.value = notifDone && calDone && !gestureDone
                // AI 解析のセットアップ進捗
                val aiDone = preferences[SettingsKeys.AI_SETUP_DONE] ?: false
                _aiSetupDone.value = aiDone
                // 背景色の復元（未設定は Unspecified）
                preferences[SettingsKeys.BG_COLOR]?.let { colorStr ->
                    _calendarBgColor.value = if (colorStr == SettingsKeys.COLOR_UNSPECIFIED) Color.Unspecified
                    else Color(colorStr.toInt())
                }

                // 祝日表示の復元（既定 true）
                _showHolidays.value = preferences[SettingsKeys.SHOW_HOLIDAYS] ?: true

                // 曜日色の復元（デフォルトは日曜=赤、土曜=青）
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

                // 選択中カレンダー: 新形式の JSON があればそれを使う。無ければ旧アカウント設定から移行する
                val rawJson = preferences[SettingsKeys.SELECTED_CALENDARS]
                if (!rawJson.isNullOrBlank()) {
                    _selectedCalendars.value = SelectedCalendarJson.fromJson(rawJson)
                } else {
                    val oldAccount = preferences[SettingsKeys.ACCOUNT]
                    if (!oldAccount.isNullOrBlank()) {
                        migrateFromOldAccount(oldAccount)
                    }
                }

                // カレンダー選択ダイアログの表示判定
                // Googleモードで選択カレンダーが空の場合は、起動時フォールバックせず選択ダイアログを表示する
                // （v1.1.4 で起動時フォールバックを繰り返して復帰不能になったユーザーを救済するため）
                val selectionDone = preferences[SettingsKeys.CALENDAR_SELECTION_DONE] ?: false
                if (_calendarMode.value == CalendarMode.GOOGLE && _selectedCalendars.value.isEmpty()) {
                    // 空なら選択完了フラグに関わらず、必ず選択ダイアログを表示する
                    _showCalendarSelection.value = true
                } else if (!selectionDone && _calendarMode.value == CalendarMode.GOOGLE) {
                    _showCalendarSelection.value = true
                } else {
                    refreshAvailableCalendars()
                }

            } catch (_: Exception) { }
            // 設定反映後に予定を読み込む
            loadEvents()
        }
    }

    // 旧アカウント設定から選択カレンダーへの移行処理
    private suspend fun migrateFromOldAccount(accountName: String) {
        val metas = repository.getAllCalendars().filter { it.accountName == accountName }
        // 該当アカウントの全カレンダーを可視状態で登録する
        val selected = metas.map {
            SelectedCalendar(it.id, it.accountName, it.displayName, 0, true)
        }
        saveSelectedCalendars(selected)
        // 旧設定を削除して選択ダイアログを完了扱いにする
        dataStore.edit {
            it[SettingsKeys.CALENDAR_SELECTION_DONE] = true
            it.remove(SettingsKeys.ACCOUNT)
        }
    }

    // 端末のカレンダー一覧を再取得して選択リストを更新する
    fun refreshAvailableCalendars() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_calendarMode.value != CalendarMode.GOOGLE) return@launch
            val metas = repository.getAllCalendars()
            val current = _selectedCalendars.value.toMutableList()
            val currentIds = current.map { it.calendarId }.toSet()
            var changed = false
            // 新規カレンダーは非表示で追加する
            for (meta in metas) {
                if (!currentIds.contains(meta.id)) {
                    current.add(SelectedCalendar(meta.id, meta.accountName, meta.displayName, 0, false))
                    changed = true
                }
            }
            // 削除済みカレンダーを除去する
            val validIds = metas.map { it.id }.toSet()
            val filtered = current.filter { validIds.contains(it.calendarId) }
            if (filtered.size != current.size) changed = true

            if (changed) {
                saveSelectedCalendars(filtered)
            }
        }
    }

    // Googleモードでカレンダー未選択の場合に Golendar モードへ自動フォールバックする
    private fun fallbackToGolendar(message: String) {
        // UI を即座に切り替える（同期）
        _calendarMode.value = CalendarMode.GOLENDAR
        _selectedCalendars.value = emptyList()
        _statusMessage.value = message

        // 永続化・再読込は非同期で行う
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dataStore.edit {
                    it[SettingsKeys.MODE] = CalendarMode.GOLENDAR.name
                    it[SettingsKeys.SELECTED_CALENDARS] = SelectedCalendarJson.toJson(emptyList())
                    // 次回 Google モード選択時にカレンダー選択ダイアログを再表示させる
                    it[SettingsKeys.CALENDAR_SELECTION_DONE] = false
                }
            } catch (_: Exception) { }
            invalidateSearchCache()
            checkAndFetchHolidays(force = true)
            loadEvents()
            updateWidgets()
        }
    }

    // 選択カレンダーリストを保存し、関連する状態を更新する
    fun saveSelectedCalendars(list: List<SelectedCalendar>) {
        // Googleモードで1件も選択されなかった場合は Golendar モードへ自動フォールバック
        if (list.isEmpty() && _calendarMode.value == CalendarMode.GOOGLE) {
            fallbackToGolendar("何も選択されなかったため Golendar モードに切り替えました")
            return
        }

        _selectedCalendars.value = list
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit {
                it[SettingsKeys.SELECTED_CALENDARS] = SelectedCalendarJson.toJson(list)
            }
            invalidateSearchCache()
            loadEvents()
            updateWidgets()
        }
    }

    // 指定カレンダーの表示 ON/OFF を切替える
    fun setCalendarVisibility(calendarId: Long, visible: Boolean) {
        val updated = _selectedCalendars.value.map {
            if (it.calendarId == calendarId) it.copy(isVisible = visible) else it
        }
        // Googleモードで全カレンダーが非表示になった場合は警告する（モードは維持）
        if (_calendarMode.value == CalendarMode.GOOGLE && updated.none { it.isVisible }) {
            _statusMessage.value = "すべてのカレンダーが非表示です。予定が表示されません"
        }
        saveSelectedCalendars(updated)
    }

    // 指定カレンダーの色を設定する
    fun setCalendarColor(calendarId: Long, colorArgb: Int) {
        val updated = _selectedCalendars.value.map {
            if (it.calendarId == calendarId) it.copy(colorArgb = colorArgb) else it
        }
        saveSelectedCalendars(updated)
    }

    // 全カレンダーの表示 ON/OFF を一括で設定する
    fun setAllCalendarsVisible(visible: Boolean) {
        val updated = _selectedCalendars.value.map { it.copy(isVisible = visible) }
        // Googleモードで全カレンダーが非表示になった場合は警告する（モードは維持）
        if (_calendarMode.value == CalendarMode.GOOGLE && updated.none { it.isVisible }) {
            _statusMessage.value = "すべてのカレンダーが非表示です。予定が表示されません"
        }
        saveSelectedCalendars(updated)
    }

    // 祝日表示の ON/OFF を設定して保存する
    fun setShowHolidays(show: Boolean) {
        _showHolidays.value = show
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it[SettingsKeys.SHOW_HOLIDAYS] = show }
            invalidateSearchCache()
            loadEvents()
            updateWidgets()
        }
    }

    // カレンダー選択ダイアログを完了扱いにして閉じる
    fun markCalendarSelectionDone() {
        _showCalendarSelection.value = false
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { it[SettingsKeys.CALENDAR_SELECTION_DONE] = true }
        }
    }

    // テーマ・週開始・モード・アカウントの設定を DataStore に保存する
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

    // 月表示の下部予定リスト表示 ON/OFF を保存する
    fun setShowBottomList(show: Boolean) {
        _showBottomList.value = show
        viewModelScope.launch(Dispatchers.IO) {
            try { dataStore.edit { prefs -> prefs[SettingsKeys.SHOW_BOTTOM_LIST] = show } } catch (_: Exception) { }
        }
    }

    // 通知セットアップ完了を記録し、次のセットアップへ進める
    fun markNotificationSetupDone() {
        _showNotificationSetup.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dataStore.edit { prefs -> prefs[SettingsKeys.NOTIFICATION_SETUP_DONE] = true }
                val prefs = dataStore.data.first()
                val calDone = prefs[SettingsKeys.CALENDAR_SETUP_DONE] ?: false
                val gestureDone = prefs[SettingsKeys.GESTURE_SETUP_DONE] ?: false
                if (!calDone) _showCalendarSetup.value = true
                else if (!gestureDone) _showGestureSetup.value = true
            } catch (_: Exception) { }
        }
    }

    // カレンダーセットアップ完了を記録し、必要ならカレンダー選択やジェスチャーへ進める
    fun markCalendarSetupDone() {
        _showCalendarSetup.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dataStore.edit { prefs -> prefs[SettingsKeys.CALENDAR_SETUP_DONE] = true }
                val gestureDone = dataStore.data.first()[SettingsKeys.GESTURE_SETUP_DONE] ?: false

                // Google モードではカレンダー選択を挟む
                if (_calendarMode.value == CalendarMode.GOOGLE) {
                    val selectionDone = dataStore.data.first()[SettingsKeys.CALENDAR_SELECTION_DONE] ?: false
                    if (!selectionDone) {
                        _showCalendarSelection.value = true
                    } else if (!gestureDone) {
                        _showGestureSetup.value = true
                    }
                } else {
                    if (!gestureDone) _showGestureSetup.value = true
                }
            } catch (_: Exception) { }
        }
    }

    // ジェスチャー案内完了を記録してダイアログを閉じる
    fun markGestureSetupDone() {
        _showGestureSetup.value = false
        viewModelScope.launch(Dispatchers.IO) {
            try { dataStore.edit { prefs -> prefs[SettingsKeys.GESTURE_SETUP_DONE] = true } } catch (_: Exception) { }
        }
    }

    // 通知設定を更新し、アラームを再スケジュールする
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

    // カレンダー背景色を設定して保存する
    fun setCalendarBgColor(color: Color) {
        _calendarBgColor.value = color
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dataStore.edit { prefs ->
                    prefs[SettingsKeys.BG_COLOR] = if (color == Color.Unspecified) SettingsKeys.COLOR_UNSPECIFIED else color.toArgb().toString()
                }
            } catch (_: Exception) { }
            updateWidgets()
        }
    }

    // 指定曜日の文字色を設定して保存する
    fun setDayColor(day: DayOfWeek, color: Color) {
        val newMap = _dayColors.value.toMutableMap()
        newMap[day] = color
        _dayColors.value = newMap
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dataStore.edit { prefs ->
                    prefs[SettingsKeys.dayColor(day)] = if (color == Color.Unspecified) SettingsKeys.COLOR_UNSPECIFIED else color.toArgb().toString()
                }
            } catch (_: Exception) { }
        }
    }

    // 利用可能な Google アカウント一覧を読み込む
    fun loadAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val accounts = repository.getAccountNames()
                _availableAccounts.value = accounts
            } catch (_: SecurityException) {
                _availableAccounts.value = emptyList()
            }
        }
    }

    // アカウント一覧をその場で再取得する（Settings の Google 切替判定用）
    // 戻り値は取得できたアカウント名リスト。SecurityException 時は空リスト
    suspend fun fetchAccountNames(): List<String> = withContext(Dispatchers.IO) {
        try {
            val accounts = repository.getAccountNames()
            _availableAccounts.value = accounts
            accounts
        } catch (_: SecurityException) {
            _availableAccounts.value = emptyList()
            emptyList()
        }
    }

    // 通常表示用に前後 1 ヶ月分の予定を読み込む
    fun loadEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            // 前後 1 ヶ月分の期間を算出する
            val yearMonth = _currentMonth.value
            val start = yearMonth.minusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = yearMonth.plusMonths(1).atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            try {
                // モード別にロードして祝日表示設定も反映する
                val fetchedEvents = if (_calendarMode.value == CalendarMode.GOOGLE) {
                    val visibleIds = _selectedCalendars.value.filter { it.isVisible }.map { it.calendarId }
                    val colorMap = _selectedCalendars.value.filter { it.colorArgb != 0 }.associate { it.calendarId to it.colorArgb }
                    val displayNameMap = _selectedCalendars.value.associate { it.calendarId to it.displayName }
                    eventLoader.loadGoogleEvents(start, end, visibleIds, colorMap, displayNameMap, _showHolidays.value)
                } else {
                    eventLoader.loadLocalEvents(start, end, _showHolidays.value)
                }
                _events.value = fetchedEvents
                // 通知再スケジュールとウィジェット更新も併せて実行する
                viewModelScope.launch(Dispatchers.IO) { NotificationScheduler.updateAlarms(context) }
                updateWidgets()
            } catch (_: SecurityException) {
                _events.value = emptyList()
            }
        }
    }

    // 日付を選択し、月が変われば予定を再読込する
    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        if (YearMonth.from(date) != _currentMonth.value) {
            _currentMonth.value = YearMonth.from(date)
            loadEvents()
        }
    }

    // 選択日を今日にリセットする
    fun resetToToday() { selectDate(LocalDate.now()) }

    // テーマモードを設定して保存する
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch(Dispatchers.IO) { saveSettings(theme = mode); updateWidgets() }
    }

    // 週の開始曜日を設定して保存する
    fun setWeekStartDay(day: DayOfWeek) {
        _weekStartDay.value = day
        viewModelScope.launch(Dispatchers.IO) { saveSettings(weekStart = day); updateWidgets() }
    }

    // カレンダーモードを設定し、必要に応じて祝日再取得やカレンダー再取得を行う
    fun setCalendarMode(mode: CalendarMode) {
        _calendarMode.value = mode
        invalidateSearchCache()
        viewModelScope.launch(Dispatchers.IO) {
            saveSettings(mode = mode)
            if (mode == CalendarMode.GOLENDAR) {
                checkAndFetchHolidays(force = true)
            } else {
                // Googleモード切替時、選択カレンダーが空なら選択ダイアログを再表示する
                if (_selectedCalendars.value.isEmpty()) {
                    _showCalendarSelection.value = true
                } else {
                    refreshAvailableCalendars()
                }
            }
        }
        loadEvents()
    }

    // 全ウィジェットを更新する
    private fun updateWidgets() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>().applicationContext
                listOf(DayWidget(), WeekWidget(), MonthWidget()).forEach { widget ->
                    GlanceAppWidgetManager(ctx).getGlanceIds(widget::class.java).forEach { id -> widget.update(ctx, id) }
                }
            } catch (_: Exception) { }
        }
    }

    // 予定を新規作成する（終日予定はミリ秒補正、写真添付対応）
    fun addEvent(
        title: String, startMillis: Long, endMillis: Long, isAllDay: Boolean,
        location: String, description: String, rrule: String?,
        newPhotoUris: List<Uri> = emptyList(),
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // 終日予定は UTC 日付境界に補正する
            val (finalStart, finalEnd) = if (isAllDay) {
                val c = correctAllDayMillis(startMillis, endMillis); c.start to c.end
            } else startMillis to endMillis
            val isGoogle = _calendarMode.value == CalendarMode.GOOGLE
            val newEventId: Long = if (isGoogle) {
                // 可視カレンダーの先頭を挿入先に選ぶ
                val targetId = _selectedCalendars.value
                    .firstOrNull { it.isVisible }?.calendarId
                if (targetId != null) {
                    repository.insertEventWithCalendarId(
                        title, finalStart, finalEnd, isAllDay,
                        location, description, rrule, targetId,
                    ) ?: -1L
                } else {
                    // 可視カレンダーが無い場合のみ、従来通りグローバル・プライマリへ
                    repository.insertEvent(
                        title, finalStart, finalEnd, isAllDay,
                        location, description, rrule, null,
                    ) ?: -1L
                }
            } else {
                repository.insertLocalEvent(title, finalStart, finalEnd, isAllDay,
                    location, description, rrule)
            }
            // Golendar モード時のみ写真を添付する
            if (!isGoogle && newEventId > 0 && newPhotoUris.isNotEmpty()) {
                newPhotoUris.take(EventPhoto.MAX_PHOTOS_PER_EVENT).forEach { uri ->
                    repository.attachPhotoToEvent(newEventId, uri)
                }
            }
            invalidateSearchCache()
            loadEvents()
            updateWidgets()
        }
    }

    // 予定を更新する（終日予定はミリ秒補正、写真の追加・削除を反映）
    fun updateEvent(
        eventId: Long, title: String, startMillis: Long, endMillis: Long,
        isAllDay: Boolean, location: String, description: String, rrule: String?,
        newPhotoUris: List<Uri> = emptyList(),
        keptPhotoIds: List<Long> = emptyList(),
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // 終日予定は UTC 日付境界に補正する
            val (finalStart, finalEnd) = if (isAllDay) {
                val c = correctAllDayMillis(startMillis, endMillis); c.start to c.end
            } else startMillis to endMillis
            val isGoogle = _calendarMode.value == CalendarMode.GOOGLE
            if (isGoogle) {
                repository.updateEvent(eventId, title, finalStart, finalEnd, isAllDay,
                    location, description, rrule)
            } else {
                repository.updateLocalEvent(eventId, title, finalStart, finalEnd, isAllDay,
                    location, description, rrule)
                // 削除された写真を消し、残り枠に新規写真を追加する
                val existing = repository.getPhotosForEvent(eventId)
                existing.filter { it.id !in keptPhotoIds }.forEach { repository.removePhoto(it) }
                val room = (EventPhoto.MAX_PHOTOS_PER_EVENT - keptPhotoIds.size).coerceAtLeast(0)
                newPhotoUris.take(room).forEach { uri ->
                    repository.attachPhotoToEvent(eventId, uri)
                }
            }
            invalidateSearchCache()
            loadEvents()
            updateWidgets()
        }
    }

    // 予定を削除する
    fun deleteEvent(eventId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_calendarMode.value == CalendarMode.GOOGLE) repository.deleteEvent(eventId)
            else repository.deleteLocalEvent(eventId)
            invalidateSearchCache()
            loadEvents()
            updateWidgets()
        }
    }

    // 複数日予定から指定日だけを削除する
    fun splitAndDeleteDay(event: Event, dateToRemove: LocalDate) {
        // 繰り返し予定・読み取り専用は対象外
        if (event.rrule != null || event.isReadOnly) return
        val isGoogle = _calendarMode.value == CalendarMode.GOOGLE
        viewModelScope.launch(Dispatchers.IO) {
            // 予定の前後分割に必要な境界時刻を求める
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
            // 開始日／終了日／中間日の 3 パターンで処理を分岐する
            if (dateToRemove == eventStart) {
                if (isGoogle) repository.updateEvent(event.id, event.title, newStart2, event.endTime, event.isAllDay, event.location, event.description, null)
                else repository.updateLocalEvent(event.id, event.title, newStart2, event.endTime, event.isAllDay, event.location, event.description, null)
            } else if (dateToRemove == eventEnd) {
                if (isGoogle) repository.updateEvent(event.id, event.title, event.startTime, newEnd1, event.isAllDay, event.location, event.description, null)
                else repository.updateLocalEvent(event.id, event.title, event.startTime, newEnd1, event.isAllDay, event.location, event.description, null)
            } else {
                // 中間日は前半を更新して後半を新規イベントとして挿入する
                if (isGoogle) {
                    repository.updateEvent(event.id, event.title, event.startTime, newEnd1, event.isAllDay, event.location, event.description, null)
                    // 分割後の予定は元予定と同じカレンダーへ（無効なら可視カレンダーの先頭にフォールバック）
                    val splitCalendarId = when {
                        event.calendarId > 0L -> event.calendarId
                        else -> _selectedCalendars.value
                            .firstOrNull { it.isVisible }?.calendarId
                    }
                    if (splitCalendarId != null) {
                        repository.insertEventWithCalendarId(
                            event.title, newStart2, event.endTime, event.isAllDay,
                            event.location, event.description, null, splitCalendarId,
                        )
                    } else {
                        repository.insertEvent(
                            event.title, newStart2, event.endTime, event.isAllDay,
                            event.location, event.description, null, null,
                        )
                    }
                } else {
                    repository.updateLocalEvent(event.id, event.title, event.startTime, newEnd1, event.isAllDay, event.location, event.description, null)
                    repository.insertLocalEvent(event.title, newStart2, event.endTime, event.isAllDay, event.location, event.description, null)
                }
            }
            invalidateSearchCache()
            loadEvents()
            updateWidgets()
        }
    }

    // AI 解析結果の予定を一括登録する
    fun addEventsFromAi(events: List<LocalEvent>) {
        viewModelScope.launch(Dispatchers.IO) {
            if (events.isNotEmpty()) repository.appendLocalEvents(events)
            invalidateSearchCache()
            loadEvents()
            updateWidgets()
        }
    }

    // 写真もバックアップ設定を保存する
    fun setBackupPhotos(enabled: Boolean) {
        _backupPhotos.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            try { dataStore.edit { prefs -> prefs[SettingsKeys.BACKUP_PHOTOS] = enabled } } catch (_: Exception) { }
        }
    }

    // 予定に添付された写真一覧を取得する（ダイアログ表示用）
    suspend fun getPhotosForEvent(eventId: Long): List<EventPhoto> =
        repository.getPhotosForEvent(eventId)

    // 予定と設定をファイルへエクスポートする
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _backupProgress.value = BackupProgress("バックアップを準備中", 0, 0)
            val result = backupManager.export(
                uri, _calendarMode.value, _selectedCalendars.value,
                includePhotos = _backupPhotos.value,
                onProgress = { phase, cur, tot ->
                    _backupProgress.value = BackupProgress(phase, cur, tot)
                },
            )
            _backupProgress.value = null
            // 結果をステータスメッセージとして通知する
            _statusMessage.value = when (result) {
                is BackupManager.Result.Success -> result.message
                is BackupManager.Result.Failure -> result.message
            }
        }
    }

    // ファイルから予定をインポートする（追記または上書き、復元先カレンダーの指定可）
    fun importBackup(uri: Uri, isAppend: Boolean, targetCalendarId: Long? = null) {
        viewModelScope.launch {
            _backupProgress.value = BackupProgress("読み込みを準備中", 0, 0)
            val result = backupManager.import(
                uri, isAppend, _calendarMode.value, _selectedCalendars.value,
                targetCalendarId = targetCalendarId,
                onProgress = { phase, cur, tot ->
                    _backupProgress.value = BackupProgress(phase, cur, tot)
                },
            )
            _backupProgress.value = null
            if (result is BackupManager.Result.Success) {
                invalidateSearchCache()
                // 復元後は祝日データが消えているため、即座に再取得を試みる
                _backupProgress.value = BackupProgress("祝日データを再取得中", 0, 0)
                val holidayOk = try { repository.fetchAndSaveHolidays() } catch (_: Exception) { false }
                try {
                    dataStore.edit {
                        it[SettingsKeys.LAST_HOLIDAY_FETCH] =
                            if (holidayOk) System.currentTimeMillis() else 0L
                    }
                } catch (_: Exception) { }
                _backupProgress.value = null
                // 設定を読み直して画面に反映する
                loadSettingsFromDataStore()
            }
            // 結果をステータスメッセージとして通知する
            _statusMessage.value = when (result) {
                is BackupManager.Result.Success -> result.message
                is BackupManager.Result.Failure -> result.message
            }
        }
    }

    // AI 解析の初回説明を再表示する要求を設定する
    fun requestShowAiSetup() { _showAiSetup.value = true }

    // AI 解析の初回説明を閉じる
    fun dismissAiSetup() { _showAiSetup.value = false }

    // 全カレンダーのメタ情報を取得する（復元先ピッカー等で使用）
    suspend fun loadAllCalendarMetas(): List<com.monsivamon.golender.data.source.CalendarMeta> =
        repository.getAllCalendars()

    // 実アカウントに紐づくカレンダーのメタ情報のみを取得する（カレンダー選択ダイアログ用）
    // account_local などの内部アカウントを除外する
    suspend fun loadGoogleCalendarMetas(): List<com.monsivamon.golender.data.source.CalendarMeta> =
        repository.getAllCalendars().filter { it.accountName.contains("@") }

    // AI 解析の初回説明完了を記録する
    fun markAiSetupDone() {
        _showAiSetup.value = false
        _aiSetupDone.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try { dataStore.edit { prefs -> prefs[SettingsKeys.AI_SETUP_DONE] = true } } catch (_: Exception) { }
        }
    }
}