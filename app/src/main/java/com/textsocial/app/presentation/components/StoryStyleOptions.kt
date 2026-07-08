package com.textsocial.app.presentation.components

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color

object StoryStyleOptions {
    val backgroundPresets: List<String> = listOf(
        "#000000", // Hitam (default)
        "#0B0F2E", // Navy gelap (senada latar ikon)
        "#3B1170", // Ungu tua (ujung gradasi ikon)
        "#1B2A8C", // Biru (tengah gradasi ikon)
        "#0E7C86", // Teal / cyan gelap
        "#7A1F2B", // Merah bata
        "#1F5C3B", // Hijau hutan
        "#B5451D", // Oranye senja
        "#FFFFFF"  // Putih
    )
    val textColorPresets: List<String> = listOf(
        "#FFFFFF", // Putih (default)
        "#000000", // Hitam
        "#39E8F9", // Cyan (brand)
        "#FFD54F", // Kuning lembut
        "#FF6F91"  // Pink
    )

    data class FontOption(val key: String, val fontFamily: FontFamily)

    val fontOptions: List<FontOption> = listOf(
        FontOption("default", FontFamily.SansSerif),
        FontOption("serif", FontFamily.Serif),
        FontOption("monospace", FontFamily.Monospace),
        FontOption("cursive", FontFamily.Cursive)
    )

    fun fontFamilyForKey(key: String?): FontFamily {
        return fontOptions.firstOrNull { it.key == key }?.fontFamily ?: FontFamily.SansSerif
    }

    fun parseColorOrDefault(hex: String?, default: Color): Color {
        if (hex.isNullOrBlank()) return default
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: IllegalArgumentException) {
            default
        }
    }

    fun readableOverlayColor(backgroundHex: String?): Color {
        val bg = parseColorOrDefault(backgroundHex, Color.Black)
        val luminance = (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue)
        return if (luminance > 0.6f) Color.Black else Color.White
    }
}