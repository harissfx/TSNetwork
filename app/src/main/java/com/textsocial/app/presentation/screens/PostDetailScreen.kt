package com.textsocial.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textsocial.app.presentation.components.AvatarSize
import com.textsocial.app.presentation.components.LinkTextComponent
import com.textsocial.app.presentation.components.UserAvatarComponent
import com.textsocial.app.presentation.viewmodel.PostDetailViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    viewModel: PostDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit
) {
    val post by viewModel.post.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val commentText by viewModel.commentText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(postId) {
        viewModel.setPost(postId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discussion", fontWeight = FontWeight.Bold) },
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
                    // Fix #1: banner konteks "membalas ke @siapa" supaya jelas komentar
                    // baru ini akan jadi balasan untuk komentar yang mana.
                    replyingTo?.let { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Membalas @${target.username}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { viewModel.cancelReply() }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Batalkan balasan", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = commentText,
                            onValueChange = { viewModel.onCommentTextChange(it) },
                            placeholder = { Text(if (replyingTo != null) "Tulis balasan..." else "Write your reply...") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("comment_input_field"),
                            maxLines = 3,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = MaterialTheme.shapes.medium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.addComment() },
                            enabled = commentText.isNotBlank(),
                            modifier = Modifier.testTag("send_comment_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send Reply",
                                tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
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
            if (isLoading && post == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Main Post Card
                    post?.let { currentPost ->
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { onNavigateToProfile(currentPost.userId) }
                                ) {
                                    UserAvatarComponent(
                                        username = currentPost.username,
                                        avatarColor = currentPost.userAvatarColor,
                                        size = AvatarSize.MEDIUM
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = currentPost.displayName ?: currentPost.username,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "@${currentPost.username}",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                LinkTextComponent(
                                    text = currentPost.text,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 18.sp,
                                        lineHeight = 26.sp
                                    ),
                                    onMentionClick = { username ->
                                        coroutineScope.launch {
                                            val result = com.textsocial.app.di.ServiceLocator.userRepository.getProfileByUsername(username)
                                            result.onSuccess { mentionedUser -> onNavigateToProfile(mentionedUser.id) }
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row {
                                        Text(
                                            text = "${currentPost.likesCount} Likes",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = "${currentPost.commentsCount} Comments",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }

                                    val context = LocalContext.current
                                    IconButton(
                                        onClick = {
                                            try {
                                                val sendIntent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TEXT, "Checkout this post by @${currentPost.username} on OpenText:\n\n\"${currentPost.text}\"")
                                                    type = "text/plain"
                                                }
                                                val shareIntent = Intent.createChooser(sendIntent, null)
                                                context.startActivity(shareIntent)
                                            } catch (e: Exception) {}
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share post",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }

                    // Comments Section Heading
                    item {
                        Text(
                            text = "Replies",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (comments.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No replies yet. Start the conversation!",
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        // Fix #1: kelompokkan komentar jadi top-level + balasannya, supaya
                        // balasan tampil menjorok (indented) di bawah komentar induknya,
                        // bukan tumpuk rata semua di satu list datar seperti sebelumnya.
                        val topLevelComments = comments.filter { it.parentId == null }
                        val repliesByParent = comments.filter { it.parentId != null }.groupBy { it.parentId }

                        items(topLevelComments, key = { it.id }) { comment ->
                            CommentRow(
                                comment = comment,
                                postOwnerId = post?.userId,
                                isReply = false,
                                onUserClick = onNavigateToProfile,
                                onReplyClick = { viewModel.startReplyTo(comment) },
                                onLikeClick = { viewModel.toggleCommentLike(comment) },
                                onDeleteClick = { viewModel.deleteComment(comment.id) }
                            )
                        }
                        topLevelComments.forEach { parent ->
                            val childReplies = repliesByParent[parent.id] ?: emptyList()
                            items(childReplies, key = { it.id }) { reply ->
                                CommentRow(
                                    comment = reply,
                                    postOwnerId = post?.userId,
                                    isReply = true,
                                    onUserClick = onNavigateToProfile,
                                    onReplyClick = { viewModel.startReplyTo(parent) },
                                    onLikeClick = { viewModel.toggleCommentLike(reply) },
                                    onDeleteClick = { viewModel.deleteComment(reply.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: com.textsocial.app.domain.model.Comment,
    postOwnerId: String?,
    isReply: Boolean,
    onUserClick: (String) -> Unit,
    onReplyClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val myId = remember { com.textsocial.app.di.ServiceLocator.encryptedPreferencesManager.getUserId() }
    val startPadding = if (isReply) 48.dp else 16.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding, end = 16.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onUserClick(comment.userId) }
            ) {
                UserAvatarComponent(
                    username = comment.username,
                    avatarColor = comment.avatarColor,
                    size = if (isReply) AvatarSize.COMPACT else AvatarSize.COMPACT
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = comment.username,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            if (comment.userId == myId || postOwnerId == myId) {
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete comment",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = comment.text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(start = 42.dp),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Fix #4: komentar cuma punya tombol Like (tanpa dislike/reaksi lain), plus
        // tombol Reply untuk fix #1.
        Row(
            modifier = Modifier.padding(start = 42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onLikeClick, modifier = Modifier.size(20.dp)) {
                Icon(
                    imageVector = if (comment.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like comment",
                    tint = if (comment.isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (comment.likesCount > 0) {
                Text(
                    text = "${comment.likesCount}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Balas",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.clickable { onReplyClick() }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Divider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 42.dp)
        )
    }
}