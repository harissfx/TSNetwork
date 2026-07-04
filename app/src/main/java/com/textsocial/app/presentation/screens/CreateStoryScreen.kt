package com.textsocial.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textsocial.app.presentation.viewmodel.StoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStoryScreen(
    viewModel: StoryViewModel,
    onNavigateBack: () -> Unit
) {
    // Reset harus terjadi SEBELUM collectAsState membaca nilai isFinished, supaya
    // sisa nilai true dari sesi sebelumnya tidak sempat memicu onNavigateBack() lagi
    // di composisi pertama layar ini.
    remember(Unit) { viewModel.resetComposerState() }

    val text by viewModel.storyText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isFinished by viewModel.isFinished.collectAsState()

    val maxStoryChars = 280
    val charsRemaining = maxStoryChars - text.length

    LaunchedEffect(isFinished) {
        if (isFinished) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Story", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.createStory() },
                        enabled = text.isNotBlank() && !isLoading,
                        modifier = Modifier.padding(end = 8.dp).testTag("publish_story_button")
                    ) {
                        Text("Share")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
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
                    .padding(24.dp)
            ) {
                TextField(
                    value = text,
                    onValueChange = { viewModel.onStoryTextChange(it) },
                    placeholder = { Text("What is on your mind? Up to 280 characters of text...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("story_text_field"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 32.sp
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$charsRemaining characters left",
                        fontSize = 13.sp,
                        color = if (charsRemaining < 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )

                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}