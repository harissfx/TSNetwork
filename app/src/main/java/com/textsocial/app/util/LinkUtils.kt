package com.textsocial.app.util

import java.util.regex.Pattern

object LinkUtils {

    private val URL_PATTERN: Pattern =
        Pattern.compile("(https?://[\\w-]+(\\.[\\w-]+)+(/\\S*)?)")
    fun extractFirstUrl(text: String): String? {
        val matcher = URL_PATTERN.matcher(text)
        return if (matcher.find()) matcher.group() else null
    }
}