package com.takina.bilisearch.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.takina.bilisearch.ui.components.ProfileSectionTitle
import com.takina.bilisearch.ui.components.TopBar

private const val REPO_URL = "https://github.com/Takina1109/BiliSearch"

/** 关于页：版本号 + 仓库入口 + 声明 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = rememberVersionName(context)

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "关于", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "BiliSearch",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "版本 $versionName",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "第三方哔哩哔哩客户端 · 仅供学习测试",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { HorizontalDivider() }
            item { ProfileSectionTitle("项目") }
            item {
                LinkRow(
                    title = "GitHub 仓库",
                    subtitle = REPO_URL,
                    onClick = { context.openUrl(REPO_URL) }
                )
            }
            item {
                LinkRow(
                    title = "问题反馈 / Issues",
                    subtitle = "$REPO_URL/issues",
                    onClick = { context.openUrl("$REPO_URL/issues") }
                )
            }
            item {
                LinkRow(
                    title = "开源许可",
                    subtitle = "MIT License",
                    onClick = { context.openUrl("$REPO_URL/blob/main/LICENSE") }
                )
            }

            item { HorizontalDivider() }
            item { ProfileSectionTitle("声明") }
            item {
                Text(
                    text = "本项目为个人出于兴趣开发，仅用于学习和测试。所用 API 均收集自官方网站，" +
                        "不提供、不存储、不破解任何内容。本项目与哔哩哔哩官方没有任何关系，" +
                        "未获得任何授权或认可。\n\n" +
                        "请勿用于商业用途或二次打包分发。若相关权利方认为本项目不妥，" +
                        "可通过仓库 Issue 联系，会立即删除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            item { HorizontalDivider() }
            item { ProfileSectionTitle("致谢") }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    CreditRow(
                        name = "bilibili-API-collect",
                        desc = "B 站接口文档",
                        url = "https://github.com/SocialSisterYi/bilibili-API-collect"
                    )
                    Spacer(Modifier.height(10.dp))
                    CreditRow(
                        name = "PiliPala",
                        desc = "播放链路思路参考",
                        url = "https://github.com/guozhigq/pilipala"
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "AndroidX Media3 · Coil · ZXing · OkHttp · Gson",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "＞",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CreditRow(name: String, desc: String, url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { context.openUrl(url) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun rememberVersionName(context: Context): String {
    return androidx.compose.runtime.remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"
    }
}

private fun Context.openUrl(url: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
