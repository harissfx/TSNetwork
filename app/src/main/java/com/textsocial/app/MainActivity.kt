package com.textsocial.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.textsocial.app.presentation.navigation.AppNavGraph
import com.textsocial.app.ui.theme.MyApplicationTheme
import com.textsocial.app.util.LocaleManager

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavGraph()
            }
        }
    }
}