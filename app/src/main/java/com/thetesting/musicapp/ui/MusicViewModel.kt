package com.thetesting.musicapp.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thetesting.musicapp.data.MusicRepository
import com.thetesting.musicapp.model.Song
import com.thetesting.musicapp.player.MusicPlaybackService
import com.thetesting.musicapp.player.PlaybackState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) {
    ALL("Semua"),
    EDITED("Diedit"),
    WITH_LYRICS("Berlirik")
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application.applicationContext)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(LibraryFilter.ALL)
    val selectedFilter: StateFlow<LibraryFilter> = _selectedFilter.asStateFlow()

    private val _hasAudioPermission = MutableStateFlow(false)
    val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    val filteredSongs: StateFlow<List<Song>> = combine(
        _songs,
        _searchQuery,
        _selectedFilter
    ) { allSongs, query, filter ->
        val byFilter = when (filter) {
            LibraryFilter.ALL -> allSongs
            LibraryFilter.EDITED -> allSongs.filter { it.isEdited }
            LibraryFilter.WITH_LYRICS -> allSongs.filter { it.lyrics.isNotBlank() }
        }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            byFilter
        } else {
            byFilter.filter { song ->
                song.title.contains(trimmed, ignoreCase = true) ||
                    song.artist.contains(trimmed, ignoreCase = true) ||
                    song.album.contains(trimmed, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var playbackService: MusicPlaybackService? = null
    private var serviceStateJob: Job? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MusicPlaybackService.MusicBinder ?: return
            val svc = binder.getService()
            playbackService = svc
            isBound = true
            serviceStateJob?.cancel()
            serviceStateJob = viewModelScope.launch {
                svc.playbackState.collect { state ->
                    _playbackState.value = state
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            isBound = false
            serviceStateJob?.cancel()
        }
    }

    init {
        bindPlaybackService()
    }

    private fun bindPlaybackService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, MusicPlaybackService::class.java)
        ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun setAudioPermissionGranted(granted: Boolean) {
        _hasAudioPermission.value = granted
        refreshSongs()
    }

    fun refreshSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            val loaded = repository.loadSongs(_hasAudioPermission.value)
            _songs.value = loaded
            playbackService?.updateQueueMetadata(loaded)
            _isLoading.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFilter(filter: LibraryFilter) {
        _selectedFilter.value = filter
    }

    fun addCustomAudioUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            var addedCount = 0
            uris.forEach { uri ->
                if (repository.addCustomAudioUri(uri) != null) {
                    addedCount++
                }
            }
            val loaded = repository.loadSongs(_hasAudioPermission.value)
            _songs.value = loaded
            playbackService?.updateQueueMetadata(loaded)
            _isLoading.value = false
            if (addedCount > 0) {
                _statusMessage.value = "$addedCount lagu berhasil ditambahkan"
            }
        }
    }

    fun playSong(song: Song) {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, MusicPlaybackService::class.java)
        ctx.startService(intent)

        val queue = filteredSongs.value.ifEmpty { _songs.value }
        val index = queue.indexOfFirst { it.id == song.id }.takeIf { it >= 0 } ?: 0
        playbackService?.setQueueAndPlay(queue, index)
    }

    fun togglePlayPause() {
        playbackService?.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playbackService?.seekTo(positionMs)
    }

    fun skipNext() {
        playbackService?.skipNext()
    }

    fun skipPrevious() {
        playbackService?.skipPrevious()
    }

    fun toggleShuffle() {
        playbackService?.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playbackService?.cycleRepeatMode()
    }

    fun clearPlaybackError() {
        playbackService?.clearError()
    }

    fun saveSongEdit(
        song: Song,
        title: String,
        artist: String,
        album: String,
        lyrics: String,
        newCoverUri: Uri?,
        removeCover: Boolean
    ) {
        viewModelScope.launch {
            repository.saveSongEdit(
                songId = song.id,
                title = title,
                artist = artist,
                album = album,
                lyrics = lyrics,
                newCoverUri = newCoverUri,
                removeCover = removeCover
            )
            val loaded = repository.loadSongs(_hasAudioPermission.value)
            _songs.value = loaded
            playbackService?.updateQueueMetadata(loaded)
            _statusMessage.value = "Perubahan lagu disimpan"
        }
    }

    fun resetSongEdit(song: Song) {
        viewModelScope.launch {
            repository.resetSongEdit(song.id)
            val loaded = repository.loadSongs(_hasAudioPermission.value)
            _songs.value = loaded
            playbackService?.updateQueueMetadata(loaded)
            _statusMessage.value = "Lagu dikembalikan ke data asli"
        }
    }

    suspend fun loadCoverBitmap(song: Song, maxSizePx: Int = 600): Bitmap? {
        return repository.loadCoverBitmap(song, maxSizePx)
    }

    suspend fun readLyricsFromUri(uri: Uri): String {
        return repository.readLyricsFromUri(uri)
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (_: Exception) {
            }
            isBound = false
        }
        super.onCleared()
    }
}
