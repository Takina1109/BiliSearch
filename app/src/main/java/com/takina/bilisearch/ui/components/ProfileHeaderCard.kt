package com.takina.bilisearch.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.takina.bilisearch.data.repo.ProfileSummary
import com.takina.bilisearch.ui.theme.BiliPink

/**
 * 「我的」与其他用户主页共用的资料头部。
 * [showExp] 为 false 时不显示经验条 / 硬币（其他用户拿不到经验数据）。
 */
@Composable
fun ProfileHeaderCard(
    profile: ProfileSummary,
    showExp: Boolean,
    onAvatarClick: () -> Unit,
    onFollowing: () -> Unit,
    onFollowers: () -> Unit
) {
    val nameColor = if (profile.isVip) {
        val parsed = profile.nicknameColor?.let { hex ->
            runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
        }
        parsed ?: BiliPink
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val expText = when (val next = profile.expNext) {
        null -> "经验 ${profile.expCurrent} · 满级"
        else -> "经验 ${profile.expCurrent}/$next"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            UserAvatar(
                face = profile.face,
                size = 72.dp,
                onClick = onAvatarClick,
                contentDescription = "头像"
            )
            Spacer(Modifier.height(8.dp))
            FollowStats(
                following = profile.following,
                followers = profile.followers,
                onFollowing = onFollowing,
                onFollowers = onFollowers
            )
        }

        Spacer(Modifier.width(18.dp))

        Column(Modifier.weight(1f)) {
            if (profile.isVip) {
                VipBadge(
                    labelText = profile.vipLabelText.ifBlank { "大会员" },
                    labelImage = profile.vipLabelImage
                )
                Spacer(Modifier.height(4.dp))
            }

            Text(
                text = profile.name.ifBlank { "B站用户" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (showExp) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Lv${profile.level}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = expText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "硬币：${formatCoins(profile.coins)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(5.dp))
                ExpBar(progress = profile.expProgress)
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Lv${profile.level}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "UID ${profile.mid}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ProfileSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
fun ProfileEntryRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(14.dp))
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
    }
}
