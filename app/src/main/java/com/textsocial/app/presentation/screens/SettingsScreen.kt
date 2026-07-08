package com.textsocial.app.presentation.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.textsocial.app.R
import com.textsocial.app.di.ServiceLocator
import com.textsocial.app.util.LocaleManager
import com.textsocial.app.util.ThemeManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogoutSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isPrivateAccount by remember { mutableStateOf(false) }
    var hideFollowingList by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var currentDisplayName by remember { mutableStateOf<String?>(null) }
    var currentBio by remember { mutableStateOf<String?>(null) }
    var isLoadingProfile by remember { mutableStateOf(true) }
    var selectedLanguage by remember { mutableStateOf(LocaleManager.getSelectedLanguage(context)) }
    val selectedTheme by ThemeManager.themeMode

    LaunchedEffect(Unit) {
        val myId = ServiceLocator.encryptedPreferencesManager.getUserId()
        if (myId != null) {
            val result = ServiceLocator.userRepository.getProfile(myId)
            result.onSuccess { user ->
                isPrivateAccount = user.isPrivate
                hideFollowingList = user.hideFollowingList
                currentDisplayName = user.displayName
                currentBio = user.bio
            }
        }
        isLoadingProfile = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.go_back))
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
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.section_account_privacy),
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(stringResource(R.string.private_account_title), fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                    Text(stringResource(R.string.private_account_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Switch(
                                checked = isPrivateAccount,
                                enabled = !isLoadingProfile,
                                onCheckedChange = { newValue ->
                                    val previousValue = isPrivateAccount
                                    isPrivateAccount = newValue
                                    coroutineScope.launch {
                                        val result = ServiceLocator.userRepository.updateProfile(
                                            displayName = currentDisplayName,
                                            bio = currentBio,
                                            isPrivate = newValue
                                        )
                                        result.onFailure {
                                            isPrivateAccount = previousValue
                                        }
                                    }
                                }
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(stringResource(R.string.hide_following_list_title), fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                    Text(stringResource(R.string.hide_following_list_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Switch(
                                checked = hideFollowingList,
                                enabled = !isLoadingProfile,
                                onCheckedChange = { newValue ->
                                    val previousValue = hideFollowingList
                                    hideFollowingList = newValue
                                    coroutineScope.launch {
                                        val result = ServiceLocator.userRepository.updateFollowListPrivacy(newValue)
                                        result.onFailure {
                                            hideFollowingList = previousValue
                                        }
                                    }
                                }
                            )
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPasswordDialog = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(stringResource(R.string.change_password_title), fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                    Text(stringResource(R.string.change_password_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.section_language),
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLanguageDialog = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.language_row_title), fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text(stringResource(R.string.language_row_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }

                Text(
                    text = stringResource(R.string.section_appearance),
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showThemeDialog = true }
                            .testTag("theme_row")
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            val themeIcon = when (selectedTheme) {
                                ThemeManager.MODE_LIGHT -> Icons.Default.LightMode
                                ThemeManager.MODE_DARK -> Icons.Default.DarkMode
                                else -> Icons.Default.BrightnessMedium
                            }
                            Icon(themeIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.theme_row_title), fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text(stringResource(R.string.theme_row_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }

                Text(
                    text = stringResource(R.string.section_about),
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
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(stringResource(R.string.app_version_label), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Text(
                            text = stringResource(R.string.app_description),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        com.textsocial.app.util.PushNotificationManager.unregisterCurrentDeviceToken()
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
                    Icon(Icons.Default.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.logout_button), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showLanguageDialog) {
        val options = listOf(
            LocaleManager.LANG_SYSTEM to stringResource(R.string.language_option_system),
            LocaleManager.LANG_INDONESIAN to stringResource(R.string.language_option_indonesian),
            LocaleManager.LANG_ENGLISH to stringResource(R.string.language_option_english)
        )
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language_row_title)) },
            text = {
                Column {
                    options.forEach { (tag, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedLanguage == tag,
                                    onClick = {
                                        selectedLanguage = tag
                                        LocaleManager.setSelectedLanguage(context, tag)
                                        showLanguageDialog = false
                                        (context as? Activity)?.recreate()
                                    }
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLanguage == tag,
                                onClick = {
                                    selectedLanguage = tag
                                    LocaleManager.setSelectedLanguage(context, tag)
                                    showLanguageDialog = false
                                    (context as? Activity)?.recreate()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }

    if (showThemeDialog) {
        val themeOptions = listOf(
            ThemeManager.MODE_SYSTEM to stringResource(R.string.theme_option_system),
            ThemeManager.MODE_LIGHT to stringResource(R.string.theme_option_light),
            ThemeManager.MODE_DARK to stringResource(R.string.theme_option_dark)
        )
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.theme_row_title)) },
            text = {
                Column {
                    themeOptions.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedTheme == mode,
                                    onClick = {
                                        ThemeManager.setSelectedTheme(context, mode)
                                        showThemeDialog = false
                                    }
                                )
                                .testTag("theme_option_$mode")
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTheme == mode,
                                onClick = {
                                    ThemeManager.setSelectedTheme(context, mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(R.string.cancel_button))
                }
            }
        )
    }

    if (showPasswordDialog) {
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var isSuccess by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(stringResource(R.string.update_password_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isSuccess) {
                        Text(stringResource(R.string.password_updated_success), color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    } else {
                        Text(stringResource(R.string.password_encrypted_note))
                        OutlinedTextField(
                            value = newPass,
                            onValueChange = { newPass = it },
                            label = { Text(stringResource(R.string.new_password_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirmPass,
                            onValueChange = { confirmPass = it },
                            label = { Text(stringResource(R.string.confirm_password_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (isSuccess) {
                    Button(onClick = { showPasswordDialog = false }) { Text(stringResource(R.string.ok_button)) }
                } else {
                    Button(
                        onClick = {
                            if (newPass.isNotEmpty() && newPass == confirmPass) {
                                isSuccess = true
                            }
                        },
                        enabled = newPass.isNotEmpty() && newPass == confirmPass
                    ) {
                        Text(stringResource(R.string.update_button))
                    }
                }
            },
            dismissButton = {
                if (!isSuccess) {
                    TextButton(onClick = { showPasswordDialog = false }) { Text(stringResource(R.string.cancel_button)) }
                }
            }
        )
    }
}