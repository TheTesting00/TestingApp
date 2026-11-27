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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thetesting.musicapp.model.Song
import com.thetesting.musicapp.ui.MusicViewModel
import com.thetesting.musicapp.ui.screens.EditSongSheet
import com.thetesting.musicapp.ui.screens.LibraryScreen
import com.thetesting.musicapp.ui.screens.NowPlayingScreen
import com.thetesting.musicapp.ui.theme.MusikKuTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MusikKuTheme {
                val vm: MusicViewModel = viewModel()
                val songs by vm.filteredSongs.collectAsState()
                val searchQuery by vm.searchQuery.collectAsState()
                val selectedFilter by vm.selectedFilter.collectAsState()
                val hasAudioPermission by vm.hasAudioPermission.collectAsState()
                val isLoading by vm.isLoading.collectAsState()
                val playbackState by vm.playbackState.collectAsState()
                val statusMessage by vm.statusMessage.collectAsState()

                var showNowPlaying by remember { mutableStateOf(false) }
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

                LibraryScreen(
                    songs = songs,
                    searchQuery = searchQuery,
                    selectedFilter = selectedFilter,
                    hasAudioPermission = hasAudioPermission,
                    isLoading = isLoading,
                    playbackState = playbackState,
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
                    onSongClick = { song ->
                        vm.playSong(song)
                    },
                    onEditSongClick = { song ->
                        pendingCoverUri = null
                        importedLyricsText = null
                        editingSong = song
                    },
                    onOpenNowPlaying = { showNowPlaying = true },
                    onTogglePlayPause = vm::togglePlayPause,
                    onSkipNext = vm::skipNext,
                    loadCoverBitmap = vm::loadCoverBitmap
                )

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
                        loadCoverBitmap = vm::loadCoverBitmap
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
                        onSave = { title, artist, album, lyrics, removeCover ->
                            vm.saveSongEdit(
                                song = targetSong,
                                title = title,
                                artist = artist,
                                album = album,
                                lyrics = lyrics,
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
