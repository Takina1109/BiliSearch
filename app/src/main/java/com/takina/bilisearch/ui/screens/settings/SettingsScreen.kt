package com.takina.bilisearch.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.takina.bilisearch.data.local.AppLog
import com.takina.bilisearch.ui.components.ProfileSectionTitle
import com.takina.bilisearch.ui.components.TopBar
import com.takina.bilisearch.ui.util.LogExporter

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBlacklist: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenAbout: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "设置", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { ProfileSectionTitle("账号") }

            if (vm.isLoggedIn) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenBlacklist)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "黑名单管理",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "＞",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    Button(
                        onClick = {
                            vm.logout()
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text("退出登录")
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "未登录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onOpenLogin) { Text("登录") }
                    }
                }
            }

            item { HorizontalDivider() }
            item { ProfileSectionTitle("诊断") }
            item {
                LogRow(title = "下载错误日志") {
                    val path = LogExporter.save(context)
                    Toast.makeText(
                        context,
                        if (path != null) "已保存到 $path" else "保存失败",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            item {
                LogRow(title = "清空日志", subtitle = "当前 ${AppLog.count()} 条") {
                    AppLog.clear()
                    Toast.makeText(context, "日志已清空", Toast.LENGTH_SHORT).show()
                }
            }

            item { HorizontalDivider() }
            item { ProfileSectionTitle("其他") }
            item {
                LogRow(title = "关于", subtitle = "版本信息 / 仓库链接") { onOpenAbout() }
            }
        }
    }
}

@Composable
private fun LogRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
