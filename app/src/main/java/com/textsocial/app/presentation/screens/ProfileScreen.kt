package com.textsocial.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.textsocial.app.R
import com.textsocial.app.presentation.components.AvatarSize
import com.textsocial.app.presentation.components.UserAvatarComponent
import com.textsocial.app.presentation.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToChat: (String, String) -> Unit,
    onNavigateToPostDetail: (String) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToCreatePost: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfileMe: () -> Unit,
    onNavigateToSettings: () -> Unit,
    showBottomBar: Boolean = true
) {
    val user by viewModel.user.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val isFollowActionLoading by viewModel.isFollowActionLoading.collectAsState()
    val currentUserId = remember { com.textsocial.app.di.ServiceLocator.encryptedPreferencesManager.getUserId() ?: "" }

    LaunchedEffect(userId) {
        viewModel.loadProfile(userId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(user?.username ?: "Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    // Ikon pengaturan dipindah ke sini (pojok kanan atas Profile), sebelumnya
                    // ada di layar Home/Feed. Hanya tampil kalau ini profil sendiri.
                    if (user?.id == "me_id" || user?.id == currentUserId) {
                        IconButton(onClick = onNavigateToSettings, modifier = Modifier.testTag("settings_button")) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (showBottomBar) {
                com.textsocial.app.presentation.components.BottomNavigationBar(
                    currentRoute = "profile/$userId",
                    onNavigateToHome = onNavigateToHome,
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToCreatePost = onNavigateToCreatePost,
                    onNavigateToNotifications = onNavigateToNotifications,
                    onNavigateToProfile = onNavigateToProfileMe
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading && user == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    user?.let { profile ->
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                UserAvatarComponent(
                                    username = profile.username,
                                    avatarColor = profile.avatarColor,
                                    size = AvatarSize.LARGE
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = profile.displayName ?: profile.username,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Text(
                                    text = "@${profile.username}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = profile.bio ?: "No bio added yet.",
                                    fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Social stats row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = profile.postsCount.toString(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = stringResource(R.string.post_title),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = profile.followersCount.toString(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = stringResource(R.string.follow_title),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = profile.followingCount.toString(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = stringResource(R.string.following_title),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Profile action button
                                if (profile.id == "me_id" || profile.id == currentUserId) {
                                    Button(
                                        onClick = onNavigateToEditProfile,
                                        modifier = Modifier
                                            .fillMaxWidth(0.8f)
                                            .testTag("edit_profile_button"),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Profile Icon"
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.edit_title))
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(0.8f),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        if (isFollowing) {
                                            OutlinedButton(
                                                onClick = { viewModel.toggleFollow() },
                                                enabled = !isFollowActionLoading,
                                                modifier = Modifier.weight(1f),
                                                shape = MaterialTheme.shapes.medium
                                            ) {
                                                Text("Following")
                                            }
                                        } else {
                                            Button(
                                                onClick = { viewModel.toggleFollow() },
                                                enabled = !isFollowActionLoading,
                                                modifier = Modifier.weight(1f),
                                                shape = MaterialTheme.shapes.medium
                                            ) {
                                                Text("Follow")
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = { onNavigateToChat(profile.id, profile.username) },
                                            modifier = Modifier.weight(1f),
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Icon(Icons.Default.Mail, contentDescription = "Message icon")
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.pesan_title))
                                        }
                                    }
                                }
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }

                    // User posts listing
                    item {
                        Text(
                            text = stringResource(R.string.history_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (posts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No posts shared by this user yet.",
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        items(posts) { post ->
                            PostItem(
                                post = post,
                                onLikeToggle = { viewModel.toggleLike(post) },
                                onPostClick = { onNavigateToPostDetail(post.id) },
                                onUserClick = {},
                                onHashtagClick = {},
                                onDeleteClick = { viewModel.deletePost(post.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}