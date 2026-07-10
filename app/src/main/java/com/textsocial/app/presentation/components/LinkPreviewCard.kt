package com.textsocial.app.presentation.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.textsocial.app.domain.model.LinkPreview

@Composable
fun LinkPreviewCard(
    preview: LinkPreview,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(preview.url)))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
    ) {
        if (!preview.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = preview.imageUrl,
                contentDescription = preview.title ?: preview.url,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    // Rasio 1.91:1 -- rasio standar gambar og:image di kebanyakan situs
                    .aspectRatio(1.91f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = (preview.siteName ?: hostnameOf(preview.url)).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!preview.title.isNullOrBlank()) {
                Text(
                    text = preview.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (!preview.description.isNullOrBlank()) {
                Text(
                    text = preview.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

private fun hostnameOf(url: String): String = try {
    Uri.parse(url).host ?: url
} catch (e: Exception) {
    url
}