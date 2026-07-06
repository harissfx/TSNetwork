package com.textsocial.app.presentation.screens

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.textsocial.app.R
import com.textsocial.app.presentation.components.AvatarSize
import com.textsocial.app.presentation.components.UserAvatarComponent
import com.textsocial.app.presentation.viewmodel.DMChatViewModel
import com.textsocial.app.domain.model.Message
import com.textsocial.app.util.TimeUtils
import kotlinx.coroutines.launch

private sealed class ChatRow {
    data class DateSeparator(val label: String) : ChatRow()
    data class MessageRow(val message: Message) : ChatRow()
}

private fun buildChatRows(context: Context, messages: List<Message>): List<ChatRow> {
    val rows = mutableListOf<ChatRow>()
    var previous: Message? = null
    for (msg in messages) {
        if (previous == null || !TimeUtils.isSameCalendarDay(previous.createdAt, msg.createdAt)) {
            rows += ChatRow.DateSeparator(TimeUtils.dayLabel(context, msg.createdAt))
        }
        rows += ChatRow.MessageRow(msg)
        previous = msg
    }
    return rows
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DMChatScreen(
    otherUserId: String,
    otherUsername: String,
    viewModel: DMChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onMessagesRead: () -> Unit = {}
) {
    val messages by viewModel.messages.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var messageForDeleteMenu by remember { mutableStateOf<String?>(null) }

    val chatRows = remember(messages) { buildChatRows(context, messages) }

    LaunchedEffect(otherUserId) {
        viewModel.initChat(otherUserId, onMessagesRead = onMessagesRead)
    }

    LaunchedEffect(chatRows.size) {
        if (chatRows.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(chatRows.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onNavigateToProfile(otherUserId) }
                            .testTag("dm_chat_header_profile")
                    ) {
                        UserAvatarComponent(
                            username = otherUsername,
                            avatarColor = "#2196F3", // custom default theme color
                            size = AvatarSize.COMPACT
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = otherUsername,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                },
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
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column {
                    if (sendError != null) {
                        Text(
                            text = sendError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = messageText,
                            onValueChange = { viewModel.onMessageTextChange(it) },
                            placeholder = { Text(stringResource(R.string.dm_entry)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dm_chat_input_field"),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = MaterialTheme.shapes.medium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.sendMessage() },
                            enabled = messageText.isNotBlank(),
                            modifier = Modifier.testTag("dm_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send Message",
                                tint = if (messageText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
            ) {
                itemsIndexed(
                    chatRows,
                    key = { index, row ->
                        when (row) {
                            is ChatRow.DateSeparator -> "sep_$index"
                            is ChatRow.MessageRow -> row.message.id
                        }
                    }
                ) { _, row ->
                    if (row is ChatRow.DateSeparator) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = row.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        return@itemsIndexed
                    }

                    val msg = (row as ChatRow.MessageRow).message
                    val isMyMessage = msg.senderId != otherUserId

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isMyMessage) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Column(
                            horizontalAlignment = if (isMyMessage) Alignment.End else Alignment.Start
                        ) {
                            Box {
                                Surface(
                                    color = if (isMyMessage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isMyMessage) 16.dp else 4.dp,
                                        bottomEnd = if (isMyMessage) 4.dp else 16.dp
                                    ),
                                    modifier = Modifier
                                        .widthIn(max = 280.dp)
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {

                                                if (isMyMessage && !msg.isDeleted) {
                                                    messageForDeleteMenu = msg.id
                                                }
                                            }
                                        )
                                ) {
                                    Text(
                                        text = if (msg.isDeleted) stringResource(R.string.pesan_dihapus) else msg.text,
                                        color = if (isMyMessage) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontSize = 14.sp,
                                        fontStyle = if (msg.isDeleted) FontStyle.Italic else FontStyle.Normal,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = messageForDeleteMenu == msg.id,
                                    onDismissRequest = { messageForDeleteMenu = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.hapus_verif)) },
                                        onClick = {
                                            viewModel.deleteMessage(msg.id)
                                            messageForDeleteMenu = null
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isMyMessage && msg.isRead) {
                                    "${TimeUtils.clockTime(msg.createdAt)} · Read"
                                } else {
                                    TimeUtils.clockTime(msg.createdAt)
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}