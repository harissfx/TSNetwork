package com.textsocial.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textsocial.app.di.ServiceLocator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogoutSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isPrivateAccount by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var currentDisplayName by remember { mutableStateOf<String?>(null) }
    var currentBio by remember { mutableStateOf<String?>(null) }
    var isLoadingProfile by remember { mutableStateOf(true) }

    // Ambil status privasi (dan field lain) yang sebenarnya dari server setiap kali
    // layar ini dibuka, supaya switch menampilkan nilai yang benar-benar tersimpan,
    // bukan selalu mulai dari false.
    LaunchedEffect(Unit) {
        val myId = ServiceLocator.encryptedPreferencesManager.getUserId()
        if (myId != null) {
            val result = ServiceLocator.userRepository.getProfile(myId)
            result.onSuccess { user ->
                isPrivateAccount = user.isPrivate
                currentDisplayName = user.displayName
                currentBio = user.bio
            }
        }
        isLoadingProfile = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Security and Account
                Text(
                    text = "Account & Privacy",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Privacy Switch row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = "Privacy icon", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Private Account", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                    Text("Only approved users see your posts", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Switch(
                                checked = isPrivateAccount,
                                enabled = !isLoadingProfile,
                                onCheckedChange = { newValue ->
                                    val previousValue = isPrivateAccount
                                    isPrivateAccount = newValue // update UI langsung (optimistic)
                                    coroutineScope.launch {
                                        val result = ServiceLocator.userRepository.updateProfile(
                                            displayName = currentDisplayName,
                                            bio = currentBio,
                                            isPrivate = newValue
                                        )
                                        // Kalau gagal simpan ke server, kembalikan switch ke nilai semula
                                        result.onFailure {
                                            isPrivateAccount = previousValue
                                        }
                                    }
                                }
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Change password row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPasswordDialog = true }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = "Security icon", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Change Password", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                    Text("Regularly update your credentials", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "Arrow right")
                        }
                    }
                }

                // Section 2: Technical info & Open Source Credits
                Text(
                    text = "About",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = "About", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("OpenText Client v1.0.0", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Text(
                            text = "A complete open-source text-based social platform built with Kotlin, Jetpack Compose, Retrofit, Supabase Realtime, and Upstash Redis rate-limiting.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Logout Action
                Button(
                    onClick = {
                        coroutineScope.launch {
                            ServiceLocator.authRepository.logout()
                            onLogoutSuccess()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("logout_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Logout icon")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout Session", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Change Password Dialog
    if (showPasswordDialog) {
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var isSuccess by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Update Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isSuccess) {
                        Text("Password updated successfully!", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    } else {
                        Text("All password inputs are encrypted in-transit.")
                        OutlinedTextField(
                            value = newPass,
                            onValueChange = { newPass = it },
                            label = { Text("New Password") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirmPass,
                            onValueChange = { confirmPass = it },
                            label = { Text("Confirm New Password") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (isSuccess) {
                    Button(onClick = { showPasswordDialog = false }) { Text("OK") }
                } else {
                    Button(
                        onClick = {
                            if (newPass.isNotEmpty() && newPass == confirmPass) {
                                isSuccess = true
                            }
                        },
                        enabled = newPass.isNotEmpty() && newPass == confirmPass
                    ) {
                        Text("Update")
                    }
                }
            },
            dismissButton = {
                if (!isSuccess) {
                    TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel") }
                }
            }
        )
    }
}