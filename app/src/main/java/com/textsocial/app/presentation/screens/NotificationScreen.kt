package com.textsocial.app.presentation.screens
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.textsocial.app.R
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
    showBottomBar: Boolean = true
) {
    val notifications by viewModel.notifications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notif_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading && notifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (notifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = "Empty activity alerts",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No notifications yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(notifications) { alert ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (!alert.isRead) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    if (alert.type == "follow") {
                                        onNavigateToProfile(alert.senderId)
                                    } else if (alert.postId != null) {
                                        onNavigateToPostDetail(alert.postId, alert.commentId)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatarComponent(
                                username = alert.senderUsername,
                                avatarColor = alert.senderAvatarColor,
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
                                        contentDescription = "Notification type Icon",
                                        tint = iconColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = alert.senderUsername,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
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
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}