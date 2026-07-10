package com.textsocial.app.presentation.screens
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.textsocial.app.R
import com.textsocial.app.domain.model.Conversation
import com.textsocial.app.presentation.components.AvatarSize
import com.textsocial.app.presentation.components.UserAvatarComponent
import com.textsocial.app.presentation.components.VerifiedBadge
import com.textsocial.app.presentation.viewmodel.DMListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DMListScreen(
    viewModel: DMListViewModel,
    onNavigateToChat: (String, String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsState()
    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(actionError) {
        val message = actionError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearActionError()
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.loadConversations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} dipilih", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.toggleSelectMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Batalkan pilihan")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Pilih semua")
                        }
                        IconButton(
                            onClick = { showDeleteSelectedConfirm = true },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus obrolan yang dipilih")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.dm_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                        }
                    },
                    actions = {
                        if (conversations.isNotEmpty()) {
                            IconButton(onClick = { viewModel.toggleSelectMode() }) {
                                Icon(Icons.Default.Checklist, contentDescription = "Pilih obrolan")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
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
            if (isLoading && conversations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (conversations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Message,
                            contentDescription = "Empty message index",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.dm_kosong),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.dm_kosongdesc),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(conversations, key = { it.id }) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            context = context,
                            isSelectMode = isSelectMode,
                            isSelected = selectedIds.contains(conversation.id),
                            onToggleSelected = { viewModel.toggleSelected(conversation.id) },
                            onLongPress = { viewModel.enterSelectModeWith(conversation.id) },
                            onClick = { onNavigateToChat(conversation.otherUserId, conversation.otherUsername) }
                        )
                        Divider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 76.dp)
                        )
                    }
                }
            }
        }
    }

    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text(stringResource(R.string.dm_del_title, selectedIds.size)) },
            text = { Text(stringResource(R.string.dm_del_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSelectedConfirm = false
                    viewModel.deleteSelected()
                }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    context: android.content.Context,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onToggleSelected: () -> Unit,
    onLongPress: () -> Unit,
    onClick: () -> Unit
) {
    val hasUnread = conversation.unreadCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .combinedClickable(
                onClick = { if (isSelectMode) onToggleSelected() else onClick() },
                onLongClick = { if (!isSelectMode) onLongPress() }
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelected() })
            Spacer(modifier = Modifier.width(4.dp))
        }
        UserAvatarComponent(
            username = conversation.otherUsername,
            avatarColor = conversation.otherAvatarColor,
            avatarUrl = conversation.otherAvatarUrl,
            size = AvatarSize.MEDIUM
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conversation.otherUsername,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (conversation.otherIsVerified) {
                        Spacer(modifier = Modifier.width(3.dp))
                        VerifiedBadge(size = 14.dp)
                    }
                }
                Text(
                    text = conversation.lastMessageTime?.let {
                        com.textsocial.app.util.TimeUtils.timeAgoShort(context, it)
                    } ?: "",
                    fontSize = 11.sp,
                    color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.lastMessage ?: "No messages",
                    fontSize = 13.sp,
                    color = if (hasUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (hasUnread) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}