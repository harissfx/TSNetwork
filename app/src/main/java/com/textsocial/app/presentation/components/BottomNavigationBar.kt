package com.textsocial.app.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun BottomNavigationBar(
    currentRoute: String,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToCreatePost: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.testTag("bottom_nav_bar"),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    ) {
        val isHome = currentRoute == "home"
        val isSearch = currentRoute == "search"
        val isCreate = currentRoute == "create_post"
        val isNotifications = currentRoute == "notifications"
        val isProfile = currentRoute.startsWith("profile")

        NavigationBarItem(
            selected = isHome,
            onClick = { if (!isHome) onNavigateToHome() },
            icon = {
                Icon(
                    imageVector = if (isHome) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Home"
                )
            },
            label = { Text("Feed") },
            modifier = Modifier.testTag("nav_home")
        )
        NavigationBarItem(
            selected = isSearch,
            onClick = { if (!isSearch) onNavigateToSearch() },
            icon = {
                Icon(
                    imageVector = if (isSearch) Icons.Filled.Search else Icons.Outlined.Search,
                    contentDescription = "Search"
                )
            },
            label = { Text("Search") },
            modifier = Modifier.testTag("nav_search")
        )
        NavigationBarItem(
            selected = isCreate,
            onClick = { if (!isCreate) onNavigateToCreatePost() },
            icon = {
                Icon(
                    imageVector = if (isCreate) Icons.Filled.AddCircle else Icons.Outlined.AddCircle,
                    contentDescription = "Create Post"
                )
            },
            label = { Text("Post") },
            modifier = Modifier.testTag("nav_create_post")
        )
        NavigationBarItem(
            selected = isNotifications,
            onClick = { if (!isNotifications) onNavigateToNotifications() },
            icon = {
                Icon(
                    imageVector = if (isNotifications) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                    contentDescription = "Notifications"
                )
            },
            label = { Text("Alerts") },
            modifier = Modifier.testTag("nav_notifications")
        )
        NavigationBarItem(
            selected = isProfile,
            onClick = { if (!isProfile) onNavigateToProfile() },
            icon = {
                Icon(
                    imageVector = if (isProfile) Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = "Profile"
                )
            },
            label = { Text("Profile") },
            modifier = Modifier.testTag("nav_profile")
        )
    }
}
