package com.textsocial.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.textsocial.app.R
import com.textsocial.app.presentation.components.StoryStyleOptions
import com.textsocial.app.presentation.viewmodel.StoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStoryScreen(
    viewModel: StoryViewModel,
    onNavigateBack: () -> Unit
) {

    remember(Unit) { viewModel.resetComposerState() }

    val text by viewModel.storyText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isFinished by viewModel.isFinished.collectAsState()
    val backgroundHex by viewModel.storyBackgroundColor.collectAsState()
    val textColorHex by viewModel.storyTextColor.collectAsState()
    val fontKey by viewModel.storyFontFamily.collectAsState()

    val maxStoryChars = 280
    val charsRemaining = maxStoryChars - text.length

    val backgroundColor = StoryStyleOptions.parseColorOrDefault(backgroundHex, Color.Black)
    val textColor = StoryStyleOptions.parseColorOrDefault(textColorHex, Color.White)
    val fontFamily = StoryStyleOptions.fontFamilyForKey(fontKey)
    val overlayColor = StoryStyleOptions.readableOverlayColor(backgroundHex)

    LaunchedEffect(isFinished) {
        if (isFinished) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.story_title), fontWeight = FontWeight.Bold, color = overlayColor) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back", tint = overlayColor)
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.createStory() },
                        enabled = text.isNotBlank() && !isLoading,
                        modifier = Modifier.padding(end = 8.dp).testTag("publish_story_button")
                    ) {
                        Text(stringResource(R.string.share_tap))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(backgroundColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                TextField(
                    value = text,
                    onValueChange = { viewModel.onStoryTextChange(it) },
                    placeholder = { Text(stringResource(R.string.story_desc), color = textColor.copy(alpha = 0.5f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp)
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .testTag("story_text_field"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = textColor,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    ),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 32.sp,
                        fontFamily = fontFamily,
                        color = textColor
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.limit_karakter, charsRemaining),
                        fontSize = 13.sp,
                        color = if (charsRemaining < 30) MaterialTheme.colorScheme.error else overlayColor.copy(alpha = 0.7f)
                    )

                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = overlayColor)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.story_background_label),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = overlayColor,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("story_bg_color_row")
                ) {
                    items(StoryStyleOptions.backgroundPresets) { hex ->
                        val swatchColor = StoryStyleOptions.parseColorOrDefault(hex, Color.Black)
                        val isSelected = hex.equals(backgroundHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) overlayColor else overlayColor.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .selectable(
                                    selected = isSelected,
                                    onClick = { viewModel.onStoryBackgroundColorChange(hex) }
                                )
                                .testTag("bg_swatch_$hex"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = StoryStyleOptions.readableOverlayColor(hex),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.story_text_color_label),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = overlayColor,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("story_text_color_row")
                ) {
                    items(StoryStyleOptions.textColorPresets) { hex ->
                        val swatchColor = StoryStyleOptions.parseColorOrDefault(hex, Color.White)
                        val isSelected = hex.equals(textColorHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) overlayColor else overlayColor.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .selectable(
                                    selected = isSelected,
                                    onClick = { viewModel.onStoryTextColorChange(hex) }
                                )
                                .testTag("text_swatch_$hex"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = StoryStyleOptions.readableOverlayColor(hex),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.story_font_label),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = overlayColor,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .testTag("story_font_row")
                ) {
                    items(StoryStyleOptions.fontOptions) { option ->
                        val isSelected = option.key == fontKey
                        val label = when (option.key) {
                            "serif" -> stringResource(R.string.font_option_serif)
                            "monospace" -> stringResource(R.string.font_option_monospace)
                            "cursive" -> stringResource(R.string.font_option_cursive)
                            else -> stringResource(R.string.font_option_default)
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) overlayColor.copy(alpha = 0.9f) else overlayColor.copy(alpha = 0.12f),
                            modifier = Modifier
                                .selectable(
                                    selected = isSelected,
                                    onClick = { viewModel.onStoryFontFamilyChange(option.key) }
                                )
                                .testTag("font_option_${option.key}")
                        ) {
                            Text(
                                text = label,
                                fontFamily = option.fontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isSelected) backgroundColor else overlayColor,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}