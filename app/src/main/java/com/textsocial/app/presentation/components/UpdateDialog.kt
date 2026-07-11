package com.textsocial.app.presentation.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.textsocial.app.R
import com.textsocial.app.domain.model.AppUpdateInfo

/**
 * Popup "ada update baru". Kalau [info].isForceUpdate == true, dialog tidak bisa
 * ditutup (tanpa tombol "Nanti", tap di luar dialog juga tidak menutupnya) --
 * dipakai saat versi app di HP user sudah di bawah min_supported_version_code.
 * Tombol "Update" membuka info.downloadUrl lewat browser (Opsi A: bukan download
 * & install langsung dari dalam app), jadi bisa berupa link GitHub Releases,
 * Google Drive, MediaFire, atau hosting lain apa pun.
 */
@Composable
fun UpdateDialog(
    info: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = {
            if (!info.isForceUpdate) onDismiss()
        },
        title = {
            Text(
                stringResource(
                    if (info.isForceUpdate) R.string.update_dialog_force_title
                    else R.string.update_dialog_title
                )
            )
        },
        text = {
            Column {
                Text(
                    stringResource(
                        if (info.isForceUpdate) R.string.update_dialog_force_message
                        else R.string.update_dialog_message,
                        info.versionName
                    )
                )
                if (!info.releaseNotes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = info.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { openDownloadLink(context, info.downloadUrl) }) {
                Text(stringResource(R.string.update_dialog_button_update))
            }
        },
        dismissButton = if (info.isForceUpdate) null else {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_dialog_button_later))
                }
            }
        }
    )
}

private fun openDownloadLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        // Tidak ada browser yang bisa menangani link -- diamkan, dialog tetap terbuka
        // (untuk update biasa) supaya user bisa coba lagi atau tutup manual.
    }
}