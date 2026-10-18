package com.takina.bilisearch.ui.screens.player

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.takina.bilisearch.data.model.DanmakuItem
import kotlinx.coroutines.delay
import kotlin.math.abs

/** 同时最多渲染多少条，太多了会明显掉帧 */
private const val MAX_ACTIVE = 40

/** 顶部/底部弹幕停留时长 */
private const val FIXED_SHOW_MS = 4500L

/** 非底部弹幕占用的高度比例（用户要求塞在上 1/3） */
private const val TOP_AREA_RATIO = 1f / 3f

/**
 * 弹幕层。
 *
 * 性能要点（之前的版本滚起来很卡，原因都在这里）：
 * 1. **位置只走绘制阶段**：每条弹幕的位移都写在 `graphicsLayer {}` 里，靠一个逐帧时钟
 *    （`withFrameNanos`）驱动。状态只在绘制时被读取，不会触发重组 / 重新测量文本，
 *    因此能跑满 60fps。
 * 2. **不再用 `Modifier.layout` 每 100ms 重排**：老写法每帧都要重新测量并布局所有弹幕，
 *    这是卡顿的主因。
 * 3. **当前显示哪几条** 每 100ms 算一次（不是每帧），并用二分查找定位时间窗口，
 *    避免每次都遍历几千条；列表内容没变时也不写入 state，跳过重组。
 *
 * 布局：除底部弹幕（mode 4）外，滚动 / 顶部弹幕都限制在画面上方 1/3 区域内。
 */
@Composable
fun DanmakuOverlay(
    items: List<DanmakuItem>,
    positionState: State<Long>,
    isPlaying: Boolean,
    speed: Float,
    opacity: Float,
    scale: Float,
    durationMs: Long,
    showScroll: Boolean,
    showTop: Boolean,
    showBottom: Boolean,
    showColorful: Boolean,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    BoxWithConstraints(modifier.clipToBounds()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        if (widthPx <= 0f || heightPx <= 0f) return@BoxWithConstraints

        val scrollDuration = durationMs.coerceAtLeast(1000L)

        // ---- 逐帧时钟：只在 graphicsLayer 里被读取，不参与重组 ----
        // 注意：初值不能写 positionState.value —— remember 的 lambda 在组合期执行，
        // 那里读 State 会让整个弹幕层每次进度变化都重组，白优化。交给下面的对表循环校正。
        val smoothMs = remember { mutableLongStateOf(0L) }
        // 播放器是否「卡住了」（进度长时间不动）。卡住时平滑时钟要跟着冻住，
        // 否则真实进度一直是 0、时钟一直涨，每 100ms 又被拽回 0 → 弹幕反复横跳。
        val stalled = remember { mutableStateOf(false) }
        val playingState = rememberUpdatedState(isPlaying)
        val speedState = rememberUpdatedState(speed)
        LaunchedEffect(Unit) {
            smoothMs.longValue = positionState.value
            var lastFrame = 0L
            while (true) {
                withFrameNanos { now ->
                    if (lastFrame != 0L) {
                        val dtMs = (now - lastFrame) / 1_000_000L
                        if (playingState.value && !stalled.value) {
                            smoothMs.longValue += (dtMs * speedState.value).toLong()
                        }
                    }
                    lastFrame = now
                }
            }
        }

        // ---- 每 100ms 和播放器对一次表 ----
        // 进度在动时只做「跳转对齐」；进度不动超过 800ms 就判定卡住，时钟跟随真实进度冻结。
        LaunchedEffect(Unit) {
            var lastReal = -1L
            var lastChangeAt = System.currentTimeMillis()
            while (true) {
                delay(100)
                val real = positionState.value
                val now = System.currentTimeMillis()
                if (real != lastReal) {
                    lastReal = real
                    lastChangeAt = now
                }
                val stuck = now - lastChangeAt > 800
                if (stuck != stalled.value) stalled.value = stuck
                if (stuck) {
                    // 每 100ms 赋同一个值，不会产生抖动
                    smoothMs.longValue = real
                } else if (abs(real - smoothMs.longValue) > 400) {
                    // 拖动进度条 / 起播
                    smoothMs.longValue = real
                }
            }
        }

        // ---- 当前要显示哪几条：100ms 更新一次 ----
        var active by remember { mutableStateOf<List<DanmakuItem>>(emptyList()) }
        LaunchedEffect(items, showScroll, showTop, showBottom, scrollDuration) {
            while (true) {
                val next = pickActive(
                    items = items,
                    pos = smoothMs.longValue,
                    scrollDuration = scrollDuration,
                    showScroll = showScroll,
                    showTop = showTop,
                    showBottom = showBottom
                )
                if (next != active) active = next
                delay(100)
            }
        }

        active.forEach { item ->
            val isBottom = item.mode == 4
            val isFixed = item.mode == 4 || item.mode == 5
            val baseSize = item.fontSize.coerceIn(18, 36) / 25f * 15f * scale
            val lineHeight = baseSize * 1.45f

            // 滚动 / 顶部弹幕只用上方 1/3；底部弹幕只占下方 1/3
            val areaHeight = heightPx * TOP_AREA_RATIO
            val laneCount = (areaHeight / lineHeight).toInt().coerceAtLeast(1)
            val seed = item.timeMs * 31L + item.text.hashCode().toLong()
            val lane = ((seed and 0x7FFFFFFFL) % laneCount).toInt()
            val laneOffset = lane * lineHeight

            Text(
                text = item.text,
                color = if (showColorful) {
                    Color(0xFF000000L or (item.color.toLong() and 0xFFFFFF))
                } else {
                    Color.White
                }.copy(alpha = opacity.coerceIn(0.1f, 1f)),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = TextStyle(
                    fontSize = baseSize.sp,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.75f * opacity),
                        offset = Offset(1.5f, 1.5f),
                        blurRadius = 2f
                    )
                ),
                modifier = Modifier.graphicsLayer {
                    val pos = smoothMs.longValue
                    val elapsed = (pos - item.timeMs).coerceAtLeast(0L)
                    translationY = if (isBottom) {
                        // bottom: 从最底往上排
                        heightPx - lineHeight - laneOffset
                    } else {
                        laneOffset
                    }
                    translationX = if (isFixed) {
                        (widthPx - size.width) / 2f
                    } else {
                        // 从右侧进入，向左移出
                        val progress = elapsed.toFloat() / scrollDuration
                        widthPx - progress * (widthPx + size.width)
                    }
                }
            )
        }
    }
}

/**
 * 找出当前可见的弹幕。
 * 列表按时间升序，先用二分查找定位窗口起点，再顺序扫到当前位置为止。
 */
private fun pickActive(
    items: List<DanmakuItem>,
    pos: Long,
    scrollDuration: Long,
    showScroll: Boolean,
    showTop: Boolean,
    showBottom: Boolean
): List<DanmakuItem> {
    val window = maxOf(scrollDuration, FIXED_SHOW_MS)
    var index = lowerBound(items, pos - window)
    val out = ArrayList<DanmakuItem>(64)
    while (index < items.size) {
        val item = items[index]
        if (item.timeMs > pos) break
        val elapsed = pos - item.timeMs
        val visible = when (item.mode) {
            4 -> showBottom && elapsed <= FIXED_SHOW_MS
            5 -> showTop && elapsed <= FIXED_SHOW_MS
            else -> showScroll && elapsed <= scrollDuration
        }
        if (visible) out.add(item)
        index++
    }
    return if (out.size > MAX_ACTIVE) out.subList(out.size - MAX_ACTIVE, out.size).toList() else out
}

private fun lowerBound(items: List<DanmakuItem>, time: Long): Int {
    var lo = 0
    var hi = items.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (items[mid].timeMs < time) lo = mid + 1 else hi = mid
    }
    return lo
}
