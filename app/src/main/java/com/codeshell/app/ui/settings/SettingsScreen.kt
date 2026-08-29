package com.codeshell.app.ui.settings

import android.Manifest
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.app.ActivityCompat
import com.codeshell.app.MainActivity
import com.codeshell.app.R
import com.codeshell.app.data.model.ChatInterfaceStyle
import com.codeshell.app.data.model.VaultItem
import com.codeshell.app.data.preferences.AppPreferences
import com.codeshell.app.ui.chat.ChatAccessLockOverlay
import com.codeshell.app.ui.chat.ChatVaultPickerDialog
import com.codeshell.app.ui.chat.rememberAdaptiveChatTheme
import com.codeshell.app.ui.chat.rememberChatColors
import com.codeshell.app.ui.components.CodeShellDialog
import com.codeshell.app.ui.components.CodeShellDialogAction
import com.codeshell.app.ui.components.CodeShellDialogChoice
import com.codeshell.app.ui.components.CodeShellChoiceDivider
import com.codeshell.app.ui.components.OfflineBanner
import com.codeshell.app.ui.components.ProfilePhotoCropDialog
import com.codeshell.app.ui.theme.DisguiseTheme
import com.codeshell.app.ui.vault.PendingChatVaultPick
import com.codeshell.app.ui.vault.VaultAccessSession
import com.codeshell.app.ui.vault.VaultSaveState
import com.codeshell.app.ui.vault.VaultSaveTracker
import com.codeshell.app.ui.vault.VaultViewModel
import com.codeshell.app.util.ChatAccessCodeHasher
import com.codeshell.app.util.ChatInactivityTimeout
import com.codeshell.app.util.ChatMessageFontScale
import com.codeshell.app.util.filterFourDigitCode
import com.codeshell.app.util.findActivity
import com.codeshell.app.util.downloadFileToCache
import com.codeshell.app.ui.util.AdaptiveWidthContainer
import com.codeshell.app.ui.util.TerminalInactivityEffect
import com.codeshell.app.ui.util.rememberTerminalActivity
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EditorBackground: Color
    @Composable get() = MaterialTheme.colorScheme.background

private val EditorDefaultText: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

private val TopBarBackground: Color
    @Composable get() = MaterialTheme.colorScheme.surface

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    chatId: String? = null,
    openVaultPicker: Boolean = false,
    onNavigateBack: () -> Unit,
    onSignedOut: () -> Unit,
    onInactivityTimeout: () -> Unit = onNavigateBack,
    onOpenVault: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as Application
    val context = LocalContext.current
    val activity = context.findActivity() as? MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(app, chatId) }
    val vaultViewModel: VaultViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val vaultUiState by vaultViewModel.uiState.collectAsStateWithLifecycle()
    val vaultSaveState by VaultSaveTracker.state.collectAsStateWithLifecycle()
    val chatColors = rememberChatColors()
    val appPreferences = remember { AppPreferences(context.applicationContext) }
    val accessCodeEnabled by appPreferences.chatAccessCodeEnabled.collectAsStateWithLifecycle(initialValue = false)
    val vaultAccessUnlocked by VaultAccessSession.unlocked.collectAsStateWithLifecycle()
    val vaultTheme = rememberAdaptiveChatTheme(
        if (uiState.chatInterfaceStyle == ChatInterfaceStyle.IDE_LIGHT) {
            ChatInterfaceStyle.IDE_LIGHT
        } else {
            ChatInterfaceStyle.DEFAULT
        }
    )
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showSetupAccessCodeDialog by remember { mutableStateOf(false) }
    var showDisableAccessCodeDialog by remember { mutableStateOf(false) }
    var showChangeAccessCodeDialog by remember { mutableStateOf(false) }
    var setupPin by remember { mutableStateOf("") }
    var setupConfirm by remember { mutableStateOf("") }
    var setupPinVisible by remember { mutableStateOf(false) }
    var setupConfirmVisible by remember { mutableStateOf(false) }
    var disablePin by remember { mutableStateOf("") }
    var disablePinVisible by remember { mutableStateOf(false) }
    var changeCurrentPin by remember { mutableStateOf("") }
    var changeNewPin by remember { mutableStateOf("") }
    var changeConfirmPin by remember { mutableStateOf("") }
    var changeCurrentVisible by remember { mutableStateOf(false) }
    var changeNewVisible by remember { mutableStateOf(false) }
    var changeConfirmVisible by remember { mutableStateOf(false) }
    var accessCodeDialogError by remember { mutableStateOf<String?>(null) }
    var profileExpanded by remember { mutableStateOf(false) }
    var securityExpanded by remember { mutableStateOf(false) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var showBlockedUsersDialog by remember { mutableStateOf(false) }
    var showNotificationSettingsDialog by remember { mutableStateOf(false) }
    var pendingProfilePhotoCropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var profilePhotoWarning by remember { mutableStateOf<String?>(null) }
    var showProfilePhotoViewer by remember { mutableStateOf(false) }
    var profilePhotoSaveRequested by remember { mutableStateOf(false) }
    var profilePhotoEditUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var profilePhotoEditLoading by remember { mutableStateOf(false) }
    var profilePhotoUpdating by remember { mutableStateOf(false) }
    var showWallpaperVaultPicker by remember { mutableStateOf(false) }
    var showChatVaultPicker by remember { mutableStateOf(false) }
    var vaultAccessDigits by remember { mutableStateOf("") }
    var vaultAccessError by remember { mutableStateOf<String?>(null) }
    var externalProfilePhotoFlowActive by remember { mutableStateOf(false) }
    var notificationPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val setupPinFocusRequester = remember { FocusRequester() }
    val setupConfirmFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val (bumpActivity, activityGeneration) = rememberTerminalActivity()

    LaunchedEffect(uiState.isSavingProfilePhoto, uiState.successMessage, uiState.errorMessage) {
        if (profilePhotoUpdating &&
            !uiState.isSavingProfilePhoto &&
            (uiState.successMessage != null || uiState.errorMessage != null)
        ) {
            profilePhotoUpdating = false
        }
    }

    fun refreshNotificationPermission() {
        notificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshNotificationPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose { VaultAccessSession.lock() }
    }

    LaunchedEffect(notificationPermissionGranted) {
        viewModel.ensureCallNotificationControlsDefault(notificationPermissionGranted)
    }

    TerminalInactivityEffect(
        onInactivityTimeout,
        activityGeneration,
        uiState.deleteAccountPassword,
        blocked = showDeleteAccountDialog ||
            showSignOutDialog ||
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
        notificationPermissionGranted = granted
        viewModel.onNewTerminalAlertPermissionResult(granted)
        if (!granted) showNotificationSettingsDialog = true
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
                viewModel.setNewTerminalAlert(true)
                activity?.applySuppressHideOnHome(true)
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.setNewTerminalAlert(true)
        }
    }

    fun requestNotificationPermissionAgain() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        refreshNotificationPermission()
        if (notificationPermissionGranted) return

        val permanentlyBlocked = !ActivityCompat.shouldShowRequestPermissionRationale(
            activity ?: return,
            Manifest.permission.POST_NOTIFICATIONS,
        ) && context.getSharedPreferences("notification_permission_state", 0)
            .getBoolean("requested", false)
        if (permanentlyBlocked) {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            })
        } else {
            context.getSharedPreferences("notification_permission_state", 0)
                .edit().putBoolean("requested", true).apply()
            activity?.applySuppressHideOnHome(true)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showNotificationSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationSettingsDialog = false },
            title = { Text("Notifications are off") },
            text = { Text("To receive new terminal alerts, allow notifications for CodeShell in system settings.") },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    })
                    showNotificationSettingsDialog = false
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationSettingsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
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
        containerColor = MaterialTheme.colorScheme.background,
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
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
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
                .weight(1f)
                .fillMaxWidth()
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
                    onAvatarClick = {
                        profilePhotoSaveRequested = false
                        showProfilePhotoViewer = true
                    },
                    onViewingEnabledChange = viewModel::setProfilePicturesEnabled,
                    modifier = Modifier.padding(top = 8.dp),
                )
                HorizontalDivider(
                    color = EditorDefaultText.copy(alpha = 0.1f),
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                )
                ChatWallpaperSettingsSection(
                    chatWallpaperEnabled = uiState.chatWallpaperEnabled,
                    chatId = chatId,
                    overlay = uiState.chatWallpaperOverlay,
                    source = uiState.wallpaperSource,
                    hasVaultWallpaper = !uiState.wallpaperVaultId.isNullOrBlank(),
                    onChatWallpaperEnabledChange = viewModel::setChatWallpaperEnabled,
                    onOverlayChange = viewModel::setChatWallpaperOverlay,
                    onSourceChange = viewModel::setChatWallpaperSource,
                    onOpenVaultPicker = {
                        viewModel.loadWallpaperVaultItems()
                        showWallpaperVaultPicker = true
                    },
                    onClearVaultWallpaper = { viewModel.setChatWallpaperVaultItem(null) },
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        SettingsToggleRow(
                            title = stringResource(R.string.new_terminal_alert),
                            description = stringResource(R.string.new_terminal_alert_desc),
                            checked = uiState.newTerminalAlert,
                            onCheckedChange = ::onNewTerminalAlertToggle,
                        )
                        if (uiState.newTerminalAlert) {
                            SettingsToggleRow(
                                title = stringResource(R.string.call_notification_controls),
                                description = stringResource(R.string.call_notification_controls_desc),
                                checked = uiState.callNotificationControls,
                                onCheckedChange = viewModel::setCallNotificationControls,
                                enabled = !uiState.chatAccessCodeEnabled,
                            )
                        }
                        SettingsToggleRow(
                            title = stringResource(R.string.disconnect_on_device_lock),
                            description = stringResource(R.string.disconnect_on_device_lock_desc),
                            checked = uiState.disconnectOnDeviceLock,
                            onCheckedChange = viewModel::setDisconnectOnDeviceLock,
                        )
                    }
                    if (uiState.newTerminalAlert && !notificationPermissionGranted) {
                        val warningShape = RoundedCornerShape(50)
                        Box(
                            modifier = Modifier
                                .wrapContentWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp)
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFFFFC107),
                                    shape = warningShape,
                                )
                                .clip(warningShape)
                                .clickable(onClick = ::requestNotificationPermissionAgain)
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = stringResource(R.string.allow_notification_permission_terminal_alert),
                                    color = Color(0xFFFFC107),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
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
                    ChatInterfaceStyleDropdown(
                        selectedStyle = uiState.chatInterfaceStyle,
                        onStyleSelected = viewModel::setChatInterfaceStyle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.vault_view_grid),
                        style = MaterialTheme.typography.titleMedium,
                        color = EditorDefaultText,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = uiState.vaultGridColumns == 3,
                            onClick = { viewModel.setVaultGridColumns(3) },
                            label = { Text(stringResource(R.string.vault_view_grid_3)) },
                        )
                        FilterChip(
                            selected = uiState.vaultGridColumns == 4,
                            onClick = { viewModel.setVaultGridColumns(4) },
                            label = { Text(stringResource(R.string.vault_view_grid_4)) },
                        )
                    }
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
                        onClick = {
                            if (!openVaultPicker) {
                                onOpenVault()
                                return@TextButton
                            }
                            vaultAccessDigits = ""
                            vaultAccessError = null
                            vaultViewModel.loadItems()
                            showChatVaultPicker = true
                        },
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
                        onClick = { showSignOutDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.sign_out))
                    }
                }
            }
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.sign_out)) },
            text = { Text(stringResource(R.string.sign_out_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    viewModel.signOut(onSignedOut)
                }) {
                    Text(stringResource(R.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
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
                        visualTransformation = if (setupPinVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            AccessCodeVisibilityIcon(setupPinVisible) {
                                setupPinVisible = !setupPinVisible
                            }
                        },
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
                        visualTransformation = if (setupConfirmVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            AccessCodeVisibilityIcon(setupConfirmVisible) {
                                setupConfirmVisible = !setupConfirmVisible
                            }
                        },
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
                                Toast.makeText(context, R.string.chat_access_code_enabled, Toast.LENGTH_SHORT).show()
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
            onCropped = { photoUris ->
                pendingProfilePhotoCropUri = null
                profilePhotoWarning = null
                viewModel.uploadProfilePhoto(
                    photoUris.croppedUri,
                    photoUris.fullUri,
                    photoUris.useCroppedForSharing,
                )
            },
            onTooLarge = {
                pendingProfilePhotoCropUri = null
                profilePhotoWarning = context.getString(R.string.profile_picture_too_large)
            },
        )
    }

    // The self viewer shows the cropped avatar used on the tile. The shared
    // URL is intentionally reserved for the other person's view.
    val selfProfileViewerUrl = uiState.profile?.avatarUrl
        ?: uiState.profile?.avatarFullUrl
    if (showProfilePhotoViewer && !selfProfileViewerUrl.isNullOrBlank()) {
        var profileViewerLoading by remember(selfProfileViewerUrl) { mutableStateOf(true) }
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
                    model = selfProfileViewerUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onLoading = { profileViewerLoading = true },
                    onSuccess = { profileViewerLoading = false },
                    onError = { profileViewerLoading = false },
                    modifier = Modifier.fillMaxSize(),
                )
                if (profileViewerLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(42.dp),
                    )
                }
                val saveState = if (profilePhotoSaveRequested) vaultSaveState else null
                val saveLabel = when (saveState) {
                    is VaultSaveState.Saving -> "Saving…"
                    is VaultSaveState.Success -> "Saved"
                    is VaultSaveState.Error -> "Retry save"
                    null, VaultSaveState.Idle -> "Save"
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.Button(
                        onClick = {
                            profilePhotoEditLoading = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    downloadFileToCache(
                                        context,
                                        uiState.profile?.avatarFullUrl ?: uiState.profile?.avatarUrl.orEmpty(),
                                        "profile_edit_source.jpg",
                                    )
                                }
                                profilePhotoEditLoading = false
                                result.onSuccess { file -> profilePhotoEditUri = Uri.fromFile(file) }
                                    .onFailure { profilePhotoWarning = "Could not open profile picture editor" }
                            }
                        },
                        enabled = !profilePhotoEditLoading && !profilePhotoUpdating && saveState !is VaultSaveState.Saving,
                    ) {
                        if (profilePhotoEditLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                        }
                    }
                    androidx.compose.material3.Button(
                        onClick = {
                            profilePhotoSaveRequested = true
                            vaultViewModel.saveToVault(
                                url = uiState.profile?.avatarUrl.orEmpty(),
                                fileName = "profile_picture.jpg",
                                mimeType = "image/jpeg",
                            )
                        },
                        enabled = !profilePhotoUpdating && saveState !is VaultSaveState.Saving,
                    ) {
                        Text(saveLabel)
                    }
                }
                if (profilePhotoUpdating) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 72.dp)
                            .background(
                                Color.Black.copy(alpha = 0.78f),
                                RoundedCornerShape(24.dp),
                            )
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("Updating", color = Color.White)
                    }
                }
            }
        }
    }

    profilePhotoEditUri?.let { sourceUri ->
        ProfilePhotoCropDialog(
            sourceUri = sourceUri,
            confirmLabel = "Update",
            onDismiss = { profilePhotoEditUri = null },
            onCropped = { photoUris ->
                profilePhotoEditUri = null
                profilePhotoUpdating = true
                viewModel.uploadProfilePhoto(
                    photoUris.croppedUri,
                    photoUris.fullUri,
                    photoUris.useCroppedForSharing,
                )
            },
            onTooLarge = {
                profilePhotoEditUri = null
                profilePhotoWarning = context.getString(R.string.profile_picture_too_large)
            },
        )
    }

    if (showWallpaperVaultPicker) {
        WallpaperVaultPickerDialog(
            items = uiState.wallpaperVaultItems,
            loading = uiState.wallpaperVaultLoading,
            selectedVaultId = uiState.wallpaperVaultId,
            onThumbnail = viewModel::getWallpaperThumbnailBytes,
            onSelect = { item ->
                showWallpaperVaultPicker = false
                viewModel.setChatWallpaperVaultItem(item)
            },
            onDismiss = { showWallpaperVaultPicker = false },
        )
    }

    if (showChatVaultPicker) {
        if (accessCodeEnabled && !vaultAccessUnlocked) {
            ChatAccessLockOverlay(
                digits = vaultAccessDigits,
                onDigitsChange = {
                    vaultAccessDigits = it
                    vaultAccessError = null
                },
                onComplete = { code ->
                    scope.launch {
                        val storedHash = appPreferences.chatAccessCodeHash.first()
                        if (ChatAccessCodeHasher.verify(code, storedHash)) {
                            VaultAccessSession.unlock()
                            vaultAccessDigits = ""
                        } else {
                            vaultAccessDigits = ""
                            vaultAccessError = "Incorrect access code"
                        }
                    }
                },
                errorMessage = vaultAccessError,
                theme = vaultTheme,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ChatVaultPickerDialog(
                items = vaultUiState.items,
                loading = vaultUiState.isLoading,
                onThumbnail = vaultViewModel::getThumbnailBytes,
                onAddDrafts = { selected ->
                    PendingChatVaultPick.setAll(selected.map { it.id })
                    VaultAccessSession.lock()
                    showChatVaultPicker = false
                    onNavigateBack()
                },
                onCancel = {
                    VaultAccessSession.lock()
                    showChatVaultPicker = false
                    onNavigateBack()
                },
                onDismiss = {
                    VaultAccessSession.lock()
                    showChatVaultPicker = false
                },
            )
        }
    }

    if (showDisableAccessCodeDialog) {
        AlertDialog(
            onDismissRequest = {
                showDisableAccessCodeDialog = false
                disablePin = ""
                accessCodeDialogError = null
            },
            title = { Text(stringResource(R.string.chat_access_code_remove)) },
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
                        visualTransformation = if (disablePinVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            AccessCodeVisibilityIcon(disablePinVisible) {
                                disablePinVisible = !disablePinVisible
                            }
                        },
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
                                Toast.makeText(context, R.string.chat_access_code_disabled, Toast.LENGTH_SHORT).show()
                                showDisableAccessCodeDialog = false
                                disablePin = ""
                                accessCodeDialogError = null
                            } else {
                                accessCodeDialogError = error
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.chat_access_code_remove))
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
                        visualTransformation = if (changeCurrentVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            AccessCodeVisibilityIcon(changeCurrentVisible) {
                                changeCurrentVisible = !changeCurrentVisible
                            }
                        },
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
                        visualTransformation = if (changeNewVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            AccessCodeVisibilityIcon(changeNewVisible) {
                                changeNewVisible = !changeNewVisible
                            }
                        },
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
                        visualTransformation = if (changeConfirmVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            AccessCodeVisibilityIcon(changeConfirmVisible) {
                                changeConfirmVisible = !changeConfirmVisible
                            }
                        },
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
                                Toast.makeText(context, R.string.chat_access_code_changed, Toast.LENGTH_SHORT).show()
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
private fun ChatWallpaperSettingsSection(
    chatWallpaperEnabled: Boolean,
    chatId: String?,
    overlay: Int,
    source: String,
    hasVaultWallpaper: Boolean,
    onChatWallpaperEnabledChange: (Boolean) -> Unit,
    onOverlayChange: (Int) -> Unit,
    onSourceChange: (String) -> Unit,
    onOpenVaultPicker: () -> Unit,
    onClearVaultWallpaper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snappedOverlay = ((overlay + 10) / 20 * 20).coerceIn(0, 100)
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsToggleRow(
            title = stringResource(R.string.chat_wallpaper_enabled),
            description = stringResource(R.string.chat_wallpaper_enabled_desc),
            checked = chatWallpaperEnabled,
            onCheckedChange = onChatWallpaperEnabledChange,
        )
        if (chatWallpaperEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chat_wallpaper_overlay),
                    style = MaterialTheme.typography.titleMedium,
                    color = EditorDefaultText,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.chat_wallpaper_overlay_value, snappedOverlay),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EditorDefaultText.copy(alpha = 0.8f),
                )
            }
            Slider(
                value = snappedOverlay.toFloat(),
                onValueChange = { onOverlayChange(it.toInt()) },
                valueRange = 0f..100f,
                steps = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            if (source == AppPreferences.WALLPAPER_SOURCE_VAULT) {
                TextButton(onClick = onClearVaultWallpaper) {
                    Text("Use profile picture wallpaper")
                }
            }
        }
    }
}

@Composable
private fun WallpaperVaultPickerDialog(
    items: List<VaultItem>,
    loading: Boolean,
    selectedVaultId: String?,
    onThumbnail: suspend (VaultItem) -> Result<ByteArray>,
    onSelect: (VaultItem) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.8f)
                .clip(RoundedCornerShape(16.dp))
                .background(EditorBackground),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.chat_wallpaper_vault_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = EditorDefaultText,
                )
                Text(
                    text = stringResource(R.string.chat_wallpaper_vault_picker_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = EditorDefaultText.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                when {
                    loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    items.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.chat_wallpaper_no_vault_images),
                                color = EditorDefaultText.copy(alpha = 0.6f),
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            items(items, key = { it.id }) { item ->
                                WallpaperVaultThumb(
                                    item = item,
                                    onThumbnail = onThumbnail,
                                    selected = item.id == selectedVaultId,
                                    onClick = { onSelect(item) },
                                    modifier = Modifier.aspectRatio(1f),
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun WallpaperVaultThumb(
    item: VaultItem,
    onThumbnail: suspend (VaultItem) -> Result<ByteArray>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(item.id, item.contentVersion) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(item.id, item.contentVersion) { mutableStateOf(true) }

    LaunchedEffect(item.id, item.contentVersion) {
        loading = true
        bitmap = withContext(Dispatchers.IO) {
            onThumbnail(item).getOrNull()?.let { bytes ->
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            }
        }
        loading = false
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else EditorDefaultText.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .background(EditorDefaultText.copy(alpha = 0.08f)),
    ) {
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            bitmap != null -> {
                androidx.compose.foundation.Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = item.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = EditorDefaultText.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            )
        }
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
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clickable(enabled = enabled, onClick = { onCheckedChange(!checked) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = EditorDefaultText.copy(alpha = if (enabled) 1f else 0.5f),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = EditorDefaultText.copy(alpha = if (enabled) 0.62f else 0.35f),
                modifier = Modifier.padding(top = 2.dp, end = 12.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
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
    val options = ChatInterfaceStyle.entries.filter { style ->
        style != ChatInterfaceStyle.IDE_LIGHT || DisguiseTheme.supportsIdeLight
    }

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
        ChatInterfaceStyle.IDE_LIGHT -> stringResource(R.string.chat_interface_style_ide_light)
    }
}

@Composable
private fun AccessCodeVisibilityIcon(
    visible: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (visible) "Hide access code" else "Show access code",
        )
    }
}
