package com.codeshell.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.codeshell.app.data.repository.AuthRepository
import com.codeshell.app.MainActivity
import com.codeshell.app.messaging.PendingChatNavigation
import com.codeshell.app.ui.auth.LoginScreen
import com.codeshell.app.ui.auth.SignUpScreen
import com.codeshell.app.ui.aitutor.AiTutorScreen
import com.codeshell.app.ui.chat.ChatScreen
import com.codeshell.app.ui.disguise.DisguiseLearnScreen
import com.codeshell.app.ui.home.HomeScreen
import com.codeshell.app.ui.media.MediaViewerScreen
import com.codeshell.app.ui.settings.SettingsScreen
import com.codeshell.app.ui.vault.VaultScreen
import com.codeshell.app.ui.util.SecureScreenEffect
import com.codeshell.app.data.model.AiTutorEntrySource
import com.codeshell.app.util.findActivity

private const val FADE_DURATION_MS = 300
const val MEDIA_URL_KEY = "mediaUrl"
const val MEDIA_FILE_NAME_KEY = "mediaFileName"
const val MEDIA_CHAT_ID_KEY = "mediaChatId"
const val MEDIA_MESSAGE_ID_KEY = "mediaMessageId"

@Composable
fun CodeShellNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    authRepository: AuthRepository = AuthRepository()
) {
    val onSecretEntry: () -> Unit = {
        val destination = if (authRepository.isLoggedIn) Routes.home() else Routes.LOGIN
        navController.navigate(destination) {
            launchSingleTop = true
        }
    }

    val navigateToHome: () -> Unit = {
        navController.navigate(Routes.home()) {
            popUpTo(Routes.CODING_LEARN) { saveState = true }
            launchSingleTop = true
        }
    }

    val navigateToLogin: () -> Unit = {
        navController.navigate(Routes.LOGIN) {
            popUpTo(Routes.CODING_LEARN) { inclusive = false }
            launchSingleTop = true
        }
    }

    val navigateToLearning: () -> Unit = {
        navController.popBackStack(Routes.CODING_LEARN, inclusive = false)
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val secure = backStackEntry?.destination?.route.requiresSecureScreen()
    SecureScreenEffect(secure = secure)

    val activity = LocalContext.current.findActivity() as? MainActivity
    LaunchedEffect(backStackEntry?.destination?.route) {
        activity?.isOnTerminalScreen = backStackEntry?.destination?.route.isTerminalRoute()
    }

    LaunchedEffect(activity?.notificationOpenTick) {
        if (!authRepository.isLoggedIn) return@LaunchedEffect
        val chatId = PendingChatNavigation.consume() ?: return@LaunchedEffect
        navController.navigate(Routes.chat(chatId)) {
            launchSingleTop = true
        }
    }

    LaunchedEffect(activity?.learningOpenTick) {
        if (activity?.learningOpenTick == 0) return@LaunchedEffect
        navController.popBackStack(Routes.CODING_LEARN, inclusive = false)
    }

    // NOTE: Externally shared content is deliberately NOT auto-routed to the
    // conversation tile page. The disguise shell must stay in front so the
    // user reaches the terminal on their own (e.g. via the secret entry); the
    // shared content then surfaces as a banner on the conversation tile page.

    NavHost(
        navController = navController,
        startDestination = Routes.CODING_LEARN,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(FADE_DURATION_MS)) },
        exitTransition = { fadeOut(animationSpec = tween(FADE_DURATION_MS)) },
        popEnterTransition = { fadeIn(animationSpec = tween(FADE_DURATION_MS)) },
        popExitTransition = { fadeOut(animationSpec = tween(FADE_DURATION_MS)) }
    ) {
        composable(Routes.CODING_LEARN) {
            DisguiseLearnScreen(onSecretEntry = onSecretEntry)
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSignUp = { navController.navigate(Routes.SIGNUP) },
                onLoginSuccess = navigateToHome,
                onInactivityTimeout = navigateToLearning,
            )
        }

        composable(Routes.SIGNUP) {
            SignUpScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = { navController.popBackStack() },
                onSignUpSuccess = navigateToHome,
                onInactivityTimeout = navigateToLearning,
            )
        }

        // Check for vaultSend flag in saved state handle and get the pending item IDs.
        // Supports both the legacy single-send path and the multiselect send path.
        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
        val pendingVaultSendIds = com.codeshell.app.ui.vault.PendingVaultSend.consumeAll()

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateBack = {
                    navController.popBackStack(Routes.CODING_LEARN, inclusive = false)
                },
                onOpenChat = { chatId ->
                    navController.navigate(Routes.chat(chatId))
                },
                onOpenSettings = {
                    navController.navigate(Routes.settings())
                },
                onOpenAiTutor = { topicId, topicLabel, welcomeDigits, entrySource ->
                    navController.navigate(
                        Routes.aiTutor(
                            topicId = topicId,
                            topicLabel = topicLabel,
                            entrySource = entrySource,
                            welcomeDigits = welcomeDigits,
                        ),
                    )
                },
                onTerminalRevoked = {
                    navController.popBackStack(Routes.CODING_LEARN, inclusive = false)
                },
                vaultSendItemIds = pendingVaultSendIds,
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
            ChatScreen(
                chatId = chatId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToLearning = {
                    navController.popBackStack(Routes.CODING_LEARN, inclusive = false)
                },
                onOpenSettings = {
                    navController.navigate(Routes.settings(chatId))
                },
                onOpenMedia = { type, url, fileName, messageId ->
                    navController.currentBackStackEntry?.savedStateHandle?.apply {
                        set(MEDIA_URL_KEY, url)
                        set(MEDIA_FILE_NAME_KEY, fileName)
                        set(MEDIA_CHAT_ID_KEY, chatId)
                        set(MEDIA_MESSAGE_ID_KEY, messageId)
                    }
                    navController.navigate(Routes.media(type))
                },
            )
        }

        composable(
            route = Routes.MEDIA,
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type").orEmpty()
            val savedStateHandle = navController.previousBackStackEntry?.savedStateHandle
            val mediaUrl = savedStateHandle?.get<String>(MEDIA_URL_KEY).orEmpty()
            val fileName = savedStateHandle?.get<String>(MEDIA_FILE_NAME_KEY)
            val mediaChatId = savedStateHandle?.get<String>(MEDIA_CHAT_ID_KEY)
            val mediaMessageId = savedStateHandle?.get<String>(MEDIA_MESSAGE_ID_KEY)
            MediaViewerScreen(
                mediaType = type,
                mediaUrl = mediaUrl,
                fileName = fileName,
                chatId = mediaChatId,
                messageId = mediaMessageId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.AI_TUTOR,
            arguments = listOf(
                navArgument("topicId") { type = NavType.StringType },
                navArgument("entrySource") {
                    type = NavType.StringType
                    defaultValue = "fab"
                },
                navArgument("welcomeDigits") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("topicLabel") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val topicId = backStackEntry.arguments?.getString("topicId").orEmpty()
            val entrySourceRaw = backStackEntry.arguments?.getString("entrySource").orEmpty()
            val welcomeDigits = backStackEntry.arguments
                ?.getString("welcomeDigits")
                .orEmpty()
                .decodeFromRoute()
            val topicLabel = backStackEntry.arguments
                ?.getString("topicLabel")
                .orEmpty()
                .decodeFromRoute()
            val entrySource = when (entrySourceRaw) {
                "disguise" -> AiTutorEntrySource.DISGUISE
                "bot" -> AiTutorEntrySource.BOT
                else -> AiTutorEntrySource.FAB
            }
            AiTutorScreen(
                topicId = topicId,
                topicLabel = topicLabel,
                entrySource = entrySource,
                welcomeDigits = welcomeDigits,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.SETTINGS,
            arguments = listOf(
                navArgument("chatId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val settingsChatId = backStackEntry.arguments?.getString("chatId")
                ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }
            SettingsScreen(
                chatId = settingsChatId,
                onNavigateBack = { navController.popBackStack() },
                onSignedOut = navigateToLogin,
                onInactivityTimeout = navigateToLearning,
                onOpenVault = {
                    navController.navigate(Routes.vault())
                },
            )
        }

        composable(Routes.VAULT) {
            VaultScreen(
                onNavigateBack = { navController.popBackStack() },
                onInactivityTimeout = navigateToLearning,
                onOpenChat = { chatId ->
                    navController.navigate(Routes.chat(chatId))
                },
                onNavigateToHomeForVaultSend = {
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set("vaultSend", true)
                    }
                    navController.navigate(Routes.home()) {
                        popUpTo(Routes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
