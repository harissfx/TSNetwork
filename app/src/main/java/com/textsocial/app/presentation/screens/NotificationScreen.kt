package com.textsocial.app.presentation.screens
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.textsocial.app.R
import com.textsocial.app.domain.model.Notification
import com.textsocial.app.presentation.components.AvatarSize
import com.textsocial.app.presentation.components.UserAvatarComponent
import com.textsocial.app.presentation.viewmodel.NotificationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    viewModel: NotificationViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToCreatePost: () -> Unit,
    onNavigateToProfileMe: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToPostDetail: (String, String?) -> Unit,
    onNotificationRead: () -> Unit = {},
    onAllNotificationsRead: () -> Unit = {},
    showBottomBar: Boolean = true
) {
    val notifications by viewModel.notifications.collectAsState()
    val filteredNotifications by viewModel.filteredNotifications.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(actionError) {
        val message = actionError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearActionError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.notif_select_count_title, selectedIds.size), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.toggleSelectMode() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_cancel_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.DoneAll, contentDescription = stringResource(R.string.cd_select_all))
                        }
                        IconButton(
                            onClick = { showDeleteSelectedConfirm = true },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_selected))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.notif_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_go_back))
                        }
                    },
                    actions = {
                        if (notifications.any { !it.isRead }) {
                            IconButton(onClick = {
                                viewModel.markAllAsRead(onMarked = onAllNotificationsRead)
                            }) {
                                Icon(Icons.Default.DoneAll, contentDescription = stringResource(R.string.cd_mark_all_read))
                            }
                        }
                        if (notifications.isNotEmpty()) {
                            IconButton(onClick = { viewModel.toggleSelectMode() }) {
                                Icon(Icons.Default.Checklist, contentDescription = stringResource(R.string.cd_select_notifications))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                com.textsocial.app.presentation.components.BottomNavigationBar(
                    currentRoute = "notifications",
                    onNavigateToHome = onNavigateToHome,
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToCreatePost = onNavigateToCreatePost,
                    onNavigateToNotifications = { /* Already Notifications */ },
                    onNavigateToProfile = onNavigateToProfileMe
                )
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.loadNotifications() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (!isSelectMode && notifications.isNotEmpty()) {
                    NotificationFilterTabs(
                        selectedFilter = selectedFilter,
                        onSelectFilter = { viewModel.selectFilter(it) }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (isLoading && notifications.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (notifications.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = stringResource(R.string.cd_empty_notifications),
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.notif_empty_all),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    } else if (filteredNotifications.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = stringResource(R.string.cd_empty_notifications),
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = when (selectedFilter) {
                                        "like" -> stringResource(R.string.notif_empty_like)
                                        "comment" -> stringResource(R.string.notif_empty_comment)
                                        "mention" -> stringResource(R.string.notif_empty_mention)
                                        "follow" -> stringResource(R.string.notif_empty_follow)
                                        else -> stringResource(R.string.notif_empty_all)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredNotifications, key = { it.id }) { alert ->
                                NotificationRow(
                                    alert = alert,
                                    context = context,
                                    isSelectMode = isSelectMode,
                                    isSelected = selectedIds.contains(alert.id),
                                    onToggleSelected = { viewModel.toggleSelected(alert.id) },
                                    onLongPress = { viewModel.enterSelectModeWith(alert.id) },
                                    onClick = {
                                        viewModel.markAsRead(alert, onMarked = onNotificationRead)
                                        if (alert.type == "follow") {
                                            onNavigateToProfile(alert.senderId)
                                        } else if (alert.postId != null) {
                                            onNavigateToPostDetail(alert.postId, alert.commentId)
                                        }
                                    }
                                )
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text(stringResource(R.string.del_title, selectedIds.size),) },
            text = { Text(stringResource(R.string.notif_del)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSelectedConfirm = false
                    viewModel.deleteSelected()
                }) {
                    Text(stringResource(R.string.delete_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirm = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }
}

private val notificationFilterTabs = listOf(
    "all" to R.string.notif_tab_all,
    "like" to R.string.notif_tab_like,
    "comment" to R.string.notif_tab_comment,
    "mention" to R.string.notif_tab_mention,
    "follow" to R.string.notif_tab_follow
)

@Composable
private fun NotificationFilterTabs(
    selectedFilter: String,
    onSelectFilter: (String) -> Unit
) {
    val selectedIndex = notificationFilterTabs.indexOfFirst { it.first == selectedFilter }.coerceAtLeast(0)
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.background,
        edgePadding = 14.dp,
        divider = { Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
    ) {
        notificationFilterTabs.forEachIndexed { index, (key, labelRes) ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelectFilter(key) },
                text = {
                    Text(
                        text = stringResource(labelRes),
                        fontSize = 13.sp,
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NotificationRow(
    alert: Notification,
    context: android.content.Context,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onToggleSelected: () -> Unit,
    onLongPress: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    !alert.isRead -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    else -> Color.Transparent
                }
            )
            .combinedClickable(
                onClick = { if (isSelectMode) onToggleSelected() else onClick() },
                onLongClick = { if (!isSelectMode) onLongPress() }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelected() })
            Spacer(modifier = Modifier.width(8.dp))
        }
        UserAvatarComponent(
            username = alert.senderUsername,
            avatarColor = alert.senderAvatarColor,
            avatarUrl = alert.senderAvatarUrl,
            size = AvatarSize.COMPACT
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val alertIcon = when (alert.type) {
                    "like" -> Icons.Default.Favorite
                    "comment" -> Icons.Default.ChatBubble
                    "follow" -> Icons.Default.PersonAdd
                    else -> Icons.Default.AlternateEmail
                }
                val iconColor = when (alert.type) {
                    "like" -> Color.Red
                    "comment" -> MaterialTheme.colorScheme.primary
                    "follow" -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.tertiary
                }

                Icon(
                    imageVector = alertIcon,
                    contentDescription = stringResource(R.string.cd_notification_type_icon),
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = alert.senderUsername,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (alert.senderIsVerified) {
                    Spacer(modifier = Modifier.width(3.dp))
                    com.textsocial.app.presentation.components.VerifiedBadge(size = 13.dp)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            val typeText = when (alert.type) {
                "like" -> stringResource(R.string.notif_liked_post)
                "comment" -> stringResource(R.string.notif_commented_post)
                "follow" -> stringResource(R.string.notif_started_following)
                "mention" -> stringResource(R.string.notif_mentioned_you)
                else -> stringResource(R.string.notif_generic)
            }
            Text(
                text = typeText,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = com.textsocial.app.util.TimeUtils.timeAgoShort(context, alert.createdAt),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
            if (!alert.isRead) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                )
            }
        }
    }
}