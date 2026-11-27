package com.thetesting.musicapp.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.media.session.MediaSession
import android.media.session.PlaybackState as PlatformPlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.thetesting.musicapp.MainActivity
import com.thetesting.musicapp.R
import com.thetesting.musicapp.data.MusicRepository
import com.thetesting.musicapp.model.EarphoneConnectionType
import com.thetesting.musicapp.model.EarphoneSettings
import com.thetesting.musicapp.model.EarphoneStatus
import com.thetesting.musicapp.model.RepeatMode
import com.thetesting.musicapp.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaybackState(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val sleepTimerActive: Boolean = false,
    val sleepTimerRemainingMs: Long = 0L,
    val sleepTimerUntilTrackEnd: Boolean = false,
    val errorMessage: String? = null
)

class MusicPlaybackService : Service() {

    companion object {
        private const val CHANNEL_ID = "musikku_playback_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.thetesting.musicapp.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.thetesting.musicapp.ACTION_NEXT"
        const val ACTION_PREV = "com.thetesting.musicapp.ACTION_PREV"
        const val ACTION_STOP = "com.thetesting.musicapp.ACTION_STOP"
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    private val binder = MusicBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var sleepTimerEndRealtimeMs: Long = 0L
    private var currentCoverBitmap: Bitmap? = null
    private lateinit var repository: MusicRepository
    private lateinit var audioManager: AudioManager
    private var mediaSession: MediaSession? = null

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var attachedSessionId: Int = -1

    private var lastHeadsetClickTimeMs = 0L

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _earphoneStatus = MutableStateFlow(EarphoneStatus())
    val earphoneStatus: StateFlow<EarphoneStatus> = _earphoneStatus.asStateFlow()

    private val _earphoneSettings = MutableStateFlow(EarphoneSettings())
    val earphoneSettings: StateFlow<EarphoneSettings> = _earphoneSettings.asStateFlow()

    private val _eqBandLabels = MutableStateFlow(listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz"))
    val eqBandLabels: StateFlow<List<String>> = _eqBandLabels.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            val wasConnected = _earphoneStatus.value.isConnected
            refreshEarphoneStatus()
            val nowConnected = _earphoneStatus.value.isConnected
            if (!wasConnected && nowConnected) {
                applyAudioEffects()
                if (_earphoneSettings.value.autoPlayOnConnect &&
                    !_playbackState.value.isPlaying &&
                    _playbackState.value.currentSong != null
                ) {
                    togglePlayPause()
                }
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshEarphoneStatus()
            applyAudioEffects()
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (_earphoneSettings.value.autoPauseOnDisconnect && _playbackState.value.isPlaying) {
                    pauseFromExternal()
                }
                refreshEarphoneStatus()
                applyAudioEffects()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = MusicRepository(applicationContext)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        _earphoneSettings.value = repository.loadEarphoneSettingsSync()

        createNotificationChannel()
        initMediaSession()
        refreshEarphoneStatus()

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(noisyReceiver, filter)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> skipNext()
            ACTION_PREV -> skipPrevious()
            ACTION_STOP -> stopPlaybackAndSelf()
        }
        return START_NOT_STICKY
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "MusikKuSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (!_playbackState.value.isPlaying) togglePlayPause()
                }

                override fun onPause() {
                    if (_playbackState.value.isPlaying) togglePlayPause()
                }

                override fun onSkipToNext() {
                    skipNext()
                }

                override fun onSkipToPrevious() {
                    skipPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    seekTo(pos)
                }

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }
                    if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_HEADSETHOOK,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                val now = SystemClock.elapsedRealtime()
                                if (_earphoneSettings.value.doubleTapNextTrack && now - lastHeadsetClickTimeMs < 450L) {
                                    lastHeadsetClickTimeMs = 0L
                                    skipNext()
                                } else {
                                    lastHeadsetClickTimeMs = now
                                    togglePlayPause()
                                }
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                skipNext()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                skipPrevious()
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })
            isActive = true
        }
    }

    fun refreshEarphoneStatus() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bluetoothDevice = outputs.firstOrNull { device ->
            val isBleHeadset = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                isBleHeadset
        }
        val wiredDevice = outputs.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }

        val status = when {
            bluetoothDevice != null -> {
                val rawName = bluetoothDevice.productName?.toString()?.trim()
                val label = if (!rawName.isNullOrEmpty()) "$rawName (Bluetooth)" else "Earphone / TWS Bluetooth"
                EarphoneStatus(
                    isConnected = true,
                    type = EarphoneConnectionType.BLUETOOTH,
                    deviceName = label
                )
            }
            wiredDevice != null -> {
                val rawName = wiredDevice.productName?.toString()?.trim()
                val label = if (!rawName.isNullOrEmpty()) "$rawName (Kabel)" else "Earphone Kabel"
                EarphoneStatus(
                    isConnected = true,
                    type = EarphoneConnectionType.WIRED,
                    deviceName = label
                )
            }
            else -> EarphoneStatus(
                isConnected = false,
                type = EarphoneConnectionType.SPEAKER,
                deviceName = "Speaker HP"
            )
        }
        _earphoneStatus.value = status
    }

    fun updateEarphoneSettings(newSettings: EarphoneSettings) {
        _earphoneSettings.value = newSettings
        serviceScope.launch {
            repository.saveEarphoneSettings(newSettings)
        }
        applyAudioEffects()
    }

    fun setQueueAndPlay(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty() || startIndex !in songs.indices) return
        _playbackState.update {
            it.copy(queue = songs, currentIndex = startIndex, errorMessage = null)
        }
        playIndex(startIndex)
    }

    fun updateQueueMetadata(updatedSongs: List<Song>) {
        val current = _playbackState.value.currentSong ?: return
        val newCurrent = updatedSongs.find { it.id == current.id } ?: current
        val newIndex = updatedSongs.indexOfFirst { it.id == current.id }.takeIf { it >= 0 }
            ?: _playbackState.value.currentIndex
        _playbackState.update {
            it.copy(
                queue = if (updatedSongs.isNotEmpty()) updatedSongs else it.queue,
                currentSong = newCurrent,
                currentIndex = newIndex
            )
        }
        applySongPlaybackParams(newCurrent)
        serviceScope.launch {
            currentCoverBitmap = repository.loadCoverBitmap(newCurrent, 256)
            updateMediaSessionMetadata()
            updateNotification()
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer
        val current = _playbackState.value.currentSong
        if (player == null) {
            if (current != null && _playbackState.value.currentIndex >= 0) {
                playIndex(_playbackState.value.currentIndex)
            }
            return
        }
        if (player.isPlaying) {
            player.pause()
            _playbackState.update { it.copy(isPlaying = false, positionMs = player.currentPosition.toLong()) }
            stopProgressTicker()
        } else {
            player.start()
            _playbackState.update { it.copy(isPlaying = true) }
            startProgressTicker()
        }
        updateMediaSessionState()
        updateNotification()
    }

    private fun pauseFromExternal() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _playbackState.update { it.copy(isPlaying = false, positionMs = player.currentPosition.toLong()) }
            stopProgressTicker()
            updateMediaSessionState()
            updateNotification()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        val clamped = positionMs.coerceIn(0L, _playbackState.value.durationMs.coerceAtLeast(1L))
        try {
            player.seekTo(clamped.toInt())
            _playbackState.update { it.copy(positionMs = clamped) }
            updateMediaSessionState()
        } catch (_: Exception) {
        }
    }

    fun skipNext() {
        val state = _playbackState.value
        val queue = state.queue
        if (queue.isEmpty()) return

        val nextIndex = if (state.isShuffle && queue.size > 1) {
            val candidates = queue.indices.filter { it != state.currentIndex }
            candidates.random()
        } else {
            val candidate = state.currentIndex + 1
            if (candidate < queue.size) candidate else 0
        }
        playIndex(nextIndex)
    }

    fun skipPrevious() {
        val state = _playbackState.value
        val queue = state.queue
        if (queue.isEmpty()) return

        val startPoint = state.currentSong?.trimStartMs ?: 0L
        if (state.positionMs - startPoint > 3000L) {
            seekTo(startPoint)
            return
        }

        val prevIndex = if (state.isShuffle && queue.size > 1) {
            val candidates = queue.indices.filter { it != state.currentIndex }
            candidates.random()
        } else {
            if (state.currentIndex > 0) state.currentIndex - 1 else queue.lastIndex
        }
        playIndex(prevIndex)
    }

    fun toggleShuffle() {
        _playbackState.update { it.copy(isShuffle = !it.isShuffle) }
    }

    fun cycleRepeatMode() {
        _playbackState.update { state ->
            val next = when (state.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            state.copy(repeatMode = next)
        }
    }

    fun clearError() {
        _playbackState.update { it.copy(errorMessage = null) }
    }

    fun startSleepTimer(durationMinutes: Int, playIfPaused: Boolean) {
        val clampedMinutes = durationMinutes.coerceIn(1, 720)
        sleepTimerJob?.cancel()
        val totalMs = clampedMinutes * 60_000L
        sleepTimerEndRealtimeMs = SystemClock.elapsedRealtime() + totalMs

        if (playIfPaused && !_playbackState.value.isPlaying) {
            if (_playbackState.value.currentSong != null) {
                togglePlayPause()
            } else if (_playbackState.value.queue.isNotEmpty()) {
                playIndex(0)
            }
        }

        _playbackState.update {
            it.copy(
                sleepTimerActive = true,
                sleepTimerUntilTrackEnd = false,
                sleepTimerRemainingMs = totalMs
            )
        }
        updateNotification()

        sleepTimerJob = serviceScope.launch {
            while (true) {
                val remaining = (sleepTimerEndRealtimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                _playbackState.update { it.copy(sleepTimerRemainingMs = remaining) }
                if (remaining <= 0L) {
                    fadeOutAndPause()
                    break
                }
                delay(500L)
            }
        }
    }

    fun startSleepTimerUntilTrackEnd(playIfPaused: Boolean) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerEndRealtimeMs = 0L

        if (playIfPaused && !_playbackState.value.isPlaying) {
            if (_playbackState.value.currentSong != null) {
                togglePlayPause()
            } else if (_playbackState.value.queue.isNotEmpty()) {
                playIndex(0)
            }
        }

        _playbackState.update {
            it.copy(
                sleepTimerActive = true,
                sleepTimerUntilTrackEnd = true,
                sleepTimerRemainingMs = 0L
            )
        }
        updateNotification()
    }

    fun extendSleepTimer(extraMinutes: Int) {
        val state = _playbackState.value
        if (!state.sleepTimerActive || state.sleepTimerUntilTrackEnd) return
        val extraMs = extraMinutes.coerceAtLeast(1) * 60_000L
        sleepTimerEndRealtimeMs += extraMs
        val newRemaining = (sleepTimerEndRealtimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        _playbackState.update { it.copy(sleepTimerRemainingMs = newRemaining) }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerEndRealtimeMs = 0L
        try {
            mediaPlayer?.setVolume(1f, 1f)
        } catch (_: Exception) {
        }
        _playbackState.update {
            it.copy(
                sleepTimerActive = false,
                sleepTimerUntilTrackEnd = false,
                sleepTimerRemainingMs = 0L
            )
        }
        updateNotification()
    }

    private suspend fun fadeOutAndPause() {
        val player = mediaPlayer
        if (player != null && player.isPlaying) {
            val steps = 8
            for (i in steps downTo 1) {
                val vol = i / steps.toFloat()
                try {
                    mediaPlayer?.setVolume(vol, vol)
                } catch (_: Exception) {
                }
                delay(150L)
            }
        }
        pauseFromExternal()
        try {
            mediaPlayer?.setVolume(1f, 1f)
        } catch (_: Exception) {
        }
        _playbackState.update {
            it.copy(
                sleepTimerActive = false,
                sleepTimerUntilTrackEnd = false,
                sleepTimerRemainingMs = 0L
            )
        }
        updateNotification()
    }

    private fun playIndex(index: Int) {
        val queue = _playbackState.value.queue
        if (index !in queue.indices) return
        val song = queue[index]

        releasePlayer()

        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnCompletionListener {
                handleTrackCompleted()
            }
            setOnErrorListener { _, _, _ ->
                _playbackState.update {
                    it.copy(isPlaying = false, errorMessage = "Gagal memutar lagu: ${song.title}")
                }
                stopProgressTicker()
                updateMediaSessionState()
                updateNotification()
                true
            }
        }

        try {
            player.setDataSource(applicationContext, Uri.parse(song.uri))
            player.prepare()

            val duration = player.duration.toLong().takeIf { it > 0L } ?: song.durationMs
            val startMs = if (song.trimStartMs in 1L until duration) song.trimStartMs else 0L
            if (startMs > 0L) {
                player.seekTo(startMs.toInt())
            }

            mediaPlayer = player
            attachAudioEffects(player.audioSessionId)
            applySongPlaybackParams(song)
            player.start()
            applyAudioEffects()

            _playbackState.update {
                it.copy(
                    currentSong = song,
                    currentIndex = index,
                    isPlaying = true,
                    positionMs = startMs,
                    durationMs = duration,
                    errorMessage = null
                )
            }
            startProgressTicker()
            updateMediaSessionState()

            serviceScope.launch {
                currentCoverBitmap = repository.loadCoverBitmap(song, 256)
                updateMediaSessionMetadata()
                showForegroundNotification()
            }
            showForegroundNotification()
        } catch (e: Exception) {
            player.release()
            _playbackState.update {
                it.copy(
                    currentSong = song,
                    currentIndex = index,
                    isPlaying = false,
                    errorMessage = "File audio tidak dapat dibuka: ${song.title}"
                )
            }
        }
    }

    private fun applySongPlaybackParams(song: Song) {
        val player = mediaPlayer ?: return
        try {
            val params = PlaybackParams()
                .setSpeed(song.playbackSpeed.coerceIn(0.5f, 2.0f))
                .setPitch(song.playbackPitch.coerceIn(0.75f, 1.5f))
            val wasPlaying = player.isPlaying
            player.playbackParams = params
            if (!wasPlaying && player.isPlaying) {
                player.pause()
            }
        } catch (_: Exception) {
        }
    }

    private fun attachAudioEffects(sessionId: Int) {
        if (sessionId <= 0 || sessionId == attachedSessionId) return
        releaseAudioEffects()
        attachedSessionId = sessionId

        try {
            equalizer = Equalizer(0, sessionId).apply {
                val count = numberOfBands.toInt().coerceAtMost(5)
                if (count > 0) {
                    val labels = (0 until count).map { idx ->
                        val hz = getCenterFreq(idx.toShort()) / 1000
                        if (hz >= 1000) "${hz / 1000}kHz" else "${hz}Hz"
                    }
                    _eqBandLabels.value = labels
                }
            }
        } catch (_: Exception) {
            equalizer = null
        }

        try {
            bassBoost = BassBoost(0, sessionId)
        } catch (_: Exception) {
            bassBoost = null
        }

        try {
            virtualizer = Virtualizer(0, sessionId)
        } catch (_: Exception) {
            virtualizer = null
        }

        try {
            loudnessEnhancer = LoudnessEnhancer(sessionId)
        } catch (_: Exception) {
            loudnessEnhancer = null
        }
    }

    private fun applyAudioEffects() {
        val settings = _earphoneSettings.value
        val connected = _earphoneStatus.value.isConnected
        val shouldEnable = settings.enabled && (!settings.autoActivateOnConnect || connected)

        try {
            equalizer?.let { eq ->
                eq.enabled = shouldEnable
                if (shouldEnable) {
                    val range = eq.bandLevelRange
                    val minLvl = range[0].toInt()
                    val maxLvl = range[1].toInt()
                    val bands = eq.numberOfBands.toInt()
                    for (i in 0 until bands) {
                        val normalized = settings.bandValues.getOrElse(i) { 0 }.coerceIn(-100, 100)
                        val target = if (normalized >= 0) {
                            (normalized / 100f * maxLvl).toInt()
                        } else {
                            (-normalized / 100f * minLvl).toInt()
                        }
                        eq.setBandLevel(i.toShort(), target.coerceIn(minLvl, maxLvl).toShort())
                    }
                }
            }
        } catch (_: Exception) {
        }

        try {
            bassBoost?.let { bb ->
                bb.enabled = shouldEnable && settings.bassBoostPercent > 0
                if (bb.enabled && bb.strengthSupported) {
                    val strength = (settings.bassBoostPercent.coerceIn(0, 100) * 10).toShort()
                    bb.setStrength(strength)
                }
            }
        } catch (_: Exception) {
        }

        try {
            virtualizer?.let { virt ->
                virt.enabled = shouldEnable && settings.virtualizerPercent > 0
                if (virt.enabled && virt.strengthSupported) {
                    val strength = (settings.virtualizerPercent.coerceIn(0, 100) * 10).toShort()
                    virt.setStrength(strength)
                }
            }
        } catch (_: Exception) {
        }

        try {
            loudnessEnhancer?.let { le ->
                le.enabled = shouldEnable && settings.loudnessGainPercent > 0
                if (le.enabled) {
                    val gainMb = settings.loudnessGainPercent.coerceIn(0, 100) * 15 // up to +1500 mB (15 dB)
                    le.setTargetGain(gainMb)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseAudioEffects() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        attachedSessionId = -1
    }

    private fun handleTrackCompleted() {
        val state = _playbackState.value
        if (state.sleepTimerActive && state.sleepTimerUntilTrackEnd) {
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    positionMs = it.durationMs,
                    sleepTimerActive = false,
                    sleepTimerUntilTrackEnd = false,
                    sleepTimerRemainingMs = 0L
                )
            }
            stopProgressTicker()
            updateMediaSessionState()
            updateNotification()
            return
        }
        when (state.repeatMode) {
            RepeatMode.ONE -> playIndex(state.currentIndex)
            RepeatMode.ALL -> skipNext()
            RepeatMode.OFF -> {
                if (state.isShuffle || state.currentIndex < state.queue.lastIndex) {
                    skipNext()
                } else {
                    _playbackState.update {
                        it.copy(isPlaying = false, positionMs = it.durationMs)
                    }
                    stopProgressTicker()
                    updateMediaSessionState()
                    updateNotification()
                }
            }
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (true) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    val pos = player.currentPosition.toLong()
                    _playbackState.update { it.copy(positionMs = pos) }

                    val current = _playbackState.value.currentSong
                    if (current != null && current.trimEndMs > current.trimStartMs && pos >= current.trimEndMs) {
                        handleTrackCompleted()
                    }
                }
                delay(250L)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun stopPlaybackAndSelf() {
        stopProgressTicker()
        releasePlayer()
        _playbackState.update { it.copy(isPlaying = false, positionMs = 0L) }
        updateMediaSessionState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayer() {
        releaseAudioEffects()
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    private fun updateMediaSessionMetadata() {
        val song = _playbackState.value.currentSong ?: return
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, song.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, _playbackState.value.durationMs)
            .apply {
                currentCoverBitmap?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
            }
            .build()
        mediaSession?.setMetadata(metadata)
    }

    private fun updateMediaSessionState() {
        val state = _playbackState.value
        val platformState = if (state.isPlaying) {
            PlatformPlaybackState.STATE_PLAYING
        } else {
            PlatformPlaybackState.STATE_PAUSED
        }
        val speed = state.currentSong?.playbackSpeed ?: 1.0f
        val actions = PlatformPlaybackState.ACTION_PLAY or
            PlatformPlaybackState.ACTION_PAUSE or
            PlatformPlaybackState.ACTION_PLAY_PAUSE or
            PlatformPlaybackState.ACTION_SKIP_TO_NEXT or
            PlatformPlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlatformPlaybackState.ACTION_SEEK_TO

        mediaSession?.setPlaybackState(
            PlatformPlaybackState.Builder()
                .setActions(actions)
                .setState(platformState, state.positionMs, speed)
                .build()
        )
    }

    private fun showForegroundNotification() {
        val notification = buildNotification() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notification = buildNotification() ?: return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification? {
        val state = _playbackState.value
        val song = state.currentSong ?: return null

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevPending = serviceActionPendingIntent(ACTION_PREV, 1)
        val playPausePending = serviceActionPendingIntent(ACTION_PLAY_PAUSE, 2)
        val nextPending = serviceActionPendingIntent(ACTION_NEXT, 3)

        val playPauseLabel = if (state.isPlaying) "Jeda" else "Putar"
        val playPauseIcon = if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val timerBadge = when {
            state.sleepTimerActive && state.sleepTimerUntilTrackEnd -> " • ⏱ Akhir lagu"
            state.sleepTimerActive && state.sleepTimerRemainingMs > 0L ->
                " • ⏱ ${com.thetesting.musicapp.model.formatDuration(state.sleepTimerRemainingMs)}"
            else -> ""
        }
        val statusBadge = if (_earphoneStatus.value.isConnected) {
            "🎧 ${_earphoneStatus.value.deviceName}$timerBadge"
        } else {
            "${song.artist} • ${song.album}$timerBadge"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(song.title)
            .setContentText(statusBadge)
            .setLargeIcon(currentCoverBitmap)
            .setContentIntent(openAppIntent)
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Sebelumnya", prevPending)
            .addAction(playPauseIcon, playPauseLabel, playPausePending)
            .addAction(android.R.drawable.ic_media_next, "Berikutnya", nextPending)
            .build()
    }

    private fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pemutar Musik",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kontrol pemutaran musik MusikKu"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) } catch (_: Exception) {}
        try { unregisterReceiver(noisyReceiver) } catch (_: Exception) {}
        sleepTimerJob?.cancel()
        mediaSession?.release()
        stopProgressTicker()
        releasePlayer()
        serviceScope.cancel()
        super.onDestroy()
    }
}
