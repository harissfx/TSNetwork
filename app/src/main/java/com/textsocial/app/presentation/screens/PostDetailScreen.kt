package com.textsocial.app.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.textsocial.app.R
import com.textsocial.app.di.ServiceLocator
import com.textsocial.app.domain.model.Comment
import com.textsocial.app.domain.model.User
import com.textsocial.app.presentation.components.AvatarSize
import com.textsocial.app.presentation.components.LinkTextComponent
import com.textsocial.app.presentation.components.UserAvatarComponent
import com.textsocial.app.presentation.components.VerifiedBadge
import com.textsocial.app.presentation.viewmodel.PostDetailViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COLLAPSE_THRESHOLD = 1


private sealed class CommentRowItem {
    abstract val key: String
    data class Top(val comment: Comment) : CommentRowItem() {
        override val key get() = comment.id
    }
    data class Reply(val comment: Comment, val replyToUsername: String) : CommentRowItem() {
        override val key get() = comment.id
    }
    data class ShowMore(val rootId: String, val hiddenCount: Int) : CommentRowItem() {
        override val key get() = "show_more_$rootId"
    }
    data class ShowLess(val rootId: String) : CommentRowItem() {
        override val key get() = "show_less_$rootId"
    }
}

private fun buildCommentRows(
    comments: List<Comment>,
    expandedRoots: Set<String>,
    forceExpandRootId: String?
): List<CommentRowItem> {
    val byId = comments.associateBy { it.id }
    fun rootIdOf(comment: Comment): String {
        var current = comment
        val visited = mutableSetOf<String>()
        while (current.parentId != null && visited.add(current.id)) {
            current = byId[current.parentId] ?: return current.parentId!!
        }
        return current.id
    }

    val topLevel = comments.filter { it.parentId == null }.sortedBy { it.createdAt }
    val repliesByRoot = comments.filter { it.parentId != null }
        .groupBy { rootIdOf(it) }
        .mapValues { (_, list) -> list.sortedBy { it.createdAt } }

    val rows = mutableListOf<CommentRowItem>()
    for (top in topLevel) {
        rows += CommentRowItem.Top(top)
        val replies = repliesByRoot[top.id] ?: emptyList()
        val isExpanded = expandedRoots.contains(top.id) || forceExpandRootId == top.id
        val visibleReplies = if (isExpanded || replies.size <= COLLAPSE_THRESHOLD) replies else replies.take(COLLAPSE_THRESHOLD)
        for (reply in visibleReplies) {
            val directParentUsername = byId[reply.parentId]?.username ?: top.username
            rows += CommentRowItem.Reply(reply, directParentUsername)
        }
        if (replies.size > COLLAPSE_THRESHOLD) {
            if (isExpanded) rows += CommentRowItem.ShowLess(top.id)
            else rows += CommentRowItem.ShowMore(top.id, replies.size - COLLAPSE_THRESHOLD)
        }
    }
    return rows
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    highlightCommentId: String? = null,
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
    val listState = rememberLazyListState()
    val context = LocalContext.current

    var expandedRoots by remember { mutableStateOf(setOf<String>()) }
    var highlightedCommentId by remember { mutableStateOf(highlightCommentId) }
    var commentFieldValue by remember { mutableStateOf(TextFieldValue(commentText)) }
    var mentionSuggestions by remember { mutableStateOf<List<User>>(emptyList()) }
    LaunchedEffect(commentText) {
        if (commentText != commentFieldValue.text) {
            commentFieldValue = TextFieldValue(commentText, TextRange(commentText.length))
        }
    }

    val activeMentionQuery: String? = remember(commentFieldValue) {
        val cursor = commentFieldValue.selection.end
        if (cursor <= 0) return@remember null
        val beforeCursor = commentFieldValue.text.substring(0, cursor)
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
        val cursor = commentFieldValue.selection.end
        val beforeCursor = commentFieldValue.text.substring(0, cursor)
        val atIndex = beforeCursor.lastIndexOf('@')
        if (atIndex == -1) return
        val before = commentFieldValue.text.substring(0, atIndex)
        val after = commentFieldValue.text.substring(cursor)
        val newText = "$before@${user.username} $after"
        val newCursorPos = before.length + user.username.length + 2
        commentFieldValue = TextFieldValue(newText, TextRange(newCursorPos))
        viewModel.onCommentTextChange(newText)
        mentionSuggestions = emptyList()
    }

    LaunchedEffect(postId) {
        viewModel.setPost(postId)
    }

    LaunchedEffect(comments, highlightCommentId) {
        val targetId = highlightCommentId ?: return@LaunchedEffect
        if (comments.none { it.id == targetId }) return@LaunchedEffect

        val byId = comments.associateBy { it.id }
        val target = byId[targetId] ?: return@LaunchedEffect
        var rootWalker = target
        val visited = mutableSetOf<String>()
        while (rootWalker.parentId != null && visited.add(rootWalker.id)) {
            rootWalker = byId[rootWalker.parentId] ?: break
        }
        val rootId = rootWalker.id
        if (rootId != targetId) {
            expandedRoots = expandedRoots + rootId
        }

        delay(50)
        val rows = buildCommentRows(comments, expandedRoots, forceExpandRootId = rootId)
        val index = rows.indexOfFirst { it.key == targetId }
        if (index >= 0) {
            listState.animateScrollToItem((index + 2).coerceAtLeast(0))
        }
        highlightedCommentId = targetId
        delay(2500)
        highlightedCommentId = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diskusi_title), fontWeight = FontWeight.Bold) },
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
                                text = stringResource(R.string.rp_komen, target.username),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { viewModel.cancelReply() }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Batalkan balasan", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (mentionSuggestions.isNotEmpty()) {
                        Surface(
                            tonalElevation = 4.dp,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .padding(horizontal = 16.dp)
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
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = commentFieldValue,
                            onValueChange = {
                                commentFieldValue = it
                                viewModel.onCommentTextChange(it.text)
                            },
                            placeholder = {
                                Text(
                                    if (replyingTo != null) stringResource(R.string.comment_placeholder_reply)
                                    else stringResource(R.string.comment_placeholder_new)
                                )
                            },
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
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    post?.let { currentPost ->
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { onNavigateToProfile(currentPost.userId) }
                                ) {
                                    UserAvatarComponent(
                                        username = currentPost.username,
                                        avatarColor = currentPost.userAvatarColor,
                                        avatarUrl = currentPost.userAvatarUrl,
                                        size = AvatarSize.MEDIUM
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = currentPost.displayName ?: currentPost.username,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                            if (currentPost.isVerified) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                VerifiedBadge(size = 15.dp)
                                            }
                                        }
                                        Text(
                                            text = "@${currentPost.username}",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = com.textsocial.app.util.TimeUtils.timeFull(currentPost.createdAt),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )

                                Spacer(modifier = Modifier.height(12.dp))

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

                                Spacer(modifier = Modifier.height(12.dp))

                                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row {
                                        Text(
                                            text = stringResource(R.string.like_tit, currentPost.likesCount),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = stringResource(R.string.komen_tit, currentPost.commentsCount),
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
                    item {
                        Text(
                            text = stringResource(R.string.rpl_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (comments.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.rpl_postkos),
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {

                        val rows = buildCommentRows(comments, expandedRoots, forceExpandRootId = null)

                        items(rows, key = { it.key }) { row ->
                            when (row) {
                                is CommentRowItem.Top -> CommentRow(
                                    comment = row.comment,
                                    postOwnerId = post?.userId,
                                    isReply = false,
                                    replyToUsername = null,
                                    isHighlighted = row.comment.id == highlightedCommentId,
                                    onUserClick = onNavigateToProfile,
                                    onReplyClick = { viewModel.startReplyTo(row.comment) },
                                    onLikeClick = { viewModel.toggleCommentLike(row.comment) },
                                    onDeleteClick = { viewModel.deleteComment(row.comment.id) }
                                )
                                is CommentRowItem.Reply -> CommentRow(
                                    comment = row.comment,
                                    postOwnerId = post?.userId,
                                    isReply = true,
                                    replyToUsername = row.replyToUsername,
                                    isHighlighted = row.comment.id == highlightedCommentId,
                                    onUserClick = onNavigateToProfile,
                                    onReplyClick = {

                                        viewModel.startReplyTo(row.comment)
                                    },
                                    onLikeClick = { viewModel.toggleCommentLike(row.comment) },
                                    onDeleteClick = { viewModel.deleteComment(row.comment.id) }
                                )
                                is CommentRowItem.ShowMore -> Text(
                                    text = stringResource(R.string.balasan_title, row.hiddenCount),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(start = 48.dp, top = 2.dp, bottom = 10.dp)
                                        .clickable { expandedRoots = expandedRoots + row.rootId }
                                )
                                is CommentRowItem.ShowLess -> Text(
                                    text = stringResource(R.string.lebih_dikit),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(start = 48.dp, top = 2.dp, bottom = 10.dp)
                                        .clickable { expandedRoots = expandedRoots - row.rootId }
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
    replyToUsername: String?,
    isHighlighted: Boolean,
    onUserClick: (String) -> Unit,
    onReplyClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val myId = remember { com.textsocial.app.di.ServiceLocator.encryptedPreferencesManager.getUserId() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val startPadding = if (isReply) 48.dp else 16.dp
    val highlightColor by animateColorAsState(
        targetValue = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        label = "comment_highlight"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(highlightColor)
            .padding(start = startPadding, end = 14.dp, top = 8.dp, bottom = 8.dp)
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
                    avatarUrl = comment.avatarUrl,
                    size = if (isReply) AvatarSize.COMPACT else AvatarSize.COMPACT
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = comment.displayName ?: comment.username,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (comment.isVerified) {
                    Spacer(modifier = Modifier.width(3.dp))
                    VerifiedBadge(size = 13.dp)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = com.textsocial.app.util.TimeUtils.timeAgoShort(context, comment.createdAt),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
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

        if (isReply && replyToUsername != null) {
            Text(
                text = stringResource(R.string.reply_title, replyToUsername),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 42.dp, bottom = 2.dp)
            )
        }

        LinkTextComponent(
            text = comment.text,
            style = TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp
            ),
            modifier = Modifier.padding(start = 42.dp),
            onMentionClick = { username ->
                coroutineScope.launch {
                    val result = com.textsocial.app.di.ServiceLocator.userRepository.getProfileByUsername(username)
                    result.onSuccess { mentionedUser -> onUserClick(mentionedUser.id) }
                }
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

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
                text = stringResource(R.string.reply_tit),
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