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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.monsivamon.golender.data.prefs.SettingsKeys
import com.monsivamon.golender.data.util.ReverseGeocoder
import com.monsivamon.golender.ui.common.findActivity
import com.monsivamon.golender.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/bright"

private const val DEFAULT_LAT = 35.681236
private const val DEFAULT_LNG = 139.767125

private const val GEOCODE_DEBOUNCE_MS = 600L

// 位置情報権限が許可されているかを判定する。
private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

// 「今後表示しない」が選択されたかを判定する。
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

// 最後に取得した現在地を返す。
@SuppressLint("MissingPermission")
private fun getBestLastKnownLocation(context: Context): Pair<Double, Double>? {
    if (!hasLocationPermission(context)) return null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
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

// 権限がある場合のみLocationComponentを有効化する。
@SuppressLint("MissingPermission")
private fun activateLocationComponent(context: Context, map: MapLibreMap) {
    if (!hasLocationPermission(context)) return
    val style = map.style ?: return
    try {
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

// アプリの設定画面（権限一覧）を開く。
private fun openAppSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) { }
}

// 地図を動かして中央のピンで場所を選ぶダイアログを表示する。
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
    MapLibre.getInstance(context)

    val mapHeight = (configuration.screenHeightDp * 0.70f).dp

    var resolvedLat by remember { mutableStateOf(initialLatitude) }
    var resolvedLng by remember { mutableStateOf(initialLongitude) }

    var showPermissionIntro by remember { mutableStateOf(false) }
    var showButtonIntro by remember { mutableStateOf(false) }
    var showPermanentlyDenied by remember { mutableStateOf(false) }

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var trackingMode by remember { mutableIntStateOf(CameraMode.NONE) }
    var styleLoaded by remember { mutableStateOf(false) }

    fun markLocationSetupDone() {
        scope.launch {
            try {
                context.dataStore.edit { prefs ->
                    prefs[SettingsKeys.LOCATION_SETUP_DONE] = true
                }
            } catch (_: Exception) { }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            val loc = getBestLastKnownLocation(context)
            resolvedLat = loc?.first ?: DEFAULT_LAT
            resolvedLng = loc?.second ?: DEFAULT_LNG
            if (styleLoaded) {
                mapInstance?.let { activateLocationComponent(context, it) }
            }
        } else {
            if (isPermanentlyDenied(context)) showPermanentlyDenied = true
            if (resolvedLat == null || resolvedLng == null) {
                resolvedLat = DEFAULT_LAT
                resolvedLng = DEFAULT_LNG
            }
        }
    }

    fun launchPermissionRequest() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

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

    var currentLat by remember { mutableDoubleStateOf(DEFAULT_LAT) }
    var currentLng by remember { mutableDoubleStateOf(DEFAULT_LNG) }

    var addressText by remember { mutableStateOf("") }
    var poiText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

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

    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(Unit) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    if (showPermissionIntro) {
        AlertDialog(
            onDismissRequest = {
                showPermissionIntro = false
                markLocationSetupDone()
                resolvedLat = DEFAULT_LAT
                resolvedLng = DEFAULT_LNG
            },
            containerColor = colors.surface,
            title = { Text("現在地の利用について", color = colors.text, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "現在地を初期位置として地図に表示します。\n\n" +
                            "位置情報を許可しなくても、この機能はそのまま利用できます（初期位置は東京駅付近になります）。\n" +
                            "あとから許可したい場合は、地図右下の現在地ボタンをタップしてください。",
                    color = colors.text,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionIntro = false
                    markLocationSetupDone()
                    launchPermissionRequest()
                }) {
                    Text("許可する", color = colors.primaryAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionIntro = false
                    markLocationSetupDone()
                    resolvedLat = DEFAULT_LAT
                    resolvedLng = DEFAULT_LNG
                }) {
                    Text("許可しない", color = colors.textGray)
                }
            },
        )
    }

    if (showButtonIntro) {
        AlertDialog(
            onDismissRequest = { showButtonIntro = false },
            containerColor = colors.surface,
            title = { Text("現在地を表示", color = colors.text, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "地図に現在地を表示するには、位置情報の許可が必要です。\n\n" +
                            "次の画面で「許可」を選んでください。",
                    color = colors.text,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showButtonIntro = false
                    launchPermissionRequest()
                }) {
                    Text("許可する", color = colors.primaryAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showButtonIntro = false }) {
                    Text("キャンセル", color = colors.textGray)
                }
            },
        )
    }

    if (showPermanentlyDenied) {
        AlertDialog(
            onDismissRequest = { showPermanentlyDenied = false },
            containerColor = colors.surface,
            title = { Text("位置情報が許可されていません", color = colors.text, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "現在地を表示するには、端末の設定から位置情報を許可してください。\n\n" +
                            "「設定を開く」をタップすると、Golendar の権限設定画面が開きます。\n" +
                            "「権限」→「位置情報」→「許可」を選択してください。",
                    color = colors.text,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermanentlyDenied = false
                    openAppSettings(context)
                }) {
                    Text("設定を開く", color = colors.primaryAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentlyDenied = false }) {
                    Text("閉じる", color = colors.textGray)
                }
            },
        )
    }

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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "場所を選択",
                        color = colors.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.weight(1f),
                    )
                }

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
                        AndroidView(
                            factory = { mapView },
                            modifier = Modifier.fillMaxSize(),
                            update = { view ->
                                view.layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                view.getMapAsync { map ->
                                    mapInstance = map

                                    try {
                                        map.uiSettings.isAttributionEnabled = false
                                        map.uiSettings.isLogoEnabled = false
                                    } catch (_: Exception) { }

                                    map.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) {
                                        styleLoaded = true
                                        activateLocationComponent(context, map)
                                    }
                                    map.cameraPosition = CameraPosition.Builder()
                                        .target(LatLng(sLat, sLng))
                                        .zoom(17.0)
                                        .build()
                                    currentLat = sLat
                                    currentLng = sLng
                                    map.addOnCameraIdleListener {
                                        map.cameraPosition.target?.let { target ->
                                            currentLat = target.latitude
                                            currentLng = target.longitude
                                        }
                                    }
                                }
                            },
                        )
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = colors.primaryAccent,
                            modifier = Modifier.size(48.dp).offset(y = (-12).dp),
                        )
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
                                contentDescription = "現在地",
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    when {
                        resolvedLat == null -> Text(
                            "現在地を取得中...",
                            color = colors.textGray,
                            fontSize = 14.sp,
                        )
                        isLoading -> Text(
                            "住所を取得中...",
                            color = colors.textGray,
                            fontSize = 14.sp,
                        )
                        else -> {
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
                            Text(
                                text = addressText.ifBlank { "住所不明" },
                                color = if (poiText.isNullOrBlank()) colors.text else colors.textGray,
                                fontSize = if (poiText.isNullOrBlank()) 15.sp else 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        "地図を動かして中央のピンを場所に合わせてください。",
                        color = colors.textGray,
                        fontSize = 12.sp,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("キャンセル", color = colors.textGray)
                    }
                    Spacer(Modifier.width(4.dp))
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
                            "決定",
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