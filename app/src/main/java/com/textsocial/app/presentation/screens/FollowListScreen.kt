package com.textsocial.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textsocial.app.R
import com.textsocial.app.di.ServiceLocator
import com.textsocial.app.domain.model.FollowListEntry
import com.textsocial.app.presentation.components.AvatarSize
import com.textsocial.app.presentation.components.UserAvatarComponent
import com.textsocial.app.presentation.components.VerifiedBadge
import com.textsocial.app.presentation.viewmodel.FollowListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListScreen(
    viewModel: FollowListViewModel,
    targetUserId: String,
    targetUsername: String,
    initialTab: Int = 0,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit
) {
    val followers by viewModel.followers.collectAsState()
    val following by viewModel.following.collectAsState()
    val isLoadingFollowers by viewModel.isLoadingFollowers.collectAsState()
    val isLoadingFollowing by viewModel.isLoadingFollowing.collectAsState()
    val isFollowingListHidden by viewModel.isFollowingListHidden.collectAsState()
    val followActionLoadingIds by viewModel.followActionLoadingIds.collectAsState()
    var selectedTab by remember { mutableStateOf(initialTab.coerceIn(0, 1)) }

    LaunchedEffect(targetUserId) {
        viewModel.load(targetUserId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("@$targetUsername", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.follow_title)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.following_title)) }
                )
            }

            when (selectedTab) {
                0 -> FollowEntryList(
                    entries = followers,
                    isLoading = isLoadingFollowers,
                    emptyMessage = stringResource(R.string.no_followers_yet),
                    followActionLoadingIds = followActionLoadingIds,
                    onEntryClick = { onNavigateToProfile(it.user.id) },
                    onToggleFollow = { viewModel.toggleFollow(it) }
                )
                else -> {
                    if (isFollowingListHidden) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.following_list_hidden_message),
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        FollowEntryList(
                            entries = following,
                            isLoading = isLoadingFollowing,
                            emptyMessage = stringResource(R.string.not_following_anyone_yet),
                            followActionLoadingIds = followActionLoadingIds,
                            onEntryClick = { onNavigateToProfile(it.user.id) },
                            onToggleFollow = { viewModel.toggleFollow(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowEntryList(
    entries: List<FollowListEntry>,
    isLoading: Boolean,
    emptyMessage: String,
    followActionLoadingIds: Set<String>,
    onEntryClick: (FollowListEntry) -> Unit,
    onToggleFollow: (FollowListEntry) -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyMessage,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 14.sp
            )
        }
        return
    }

    val myId = ServiceLocator.encryptedPreferencesManager.getUserId()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(entries, key = { it.user.id }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEntryClick(entry) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatarComponent(
                    username = entry.user.username,
                    avatarColor = entry.user.avatarColor,
                    avatarUrl = entry.user.avatarUrl,
                    size = AvatarSize.MEDIUM
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.user.displayName ?: entry.user.username,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        if (entry.user.isVerified) {
                            Spacer(modifier = Modifier.width(3.dp))
                            VerifiedBadge(size = 14.dp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "@${entry.user.username}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        if (entry.followsMe) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = stringResource(R.string.follows_you_tag),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                if (entry.user.id != myId) {
                    val isActionLoading = followActionLoadingIds.contains(entry.user.id)
                    if (entry.isFollowedByMe) {
                        OutlinedButton(
                            onClick = { onToggleFollow(entry) },
                            enabled = !isActionLoading,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(R.string.following_title), fontSize = 13.sp)
                        }
                    } else {
                        Button(
                            onClick = { onToggleFollow(entry) },
                            enabled = !isActionLoading,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (entry.followsMe) stringResource(R.string.follow_back_button) else "Follow",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}