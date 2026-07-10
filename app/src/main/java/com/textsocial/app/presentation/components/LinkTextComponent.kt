package com.textsocial.app.presentation.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import java.util.regex.Pattern

// PENTING: dulu ini pakai `ClickableText`, yang punya gesture detector (pointerInput) sendiri
// buat nangkep tap. Begitu komponen ini dipasang DI DALAM/DI BAWAH modifier `combinedClickable`
// milik parent (mis. bubble chat, atau area salin komentar), gesture detector `ClickableText`
// itu "menang" duluan dan bikin gesture detector parent-nya (termasuk long-press-nya) nggak
// pernah kepanggil sama sekali -- makanya fitur tahan-buat-salin sempat nggak jalan.
// Fix-nya: satuin SEMUA deteksi gesture (tap link, tap fallback, & long-press) jadi SATU
// `detectTapGestures` di sini, jangan taruh gesture detector terpisah di composable luar.
@Composable
fun LinkTextComponent(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    // Default-nya sama seperti sebelumnya (dipakai di feed & detail post). Chat DM meng-override
    // ini karena warna bubble-nya berubah-ubah (primary utk pesan sendiri, secondaryContainer
    // utk lawan bicara) -- link warna biru default bisa nyaris tak kelihatan di atas bubble
    // yang warnanya sama-sama primary.
    textColor: Color? = null,
    linkColor: Color? = null,
    onHashtagClick: ((String) -> Unit)? = null,
    onMentionClick: ((String) -> Unit)? = null,
    // Dipanggil kalau user tap di teks TAPI bukan di atas link/hashtag/mention (mis. dipakai
    // buat "tap bubble buat kirim ulang pesan yang gagal" di chat).
    onTapFallback: (() -> Unit)? = null,
    // Dipanggil kalau user tahan (long-press) di teks -- dipakai buat "salin teks" di komentar
    // & chat, dan buka menu pesan di chat.
    onLongPress: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val resolvedLinkColor = linkColor ?: MaterialTheme.colorScheme.primary
    val resolvedTextColor = textColor ?: MaterialTheme.colorScheme.onSurface
    val annotatedString = buildAnnotatedString {
        append(text)

        val urlPattern = Pattern.compile("(https?://[\\w-]+(\\.[\\w-]+)+(/\\S*)?)")
        val urlMatcher = urlPattern.matcher(text)
        while (urlMatcher.find()) {
            val start = urlMatcher.start()
            val end = urlMatcher.end()
            addStyle(
                style = SpanStyle(
                    color = resolvedLinkColor,
                    textDecoration = TextDecoration.Underline
                ),
                start = start,
                end = end
            )
            addStringAnnotation(
                tag = "URL",
                annotation = urlMatcher.group(),
                start = start,
                end = end
            )
        }

        val hashtagPattern = Pattern.compile("#(\\w+)")
        val hashtagMatcher = hashtagPattern.matcher(text)
        while (hashtagMatcher.find()) {
            val start = hashtagMatcher.start()
            val end = hashtagMatcher.end()
            addStyle(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.secondary,
                    textDecoration = TextDecoration.None
                ),
                start = start,
                end = end
            )
            addStringAnnotation(
                tag = "HASHTAG",
                annotation = hashtagMatcher.group(1) ?: "",
                start = start,
                end = end
            )
        }

        val mentionPattern = Pattern.compile("@(\\w+)")
        val mentionMatcher = mentionPattern.matcher(text)
        while (mentionMatcher.find()) {
            val start = mentionMatcher.start()
            val end = mentionMatcher.end()
            addStyle(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.tertiary,
                    textDecoration = TextDecoration.None
                ),
                start = start,
                end = end
            )
            addStringAnnotation(
                tag = "MENTION",
                annotation = mentionMatcher.group(1) ?: "",
                start = start,
                end = end
            )
        }
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicText(
        text = annotatedString,
        modifier = modifier.pointerInput(text, onLongPress, onTapFallback) {
            detectTapGestures(
                onTap = { offset ->
                    val position = layoutResult?.getOffsetForPosition(offset) ?: return@detectTapGestures
                    var handled = false

                    annotatedString.getStringAnnotations(tag = "URL", start = position, end = position)
                        .firstOrNull()?.let { annotation ->
                            handled = true
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                    annotatedString.getStringAnnotations(tag = "HASHTAG", start = position, end = position)
                        .firstOrNull()?.let { annotation ->
                            handled = true
                            onHashtagClick?.invoke(annotation.item)
                        }

                    annotatedString.getStringAnnotations(tag = "MENTION", start = position, end = position)
                        .firstOrNull()?.let { annotation ->
                            handled = true
                            onMentionClick?.invoke(annotation.item)
                        }

                    if (!handled) {
                        onTapFallback?.invoke()
                    }
                },
                onLongPress = { onLongPress?.invoke() }
            )
        },
        style = style.copy(color = resolvedTextColor),
        onTextLayout = { layoutResult = it }
    )
}