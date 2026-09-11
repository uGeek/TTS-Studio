package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.TtsPlaybackState
import com.example.ui.components.ExportAudioDialog
import com.example.ui.screens.AjustesScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.TtsSettingsScreen
import com.example.ui.theme.StatusPlaying

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val exportedAudios by viewModel.exportedAudios.collectAsStateWithLifecycle()
    val exportDialogState by viewModel.exportDialogState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Export Dialog
    ExportAudioDialog(
        state = exportDialogState,
        onDismiss = { viewModel.closeExportDialog() },
        onExport = { title, format -> viewModel.executeExport(title, format) },
        onFormatSelected = { format -> viewModel.setExportFormat(format) }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ttsState.playbackState == TtsPlaybackState.PLAYING) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(StatusPlaying, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "TTS Studio",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleDarkMode() },
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkMode == true) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Cambiar tema"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                // Tab 0: Lector
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { viewModel.currentTab.value = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Lector"
                        )
                    },
                    label = { Text("Lector") },
                    modifier = Modifier.testTag("tab_reader")
                )

                // Tab 1: Motores TTS
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { viewModel.currentTab.value = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Motores"
                        )
                    },
                    label = { Text("Motores") },
                    modifier = Modifier.testTag("tab_engines")
                )

                // Tab 2: Grabaciones & Historial
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { viewModel.currentTab.value = 2 },
                    icon = {
                        if (exportedAudios.isNotEmpty()) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text("${exportedAudios.size}")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Grabaciones"
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Grabaciones"
                            )
                        }
                    },
                    label = { Text("Grabaciones") },
                    modifier = Modifier.testTag("tab_history")
                )

                // Tab 3: Ajustes (Telegram, WebDAV, etc.)
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { viewModel.currentTab.value = 3 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ajustes"
                        )
                    },
                    label = { Text("Ajustes") },
                    modifier = Modifier.testTag("tab_ajustes")
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "tab_content_anim"
        ) { targetTab ->
            when (targetTab) {
                0 -> ReaderScreen(viewModel = viewModel)
                1 -> TtsSettingsScreen(viewModel = viewModel)
                2 -> HistoryScreen(viewModel = viewModel)
                3 -> AjustesScreen(viewModel = viewModel)
            }
        }
    }
}
