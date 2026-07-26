package com.codeshell.app.ui.settings

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeshell.app.MainActivity
import com.codeshell.app.R
import com.codeshell.app.data.model.ChatInterfaceStyle
import com.codeshell.app.ui.chat.rememberChatColors
import com.codeshell.app.ui.components.CodeShellDialog
import com.codeshell.app.ui.components.CodeShellDialogAction
import com.codeshell.app.ui.components.CodeShellDialogChoice
import com.codeshell.app.ui.components.CodeShellChoiceDivider
import com.codeshell.app.ui.components.OfflineBanner
import com.codeshell.app.ui.components.ProfilePhotoCropDialog
import com.codeshell.app.ui.theme.EditorBackground
import com.codeshell.app.ui.theme.EditorDefaultText
import com.codeshell.app.ui.theme.TopBarBackground
import com.codeshell.app.util.ChatInactivityTimeout
import com.codeshell.app.util.ChatMessageFontScale
import com.codeshell.app.util.filterFourDigitCode
import com.codeshell.app.util.findActivity
import com.codeshell.app.ui.util.AdaptiveWidthContainer
import com.codeshell.app.ui.util.TerminalInactivityEffect
import com.codeshell.app.ui.util.rememberTerminalActivity
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onSignedOut: () -> Unit,
    onInactivityTimeout: () -> Unit = onNavigateBack,
    onOpenVault: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as Application
    val context = LocalContext.current
    val activity = context.findActivity() as? MainActivity
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(app) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chatColors = rememberChatColors()
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showSetupAccessCodeDialog by remember { mutableStateOf(false) }
    var showDisableAccessCodeDialog by remember { mutableStateOf(false) }
    var showChangeAccessCodeDialog by remember { mutableStateOf(false) }
    var setupPin by remember { mutableStateOf("") }
    var setupConfirm by remember { mutableStateOf("") }
    var disablePin by remember { mutableStateOf("") }
    var changeCurrentPin by remember { mutableStateOf("") }
    var changeNewPin by remember { mutableStateOf("") }
    var changeConfirmPin by remember { mutableStateOf("") }
    var accessCodeDialogError by remember { mutableStateOf<String?>(null) }
    var profileExpanded by remember { mutableStateOf(false) }
    var securityExpanded by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var showBlockedUsersDialog by remember { mutableStateOf(false) }
    var pendingProfilePhotoCropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var profilePhotoWarning by remember { mutableStateOf<String?>(null) }
    var showProfilePhotoViewer by remember { mutableStateOf(false) }
    var externalProfilePhotoFlowActive by remember { mutableStateOf(false) }
    val setupPinFocusRequester = remember { FocusRequester() }
    val setupConfirmFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val (bumpActivity, activityGeneration) = rememberTerminalActivity()

    TerminalInactivityEffect(
        onInactivityTimeout,
        activityGeneration,
        uiState.deleteAccountPassword,
        blocked = showDeleteAccountDialog ||
            showSetupAccessCodeDialog ||
            showDisableAccessCodeDialog ||
            showChangeAccessCodeDialog ||
            showBlockedUsersDialog ||
            pendingProfilePhotoCropUri != null ||
            externalProfilePhotoFlowActive ||
            scrollState.isScrollInProgress ||
            uiState.isDeletingAccount ||
            uiState.isSaving,
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        activity?.applySuppressHideOnHome(false)
        viewModel.onNewTerminalAlertPermissionResult(granted)
    }
    val profilePhotoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        externalProfilePhotoFlowActive = false
        activity?.applySuppressHideOnHome(false)
        uri?.let {
            profilePhotoWarning = null
            pendingProfilePhotoCropUri = it
        }
    }

    fun onNewTerminalAlertToggle(enabled: Boolean) {
        if (!enabled) {
            viewModel.setNewTerminalAlert(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.setNewTerminalAlert(true)
            } else {
                activity?.applySuppressHideOnHome(true)
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.setNewTerminalAlert(true)
        }
    }

    val appearanceBringIntoViewRequester = remember { BringIntoViewRequester() }
    val accountBringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(appearanceExpanded) {
        if (appearanceExpanded) {
            delay(100)
            appearanceBringIntoViewRequester.bringIntoView()
        }
    }

    LaunchedEffect(accountExpanded) {
        if (accountExpanded) {
            delay(100)
            accountBringIntoViewRequester.bringIntoView()
        }
    }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) bumpActivity()
    }

    AdaptiveWidthContainer {
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = EditorBackground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TopBarBackground,
                    titleContentColor = EditorDefaultText,
                    navigationIconContentColor = EditorDefaultText
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OfflineBanner(colors = chatColors)
            Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text(
                text = uiState.displayId.ifBlank { "..." },
                style = MaterialTheme.typography.headlineSmall,
                color = EditorDefaultText,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            uiState.successMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            }
            uiState.errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }
            profilePhotoWarning?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }

            HorizontalDivider(color = EditorDefaultText.copy(alpha = 0.1f))

            SettingsCollapsibleSection(
                title = stringResource(R.string.settings_section_profile),
                expanded = profileExpanded,
                onToggle = { profileExpanded = !profileExpanded },
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
            ) {
                ProfilePictureSettingsSection(
                    avatarUrl = uiState.profile?.avatarUrl,
                    viewingEnabled = uiState.profilePicturesEnabled,
                    isSaving = uiState.isSavingProfilePhoto,
                    onPickPhoto = {
                        externalProfilePhotoFlowActive = true
                        activity?.applySuppressHideOnHome(true)
                        profilePhotoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemovePhoto = viewModel::removeProfilePhoto,
                    onAvatarClick = { showProfilePhotoViewer = true },
                    onViewingEnabledChange = viewModel::setProfilePicturesEnabled,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            SettingsCollapsibleSection(
                title = stringResource(R.string.settings_section_security),
                expanded = securityExpanded,
                onToggle = { securityExpanded = !securityExpanded },
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    SettingsToggleRow(
                        title = stringResource(R.string.secure_terminal_only),
                        description = stringResource(R.string.secure_terminal_only_desc),
                        checked = uiState.secureTerminalOnly,
                        onCheckedChange = viewModel::setSecureTerminalOnly,
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.chat_access_code),
                        description = stringResource(R.string.chat_access_code_desc),
                        checked = uiState.chatAccessCodeEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                setupPin = ""
                                setupConfirm = ""
                                accessCodeDialogError = null
                                showSetupAccessCodeDialog = true
                            } else if (uiState.chatAccessCodeEnabled) {
                                disablePin = ""
                                accessCodeDialogError = null
                                showDisableAccessCodeDialog = true
                            }
                        },
                    )
                    if (uiState.chatAccessCodeEnabled) {
                        TextButton(
                            onClick = {
                                changeCurrentPin = ""
                                changeNewPin = ""
                                changeConfirmPin = ""
                                accessCodeDialogError = null
                                showChangeAccessCodeDialog = true
                            },
                        ) {
                            Text(stringResource(R.string.chat_access_code_change))
                        }
                    }
                    SettingsToggleRow(
                        title = stringResource(R.string.hide_on_home),
                        description = stringResource(R.string.hide_on_home_desc),
                        checked = uiState.hideOnHomePress,
                        onCheckedChange = viewModel::setHideOnHomePress,
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.terminate_on_lock),
                        description = stringResource(R.string.terminate_on_lock_desc),
                        checked = uiState.terminateOnLock,
                        onCheckedChange = viewModel::setTerminateOnLock,
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.close_chat_on_inactivity),
                        description = stringResource(R.string.close_chat_on_inactivity_desc),
                        checked = uiState.closeChatOnInactivity,
                        onCheckedChange = viewModel::setCloseChatOnInactivity,
                    )
                    AnimatedVisibility(visible = uiState.closeChatOnInactivity) {
                        ChatInactivityTimeoutPicker(
                            selectedSeconds = uiState.chatInactivityTimeoutSeconds,
                            onSelected = viewModel::setChatInactivityTimeoutSeconds,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                    SettingsToggleRow(
                        title = stringResource(R.string.new_terminal_alert),
                        description = stringResource(R.string.new_terminal_alert_desc),
                        checked = uiState.newTerminalAlert,
                        onCheckedChange = ::onNewTerminalAlertToggle,
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.share_panel_enabled),
                        description = stringResource(R.string.share_panel_enabled_desc),
                        checked = uiState.sharePanelEnabled,
                        onCheckedChange = viewModel::setSharePanelEnabled,
                    )

                    TextButton(
                        onClick = { showBlockedUsersDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.blocked_users))
                    }
                }
            }

            SettingsCollapsibleSection(
                title = stringResource(R.string.settings_section_appearance),
                expanded = appearanceExpanded,
                onToggle = { appearanceExpanded = !appearanceExpanded },
                modifier = Modifier
                    .padding(top = 16.dp)
                    .bringIntoViewRequester(appearanceBringIntoViewRequester),
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = stringResource(R.string.chat_interface_style),
                        style = MaterialTheme.typography.titleMedium,
                        color = EditorDefaultText,
                    )
                    Text(
                        text = stringResource(R.string.chat_interface_style_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = EditorDefaultText.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    ChatInterfaceStyleDropdown(
                        selectedStyle = uiState.chatInterfaceStyle,
                        onStyleSelected = viewModel::setChatInterfaceStyle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_message_font_scale),
                            style = MaterialTheme.typography.titleMedium,
                            color = EditorDefaultText,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(
                                R.string.chat_message_font_scale_value,
                                (uiState.chatMessageFontScale * 100).toInt(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = EditorDefaultText.copy(alpha = 0.8f),
                        )
                    }
                    Text(
                        text = stringResource(R.string.chat_message_font_scale_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = EditorDefaultText.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                    Slider(
                        value = uiState.chatMessageFontScale,
                        onValueChange = viewModel::setChatMessageFontScale,
                        valueRange = ChatMessageFontScale.MIN..ChatMessageFontScale.MAX,
                        steps = ChatMessageFontScale.SLIDER_STEPS,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SettingsCollapsibleSection(
                title = stringResource(R.string.settings_section_account),
                expanded = accountExpanded,
                onToggle = { accountExpanded = !accountExpanded },
                modifier = Modifier
                    .padding(top = 16.dp)
                    .bringIntoViewRequester(accountBringIntoViewRequester),
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(
                        onClick = onOpenVault,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Vault")
                    }
                    TextButton(
                        onClick = { showDeleteAccountDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSaving,
                    ) {
                        Text(
                            text = stringResource(R.string.delete_account),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(
                        onClick = { viewModel.signOut(onSignedOut) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.sign_out))
                    }
                }
            }
            }
        }
    }

    if (showSetupAccessCodeDialog) {
        LaunchedEffect(showSetupAccessCodeDialog) {
            if (showSetupAccessCodeDialog) {
                setupPinFocusRequester.requestFocus()
            }
        }
        LaunchedEffect(setupPin) {
            if (setupPin.length == 4) {
                setupConfirmFocusRequester.requestFocus()
            }
        }
        AlertDialog(
            onDismissRequest = {
                showSetupAccessCodeDialog = false
                setupPin = ""
                setupConfirm = ""
                accessCodeDialogError = null
            },
            title = { Text(stringResource(R.string.chat_access_code_set)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = setupPin,
                        onValueChange = {
                            setupPin = filterFourDigitCode(it)
                            accessCodeDialogError = null
                        },
                        label = { Text(stringResource(R.string.chat_access_code_set)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(setupPinFocusRequester),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = EditorDefaultText,
                            unfocusedTextColor = EditorDefaultText,
                        )
                    )
                    OutlinedTextField(
                        value = setupConfirm,
                        onValueChange = {
                            setupConfirm = filterFourDigitCode(it)
                            accessCodeDialogError = null
                        },
                        label = { Text(stringResource(R.string.chat_access_code_confirm)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .focusRequester(setupConfirmFocusRequester),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = EditorDefaultText,
                            unfocusedTextColor = EditorDefaultText,
                        )
                    )
                    accessCodeDialogError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.enableChatAccessCode(setupPin, setupConfirm) { error ->
                            if (error == null) {
                                showSetupAccessCodeDialog = false
                                setupPin = ""
                                setupConfirm = ""
                                accessCodeDialogError = null
                            } else {
                                accessCodeDialogError = error
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.chat_access_code_set))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSetupAccessCodeDialog = false
                    setupPin = ""
                    setupConfirm = ""
                    accessCodeDialogError = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingProfilePhotoCropUri?.let { sourceUri ->
        ProfilePhotoCropDialog(
            sourceUri = sourceUri,
            onDismiss = { pendingProfilePhotoCropUri = null },
            onCropped = { croppedUri ->
                pendingProfilePhotoCropUri = null
                profilePhotoWarning = null
                viewModel.uploadProfilePhoto(croppedUri)
            },
            onTooLarge = {
                pendingProfilePhotoCropUri = null
                profilePhotoWarning = context.getString(R.string.profile_picture_too_large)
            },
        )
    }

    if (showProfilePhotoViewer && !uiState.profile?.avatarUrl.isNullOrBlank()) {
        Dialog(
            onDismissRequest = { showProfilePhotoViewer = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { showProfilePhotoViewer = false },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = uiState.profile?.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showDisableAccessCodeDialog) {
        AlertDialog(
            onDismissRequest = {
                showDisableAccessCodeDialog = false
                disablePin = ""
                accessCodeDialogError = null
            },
            title = { Text(stringResource(R.string.chat_access_code)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = disablePin,
                        onValueChange = {
                            disablePin = filterFourDigitCode(it)
                            accessCodeDialogError = null
                        },
                        label = { Text(stringResource(R.string.chat_access_code_current)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = EditorDefaultText,
                            unfocusedTextColor = EditorDefaultText,
                        )
                    )
                    accessCodeDialogError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disableChatAccessCode(disablePin) { error ->
                            if (error == null) {
                                showDisableAccessCodeDialog = false
                                disablePin = ""
                                accessCodeDialogError = null
                            } else {
                                accessCodeDialogError = error
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.chat_access_code))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDisableAccessCodeDialog = false
                    disablePin = ""
                    accessCodeDialogError = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showChangeAccessCodeDialog) {
        AlertDialog(
            onDismissRequest = {
                showChangeAccessCodeDialog = false
                changeCurrentPin = ""
                changeNewPin = ""
                changeConfirmPin = ""
                accessCodeDialogError = null
            },
            title = { Text(stringResource(R.string.chat_access_code_change)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = changeCurrentPin,
                        onValueChange = {
                            changeCurrentPin = filterFourDigitCode(it)
                            accessCodeDialogError = null
                        },
                        label = { Text(stringResource(R.string.chat_access_code_current)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = EditorDefaultText,
                            unfocusedTextColor = EditorDefaultText,
                        )
                    )
                    OutlinedTextField(
                        value = changeNewPin,
                        onValueChange = {
                            changeNewPin = filterFourDigitCode(it)
                            accessCodeDialogError = null
                        },
                        label = { Text(stringResource(R.string.chat_access_code_set)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = EditorDefaultText,
                            unfocusedTextColor = EditorDefaultText,
                        )
                    )
                    OutlinedTextField(
                        value = changeConfirmPin,
                        onValueChange = {
                            changeConfirmPin = filterFourDigitCode(it)
                            accessCodeDialogError = null
                        },
                        label = { Text(stringResource(R.string.chat_access_code_confirm)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = EditorDefaultText,
                            unfocusedTextColor = EditorDefaultText,
                        )
                    )
                    accessCodeDialogError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.changeChatAccessCode(changeCurrentPin, changeNewPin, changeConfirmPin) { error ->
                            if (error == null) {
                                showChangeAccessCodeDialog = false
                                changeCurrentPin = ""
                                changeNewPin = ""
                                changeConfirmPin = ""
                                accessCodeDialogError = null
                            } else {
                                accessCodeDialogError = error
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.chat_access_code_change))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showChangeAccessCodeDialog = false
                    changeCurrentPin = ""
                    changeNewPin = ""
                    changeConfirmPin = ""
                    accessCodeDialogError = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showBlockedUsersDialog) {
        CodeShellDialog(
            onDismiss = { showBlockedUsersDialog = false },
            title = stringResource(R.string.blocked_users),
            content = {
                if (uiState.blockedUsers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_blocked_users),
                        style = MaterialTheme.typography.bodyMedium,
                        color = EditorDefaultText.copy(alpha = 0.75f),
                    )
                } else {
                    uiState.blockedUsers.forEachIndexed { index, userId ->
                        if (index > 0) {
                            CodeShellChoiceDivider()
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = uiState.blockedUserLabels[userId] ?: userId,
                                color = EditorDefaultText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            CodeShellDialogAction(
                                label = stringResource(R.string.unblock),
                                onClick = { viewModel.unblockUser(userId) },
                            )
                        }
                    }
                }
            },
            actions = {
                CodeShellDialogAction(
                    label = stringResource(R.string.cancel),
                    onClick = { showBlockedUsersDialog = false },
                )
            },
        )
    }

    if (showDeleteAccountDialog) {
        CodeShellDialog(
            onDismiss = {
                if (!uiState.isSaving && !uiState.isDeletingAccount) {
                    showDeleteAccountDialog = false
                    viewModel.onDeleteAccountPasswordChange("")
                }
            },
            title = stringResource(R.string.delete_account),
            content = {
                Text(
                    text = stringResource(R.string.delete_account_confirm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EditorDefaultText.copy(alpha = 0.85f),
                )
                OutlinedTextField(
                    value = uiState.deleteAccountPassword,
                    onValueChange = viewModel::onDeleteAccountPasswordChange,
                    label = { Text(stringResource(R.string.delete_account_password_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !uiState.isSaving && !uiState.isDeletingAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = EditorDefaultText,
                        unfocusedTextColor = EditorDefaultText,
                        focusedBorderColor = EditorDefaultText.copy(alpha = 0.5f),
                        unfocusedBorderColor = EditorDefaultText.copy(alpha = 0.3f),
                        focusedLabelColor = EditorDefaultText.copy(alpha = 0.7f),
                        unfocusedLabelColor = EditorDefaultText.copy(alpha = 0.5f),
                    ),
                )
            },
            actions = {
                CodeShellDialogAction(
                    label = stringResource(R.string.cancel),
                    onClick = {
                        showDeleteAccountDialog = false
                        viewModel.onDeleteAccountPasswordChange("")
                    },
                )
                CodeShellDialogAction(
                    label = stringResource(R.string.delete),
                    onClick = {
                        viewModel.deleteAccount { onSignedOut() }
                    },
                    destructive = true,
                )
            },
        )
    }

    if (uiState.isDeletingAccount) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = EditorDefaultText)
                    Text(
                        text = stringResource(R.string.deleting_account),
                        color = EditorDefaultText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
    }
    }
}

@Composable
private fun ProfilePictureSettingsSection(
    avatarUrl: String?,
    viewingEnabled: Boolean,
    isSaving: Boolean,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onAvatarClick: () -> Unit,
    onViewingEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.profile_picture_section_desc),
            style = MaterialTheme.typography.bodySmall,
            color = EditorDefaultText.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(EditorDefaultText.copy(alpha = 0.08f))
                    .then(
                        if (!avatarUrl.isNullOrBlank()) {
                            Modifier.clickable(onClick = onAvatarClick)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = "[>_]",
                        color = EditorDefaultText.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                TextButton(
                    onClick = onPickPhoto,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            stringResource(
                                if (avatarUrl.isNullOrBlank()) {
                                    R.string.profile_picture_upload
                                } else {
                                    R.string.profile_picture_change
                                }
                            )
                        )
                    }
                }
                if (!avatarUrl.isNullOrBlank()) {
                    TextButton(
                        onClick = onRemovePhoto,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.profile_picture_remove))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsToggleRow(
            title = stringResource(R.string.profile_picture_viewing),
            description = stringResource(R.string.profile_picture_viewing_desc),
            checked = viewingEnabled,
            onCheckedChange = onViewingEnabledChange,
        )
    }
}

@Composable
private fun SettingsCollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = EditorDefaultText,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = EditorDefaultText,
            )
        }
        AnimatedVisibility(visible = expanded) {
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = EditorDefaultText,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = EditorDefaultText.copy(alpha = 0.7f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ChatInactivityTimeoutPicker(
    selectedSeconds: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.chat_inactivity_timeout),
            style = MaterialTheme.typography.bodyMedium,
            color = EditorDefaultText,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChatInactivityTimeout.OPTIONS_SECONDS.forEach { seconds ->
                FilterChip(
                    selected = selectedSeconds == seconds,
                    onClick = { onSelected(seconds) },
                    label = {
                        Text(stringResource(R.string.chat_inactivity_timeout_option, seconds))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        selectedLabelColor = EditorDefaultText,
                        labelColor = EditorDefaultText.copy(alpha = 0.8f),
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInterfaceStyleDropdown(
    selectedStyle: ChatInterfaceStyle,
    onStyleSelected: (ChatInterfaceStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = ChatInterfaceStyle.entries

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = chatInterfaceStyleLabel(selectedStyle),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = EditorDefaultText,
                unfocusedTextColor = EditorDefaultText,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { style ->
                DropdownMenuItem(
                    text = { Text(chatInterfaceStyleLabel(style)) },
                    onClick = {
                        expanded = false
                        onStyleSelected(style)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun chatInterfaceStyleLabel(style: ChatInterfaceStyle): String {
    return when (style) {
        ChatInterfaceStyle.DEFAULT -> stringResource(R.string.chat_interface_style_default)
        ChatInterfaceStyle.VINTAGE -> stringResource(R.string.chat_interface_style_vintage)
        ChatInterfaceStyle.IDE -> stringResource(R.string.chat_interface_style_ide)
    }
}
