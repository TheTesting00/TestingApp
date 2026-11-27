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
import com.thetesting.musicapp.model.AppAppearanceSettings
import com.thetesting.musicapp.model.AppThemeId
import com.thetesting.musicapp.model.EarphoneSettings
import com.thetesting.musicapp.model.EarphoneStatus
import com.thetesting.musicapp.model.EqPreset
import com.thetesting.musicapp.model.NavBarStyleId
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

    private val _appearance = MutableStateFlow(AppAppearanceSettings())
    val appearance: StateFlow<AppAppearanceSettings> = _appearance.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _earphoneStatus = MutableStateFlow(EarphoneStatus())
    val earphoneStatus: StateFlow<EarphoneStatus> = _earphoneStatus.asStateFlow()

    private val _earphoneSettings = MutableStateFlow(repository.loadEarphoneSettingsSync())
    val earphoneSettings: StateFlow<EarphoneSettings> = _earphoneSettings.asStateFlow()

    private val _eqBandLabels = MutableStateFlow(listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz"))
    val eqBandLabels: StateFlow<List<String>> = _eqBandLabels.asStateFlow()

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
    private var serviceJobs: List<Job> = emptyList()
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MusicPlaybackService.MusicBinder ?: return
            val svc = binder.getService()
            playbackService = svc
            isBound = true
            serviceJobs.forEach { it.cancel() }
            serviceJobs = listOf(
                viewModelScope.launch {
                    svc.playbackState.collect { _playbackState.value = it }
                },
                viewModelScope.launch {
                    svc.earphoneStatus.collect { _earphoneStatus.value = it }
                },
                viewModelScope.launch {
                    svc.earphoneSettings.collect { _earphoneSettings.value = it }
                },
                viewModelScope.launch {
                    svc.eqBandLabels.collect { _eqBandLabels.value = it }
                }
            )
            svc.refreshEarphoneStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            isBound = false
            serviceJobs.forEach { it.cancel() }
            serviceJobs = emptyList()
        }
    }

    init {
        viewModelScope.launch {
            _appearance.value = repository.loadAppearanceSettings()
        }
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

    fun selectTheme(themeId: AppThemeId) {
        val updated = _appearance.value.copy(themeId = themeId)
        _appearance.value = updated
        viewModelScope.launch { repository.saveAppearanceSettings(updated) }
        _statusMessage.value = "Tema ${themeId.displayName} diterapkan"
    }

    fun selectNavBarStyle(styleId: NavBarStyleId) {
        val updated = _appearance.value.copy(navBarStyleId = styleId)
        _appearance.value = updated
        viewModelScope.launch { repository.saveAppearanceSettings(updated) }
        _statusMessage.value = "Gaya navigasi ${styleId.displayName} diterapkan"
    }

    fun updateEarphoneSettings(settings: EarphoneSettings) {
        _earphoneSettings.value = settings
        playbackService?.updateEarphoneSettings(settings)
        if (playbackService == null) {
            viewModelScope.launch { repository.saveEarphoneSettings(settings) }
        }
    }

    fun selectEqPreset(preset: EqPreset) {
        val current = _earphoneSettings.value
        val updated = if (preset == EqPreset.CUSTOM) {
            current.copy(preset = preset)
        } else {
            current.copy(preset = preset, bandValues = preset.bandLevels)
        }
        updateEarphoneSettings(updated)
    }

    fun updateEqBand(index: Int, value: Int) {
        val current = _earphoneSettings.value
        val bands = current.bandValues.toMutableList()
        if (index in bands.indices) {
            bands[index] = value.coerceIn(-100, 100)
            updateEarphoneSettings(current.copy(preset = EqPreset.CUSTOM, bandValues = bands))
        }
    }

    fun refreshEarphoneStatus() {
        playbackService?.refreshEarphoneStatus()
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
        trimStartMs: Long,
        trimEndMs: Long,
        playbackSpeed: Float,
        playbackPitch: Float,
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
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                playbackSpeed = playbackSpeed,
                playbackPitch = playbackPitch,
                newCoverUri = newCoverUri,
                removeCover = removeCover
            )
            val loaded = repository.loadSongs(_hasAudioPermission.value)
            _songs.value = loaded
            playbackService?.updateQueueMetadata(loaded)
            _statusMessage.value = "Perubahan musik & lirik disimpan"
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
