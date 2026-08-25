package com.codeshell.app.ui.auth

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeshell.app.MainActivity
import com.codeshell.app.R
import com.codeshell.app.ui.components.CodeShellUsernameField
import com.codeshell.app.ui.components.OfflineBanner
import com.codeshell.app.ui.components.ProfilePhotoCropDialog
import com.codeshell.app.ui.chat.rememberChatColors
import com.codeshell.app.ui.util.ConstrainedFormColumn
import com.codeshell.app.ui.util.TerminalInactivityEffect
import com.codeshell.app.ui.util.rememberTerminalActivity
import com.codeshell.app.util.NetworkConnectivity
import com.codeshell.app.ui.theme.EditorBackground
import com.codeshell.app.ui.theme.EditorDefaultText
import com.codeshell.app.ui.theme.FabAccent
import com.codeshell.app.ui.theme.TopBarBackground
import com.codeshell.app.util.findActivity
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onSignUpSuccess: () -> Unit,
    onInactivityTimeout: () -> Unit = onNavigateBack,
) {
    val app = LocalContext.current.applicationContext as Application
    val context = LocalContext.current
    val activity = context.findActivity() as? MainActivity
    val viewModel: SignUpViewModel = viewModel { SignUpViewModel(app) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chatColors = rememberChatColors()
    val isOnline by NetworkConnectivity.observe(app)
        .collectAsStateWithLifecycle(initialValue = NetworkConnectivity.hasInternet(app))
    val scrollState = rememberScrollState()
    val (bumpActivity, activityGeneration) = rememberTerminalActivity()
    var profilePhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var profilePhotoFullUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var useCroppedForSharing by remember { mutableStateOf(false) }
    var pendingProfilePhotoCropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var profilePhotoWarning by remember { mutableStateOf<String?>(null) }
    var externalProfilePhotoFlowActive by remember { mutableStateOf(false) }
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

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) bumpActivity()
    }

    TerminalInactivityEffect(
        onInactivityTimeout,
        activityGeneration,
        uiState.username,
        uiState.password,
        uiState.confirmPassword,
        blocked = uiState.isLoading ||
            pendingProfilePhotoCropUri != null ||
            externalProfilePhotoFlowActive ||
            scrollState.isScrollInProgress,
    )

    val availabilityColor = when (uiState.availability) {
        UsernameAvailability.Available -> FabAccent
        UsernameAvailability.Taken, UsernameAvailability.Invalid -> MaterialTheme.colorScheme.error
        UsernameAvailability.Checking -> EditorDefaultText
        UsernameAvailability.Idle -> EditorDefaultText
    }

    Scaffold(
        containerColor = EditorBackground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sign_up_title)) },
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
            ConstrainedFormColumn {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .imePadding()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CodeShellUsernameField(
                value = uiState.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text(stringResource(R.string.username)) },
                supportingText = uiState.availabilityMessage?.let { message ->
                    { Text(message, color = availabilityColor) }
                },
                isError = uiState.availability == UsernameAvailability.Taken ||
                    uiState.availability == UsernameAvailability.Invalid,
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = { Text(stringResource(R.string.confirm_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        viewModel.signUp(
                            profilePhotoUri = profilePhotoUri,
                            profilePhotoFullUri = profilePhotoFullUri,
                            useCroppedForSharing = useCroppedForSharing,
                            onSuccess = onSignUpSuccess,
                        )
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(EditorDefaultText.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (profilePhotoUri != null) {
                    AsyncImage(
                        model = profilePhotoUri,
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
            TextButton(
                onClick = {
                    externalProfilePhotoFlowActive = true
                    activity?.applySuppressHideOnHome(true)
                    profilePhotoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = !uiState.isLoading,
            ) {
                Text(
                    stringResource(
                        if (profilePhotoUri == null) {
                            R.string.profile_picture_optional_upload
                        } else {
                            R.string.profile_picture_change
                        }
                    )
                )
            }

            uiState.errorMessage?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            profilePhotoWarning?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.signUp(
                        profilePhotoUri = profilePhotoUri,
                        profilePhotoFullUri = profilePhotoFullUri,
                        useCroppedForSharing = useCroppedForSharing,
                        onSuccess = onSignUpSuccess,
                    )
                },
                enabled = !uiState.isLoading &&
                    uiState.availability == UsernameAvailability.Available &&
                    isOnline,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.sign_up))
                }
            }

            TextButton(onClick = onNavigateToLogin) {
                Text(stringResource(R.string.have_account_login))
            }
            }
            }
        }
    }

    pendingProfilePhotoCropUri?.let { sourceUri ->
        ProfilePhotoCropDialog(
            sourceUri = sourceUri,
            onDismiss = { pendingProfilePhotoCropUri = null },
            onCropped = { photoUris ->
                pendingProfilePhotoCropUri = null
                profilePhotoWarning = null
                profilePhotoUri = photoUris.croppedUri
                profilePhotoFullUri = photoUris.fullUri
                useCroppedForSharing = photoUris.useCroppedForSharing
            },
            onTooLarge = {
                pendingProfilePhotoCropUri = null
                profilePhotoWarning = context.getString(R.string.profile_picture_too_large)
            },
        )
    }
}
