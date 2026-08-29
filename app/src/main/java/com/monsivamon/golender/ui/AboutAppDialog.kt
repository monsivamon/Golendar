package com.monsivamon.golender.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 「このアプリについて」の内容表示
@Composable
fun AboutAppContent(colors: AppColors) {
    val context = LocalContext.current

    val pInfo = try {
        context.packageManager.getPackageInfo(context.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) { null }
    val versionName = pInfo?.versionName ?: "1.0"

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Golendar", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.text)
        Text("v$versionName", fontSize = 14.sp, color = colors.textGray)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "月表示のカレンダーで、終日予定と時間指定予定をひと目で区別できるアプリです。\nGoogleカレンダーと連携し、月・週・日表示を切り替えられます。",
            color = colors.text,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // GitHubリポジトリへのリンク
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/monsivamon/Golendar"))
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.surface,
                contentColor = colors.primaryAccent
            ),
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.divider, RoundedCornerShape(12.dp))
        ) {
            Text("ソースコード (GitHub)")
        }
    }
}