package com.textsocial.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.textsocial.app.R
import com.textsocial.app.di.ServiceLocator
import com.textsocial.app.domain.model.User
import com.textsocial.app.presentation.components.AvatarSize
import com.textsocial.app.presentation.components.UserAvatarComponent
import com.textsocial.app.presentation.components.VerifiedBadge
import com.textsocial.app.presentation.viewmodel.CreatePostViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    viewModel: CreatePostViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfileMe: () -> Unit,
    showBottomBar: Boolean = true
) {
    val text by viewModel.text.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isFinished by viewModel.isFinished.collectAsState()

    val maxChars = 500
    val charsRemaining = maxChars - text.length

    var fieldValue by remember { mutableStateOf(TextFieldValue(text)) }
    var mentionSuggestions by remember { mutableStateOf<List<User>>(emptyList()) }

    val activeMentionQuery: String? = remember(fieldValue) {
        val cursor = fieldValue.selection.end
        if (cursor <= 0) return@remember null
        val beforeCursor = fieldValue.text.substring(0, cursor)
        val atIndex = beforeCursor.lastIndexOf('@')
        if (atIndex == -1) return@remember null
        val between = beforeCursor.substring(atIndex + 1)
        if (between.contains(' ') || between.contains('\n')) return@remember null
        between
    }

    LaunchedEffect(activeMentionQuery) {
        if (activeMentionQuery == null) {
            mentionSuggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        val result = if (activeMentionQuery.isBlank()) {
            ServiceLocator.userRepository.getFollowingUsers()
        } else {
            ServiceLocator.userRepository.searchUsers(activeMentionQuery)
        }
        mentionSuggestions = result.getOrDefault(emptyList()).take(5)
    }

    fun onMentionUserSelected(user: User) {
        val cursor = fieldValue.selection.end
        val beforeCursor = fieldValue.text.substring(0, cursor)
        val atIndex = beforeCursor.lastIndexOf('@')
        if (atIndex == -1) return
        val before = fieldValue.text.substring(0, atIndex)
        val after = fieldValue.text.substring(cursor)
        val newText = "$before@${user.username} $after"
        val newCursorPos = before.length + user.username.length + 2
        fieldValue = TextFieldValue(newText, TextRange(newCursorPos))
        viewModel.onTextChange(newText)
        mentionSuggestions = emptyList()
    }

    LaunchedEffect(isFinished) {
        if (isFinished) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.newpost_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.createPost() },
                        enabled = text.isNotBlank() && !isLoading,
                        modifier = Modifier.padding(end = 8.dp).testTag("publish_post_button")
                    ) {
                        Text(stringResource(R.string.posts_title))
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
                    currentRoute = "create_post",
                    onNavigateToHome = onNavigateToHome,
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToCreatePost = { /* Already Create Post */ },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                if (error != null) {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                TextField(
                    value = fieldValue,
                    onValueChange = {
                        fieldValue = it
                        viewModel.onTextChange(it.text)
                    },
                    placeholder = { Text(stringResource(R.string.penjelasanpost_title)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("post_text_field"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
                )

                if (mentionSuggestions.isNotEmpty()) {
                    Surface(
                        tonalElevation = 4.dp,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .padding(bottom = 8.dp)
                    ) {
                        LazyColumn {
                            items(mentionSuggestions) { user ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onMentionUserSelected(user) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatarComponent(
                                        username = user.username,
                                        avatarColor = user.avatarColor,
                                        avatarUrl = user.avatarUrl,
                                        size = AvatarSize.COMPACT
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = user.username, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            if (user.isVerified) {
                                                Spacer(modifier = Modifier.width(3.dp))
                                                VerifiedBadge(size = 13.dp)
                                            }
                                        }
                                        if (!user.displayName.isNullOrBlank()) {
                                            Text(
                                                text = user.displayName,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.limit_karakter, charsRemaining),
                        fontSize = 13.sp,
                        color = if (charsRemaining < 50) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )

                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}