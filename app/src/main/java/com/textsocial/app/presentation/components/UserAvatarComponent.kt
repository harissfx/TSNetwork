package com.textsocial.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AvatarSize(val sizeDp: Dp) {
    COMPACT(32.dp),
    MEDIUM(48.dp),
    LARGE(72.dp)
}

@Composable
fun UserAvatarComponent(
    username: String,
    avatarColor: String,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.MEDIUM
) {
    val initial = if (username.isNotEmpty()) username.take(1).uppercase() else "?"
    val colorParsed = try {
        Color(android.graphics.Color.parseColor(avatarColor))
    } catch (e: Exception) {
        Color(0xFFFF5722) // orange fallback
    }

    Box(
        modifier = modifier
            .size(size.sizeDp)
            .clip(CircleShape)
            .background(colorParsed),
        contentAlignment = Alignment.Center
    ) {
        val fontSize = when (size) {
            AvatarSize.COMPACT -> 14.sp
            AvatarSize.MEDIUM -> 18.sp
            AvatarSize.LARGE -> 28.sp
        }

        Text(
            text = initial,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold
        )
    }
}
