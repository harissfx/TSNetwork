package com.textsocial.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.textsocial.app.presentation.screens.*
import com.textsocial.app.presentation.viewmodel.*

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val homeViewModel: HomeViewModel = viewModel { HomeViewModel() }
    val storyViewModel: StoryViewModel = viewModel { StoryViewModel() }
    val profileViewModel: ProfileViewModel = viewModel { ProfileViewModel(homeViewModel) }

    // ViewModel milik tab-tab utama (Home/Search/CreatePost/Notifications/Profile) yang
    // dulunya dibuat ulang tiap kali route-nya dimasuki, sekarang dibuat sekali di sini
    // supaya tetap hidup selama MainScreen (HorizontalPager) tampil, mirip homeViewModel.
    val mainTabViewModel: MainTabViewModel = viewModel { MainTabViewModel() }
    val badgeViewModel: BadgeViewModel = viewModel { BadgeViewModel() }
    val createPostViewModel: CreatePostViewModel = viewModel { CreatePostViewModel() }
    val notificationViewModel: NotificationViewModel = viewModel { NotificationViewModel() }
    val searchViewModel: SearchViewModel = viewModel { SearchViewModel() }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        composable(Routes.SPLASH) {
            val splashViewModel: SplashViewModel = viewModel { SplashViewModel() }
            SplashScreen(
                viewModel = splashViewModel,
                onNavigateToHome = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            val loginViewModel: LoginViewModel = viewModel { LoginViewModel() }
            LoginScreen(
                viewModel = loginViewModel,
                onNavigateToHome = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                }
            )
        }

        composable(Routes.REGISTER) {
            val registerViewModel: RegisterViewModel = viewModel { RegisterViewModel() }
            RegisterScreen(
                viewModel = registerViewModel,
                onNavigateToHome = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                }
            )
        }

        // MAIN: satu destination yang membungkus 5 tab utama (Home, Search, Create Post,
        // Notifications, Profile) di dalam HorizontalPager, supaya bisa pindah tab dengan
        // geser kiri/kanan selain lewat BottomNavigationBar.
        composable(Routes.MAIN) {
            MainScreen(
                mainTabViewModel = mainTabViewModel,
                badgeViewModel = badgeViewModel,
                homeViewModel = homeViewModel,
                storyViewModel = storyViewModel,
                profileViewModel = profileViewModel,
                createPostViewModel = createPostViewModel,
                notificationViewModel = notificationViewModel,
                searchViewModel = searchViewModel,
                onNavigateToPostDetail = { postId -> navController.navigate(Routes.postDetail(postId)) },
                onNavigateToProfile = { userId -> navController.navigate(Routes.profile(userId)) },
                onNavigateToStories = { navController.navigate(Routes.STORY_VIEW) },
                onNavigateToCreateStory = { navController.navigate(Routes.CREATE_STORY) },
                onNavigateToDMs = { navController.navigate(Routes.DM_LIST) },
                onNavigateToChat = { uid, username -> navController.navigate(Routes.dmChat(uid, username)) },
                onNavigateToEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToPostDetailFromNotif = { postId, commentId -> navController.navigate(Routes.postDetail(postId, commentId)) }
            )
        }

        composable(
            route = Routes.POST_DETAIL,
            arguments = listOf(
                navArgument("postId") { type = NavType.StringType },
                navArgument("commentId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: ""
            val highlightCommentId = backStackEntry.arguments?.getString("commentId")
            val postDetailViewModel: PostDetailViewModel = viewModel { PostDetailViewModel(homeViewModel) }
            PostDetailScreen(
                postId = postId,
                highlightCommentId = highlightCommentId,
                viewModel = postDetailViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProfile = { userId -> navController.navigate(Routes.profile(userId)) }
            )
        }

        composable(Routes.STORY_VIEW) { backStackEntry ->
            StoryScreen(
                viewModel = storyViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CREATE_STORY) { backStackEntry ->
            CreateStoryScreen(
                viewModel = storyViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PROFILE,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            ProfileScreen(
                userId = userId,
                viewModel = profileViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEditProfile = { navController.navigate(Routes.EDIT_PROFILE) },
                onNavigateToChat = { uid, username -> navController.navigate(Routes.dmChat(uid, username)) },
                onNavigateToPostDetail = { pid -> navController.navigate(Routes.postDetail(pid)) },
                // Layar profil ini dibuka di atas MainScreen (mis. tap avatar orang lain).
                // Kalau user tap ikon bottom nav di sini, kita pop balik ke MainScreen lalu
                // pindahkan pager-nya ke tab yang sesuai.
                onNavigateToHome = {
                    mainTabViewModel.goToTab(0)
                    navController.popBackStack(Routes.MAIN, inclusive = false)
                },
                onNavigateToSearch = {
                    mainTabViewModel.goToTab(1)
                    navController.popBackStack(Routes.MAIN, inclusive = false)
                },
                onNavigateToCreatePost = {
                    mainTabViewModel.goToTab(2)
                    navController.popBackStack(Routes.MAIN, inclusive = false)
                },
                onNavigateToNotifications = {
                    mainTabViewModel.goToTab(3)
                    navController.popBackStack(Routes.MAIN, inclusive = false)
                },
                onNavigateToProfileMe = {
                    mainTabViewModel.goToTab(4)
                    navController.popBackStack(Routes.MAIN, inclusive = false)
                },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.EDIT_PROFILE) { backStackEntry ->
            EditProfileScreen(
                viewModel = profileViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DM_LIST) {
            val dmListViewModel: DMListViewModel = viewModel { DMListViewModel() }
            DMListScreen(
                viewModel = dmListViewModel,
                onNavigateToChat = { uid, uname -> navController.navigate(Routes.dmChat(uid, uname)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.DM_CHAT,
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("username") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("userId") ?: ""
            val uname = backStackEntry.arguments?.getString("username") ?: ""
            val dmChatViewModel: DMChatViewModel = viewModel { DMChatViewModel() }
            DMChatScreen(
                otherUserId = uid,
                otherUsername = uname,
                viewModel = dmChatViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProfile = { userId -> navController.navigate(Routes.profile(userId)) },
                onMessagesRead = { badgeViewModel.refreshUnreadMessages() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogoutSuccess = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                }
            )
        }
    }
}