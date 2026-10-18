package com.takina.bilisearch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.util.Locale

/** 圆头像；未登录或加载失败时显示默认头像 */
@Composable
fun UserAvatar(
    face: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!face.isNullOrBlank()) {
            AsyncImage(
                model = face,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

/**
 * 大会员标识：优先使用 B 站官方的会员标签图片，取不到时退回粉底白字样式。
 */
@Composable
fun VipBadge(
    labelText: String,
    labelImage: String?,
    modifier: Modifier = Modifier
) {
    if (!labelImage.isNullOrBlank()) {
        AsyncImage(
            model = labelImage,
            contentDescription = labelText.ifBlank { "大会员" },
            contentScale = ContentScale.Fit,
            modifier = modifier.height(16.dp)
        )
    } else if (labelText.isNotBlank()) {
        Text(
            text = labelText,
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = modifier
                .background(Color(0xFFFB7299), RoundedCornerShape(3.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

/** 等级经验条：自绘一整条，避免 Material3 进度条的分段/圆点效果 */
@Composable
fun ExpBar(progress: Float, modifier: Modifier = Modifier) {
    val value = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(value)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/** 头像下方的关注 / 粉丝数 */
@Composable
fun FollowStats(
    following: Int,
    followers: Int,
    onFollowing: () -> Unit,
    onFollowers: () -> Unit,
    compact: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatItem(following, "关注", compact, onFollowing)
        StatItem(followers, "粉丝", compact, onFollowers)
    }
}

@Composable
private fun StatItem(
    count: Int,
    label: String,
    compact: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = count.compactText(),
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun Int.compactText(): String = when {
    this >= 100_000_000 -> String.format(Locale.US, "%.1f亿", this / 100_000_000.0)
    this >= 10_000 -> String.format(Locale.US, "%.1f万", this / 10_000.0)
    else -> toString()
}

/** 硬币数：整数不显示小数位 */
fun formatCoins(coins: Double): String =
    if (coins % 1.0 == 0.0) coins.toInt().toString()
    else String.format(Locale.US, "%.1f", coins)
