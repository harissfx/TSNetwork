package com.textsocial.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.textsocial.app.presentation.components.VerifiedBadge
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
    onNavigateToFollowList: (userId: String, username: String, tab: Int) -> Unit,
    showBottomBar: Boolean = true
) {
    val user by viewModel.user.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val followsMe by viewModel.followsMe.collectAsState()
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
                    if (user?.id == "me_id" || user?.id == currentUserId) {
                        IconButton(onClick = onNavigateToSettings, modifier = Modifier.testTag("settings_button")) {
                            Icon(Icons.Default.Menu, contentDescription = "Settings")
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
                val isMyOwnProfile = userId == currentUserId || userId == "me_id"
                com.textsocial.app.presentation.components.BottomNavigationBar(
                    currentRoute = if (isMyOwnProfile) "profile/$userId" else "other_user_profile/$userId",
                    onNavigateToHome = onNavigateToHome,
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToCreatePost = onNavigateToCreatePost,
                    onNavigateToNotifications = onNavigateToNotifications,
                    onNavigateToProfile = onNavigateToProfileMe
                )
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.loadProfile(userId) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                                        avatarUrl = profile.avatarUrl,
                                        size = AvatarSize.LARGE
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = profile.displayName ?: profile.username,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        if (profile.isVerified) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            VerifiedBadge(size = 20.dp)
                                        }
                                    }

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
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable {
                                                onNavigateToFollowList(profile.id, profile.username, 0)
                                            }
                                        ) {
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
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable {
                                                onNavigateToFollowList(profile.id, profile.username, 1)
                                            }
                                        ) {
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

                                    if (profile.id != currentUserId && followsMe) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = stringResource(R.string.follows_you_tag),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

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
                                                    Text(
                                                        if (followsMe) stringResource(R.string.follow_back_button) else "Follow"
                                                    )
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

                        item {
                            Text(
                                text = stringResource(R.string.history_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        val isPrivateAndNotFollowing = user?.isPrivate == true && user?.id != currentUserId && !isFollowing

                        if (isPrivateAndNotFollowing) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = stringResource(R.string.priv_title),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.priv_desc),
                                            color = MaterialTheme.colorScheme.outline,
                                            fontSize = 13.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else if (posts.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.prof_kosong),
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
}