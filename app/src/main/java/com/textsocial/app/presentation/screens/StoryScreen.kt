package com.textsocial.app.presentation.screens
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.textsocial.app.R
import com.textsocial.app.presentation.components.AvatarSize
import com.textsocial.app.presentation.components.StoryStyleOptions
import com.textsocial.app.presentation.components.UserAvatarComponent
import com.textsocial.app.presentation.components.VerifiedBadge
import com.textsocial.app.presentation.viewmodel.StoryViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen(
    viewModel: StoryViewModel,
    onNavigateBack: () -> Unit
) {
    val stories by viewModel.stories.collectAsState()
    val context = LocalContext.current
    val initialIndex by viewModel.selectedStoryIndex.collectAsState()
    var currentStoryIndex by remember(initialIndex) { mutableStateOf(initialIndex) }
    var progress by remember { mutableStateOf(0f) }
    var showViewersSheet by remember { mutableStateOf(false) }

    fun goToStory(newIndex: Int) {
        progress = 0f
        showViewersSheet = false
        currentStoryIndex = newIndex
    }

    LaunchedEffect(currentStoryIndex, stories.size) {
        if (stories.isNotEmpty() && currentStoryIndex < stories.size) {
            val activeStory = stories[currentStoryIndex]
            viewModel.markStoryAsViewed(activeStory.id, activeStory.userId)

            while (progress < 1.0f) {
                delay(50)
                progress += 0.02f
            }
            if (currentStoryIndex < stories.size - 1) {
                goToStory(currentStoryIndex + 1)
            } else {
                onNavigateBack()
            }
        }
    }

    Scaffold { innerPadding ->
        val currentBackgroundColor = if (stories.isNotEmpty() && currentStoryIndex < stories.size) {
            StoryStyleOptions.parseColorOrDefault(stories[currentStoryIndex].backgroundColor, Color.Black)
        } else {
            Color.Black
        }
        val overlayColor = if (stories.isNotEmpty() && currentStoryIndex < stories.size) {
            StoryStyleOptions.readableOverlayColor(stories[currentStoryIndex].backgroundColor)
        } else {
            Color.White
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(currentBackgroundColor)
        ) {
            if (stories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No active stories", color = Color.White)
                }
            } else if (currentStoryIndex < stories.size) {
                val story = stories[currentStoryIndex]
                val myId = remember { com.textsocial.app.di.ServiceLocator.encryptedPreferencesManager.getUserId() }
                val myUsername = remember { com.textsocial.app.di.ServiceLocator.encryptedPreferencesManager.getUsername() }
                val isOwnStory = story.userId == myId

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    var groupStart = currentStoryIndex
                    while (groupStart > 0 && stories[groupStart - 1].userId == story.userId) groupStart--
                    var groupEnd = currentStoryIndex
                    while (groupEnd < stories.size - 1 && stories[groupEnd + 1].userId == story.userId) groupEnd++
                    val indexWithinGroup = currentStoryIndex - groupStart

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (i in groupStart..groupEnd) {
                            val posInGroup = i - groupStart
                            val indicatorProgress = when {
                                posInGroup < indexWithinGroup -> 1.0f
                                posInGroup == indexWithinGroup -> progress.coerceIn(0f, 1f)
                                else -> 0.0f
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(overlayColor.copy(alpha = 0.3f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(indicatorProgress)
                                        .background(overlayColor)
                                )
                            }
                        }
                    }

                    AnimatedContent(
                        targetState = currentStoryIndex,
                        transitionSpec = {
                            val fromUserId = stories.getOrNull(initialState)?.userId
                            val toUserId = stories.getOrNull(targetState)?.userId
                            val samePerson = fromUserId != null && fromUserId == toUserId
                            if (samePerson) {
                                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            } else if (targetState > initialState) {
                                (slideInHorizontally(tween(220)) { width -> width } + fadeIn(tween(220)))
                                    .togetherWith(slideOutHorizontally(tween(220)) { width -> -width } + fadeOut(tween(220)))
                            } else {
                                (slideInHorizontally(tween(220)) { width -> -width } + fadeIn(tween(220)))
                                    .togetherWith(slideOutHorizontally(tween(220)) { width -> width } + fadeOut(tween(220)))
                            }.using(SizeTransform(clip = false))
                        },
                        modifier = Modifier.weight(1f),
                        label = "story_transition"
                    ) { animatedIndex ->
                        if (animatedIndex < stories.size) {
                            val animatedStory = stories[animatedIndex]
                            val isAnimatedOwnStory = animatedStory.userId == myId
                            val animatedOverlayColor = StoryStyleOptions.readableOverlayColor(animatedStory.backgroundColor)
                            val animatedTextColor = StoryStyleOptions.parseColorOrDefault(animatedStory.textColor, Color.White)
                            val animatedFontFamily = StoryStyleOptions.fontFamilyForKey(animatedStory.fontFamily)

                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        UserAvatarComponent(
                                            username = animatedStory.username,
                                            avatarColor = animatedStory.avatarColor,
                                            avatarUrl = animatedStory.avatarUrl,
                                            size = AvatarSize.COMPACT
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = animatedStory.username,
                                                    color = animatedOverlayColor,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                                if (animatedStory.isVerified) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    VerifiedBadge(size = 14.dp, tint = animatedOverlayColor)
                                                }
                                            }
                                            Text(
                                                text = "${com.textsocial.app.util.TimeUtils.timeAgoShort(context, animatedStory.createdAt)} · ${com.textsocial.app.util.TimeUtils.storyTimeLeft(context, animatedStory.expiresAt)}",
                                                color = animatedOverlayColor.copy(alpha = 0.7f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isAnimatedOwnStory) {
                                            IconButton(onClick = {
                                                viewModel.deleteStory(animatedStory.id)
                                                if (currentStoryIndex < stories.size - 1) {
                                                    goToStory(currentStoryIndex + 1)
                                                } else {
                                                    onNavigateBack()
                                                }
                                            }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete story", tint = Color.Red)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        IconButton(onClick = onNavigateBack) {
                                            Icon(Icons.Default.Close, contentDescription = "Close stories", tint = animatedOverlayColor)
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .pointerInput(currentStoryIndex, stories.size) {
                                            detectTapGestures(onTap = { offset ->
                                                if (offset.x < size.width / 2f) {
                                                    if (currentStoryIndex > 0) goToStory(currentStoryIndex - 1)
                                                } else {
                                                    if (currentStoryIndex < stories.size - 1) goToStory(currentStoryIndex + 1)
                                                    else onNavigateBack()
                                                }
                                            })
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val storyFontSize = when {
                                        animatedStory.text.length <= 60 -> 24.sp
                                        animatedStory.text.length <= 140 -> 20.sp
                                        animatedStory.text.length <= 220 -> 17.sp
                                        else -> 15.sp
                                    }
                                    val storyLineHeight = when {
                                        animatedStory.text.length <= 60 -> 34.sp
                                        animatedStory.text.length <= 140 -> 28.sp
                                        animatedStory.text.length <= 220 -> 24.sp
                                        else -> 21.sp
                                    }
                                    Text(
                                        text = animatedStory.text,
                                        color = animatedTextColor,
                                        fontFamily = animatedFontFamily,
                                        fontSize = storyFontSize,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        lineHeight = storyLineHeight,
                                        modifier = Modifier.padding(24.dp)
                                    )
                                }

                                if (isAnimatedOwnStory) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .background(animatedOverlayColor.copy(alpha = 0.15f), CircleShape)
                                                .clickable { showViewersSheet = true }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RemoveRedEye,
                                                contentDescription = "Viewers icon",
                                                tint = animatedOverlayColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(
                                                    R.string.view_story,
                                                    animatedStory.views.count { it != myUsername }
                                                ),
                                                color = animatedOverlayColor,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showViewersSheet && isOwnStory,
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.story_view),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                IconButton(onClick = { showViewersSheet = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close sheet")
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            val otherViewers = remember(story.views, myUsername) {
                                story.views.filterNot { it == myUsername }
                            }

                            if (otherViewers.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.no_view),
                                        color = MaterialTheme.colorScheme.outline,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    otherViewers.forEach { viewer ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            UserAvatarComponent(
                                                username = viewer,
                                                avatarColor = "#607D8B",
                                                size = AvatarSize.COMPACT
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = viewer,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}