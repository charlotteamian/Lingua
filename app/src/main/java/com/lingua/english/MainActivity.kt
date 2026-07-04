package com.lingua.english

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lingua.english.data.AppContainer
import com.lingua.english.ui.LinguaViewModelFactory
import com.lingua.english.ui.components.LocalWordLookup
import com.lingua.english.ui.components.WordLookupController
import com.lingua.english.ui.components.WordLookupSheet
import com.lingua.english.ui.navigation.Routes
import com.lingua.english.ui.navigation.TopDestination
import com.lingua.english.ui.screens.chat.ChatHistoryScreen
import com.lingua.english.ui.screens.chat.ChatScreen
import com.lingua.english.ui.screens.chat.ChatSessionScreen
import com.lingua.english.ui.screens.chat.ChatViewModel
import com.lingua.english.ui.screens.corpus.ComposeEntryScreen
import com.lingua.english.ui.screens.corpus.ComposeEntryViewModel
import com.lingua.english.ui.screens.corpus.CorpusScreen
import com.lingua.english.ui.screens.corpus.CorpusViewModel
import com.lingua.english.ui.screens.corpus.MaterialDetailScreen
import com.lingua.english.ui.screens.corpus.MaterialDetailViewModel
import com.lingua.english.ui.screens.media.MediaLibraryScreen
import com.lingua.english.ui.screens.media.MediaLibraryViewModel
import com.lingua.english.ui.screens.media.MediaPlayerScreen
import com.lingua.english.ui.screens.media.MediaPlayerViewModel
import com.lingua.english.ui.screens.media.YouTubeLoginScreen
import com.lingua.english.ui.screens.practice.PronunciationScreen
import com.lingua.english.ui.screens.practice.PronunciationViewModel
import com.lingua.english.ui.screens.review.LibraryScreen
import com.lingua.english.ui.screens.review.ReviewScreen
import com.lingua.english.ui.screens.review.ReviewViewModel
import com.lingua.english.ui.screens.settings.SettingsScreen
import com.lingua.english.ui.screens.settings.SettingsViewModel
import com.lingua.english.ui.screens.today.TodayScreen
import com.lingua.english.ui.screens.today.TodayViewModel
import com.lingua.english.ui.theme.LinguaTheme

class MainActivity : ComponentActivity() {

    private lateinit var container: com.lingua.english.data.AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = (application as LinguaApplication).container
        handleShareIntent(intent)
        setContent {
            LinguaTheme {
                LinguaApp(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** 系统分享 (YouTube「分享 → Lingua」等) 进来的文本, 交给听读页消费。 */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }?.let {
                container.pendingSharedUrl.value = it
            }
        }
    }
}

@Composable
private fun LinguaApp(container: AppContainer) {
    val navController = rememberNavController()
    val factory = remember { LinguaViewModelFactory(container) }
    val scope = rememberCoroutineScope()

    // 全 app 共享的点词查词控制器
    val wordLookup = remember {
        WordLookupController(scope, container.dictRepository, container.reviewRepository, container.tts)
    }

    // TTS 语速跟随偏好
    val ttsRate by container.prefs.ttsRate.collectAsStateWithLifecycle(initialValue = 0.9f)
    LaunchedEffect(ttsRate) { container.tts.setRate(ttsRate) }

    // 系统分享进来 → 跳听读页
    val sharedUrl by container.pendingSharedUrl.collectAsStateWithLifecycle()
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            navController.navigate(Routes.MEDIA) { launchSingleTop = true }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    CompositionLocalProvider(LocalWordLookup provides wordLookup::open) {
        Scaffold(
            bottomBar = {
                if (TopDestination.entries.any { it.route == currentRoute }) {
                    NavigationBar {
                        TopDestination.entries.forEach { destination ->
                            val selected = currentRoute == destination.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (selected) destination.iconSelected else destination.icon,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = TopDestination.Today.route,
                modifier = Modifier.padding(padding),
            ) {
                composable(TopDestination.Today.route) {
                    val vm: TodayViewModel = viewModel(factory = factory)
                    TodayScreen(
                        vm = vm,
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onGoChat = { navController.navigate(TopDestination.Chat.route) },
                        onGoReview = { navController.navigate(TopDestination.Review.route) },
                        onGoCompose = { navController.navigate(Routes.COMPOSE_ENTRY) },
                    )
                }
                composable(TopDestination.Chat.route) {
                    val vm: ChatViewModel = viewModel(factory = factory)
                    ChatScreen(vm, onOpenHistory = { navController.navigate(Routes.CHAT_HISTORY) })
                }
                composable(Routes.CHAT_HISTORY) {
                    val vm: ChatViewModel = viewModel(factory = factory)
                    ChatHistoryScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onOpenSession = { navController.navigate(Routes.chatSession(it)) },
                    )
                }
                composable(Routes.CHAT_SESSION) { entry ->
                    val anchorId = entry.arguments?.getString("anchorId").orEmpty()
                    val vm: ChatViewModel = viewModel(factory = factory)
                    ChatSessionScreen(vm, anchorId, onBack = { navController.popBackStack() })
                }
                composable(TopDestination.Corpus.route) {
                    val vm: CorpusViewModel = viewModel(factory = factory)
                    CorpusScreen(
                        vm = vm,
                        onOpenMaterial = { navController.navigate(Routes.materialDetail(it)) },
                        onCompose = { navController.navigate(Routes.COMPOSE_ENTRY) },
                        onOpenMedia = { navController.navigate(Routes.MEDIA) },
                    )
                }
                composable(TopDestination.Review.route) {
                    val vm: ReviewViewModel = viewModel(factory = factory)
                    ReviewScreen(vm, onOpenLibrary = { navController.navigate(Routes.LIBRARY) })
                }
                composable(Routes.LIBRARY) {
                    val vm: ReviewViewModel = viewModel(factory = factory)
                    LibraryScreen(vm, onBack = { navController.popBackStack() })
                }
                composable(Routes.MEDIA) {
                    val vm: MediaLibraryViewModel = viewModel(factory = factory)
                    MediaLibraryScreen(
                        vm = vm,
                        sharedUrl = sharedUrl,
                        onConsumeSharedUrl = { container.pendingSharedUrl.value = null },
                        onOpen = { navController.navigate(Routes.mediaPlayer(it)) },
                        onLoginYouTube = { navController.navigate(Routes.YT_LOGIN) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.YT_LOGIN) {
                    YouTubeLoginScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.MEDIA_PLAYER) { entry ->
                    val mediaId = entry.arguments?.getString("mediaId").orEmpty()
                    val vm: MediaPlayerViewModel = viewModel(
                        key = "media-$mediaId",
                        factory = LinguaViewModelFactory.mediaPlayer(container, mediaId),
                    )
                    MediaPlayerScreen(vm, onBack = { navController.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    val vm: SettingsViewModel = viewModel(factory = factory)
                    SettingsScreen(vm, onBack = { navController.popBackStack() })
                }
                composable(Routes.COMPOSE_ENTRY) {
                    val vm: ComposeEntryViewModel = viewModel(factory = factory)
                    ComposeEntryScreen(vm, onBack = { navController.popBackStack() })
                }
                composable(Routes.MATERIAL_DETAIL) { entry ->
                    val materialId = entry.arguments?.getString("materialId").orEmpty()
                    val vm: MaterialDetailViewModel = viewModel(
                        key = "material-$materialId",
                        factory = LinguaViewModelFactory.materialDetail(container, materialId),
                    )
                    MaterialDetailScreen(
                        vm = vm,
                        materialId = materialId,
                        onBack = { navController.popBackStack() },
                        onPractice = { navController.navigate(Routes.practice(materialId)) },
                    )
                }
                composable(Routes.PRACTICE) { entry ->
                    val materialId = entry.arguments?.getString("materialId").orEmpty()
                    val vm: PronunciationViewModel = viewModel(
                        key = "practice-$materialId",
                        factory = LinguaViewModelFactory.pronunciation(container, materialId),
                    )
                    PronunciationScreen(vm, onBack = { navController.popBackStack() })
                }
            }

            WordLookupSheet(wordLookup)
        }
    }
}
