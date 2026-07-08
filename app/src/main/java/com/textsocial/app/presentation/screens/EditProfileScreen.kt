package com.textsocial.app.presentation.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.textsocial.app.R
import com.textsocial.app.presentation.components.AvatarSize
import com.textsocial.app.presentation.components.UserAvatarComponent
import com.textsocial.app.presentation.viewmodel.ProfileViewModel
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit
) {
    val bioText by viewModel.bioText.collectAsState()
    val displayNameText by viewModel.displayNameText.collectAsState()
    val usernameText by viewModel.usernameText.collectAsState()
    val isUsernameSaving by viewModel.isUsernameSaving.collectAsState()
    val usernameError by viewModel.usernameError.collectAsState()
    val usernameSaveSuccess by viewModel.usernameSaveSuccess.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val user by viewModel.user.collectAsState()
    val isAvatarUploading by viewModel.isAvatarUploading.collectAsState()
    val avatarUploadError by viewModel.avatarUploadError.collectAsState()
    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val compressed = compressImageForAvatar(context, uri)
            if (compressed != null) {
                viewModel.uploadAvatar(compressed)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edits_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.updateProfile {
                                onNavigateBack()
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.testTag("save_profile_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save profile")
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
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(AvatarSize.LARGE.sizeDp)
                            .clickable(enabled = !isAvatarUploading) {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                            .testTag("change_avatar_button")
                    ) {
                        UserAvatarComponent(
                            username = user?.username ?: "",
                            avatarColor = user?.avatarColor ?: "#3F51B5",
                            avatarUrl = user?.avatarUrl,
                            size = AvatarSize.LARGE
                        )

                        if (isAvatarUploading) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 6.dp, y = 6.dp)
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Ganti foto profil",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.ava_title),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                if (avatarUploadError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = avatarUploadError ?: "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = usernameText,
                    onValueChange = { viewModel.onUsernameChange(it) },
                    label = { Text("Username") },
                    leadingIcon = { Text("@", color = MaterialTheme.colorScheme.outline) },
                    isError = usernameError != null,
                    supportingText = {
                        when {
                            usernameError != null -> Text(
                                usernameError ?: "",
                                color = MaterialTheme.colorScheme.error
                            )
                            usernameSaveSuccess -> Text(
                                "Username berhasil diubah",
                                color = MaterialTheme.colorScheme.primary
                            )
                            else -> Text(stringResource(R.string.war_ava))
                        }
                    },
                    trailingIcon = {
                        if (isUsernameSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(
                                onClick = { viewModel.updateUsername() },
                                enabled = usernameText.trim().isNotEmpty() && usernameText.trim() != (user?.username ?: "")
                            ) {
                                Text(stringResource(R.string.sv_ava))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_username"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = displayNameText,
                    onValueChange = { viewModel.onDisplayNameChange(it) },
                    label = { Text(stringResource(R.string.displayname_title))},
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_display_name"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = bioText,
                    onValueChange = { viewModel.onBioChange(it) },
                    label = { Text(stringResource(R.string.displaybio_title))},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .testTag("edit_bio"),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

private fun compressImageForAvatar(context: Context, uri: Uri, maxDimension: Int = 640): ByteArray? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val original = inputStream.use { BitmapFactory.decodeStream(it) } ?: return null

        val scale = maxDimension.toFloat() / maxOf(original.width, original.height)
        val bitmap = if (scale < 1f) {
            val targetWidth = (original.width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (original.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(original, targetWidth, targetHeight, true)
        } else {
            original
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        outputStream.toByteArray()
    } catch (e: Exception) {
        null
    }
}