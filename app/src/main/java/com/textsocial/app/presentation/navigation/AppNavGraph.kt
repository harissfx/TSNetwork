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

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        composable(Routes.SPLASH) {
            val splashViewModel: SplashViewModel = viewModel { SplashViewModel() }
            SplashScreen(
                viewModel = splashViewModel,
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
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
                    navController.navigate(Routes.HOME) {
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
                    navController.navigate(Routes.HOME) {
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

        composable(Routes.HOME) {
            HomeScreen(
                homeViewModel = homeViewModel,
                storyViewModel = storyViewModel,
                onNavigateToCreatePost = { navController.navigate(Routes.CREATE_POST) },
                onNavigateToPostDetail = { postId -> navController.navigate(Routes.postDetail(postId)) },
                onNavigateToProfile = { userId -> navController.navigate(Routes.profile(userId)) },
                onNavigateToStories = { navController.navigate(Routes.STORY_VIEW) },
                onNavigateToCreateStory = { navController.navigate(Routes.CREATE_STORY) },
                onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                onNavigateToDMs = { navController.navigate(Routes.DM_LIST) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) }
            )
        }

        composable(Routes.CREATE_POST) {
            val createPostViewModel: CreatePostViewModel = viewModel { CreatePostViewModel() }
            CreatePostScreen(
                viewModel = createPostViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    homeViewModel.loadPosts()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onNavigateToProfileMe = { navController.navigate(Routes.profile("me_id")) }
            )
        }

        composable(
            route = Routes.POST_DETAIL,
            arguments = listOf(navArgument("postId") { type = NavType.StringType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: ""
            val postDetailViewModel: PostDetailViewModel = viewModel { PostDetailViewModel(homeViewModel) }
            PostDetailScreen(
                postId = postId,
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
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                onNavigateToCreatePost = { navController.navigate(Routes.CREATE_POST) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onNavigateToProfileMe = { navController.navigate(Routes.profile("me_id")) },
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
                onNavigateToProfile = { userId -> navController.navigate(Routes.profile(userId)) }
            )
        }

        composable(Routes.NOTIFICATIONS) {
            val notificationViewModel: NotificationViewModel = viewModel { NotificationViewModel() }
            NotificationScreen(
                viewModel = notificationViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                onNavigateToCreatePost = { navController.navigate(Routes.CREATE_POST) },
                onNavigateToProfileMe = { navController.navigate(Routes.profile("me_id")) },
                onNavigateToProfile = { userId -> navController.navigate(Routes.profile(userId)) },
                onNavigateToPostDetail = { postId -> navController.navigate(Routes.postDetail(postId)) }
            )
        }

        composable(Routes.SEARCH) { backStackEntry ->
            val searchViewModel: SearchViewModel = viewModel { SearchViewModel() }
            SearchScreen(
                viewModel = searchViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProfile = { uid -> navController.navigate(Routes.profile(uid)) },
                onSearchHashtag = { tag ->
                    homeViewModel.loadPosts(tag)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onNavigateToCreatePost = { navController.navigate(Routes.CREATE_POST) },
                onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onNavigateToProfileMe = { navController.navigate(Routes.profile("me_id")) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogoutSuccess = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }
    }
}