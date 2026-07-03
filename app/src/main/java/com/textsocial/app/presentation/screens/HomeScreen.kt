package com.textsocial.app.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.textsocial.app.domain.model.Post
import com.textsocial.app.presentation.components.AvatarSize
import com.textsocial.app.presentation.components.LinkTextComponent
import com.textsocial.app.presentation.components.UserAvatarComponent
import com.textsocial.app.presentation.viewmodel.HomeViewModel
import com.textsocial.app.presentation.viewmodel.StoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    storyViewModel: StoryViewModel,
    onNavigateToCreatePost: () -> Unit,
    onNavigateToPostDetail: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToStories: () -> Unit,
    onNavigateToCreateStory: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToDMs: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val posts by homeViewModel.posts.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()
    val activeHashtag by homeViewModel.activeHashtag.collectAsState()
    val stories by storyViewModel.stories.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.loadPosts(activeHashtag)
                storyViewModel.loadStories()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (activeHashtag != null) "#$activeHashtag" else "OpenText",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                },
                actions = {
                    if (activeHashtag != null) {
                        IconButton(onClick = { homeViewModel.loadPosts(null) }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search filter")
                        }
                    }
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search Users & Tags")
                    }
                    IconButton(onClick = onNavigateToDMs) {
                        Icon(Icons.Default.MailOutline, contentDescription = "Messages")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreatePost,
                modifier = Modifier.testTag("add_post_button"),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create post icon")
            }
        },
        bottomBar = {
            com.textsocial.app.presentation.components.BottomNavigationBar(
                currentRoute = "home",
                onNavigateToHome = { /* Already Home */ },
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToCreatePost = onNavigateToCreatePost,
                onNavigateToNotifications = onNavigateToNotifications,
                onNavigateToProfile = { onNavigateToProfile("me_id") }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Stories section at the top of the feed
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Stories",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            TextButton(onClick = onNavigateToCreateStory) {
                                Text("+ Create", fontWeight = FontWeight.Bold)
                            }
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(stories) { index, story ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            storyViewModel.setSelectedStoryIndex(index)
                                            onNavigateToStories()
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    UserAvatarComponent(
                                        username = story.username,
                                        avatarColor = story.avatarColor,
                                        size = AvatarSize.MEDIUM,
                                        modifier = Modifier.background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = story.username,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(60.dp)
                                    )
                                }
                            }
                        }
                        Divider(
                            modifier = Modifier.padding(top = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                if (isLoading && posts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (posts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = "Empty",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No posts found yet. Be the first to share!",
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                } else {
                    items(posts) { post ->
                        PostItem(
                            post = post,
                            onLikeToggle = { homeViewModel.toggleLike(post) },
                            onPostClick = { onNavigateToPostDetail(post.id) },
                            onUserClick = { onNavigateToProfile(post.userId) },
                            onHashtagClick = { hashtag -> homeViewModel.loadPosts(hashtag) },
                            onDeleteClick = { homeViewModel.deletePost(post.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PostItem(
    post: Post,
    onLikeToggle: () -> Unit,
    onPostClick: () -> Unit,
    onUserClick: () -> Unit,
    onHashtagClick: (String) -> Unit,
    onDeleteClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onPostClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onUserClick() }
                ) {
                    UserAvatarComponent(
                        username = post.username,
                        avatarColor = post.userAvatarColor,
                        size = AvatarSize.COMPACT
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = post.displayName ?: post.username,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "@${post.username}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                val myId = remember { com.textsocial.app.di.ServiceLocator.encryptedPreferencesManager.getUserId() }
                if (post.userId == myId && onDeleteClick != null) {
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete post",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body
            LinkTextComponent(
                text = post.text,
                style = TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                ),
                onHashtagClick = onHashtagClick,
                onMentionClick = { username -> }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Footer Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onLikeToggle() }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like icon",
                        tint = if (post.isLiked) Color.Red else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = post.likesCount.toString(),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Comment Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onPostClick() }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comment icon",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = post.commentsCount.toString(),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Share Icon
                IconButton(onClick = {
                    try {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Checkout this post by @${post.username} on OpenText:\n\n\"${post.text}\"")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    } catch (e: Exception) {
                        // ignore or toast
                    }
                }) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share post",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
