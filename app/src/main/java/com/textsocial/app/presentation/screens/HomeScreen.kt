package com.textsocial.app.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.content.Intent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.textsocial.app.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.textsocial.app.di.ServiceLocator
import com.textsocial.app.domain.model.Post
import com.textsocial.app.presentation.components.AvatarSize
import com.textsocial.app.presentation.components.ExpandableLinkText
import com.textsocial.app.presentation.components.LinkTextComponent
import com.textsocial.app.presentation.components.UserAvatarComponent
import com.textsocial.app.presentation.components.VerifiedBadge
import com.textsocial.app.presentation.viewmodel.HomeViewModel
import com.textsocial.app.presentation.viewmodel.StoryViewModel
import kotlinx.coroutines.launch

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
    unreadMessagesCount: Int = 0,
    unreadNotificationsCount: Int = 0,
    showBottomBar: Boolean = true
) {
    val posts by homeViewModel.posts.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()
    val activeHashtag by homeViewModel.activeHashtag.collectAsState()
    val feedMode by homeViewModel.feedMode.collectAsState()
    val stories by storyViewModel.stories.collectAsState()
    val actionError by homeViewModel.actionError.collectAsState()
    val storyActionError by storyViewModel.actionError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(actionError) {
        val message = actionError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            homeViewModel.clearActionError()
        }
    }

    LaunchedEffect(storyActionError) {
        val message = storyActionError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            storyViewModel.clearActionError()
        }
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        BadgedBox(
                            badge = {
                                if (unreadMessagesCount > 0) {
                                    Badge {
                                        Text(if (unreadMessagesCount > 99) "99+" else unreadMessagesCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = "Messages")
                        }
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
            if (showBottomBar) {
                com.textsocial.app.presentation.components.BottomNavigationBar(
                    currentRoute = "home",
                    onNavigateToHome = { /* Already Home */ },
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToCreatePost = onNavigateToCreatePost,
                    onNavigateToNotifications = onNavigateToNotifications,
                    onNavigateToProfile = { onNavigateToProfile("me_id") },
                    unreadNotificationsCount = unreadNotificationsCount
                )
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = {
                homeViewModel.loadPosts(activeHashtag)
                storyViewModel.loadStories()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.home_stories_title),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                TextButton(onClick = onNavigateToCreateStory) {
                                    Text(stringResource(R.string.story_create), fontWeight = FontWeight.Bold)
                                }
                            }

                            val storyBubbleUsers = remember(stories) { stories.distinctBy { it.userId } }
                            val myUserId = remember { ServiceLocator.encryptedPreferencesManager.getUserId() }
                            val myUsername = remember { ServiceLocator.encryptedPreferencesManager.getUsername() }
                            val unseenRingBrush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFFFEDA75),
                                    Color(0xFFFA7E1E),
                                    Color(0xFFD62976),
                                    Color(0xFF962FBF),
                                    Color(0xFF4F5BD5),
                                    Color(0xFFFEDA75)
                                )
                            )
                            val seenRingColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(storyBubbleUsers) { story ->
                                    val storyCountForUser = stories.count { it.userId == story.userId }
                                    val hasUnseenStory = myUsername == null ||
                                            stories.any { it.userId == story.userId && !it.views.contains(myUsername) }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clickable {
                                                val firstIndexForUser = stories.indexOfFirst { it.userId == story.userId }
                                                storyViewModel.setSelectedStoryIndex(firstIndexForUser)
                                                onNavigateToStories()
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Box {
                                            Box(
                                                modifier = Modifier
                                                    .padding(3.dp)
                                                    .border(
                                                        width = 2.5.dp,
                                                        brush = if (hasUnseenStory) unseenRingBrush else Brush.linearGradient(
                                                            listOf(seenRingColor, seenRingColor)
                                                        ),
                                                        shape = CircleShape
                                                    )
                                                    .padding(3.dp)
                                            ) {
                                                UserAvatarComponent(
                                                    username = story.username,
                                                    avatarColor = story.avatarColor,
                                                    avatarUrl = story.avatarUrl,
                                                    size = AvatarSize.MEDIUM,
                                                    modifier = Modifier.background(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = androidx.compose.foundation.shape.CircleShape
                                                    )
                                                )
                                            }
                                            if (storyCountForUser > 1) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = androidx.compose.foundation.shape.CircleShape,
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .size(16.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = storyCountForUser.toString(),
                                                            fontSize = 9.sp,
                                                            lineHeight = 9.sp,
                                                            color = MaterialTheme.colorScheme.onPrimary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Divider(
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                    if (activeHashtag == null) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    com.textsocial.app.presentation.viewmodel.FeedMode.FOR_YOU to stringResource(R.string.feed_mode_for_you),
                                    com.textsocial.app.presentation.viewmodel.FeedMode.LATEST to stringResource(R.string.feed_mode_latest)
                                ).forEach { (mode, label) ->
                                    val selected = feedMode == mode
                                    FilterChip(
                                        selected = selected,
                                        onClick = { homeViewModel.setFeedMode(mode) },
                                        label = { Text(label, fontSize = 13.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    )
                                }
                            }
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
                                    Text(stringResource(R.string.feed_kosong),
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
                                onMentionClick = { username ->
                                    coroutineScope.launch {
                                        val result = com.textsocial.app.di.ServiceLocator.userRepository.getProfileByUsername(username)
                                        result.onSuccess { mentionedUser -> onNavigateToProfile(mentionedUser.id) }
                                    }
                                },
                                onDeleteClick = { homeViewModel.deletePost(post.id) }
                            )
                        }
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
    onMentionClick: (String) -> Unit = {},
    onDeleteClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clickable { onPostClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
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
                        avatarUrl = post.userAvatarUrl,
                        size = AvatarSize.COMPACT
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = post.displayName ?: post.username,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (post.isVerified) {
                                Spacer(modifier = Modifier.width(3.dp))
                                VerifiedBadge(size = 14.dp)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "@${post.username}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            if (post.id.startsWith("temp-")) {
                                Text(
                                    text = " · Mengirim…",
                                    fontSize = 12.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                Text(
                                    text = " · ${com.textsocial.app.util.TimeUtils.timeAgoShort(context, post.createdAt)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                val myId = remember { com.textsocial.app.di.ServiceLocator.encryptedPreferencesManager.getUserId() }
                val clipboardManager = LocalClipboardManager.current
                var showPostMenu by remember { mutableStateOf(false) }

                Box {
                    IconButton(onClick = { showPostMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opsi lainnya",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(expanded = showPostMenu, onDismissRequest = { showPostMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Salin teks") },
                            onClick = {
                                clipboardManager.setText(AnnotatedString(post.text))
                                showPostMenu = false
                            }
                        )
                        // Cuma pemilik post yang bisa hapus -- orang lain nggak mungkin ada opsi ini,
                        // makanya hanya di-render kalau post.userId cocok sama user yang lagi login.
                        if (post.userId == myId && onDeleteClick != null) {
                            DropdownMenuItem(
                                text = { Text("Hapus postingan", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showPostMenu = false
                                    onDeleteClick()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            ExpandableLinkText(
                text = post.text,
                style = TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                ),
                onHashtagClick = onHashtagClick,
                onMentionClick = onMentionClick
            )

            post.linkPreview?.let { preview ->
                Spacer(modifier = Modifier.height(8.dp))
                com.textsocial.app.presentation.components.LinkPreviewCard(preview = preview)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onPostClick() }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubble,
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
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share post",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}