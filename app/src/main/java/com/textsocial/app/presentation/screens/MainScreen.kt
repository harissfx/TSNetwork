package com.textsocial.app.presentation.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.textsocial.app.presentation.components.BottomNavigationBar
import com.textsocial.app.presentation.viewmodel.BadgeViewModel
import com.textsocial.app.presentation.viewmodel.CreatePostViewModel
import com.textsocial.app.presentation.viewmodel.HomeViewModel
import com.textsocial.app.presentation.viewmodel.MainTabViewModel
import com.textsocial.app.presentation.viewmodel.NotificationViewModel
import com.textsocial.app.presentation.viewmodel.ProfileViewModel
import com.textsocial.app.presentation.viewmodel.SearchViewModel
import com.textsocial.app.presentation.viewmodel.StoryViewModel

private const val TAB_HOME = 0
private const val TAB_SEARCH = 1
private const val TAB_CREATE_POST = 2
private const val TAB_NOTIFICATIONS = 3
private const val TAB_PROFILE = 4

@Composable
fun MainScreen(
    mainTabViewModel: MainTabViewModel,
    badgeViewModel: BadgeViewModel,
    homeViewModel: HomeViewModel,
    storyViewModel: StoryViewModel,
    profileViewModel: ProfileViewModel,
    createPostViewModel: CreatePostViewModel,
    notificationViewModel: NotificationViewModel,
    searchViewModel: SearchViewModel,
    onNavigateToPostDetail: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToStories: () -> Unit,
    onNavigateToCreateStory: () -> Unit,
    onNavigateToDMs: () -> Unit,
    onNavigateToChat: (String, String) -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPostDetailFromNotif: (String, String?) -> Unit,
    onNavigateToFollowList: (userId: String, username: String, tab: Int) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 5 })
    val currentTab by mainTabViewModel.currentTab.collectAsState()
    val unreadNotifications by badgeViewModel.unreadNotifications.collectAsState()
    val unreadMessages by badgeViewModel.unreadMessages.collectAsState()

    LaunchedEffect(currentTab) {
        if (pagerState.currentPage != currentTab) {
            pagerState.scrollToPage(currentTab)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentTab) {
            mainTabViewModel.goToTab(pagerState.currentPage)
        }
    }

    var previousTab by remember { mutableStateOf(currentTab) }
    LaunchedEffect(currentTab) {
        if (currentTab == TAB_HOME && previousTab != TAB_HOME) {
            homeViewModel.loadPosts(homeViewModel.activeHashtag.value)
            storyViewModel.loadStories()
            badgeViewModel.refreshUnreadMessages()
        }

        if (currentTab == TAB_NOTIFICATIONS && previousTab != TAB_NOTIFICATIONS) {
            notificationViewModel.loadNotifications()
        }
        previousTab = currentTab
    }

    val currentRoute = when (currentTab) {
        TAB_HOME -> "home"
        TAB_SEARCH -> "search"
        TAB_CREATE_POST -> "create_post"
        TAB_NOTIFICATIONS -> "notifications"
        else -> "profile/me_id"
    }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(
                currentRoute = currentRoute,
                onNavigateToHome = { mainTabViewModel.goToTab(TAB_HOME) },
                onNavigateToSearch = { mainTabViewModel.goToTab(TAB_SEARCH) },
                onNavigateToCreatePost = { mainTabViewModel.goToTab(TAB_CREATE_POST) },
                onNavigateToNotifications = { mainTabViewModel.goToTab(TAB_NOTIFICATIONS) },
                onNavigateToProfile = { mainTabViewModel.goToTab(TAB_PROFILE) },
                unreadNotificationsCount = unreadNotifications
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) { page ->
            when (page) {
                TAB_HOME -> HomeScreen(
                    homeViewModel = homeViewModel,
                    storyViewModel = storyViewModel,
                    onNavigateToCreatePost = { mainTabViewModel.goToTab(TAB_CREATE_POST) },
                    onNavigateToPostDetail = onNavigateToPostDetail,
                    onNavigateToProfile = onNavigateToProfile,
                    onNavigateToStories = onNavigateToStories,
                    onNavigateToCreateStory = onNavigateToCreateStory,
                    onNavigateToSearch = { mainTabViewModel.goToTab(TAB_SEARCH) },
                    onNavigateToDMs = onNavigateToDMs,
                    onNavigateToNotifications = { mainTabViewModel.goToTab(TAB_NOTIFICATIONS) },
                    unreadMessagesCount = unreadMessages,
                    unreadNotificationsCount = unreadNotifications,
                    showBottomBar = false
                )

                TAB_SEARCH -> SearchScreen(
                    viewModel = searchViewModel,
                    onNavigateBack = { mainTabViewModel.goToTab(TAB_HOME) },
                    onNavigateToProfile = onNavigateToProfile,
                    onSearchHashtag = { tag ->
                        homeViewModel.loadPosts(tag)
                        mainTabViewModel.goToTab(TAB_HOME)
                    },
                    onNavigateToHome = { mainTabViewModel.goToTab(TAB_HOME) },
                    onNavigateToCreatePost = { mainTabViewModel.goToTab(TAB_CREATE_POST) },
                    onNavigateToNotifications = { mainTabViewModel.goToTab(TAB_NOTIFICATIONS) },
                    onNavigateToProfileMe = { mainTabViewModel.goToTab(TAB_PROFILE) },
                    showBottomBar = false
                )

                TAB_CREATE_POST -> CreatePostScreen(
                    viewModel = createPostViewModel,
                    onNavigateBack = { mainTabViewModel.goToTab(TAB_HOME) },
                    onNavigateToHome = { mainTabViewModel.goToTab(TAB_HOME) },
                    onNavigateToSearch = { mainTabViewModel.goToTab(TAB_SEARCH) },
                    onNavigateToNotifications = { mainTabViewModel.goToTab(TAB_NOTIFICATIONS) },
                    onNavigateToProfileMe = { mainTabViewModel.goToTab(TAB_PROFILE) },
                    showBottomBar = false
                )

                TAB_NOTIFICATIONS -> NotificationScreen(
                    viewModel = notificationViewModel,
                    onNavigateBack = { mainTabViewModel.goToTab(TAB_HOME) },
                    onNavigateToHome = { mainTabViewModel.goToTab(TAB_HOME) },
                    onNavigateToSearch = { mainTabViewModel.goToTab(TAB_SEARCH) },
                    onNavigateToCreatePost = { mainTabViewModel.goToTab(TAB_CREATE_POST) },
                    onNavigateToProfileMe = { mainTabViewModel.goToTab(TAB_PROFILE) },
                    onNavigateToProfile = onNavigateToProfile,
                    onNavigateToPostDetail = onNavigateToPostDetailFromNotif,
                    onNotificationRead = { badgeViewModel.decrementUnreadNotifications() },
                    onAllNotificationsRead = { badgeViewModel.clearUnreadNotifications() },
                    showBottomBar = false
                )

                TAB_PROFILE -> ProfileScreen(
                    userId = "me_id",
                    viewModel = profileViewModel,
                    onNavigateBack = { mainTabViewModel.goToTab(TAB_HOME) },
                    onNavigateToEditProfile = onNavigateToEditProfile,
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToPostDetail = onNavigateToPostDetail,
                    onNavigateToHome = { mainTabViewModel.goToTab(TAB_HOME) },
                    onNavigateToSearch = { mainTabViewModel.goToTab(TAB_SEARCH) },
                    onNavigateToCreatePost = { mainTabViewModel.goToTab(TAB_CREATE_POST) },
                    onNavigateToNotifications = { mainTabViewModel.goToTab(TAB_NOTIFICATIONS) },
                    onNavigateToProfileMe = { /* sudah di tab ini */ },
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToFollowList = onNavigateToFollowList,
                    showBottomBar = false
                )
            }
        }
    }
}