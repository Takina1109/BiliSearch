package com.takina.bilisearch.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 播放器「更多」抽屉：定时关闭 / 画质 / 弹幕设置 / 播放顺序 / 镜像翻转
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerDrawer(
    qualities: List<QualityOption>,
    currentQn: Int,
    onQualitySelected: (Int) -> Unit,
    playOrder: PlayOrder,
    onPlayOrderChange: (PlayOrder) -> Unit,
    timerOption: TimerOption,
    timerRemainingMs: Long,
    onTimerChange: (TimerOption) -> Unit,
    mirror: Boolean,
    onMirrorChange: (Boolean) -> Unit,
    danmakuOn: Boolean,
    onDanmakuChange: (Boolean) -> Unit,
    danmakuScroll: Boolean,
    onScrollChange: (Boolean) -> Unit,
    danmakuTop: Boolean,
    onTopChange: (Boolean) -> Unit,
    danmakuBottom: Boolean,
    onBottomChange: (Boolean) -> Unit,
    danmakuColorful: Boolean,
    onColorfulChange: (Boolean) -> Unit,
    danmakuOpacity: Float,
    onOpacityChange: (Float) -> Unit,
    danmakuScale: Float,
    onScaleChange: (Float) -> Unit,
    danmakuDuration: Long,
    onDurationChange: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            DrawerTitle("播放设置")

            SectionLabel("定时关闭")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimerOption.entries.forEach { option ->
                    Chip(
                        text = option.label,
                        selected = option == timerOption,
                        onClick = { onTimerChange(option) }
                    )
                }
            }
            if (timerRemainingMs > 0L) {
                Text(
                    text = "剩余 " + formatTime(timerRemainingMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            SectionLabel("播放顺序")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayOrder.entries.forEach { order ->
                    Chip(
                        text = order.label,
                        selected = order == playOrder,
                        onClick = { onPlayOrderChange(order) }
                    )
                }
            }

            SectionLabel("清晰度")
            if (qualities.isEmpty()) {
                Text(
                    "暂无可选清晰度",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    qualities.forEach { option ->
                        Chip(
                            text = option.label,
                            selected = option.qn == currentQn,
                            onClick = { onQualitySelected(option.qn) }
                        )
                    }
                }
            }

            SectionLabel("弹幕设置")
            SwitchRow("显示弹幕", danmakuOn, onDanmakuChange)
            SwitchRow("滚动弹幕", danmakuScroll, onScrollChange)
            SwitchRow("顶部弹幕", danmakuTop, onTopChange)
            SwitchRow("底部弹幕", danmakuBottom, onBottomChange)
            SwitchRow("彩色弹幕", danmakuColorful, onColorfulChange)

            SliderRow(
                label = "不透明度 ${(danmakuOpacity * 100).toInt()}%",
                value = danmakuOpacity,
                range = 0.1f..1f,
                onChange = onOpacityChange
            )
            SliderRow(
                label = "字体大小 ${"%.1f".format(danmakuScale)}x",
                value = danmakuScale,
                range = 0.6f..1.8f,
                onChange = onScaleChange
            )
            SliderRow(
                label = "弹幕时长 ${danmakuDuration / 1000}s",
                value = ((danmakuDuration - 3000L).toFloat() / 9000f).coerceIn(0f, 1f),
                range = 0f..1f,
                onChange = { onDurationChange((3000L + it * 9000f).toLong()) }
            )

            SectionLabel("画面")
            SwitchRow("镜像翻转", mirror, onMirrorChange)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DrawerTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = background,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
