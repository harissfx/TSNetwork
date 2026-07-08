package com.textsocial.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import com.textsocial.app.presentation.navigation.AppNavGraph
import com.textsocial.app.ui.theme.MyApplicationTheme
import com.textsocial.app.util.LocaleManager
import com.textsocial.app.util.ThemeManager

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager.init(this)

        enableEdgeToEdge()
        setContent {
            val themeMode by ThemeManager.themeMode
            val darkTheme = when (themeMode) {
                ThemeManager.MODE_LIGHT -> false
                ThemeManager.MODE_DARK -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            MyApplicationTheme(darkTheme = darkTheme) {
                AppNavGraph()
            }
        }
    }
}