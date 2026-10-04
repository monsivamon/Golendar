package com.monsivamon.golender.ui

import coil.compose.AsyncImage
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monsivamon.golender.R
import com.monsivamon.golender.ui.theme.AppColors

// クレジット 1 行分のデータ
private data class CreditItem(
    val icon: String,
    val name: String,
    val license: String,
)

// 「このアプリについて」を別ウィンドウ（Dialog）で表示する
@Composable
fun AboutAppDialog(
    colors: AppColors,
    onDismiss: () -> Unit,
) {
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
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 32.dp),
            color = colors.surface,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                // ヘッダー行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "このアプリについて",
                        color = colors.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = onDismiss) {
                        Text("閉じる", color = colors.textGray)
                    }
                }
                HorizontalDivider(
                    color = colors.divider,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                AboutAppContent(colors = colors)
            }
        }
    }
}

// 「このアプリについて」の内容表示
@Composable
fun AboutAppContent(colors: AppColors) {
    val context = LocalContext.current
    // パッケージ情報からバージョン名を取得（失敗時は空文字）
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: PackageManager.NameNotFoundException) { "" }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AboutHeader(colors = colors, version = versionName)
        AboutDescription(colors = colors)
        AboutCredits(colors = colors)
        AboutLinks(colors = colors, context = context)
    }
}

// ヘッダー（グラデーション背景 + アイコンタイル + 名前 + タグライン + バージョン）
@Composable
private fun AboutHeader(colors: AppColors, version: String) {
    val brush = Brush.linearGradient(
        colors = listOf(
            colors.primaryAccent.copy(alpha = 0.22f),
            colors.primaryAccent.copy(alpha = 0.06f),
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ランチャーアイコンをそのまま表示する
            AsyncImage(
                model = R.mipmap.ic_launcher,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = "Golendar",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    letterSpacing = 0.8.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Your idea, your future.",
                    fontSize = 12.sp,
                    color = colors.textGray,
                    fontStyle = FontStyle.Italic,
                )
                if (version.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "v$version",
                            fontSize = 11.sp,
                            color = colors.textGray,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

// 説明カード
@Composable
private fun AboutDescription(colors: AppColors) {
    AboutCard(colors = colors, title = "アプリについて") {
        Text(
            text = "月・週・日表示を備えた Android カレンダーアプリ。\n" +
                    "プライバシー重視の Golendarモード と、\n" +
                    "Googleカレンダー同期の Googleモード を搭載しています。",
            color = colors.text,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        )
    }
}

// データ提供・ライブラリカード
@Composable
private fun AboutCredits(colors: AppColors) {
    val items = listOf(
        CreditItem("🗓", "holidays-jp", "MIT"),
        CreditItem("🗺", "OpenStreetMap", "ODbL"),
        CreditItem("🏛", "国土地理院", "公共データ"),
        CreditItem("📍", "Nominatim", "GPL v2"),
        CreditItem("🧭", "MapLibre Native", "BSD-2"),
        CreditItem("🎨", "AndroidX / Jetpack", "Apache 2.0"),
        CreditItem("⚡", "Kotlin", "Apache 2.0"),
        CreditItem("🖼", "Coil", "Apache 2.0"),
    )

    AboutCard(colors = colors, title = "データ提供・ライブラリ") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.icon, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.name,
                        color = colors.text,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = item.license,
                        color = colors.textGray,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

// リンクボタン群（GitHub / ライセンス）
@Composable
private fun AboutLinks(colors: AppColors, context: Context) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AboutLinkButton(
            icon = "🐙",
            label = "GitHub",
            colors = colors,
            modifier = Modifier.weight(1f),
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/monsivamon/Golendar"))
                )
            },
        )
        AboutLinkButton(
            icon = "📄",
            label = "ライセンス",
            colors = colors,
            modifier = Modifier.weight(1f),
            onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://raw.githubusercontent.com/monsivamon/Golendar/refs/heads/main/LICENSE")
                    )
                )
            },
        )
    }
}

// セクションカード（タイトル + 内容）の共通枠
@Composable
private fun AboutCard(
    colors: AppColors,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textGray,
            letterSpacing = 0.6.sp,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

// リンクボタン（アイコン + ラベル）
@Composable
private fun AboutLinkButton(
    icon: String,
    label: String,
    colors: AppColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}