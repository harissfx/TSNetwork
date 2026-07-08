package com.textsocial.app.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ExpandableLinkText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    collapsedMaxChars: Int = 220,
    onHashtagClick: ((String) -> Unit)? = null,
    onMentionClick: ((String) -> Unit)? = null
) {
    var isExpanded by remember(text) { mutableStateOf(false) }
    val shouldTruncate = text.length > collapsedMaxChars

    Column(modifier = modifier) {
        val displayText = if (shouldTruncate && !isExpanded) {
            text.take(collapsedMaxChars).trimEnd() + "…"
        } else {
            text
        }

        LinkTextComponent(
            text = displayText,
            style = style,
            onHashtagClick = onHashtagClick,
            onMentionClick = onMentionClick
        )

        if (shouldTruncate) {
            Text(
                text = if (isExpanded) "Sembunyikan" else "Baca Selengkapnya",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable { isExpanded = !isExpanded }
            )
        }
    }
}