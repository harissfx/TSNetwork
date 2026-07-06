package com.textsocial.app.presentation.screens
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.textsocial.app.presentation.components.UserAvatarComponent
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

    LaunchedEffect(currentStoryIndex, stories.size) {
        if (stories.isNotEmpty() && currentStoryIndex < stories.size) {
            val activeStory = stories[currentStoryIndex]
            viewModel.markStoryAsViewed(activeStory.id, activeStory.userId)

            progress = 0f
            while (progress < 1.0f) {
                delay(50)
                progress += 0.01f
            }
            if (currentStoryIndex < stories.size - 1) {
                currentStoryIndex++
            } else {
                onNavigateBack()
            }
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            if (stories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No active stories", color = Color.White)
                }
            } else if (currentStoryIndex < stories.size) {
                val story = stories[currentStoryIndex]
                val myId = remember { com.textsocial.app.di.ServiceLocator.encryptedPreferencesManager.getUserId() }
                val isOwnStory = story.userId == myId

                // Story Content Viewport
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Progress Indicators Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        stories.forEachIndexed { index, _ ->
                            val indicatorProgress = when {
                                index < currentStoryIndex -> 1.0f
                                index == currentStoryIndex -> progress
                                else -> 0.0f
                            }
                            LinearProgressIndicator(
                                progress = { indicatorProgress },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f),
                            )
                        }
                    }

                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatarComponent(
                                username = story.username,
                                avatarColor = story.avatarColor,
                                size = AvatarSize.COMPACT
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = story.username,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "${com.textsocial.app.util.TimeUtils.timeAgoShort(context, story.createdAt)} · ${com.textsocial.app.util.TimeUtils.storyTimeLeft(context, story.expiresAt)}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (story.userId == myId) {
                                IconButton(onClick = {
                                    viewModel.deleteStory(story.id)
                                    if (currentStoryIndex < stories.size - 1) {
                                        currentStoryIndex++
                                    } else {
                                        onNavigateBack()
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete story", tint = Color.Red)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.Default.Close, contentDescription = "Close stories", tint = Color.White)
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
                                        if (currentStoryIndex > 0) currentStoryIndex--
                                    } else {
                                        if (currentStoryIndex < stories.size - 1) currentStoryIndex++
                                        else onNavigateBack()
                                    }
                                })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = story.text,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            lineHeight = 34.sp,
                            modifier = Modifier.padding(24.dp)
                        )
                    }

                    if (isOwnStory) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                    .clickable { showViewersSheet = true }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RemoveRedEye,
                                    contentDescription = "Viewers icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.view_story, story.views.size),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
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

                            if (story.views.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No views recorded yet.",
                                        color = MaterialTheme.colorScheme.outline,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    story.views.forEach { viewer ->
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