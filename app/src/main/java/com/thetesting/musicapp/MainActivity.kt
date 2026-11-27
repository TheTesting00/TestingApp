package com.thetesting.musicapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thetesting.musicapp.model.Song
import com.thetesting.musicapp.ui.MusicViewModel
import com.thetesting.musicapp.ui.components.AdaptiveBottomNavBar
import com.thetesting.musicapp.ui.components.AppTab
import com.thetesting.musicapp.ui.components.SleepTimerDialog
import com.thetesting.musicapp.ui.screens.EarphoneScreen
import com.thetesting.musicapp.ui.screens.EditHubScreen
import com.thetesting.musicapp.ui.screens.EditSongSheet
import com.thetesting.musicapp.ui.screens.LibraryScreen
import com.thetesting.musicapp.ui.screens.MiniPlayerBar
import com.thetesting.musicapp.ui.screens.NowPlayingScreen
import com.thetesting.musicapp.ui.screens.ThemeScreen
import com.thetesting.musicapp.ui.theme.MusikKuTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vm: MusicViewModel = viewModel()
            val appearance by vm.appearance.collectAsState()

            MusikKuTheme(themeId = appearance.themeId) {
                val allSongs by vm.songs.collectAsState()
                val songs by vm.filteredSongs.collectAsState()
                val searchQuery by vm.searchQuery.collectAsState()
                val selectedFilter by vm.selectedFilter.collectAsState()
                val hasAudioPermission by vm.hasAudioPermission.collectAsState()
                val isLoading by vm.isLoading.collectAsState()
                val playbackState by vm.playbackState.collectAsState()
                val earphoneStatus by vm.earphoneStatus.collectAsState()
                val earphoneSettings by vm.earphoneSettings.collectAsState()
                val eqBandLabels by vm.eqBandLabels.collectAsState()
                val statusMessage by vm.statusMessage.collectAsState()

                var selectedTab by remember { mutableStateOf(AppTab.MUSIC) }
                var showNowPlaying by remember { mutableStateOf(false) }
                var showSleepTimerDialog by remember { mutableStateOf(false) }
                var editingSong by remember { mutableStateOf<Song?>(null) }
                var pendingCoverUri by remember { mutableStateOf<Uri?>(null) }
                var importedLyricsText by remember { mutableStateOf<String?>(null) }

                val scope = rememberCoroutineScope()

                val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    val audioGranted = results[audioPermission] == true || hasPermission(audioPermission)
                    vm.setAudioPermissionGranted(audioGranted)
                }

                val addMusicLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments()
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        uris.forEach { uri ->
                            try {
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (_: Exception) {
                            }
                        }
                        vm.addCustomAudioUris(uris)
                    }
                }

                val pickCoverLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    if (uri != null) {
                        pendingCoverUri = uri
                    }
                }

                val importLyricsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        scope.launch {
                            val text = vm.readLyricsFromUri(uri)
                            if (text.isNotBlank()) {
                                importedLyricsText = text
                            } else {
                                Toast.makeText(this@MainActivity, "File lirik kosong", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    val alreadyGranted = hasPermission(audioPermission)
                    vm.setAudioPermissionGranted(alreadyGranted)
                    if (!alreadyGranted) {
                        val toRequest = buildList {
                            add(audioPermission)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                            ) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        permissionLauncher.launch(toRequest.toTypedArray())
                    }
                }

                LaunchedEffect(statusMessage) {
                    statusMessage?.let { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        vm.clearStatusMessage()
                    }
                }

                LaunchedEffect(playbackState.errorMessage) {
                    playbackState.errorMessage?.let { err ->
                        Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                        vm.clearPlaybackError()
                    }
                }

                val bottomContentPadding = PaddingValues(
                    bottom = if (playbackState.currentSong != null) 152.dp else 90.dp
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                ) {
                    when (selectedTab) {
                        AppTab.MUSIC, AppTab.ADD -> {
                            LibraryScreen(
                                songs = songs,
                                searchQuery = searchQuery,
                                selectedFilter = selectedFilter,
                                hasAudioPermission = hasAudioPermission,
                                isLoading = isLoading,
                                playbackState = playbackState,
                                earphoneStatus = earphoneStatus,
                                onSearchQueryChange = vm::updateSearchQuery,
                                onFilterSelect = vm::selectFilter,
                                onRequestPermission = {
                                    val toRequest = buildList {
                                        add(audioPermission)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                    permissionLauncher.launch(toRequest.toTypedArray())
                                },
                                onRefreshSongs = vm::refreshSongs,
                                onAddMusicFiles = { addMusicLauncher.launch(arrayOf("audio/*")) },
                                onOpenEarphoneStudio = { selectedTab = AppTab.EARPHONE },
                                onOpenTimer = { showSleepTimerDialog = true },
                                onSongClick = { song -> vm.playSong(song) },
                                onEditSongClick = { song ->
                                    pendingCoverUri = null
                                    importedLyricsText = null
                                    editingSong = song
                                },
                                loadCoverBitmap = vm::loadCoverBitmap,
                                bottomSpacerHeight = bottomContentPadding
                            )
                        }

                        AppTab.EDIT -> {
                            EditHubScreen(
                                songs = allSongs,
                                currentSong = playbackState.currentSong,
                                onEditSong = { song ->
                                    pendingCoverUri = null
                                    importedLyricsText = null
                                    editingSong = song
                                },
                                onAddMusicFiles = { addMusicLauncher.launch(arrayOf("audio/*")) },
                                loadCoverBitmap = vm::loadCoverBitmap,
                                bottomPadding = bottomContentPadding
                            )
                        }

                        AppTab.EARPHONE -> {
                            EarphoneScreen(
                                status = earphoneStatus,
                                settings = earphoneSettings,
                                bandLabels = eqBandLabels,
                                onRefreshStatus = vm::refreshEarphoneStatus,
                                onUpdateSettings = vm::updateEarphoneSettings,
                                onSelectPreset = vm::selectEqPreset,
                                onBandChange = vm::updateEqBand,
                                bottomPadding = bottomContentPadding
                            )
                        }

                        AppTab.THEME -> {
                            ThemeScreen(
                                appearance = appearance,
                                onSelectTheme = vm::selectTheme,
                                onSelectNavBarStyle = vm::selectNavBarStyle,
                                bottomPadding = bottomContentPadding
                            )
                        }
                    }

                    // Bottom Mini Player + Selectable Bottom Navigation Bar
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val currentSong = playbackState.currentSong
                        if (currentSong != null) {
                            MiniPlayerBar(
                                song = currentSong,
                                isPlaying = playbackState.isPlaying,
                                positionMs = playbackState.positionMs,
                                durationMs = playbackState.durationMs,
                                onClick = { showNowPlaying = true },
                                onTogglePlayPause = vm::togglePlayPause,
                                onSkipNext = vm::skipNext,
                                loadCoverBitmap = vm::loadCoverBitmap
                            )
                        }

                        AdaptiveBottomNavBar(
                            selectedTab = selectedTab,
                            styleId = appearance.navBarStyleId,
                            earphoneConnected = earphoneStatus.isConnected,
                            onSelectTab = { tab -> selectedTab = tab },
                            onCenterAddClick = { addMusicLauncher.launch(arrayOf("audio/*")) }
                        )
                    }
                }

                if (showNowPlaying && playbackState.currentSong != null) {
                    NowPlayingScreen(
                        playbackState = playbackState,
                        onDismiss = { showNowPlaying = false },
                        onTogglePlayPause = vm::togglePlayPause,
                        onSeekTo = vm::seekTo,
                        onSkipPrevious = vm::skipPrevious,
                        onSkipNext = vm::skipNext,
                        onToggleShuffle = vm::toggleShuffle,
                        onCycleRepeat = vm::cycleRepeatMode,
                        onEditSong = { song ->
                            pendingCoverUri = null
                            importedLyricsText = null
                            editingSong = song
                        },
                        onOpenTimer = { showSleepTimerDialog = true },
                        loadCoverBitmap = vm::loadCoverBitmap
                    )
                }

                if (showSleepTimerDialog) {
                    SleepTimerDialog(
                        playbackState = playbackState,
                        onDismiss = { showSleepTimerDialog = false },
                        onStartMinutes = vm::startSleepTimer,
                        onStartUntilTrackEnd = vm::startSleepTimerUntilTrackEnd,
                        onExtendFiveMinutes = { vm.extendSleepTimer(5) },
                        onCancelTimer = vm::cancelSleepTimer
                    )
                }

                editingSong?.let { targetSong ->
                    EditSongSheet(
                        song = targetSong,
                        pendingCoverUri = pendingCoverUri,
                        importedLyricsText = importedLyricsText,
                        onConsumeImportedLyrics = { importedLyricsText = null },
                        onPickCoverPhoto = {
                            pickCoverLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onImportLyricsFile = {
                            importLyricsLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                        },
                        onDismiss = {
                            pendingCoverUri = null
                            editingSong = null
                        },
                        onSave = { title, artist, album, lyrics, trimStartMs, trimEndMs, speed, pitch, removeCover ->
                            vm.saveSongEdit(
                                song = targetSong,
                                title = title,
                                artist = artist,
                                album = album,
                                lyrics = lyrics,
                                trimStartMs = trimStartMs,
                                trimEndMs = trimEndMs,
                                playbackSpeed = speed,
                                playbackPitch = pitch,
                                newCoverUri = pendingCoverUri,
                                removeCover = removeCover
                            )
                            pendingCoverUri = null
                            editingSong = null
                        },
                        onResetToOriginal = {
                            vm.resetSongEdit(targetSong)
                            pendingCoverUri = null
                            editingSong = null
                        },
                        loadCoverBitmap = vm::loadCoverBitmap
                    )
                }
            }
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}
