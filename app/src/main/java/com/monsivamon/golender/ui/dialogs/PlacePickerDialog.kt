package com.monsivamon.golender.ui.dialogs

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import com.monsivamon.golender.R
import com.monsivamon.golender.data.dataStore
import com.monsivamon.golender.data.prefs.LanguageSetting
import com.monsivamon.golender.data.prefs.SettingsKeys
import com.monsivamon.golender.data.util.ForwardGeocoder
import com.monsivamon.golender.data.util.ReverseGeocoder
import com.monsivamon.golender.ui.common.findActivity
import com.monsivamon.golender.ui.theme.AppColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.concurrent.atomic.AtomicBoolean

// 使用する地図スタイルの URL（OpenFreeMap の bright）
private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/bright"

// 現在地が取得できない場合の既定座標（東京駅付近）
private const val DEFAULT_LAT = 35.681236
private const val DEFAULT_LNG = 139.767125

// 逆ジオコーディングのデバウンス時間（ミリ秒）
private const val GEOCODE_DEBOUNCE_MS = 600L

// 検索結果へ移動するときのズームとアニメーション時間
private const val SEARCH_RESULT_ZOOM = 17.0
private const val SEARCH_CAMERA_ANIMATION_MS = 600

// 位置情報権限が許可されているかを判定する
private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

// 「今後表示しない」が選択されたかを判定する
private fun isPermanentlyDenied(context: Context): Boolean {
    val activity = context.findActivity() ?: return false
    val fineDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
        activity, Manifest.permission.ACCESS_FINE_LOCATION
    )
    val coarseDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
        activity, Manifest.permission.ACCESS_COARSE_LOCATION
    )
    return fineDenied && coarseDenied
}

// 最後に取得した現在地のうち、最も新しいものを返す
@SuppressLint("MissingPermission")
private fun getBestLastKnownLocation(context: Context): Pair<Double, Double>? {
    if (!hasLocationPermission(context)) return null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    // 3 つのプロバイダから最新位置を選ぶ
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )
    var best: android.location.Location? = null
    for (p in providers) {
        val loc = try {
            lm.getLastKnownLocation(p)
        } catch (_: SecurityException) {
            null
        }
        if (loc != null && (best == null || loc.time > best.time)) best = loc
    }
    return best?.let { it.latitude to it.longitude }
}

// 権限がある場合のみ LocationComponent を有効化する
@SuppressLint("MissingPermission")
private fun activateLocationComponent(context: Context, map: MapLibreMap) {
    if (!hasLocationPermission(context)) return
    val style = map.style ?: return
    try {
        // 未活性なら初期化し、その後有効化する
        val lc = map.locationComponent
        if (!lc.isLocationComponentActivated) {
            lc.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style)
                    .useDefaultLocationEngine(true)
                    .build()
            )
        }
        lc.isLocationComponentEnabled = true
        lc.cameraMode = CameraMode.NONE
    } catch (_: SecurityException) {
    } catch (_: Exception) {
    }
}

// アプリの設定画面（権限一覧）を開く
private fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) { }
}

// 地図を動かして中央のピンで場所を選ぶダイアログを表示する
@Composable
fun PlacePickerDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    colors: AppColors,
    onDismiss: () -> Unit,
    onPlaceSelected: (address: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // MapLibre を初期化する
    MapLibre.getInstance(context)

    // 画面高の 70% を地図領域として確保する
    val mapHeight = (configuration.screenHeightDp * 0.70f).dp

    // 初期座標（初期値が無い場合は null で後続処理で解決する）
    var resolvedLat by remember { mutableStateOf(initialLatitude) }
    var resolvedLng by remember { mutableStateOf(initialLongitude) }

    // 各権限案内ダイアログの表示フラグ
    var showPermissionIntro by remember { mutableStateOf(false) }
    var showButtonIntro by remember { mutableStateOf(false) }
    var showPermanentlyDenied by remember { mutableStateOf(false) }

    // 地図インスタンス・トラッキングモード・スタイル読込状態
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var trackingMode by remember { mutableIntStateOf(CameraMode.NONE) }
    var styleLoaded by remember { mutableStateOf(false) }
    // 地図の初期化が一度だけ行われるようにするフラグ
    val mapInitialized = remember { AtomicBoolean(false) }

    // 地図を指定座標へ移動する（現在地の追従は解除する）
    fun moveCameraTo(latitude: Double, longitude: Double, zoom: Double = SEARCH_RESULT_ZOOM) {
        try {
            trackingMode = CameraMode.NONE
            mapInstance?.locationComponent?.cameraMode = CameraMode.NONE
        } catch (_: Exception) { }
        mapInstance?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoom),
            SEARCH_CAMERA_ANIMATION_MS,
        )
    }

    // 位置情報セットアップ完了フラグを DataStore に保存する
    fun markLocationSetupDone() {
        scope.launch {
            try {
                context.dataStore.edit { prefs ->
                    prefs[SettingsKeys.LOCATION_SETUP_DONE] = true
                }
            } catch (_: Exception) { }
        }
    }

    // 位置情報権限要求の結果を処理する
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            // 許可されたら現在地（または既定座標）に合わせる
            val loc = getBestLastKnownLocation(context)
            val newLat = loc?.first ?: DEFAULT_LAT
            val newLng = loc?.second ?: DEFAULT_LNG
            resolvedLat = newLat
            resolvedLng = newLng
            moveCameraTo(newLat, newLng)
            if (styleLoaded) {
                mapInstance?.let { activateLocationComponent(context, it) }
            }
        } else {
            // 恒久拒否なら専用ダイアログ、それ以外は既定座標で継続する
            if (isPermanentlyDenied(context)) showPermanentlyDenied = true
            if (resolvedLat == null || resolvedLng == null) {
                resolvedLat = DEFAULT_LAT
                resolvedLng = DEFAULT_LNG
            }
        }
    }

    // 位置情報権限のリクエストを起動する
    fun launchPermissionRequest() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    // 初回表示時の初期座標決定と権限案内の判定
    LaunchedEffect(Unit) {
        if (initialLatitude != null) {
            resolvedLng = initialLongitude ?: DEFAULT_LNG
            return@LaunchedEffect
        }
        if (hasLocationPermission(context)) {
            val loc = getBestLastKnownLocation(context)
            resolvedLat = loc?.first ?: DEFAULT_LAT
            resolvedLng = loc?.second ?: DEFAULT_LNG
        } else {
            // 未セットアップなら権限案内を表示し、セットアップ済みなら既定座標を使う
            val setupDone = try {
                context.dataStore.data.first()[SettingsKeys.LOCATION_SETUP_DONE] ?: false
            } catch (_: Exception) { false }

            if (!setupDone) {
                showPermissionIntro = true
            } else {
                resolvedLat = DEFAULT_LAT
                resolvedLng = DEFAULT_LNG
            }
        }
    }

    // 地図の中央座標（逆ジオコーディング対象）
    var currentLat by remember { mutableDoubleStateOf(DEFAULT_LAT) }
    var currentLng by remember { mutableDoubleStateOf(DEFAULT_LNG) }

    // 住所文字列と POI 名
    var addressText by remember { mutableStateOf("") }
    var poiText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // 検索欄と検索結果の状態
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ForwardGeocoder.Place>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    var showSearchResults by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Nominatim へ送る言語タグ（アプリ内の言語選択に合わせる）
    val languageTag = remember(context) {
        runCatching { LanguageSetting.sync(context) }
            .getOrDefault(LanguageSetting.defaultTag(context))
    }

    // 検索操作が実行されたときだけ Nominatim へ問い合わせる
    fun submitSearch() {
        val keyword = searchQuery.trim()
        if (keyword.isBlank()) return
        keyboardController?.hide()
        searchJob?.cancel()
        isSearching = true
        searchFailed = false
        searchResults = emptyList()
        showSearchResults = true
        searchJob = scope.launch {
            when (val result = ForwardGeocoder.search(keyword, languageTag)) {
                is ForwardGeocoder.SearchResult.Success -> {
                    searchResults = result.places
                    searchFailed = false
                }
                ForwardGeocoder.SearchResult.Failure -> {
                    searchResults = emptyList()
                    searchFailed = true
                }
            }
            isSearching = false
        }
    }

    // 中央座標が変わるたびにデバウンスして逆ジオコーディングを実行する
    LaunchedEffect(currentLat, currentLng) {
        isLoading = true
        delay(GEOCODE_DEBOUNCE_MS)

        val result = ReverseGeocoder.reverseGeocodeDetailed(currentLat, currentLng)
        if (result != null) {
            addressText = result.address
            poiText = result.poiName
        } else {
            addressText = ""
            poiText = null
        }
        isLoading = false
    }

    // 使い回す MapView を生成する
    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }

    // ライフサイクルに応じて MapView を開始・停止する
    DisposableEffect(Unit) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // 位置情報の権限案内ダイアログ
    if (showPermissionIntro) {
        MapPermissionIntroDialog(
            colors = colors,
            title = stringResource(R.string.dl2_locationIntroTitle),
            message = stringResource(R.string.dl2_locationIntroMessage),
            confirmLabel = stringResource(R.string.dl2_allow),
            dismissLabel = stringResource(R.string.dl2_deny),
            onConfirm = {
                showPermissionIntro = false
                markLocationSetupDone()
                launchPermissionRequest()
            },
            onDismiss = {
                showPermissionIntro = false
                markLocationSetupDone()
                resolvedLat = DEFAULT_LAT
                resolvedLng = DEFAULT_LNG
            },
        )
    }

    // 現在地ボタンタップ時の案内ダイアログ
    if (showButtonIntro) {
        MapPermissionIntroDialog(
            colors = colors,
            title = stringResource(R.string.dl2_showCurrentLocation),
            message = stringResource(R.string.dl2_locationButtonIntroMessage),
            confirmLabel = stringResource(R.string.dl2_allow),
            dismissLabel = stringResource(R.string.dl2_cancel),
            onConfirm = {
                showButtonIntro = false
                launchPermissionRequest()
            },
            onDismiss = { showButtonIntro = false },
        )
    }

    // 恒久拒否時の案内ダイアログ
    if (showPermanentlyDenied) {
        AlertDialog(
            onDismissRequest = { showPermanentlyDenied = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.dl2_locationDeniedTitle), color = colors.text, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.dl2_locationDeniedMessage),
                    color = colors.text,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermanentlyDenied = false
                    openAppSettings(context)
                }) {
                    Text(stringResource(R.string.dl2_openSettings), color = colors.primaryAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentlyDenied = false }) {
                    Text(stringResource(R.string.dl2_close), color = colors.textGray)
                }
            },
        )
    }

    // 場所選択ダイアログ本体
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            color = colors.surface,
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {

                // ダイアログタイトル
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.dl2_placePickerTitle),
                        color = colors.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.weight(1f),
                    )
                }

                // 地図領域（中央にピン、右下に現在地ボタン）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(mapHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    val sLat = resolvedLat
                    val sLng = resolvedLng

                    if (sLat == null || sLng == null) {
                        CircularProgressIndicator(color = colors.primaryAccent)
                    } else {
                        // MapLibre の地図を AndroidView で埋め込む
                        AndroidView(
                            factory = { mapView },
                            modifier = Modifier.fillMaxSize(),
                            update = { view ->
                                // 再コンポーズのたびに地図を作り直さないよう初回だけ初期化する
                                if (!mapInitialized.compareAndSet(false, true)) return@AndroidView

                                view.layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                view.getMapAsync { map ->
                                    mapInstance = map

                                    // ロゴ・アトリビューションを非表示にする
                                    try {
                                        map.uiSettings.isAttributionEnabled = false
                                        map.uiSettings.isLogoEnabled = false
                                    } catch (_: Exception) { }

                                    // スタイル読込後に位置情報コンポーネントを有効化する
                                    map.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) {
                                        styleLoaded = true
                                        activateLocationComponent(context, map)
                                    }
                                    // 初期カメラ位置を設定する
                                    map.cameraPosition = CameraPosition.Builder()
                                        .target(LatLng(sLat, sLng))
                                        .zoom(17.0)
                                        .build()
                                    currentLat = sLat
                                    currentLng = sLng
                                    // カメラが止まるたびに中央座標を更新する
                                    map.addOnCameraIdleListener {
                                        map.cameraPosition.target?.let { target ->
                                            currentLat = target.latitude
                                            currentLng = target.longitude
                                        }
                                    }
                                    // ユーザーが地図を動かしたら検索結果を閉じる
                                    map.addOnCameraMoveStartedListener { reason ->
                                        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                            showSearchResults = false
                                            keyboardController?.hide()
                                        }
                                    }
                                }
                            },
                        )
                        // 中央のピンアイコン
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = colors.primaryAccent,
                            modifier = Modifier.size(48.dp).offset(y = (-12).dp),
                        )
                        // ライセンス表記
                        Text(
                            text = "© MapLibre © OpenStreetMap contributors",
                            color = Color.White,
                            fontSize = 9.sp,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 8.dp, bottom = 8.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                        // 現在地ボタン（権限チェックとカメラモード循環）
                        SmallFloatingActionButton(
                            onClick = {
                                val map = mapInstance ?: return@SmallFloatingActionButton

                                if (!hasLocationPermission(context)) {
                                    if (isPermanentlyDenied(context)) {
                                        showPermanentlyDenied = true
                                    } else {
                                        showButtonIntro = true
                                    }
                                    return@SmallFloatingActionButton
                                }

                                // NONE → TRACKING → TRACKING_COMPASS → NONE と循環する
                                trackingMode = when (trackingMode) {
                                    CameraMode.NONE -> CameraMode.TRACKING
                                    CameraMode.TRACKING -> CameraMode.TRACKING_COMPASS
                                    else -> CameraMode.NONE
                                }
                                try {
                                    val lc = map.locationComponent
                                    if (!lc.isLocationComponentActivated) {
                                        activateLocationComponent(context, map)
                                    }
                                    lc.cameraMode = trackingMode
                                    if (trackingMode != CameraMode.NONE) {
                                        lc.lastKnownLocation?.let { lc.forceLocationUpdate(it) }
                                    }
                                } catch (_: SecurityException) {
                                } catch (_: Exception) {
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp),
                            containerColor = Color.White,
                            contentColor = colors.primaryAccent,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_my_location),
                                contentDescription = stringResource(R.string.dl2_currentLocation),
                            )
                        }

                        // 検索欄と検索結果（地図の上に重ねて表示する）
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { text ->
                                    searchQuery = text
                                    if (text.isBlank()) {
                                        searchResults = emptyList()
                                        searchFailed = false
                                        showSearchResults = false
                                    }
                                },
                                placeholder = {
                                    Text(
                                        stringResource(R.string.dl2_placeSearchHint),
                                        color = colors.textGray,
                                        fontSize = 14.sp,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = colors.textGray,
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            searchQuery = ""
                                            searchResults = emptyList()
                                            searchFailed = false
                                            showSearchResults = false
                                        }) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.dl2_placeSearchClear),
                                                tint = colors.textGray,
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(28.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = colors.text,
                                    unfocusedTextColor = colors.text,
                                    focusedContainerColor = colors.surface,
                                    unfocusedContainerColor = colors.surface,
                                    focusedBorderColor = colors.primaryAccent,
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = colors.primaryAccent,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // 検索結果の一覧（検索中・失敗・0 件もここに表示する）
                            if (showSearchResults) {
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = colors.surface,
                                    shadowElevation = 6.dp,
                                ) {
                                    when {
                                        isSearching -> Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = colors.primaryAccent,
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                stringResource(R.string.dl2_placeSearching),
                                                color = colors.textGray,
                                                fontSize = 14.sp,
                                            )
                                        }
                                        searchFailed -> Text(
                                            stringResource(R.string.dl2_placeSearchError),
                                            color = colors.textGray,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(16.dp),
                                        )
                                        searchResults.isEmpty() -> Text(
                                            stringResource(R.string.dl2_placeSearchNoResults),
                                            color = colors.textGray,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(16.dp),
                                        )
                                        else -> LazyColumn(
                                            modifier = Modifier.heightIn(max = 260.dp)
                                        ) {
                                            items(searchResults) { place ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            // 検索結果へ移動し、ピンの微調整に切り替える
                                                            keyboardController?.hide()
                                                            showSearchResults = false
                                                            searchResults = emptyList()
                                                            searchQuery = place.name
                                                            currentLat = place.latitude
                                                            currentLng = place.longitude
                                                            moveCameraTo(place.latitude, place.longitude, place.zoom)
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Icon(
                                                        Icons.Default.LocationOn,
                                                        contentDescription = null,
                                                        tint = colors.textGray,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                    Spacer(Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            place.name,
                                                            color = colors.text,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        if (place.address.isNotBlank()) {
                                                            Text(
                                                                place.address,
                                                                color = colors.textGray,
                                                                fontSize = 12.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 住所表示と操作説明
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    when {
                        resolvedLat == null -> Text(
                            stringResource(R.string.dl2_locating),
                            color = colors.textGray,
                            fontSize = 14.sp,
                        )
                        isLoading -> Text(
                            stringResource(R.string.dl2_geocoding),
                            color = colors.textGray,
                            fontSize = 14.sp,
                        )
                        else -> {
                            // POI 名（あれば）を太字で表示する
                            poiText?.takeIf { it.isNotBlank() }?.let { poi ->
                                Text(
                                    poi,
                                    color = colors.text,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                            }
                            // 住所本体（POI の有無で文字サイズ・色を切り替える）
                            Text(
                                text = addressText.ifBlank { stringResource(R.string.dl2_unknownAddress) },
                                color = if (poiText.isNullOrBlank()) colors.text else colors.textGray,
                                fontSize = if (poiText.isNullOrBlank()) 15.sp else 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        stringResource(R.string.dl2_pinInstruction),
                        color = colors.textGray,
                        fontSize = 12.sp,
                    )
                }

                // 下部のキャンセル／決定ボタン
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dl2_cancel), color = colors.textGray)
                    }
                    Spacer(Modifier.width(4.dp))
                    // 「POI 名, 住所」形式に整形する
                    val resultText = when {
                        poiText.isNullOrBlank() -> addressText
                        addressText.isBlank() -> poiText ?: ""
                        else -> "$poiText, $addressText"
                    }
                    val canConfirm = resultText.isNotBlank() && !isLoading

                    TextButton(
                        onClick = { onPlaceSelected(resultText) },
                        enabled = canConfirm,
                    ) {
                        Text(
                            stringResource(R.string.dl2_confirmDecision),
                            color = if (canConfirm) colors.primaryAccent else colors.textGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}