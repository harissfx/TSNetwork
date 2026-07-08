package com.textsocial.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.textsocial.app.presentation.navigation.AppNavGraph
import com.textsocial.app.presentation.navigation.Routes
import com.textsocial.app.ui.theme.MyApplicationTheme
import com.textsocial.app.util.LocaleManager
import com.textsocial.app.util.NotificationHelper
import com.textsocial.app.util.ThemeManager

class MainActivity : ComponentActivity() {

    // Holds the target route for a tapped push notification; AppNavGraph consumes it once
    // the user actually lands on Routes.MAIN (can't navigate there before auth resolves).
    private var pendingDeepLinkRoute by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way: pushes just won't show without it */ }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager.init(this)
        requestNotificationPermissionIfNeeded()
        pendingDeepLinkRoute = routeFromNotificationIntent(intent)

        enableEdgeToEdge()
        setContent {
            val themeMode by ThemeManager.themeMode
            val darkTheme = when (themeMode) {
                ThemeManager.MODE_LIGHT -> false
                ThemeManager.MODE_DARK -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            MyApplicationTheme(darkTheme = darkTheme) {
                AppNavGraph(
                    pendingDeepLinkRoute = pendingDeepLinkRoute,
                    onDeepLinkConsumed = { pendingDeepLinkRoute = null }
                )
            }
        }
    }

    // MainActivity is launchMode="singleTop", so tapping a notification while the app is
    // already running/backgrounded delivers here instead of creating a new instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFromNotificationIntent(intent)?.let { pendingDeepLinkRoute = it }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Turns a notification tap's Intent extras into an in-app route, or null if there's nothing to deep-link to. */
    private fun routeFromNotificationIntent(intent: Intent?): String? {
        val type = intent?.getStringExtra(NotificationHelper.EXTRA_NOTIF_TYPE) ?: return null
        val postId = intent.getStringExtra(NotificationHelper.EXTRA_NOTIF_POST_ID)
        val commentId = intent.getStringExtra(NotificationHelper.EXTRA_NOTIF_COMMENT_ID)
        val senderId = intent.getStringExtra(NotificationHelper.EXTRA_NOTIF_SENDER_ID)
        val senderUsername = intent.getStringExtra(NotificationHelper.EXTRA_NOTIF_SENDER_USERNAME)

        return when (type) {
            "like", "comment", "reply", "mention" -> postId?.let { Routes.postDetail(it, commentId) }
            "follow" -> senderId?.let { Routes.profile(it) }
            "dm" -> senderId?.let { Routes.dmChat(it, senderUsername ?: "") }
            else -> null
        }
    }
}