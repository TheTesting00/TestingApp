package com.thetesting.musicapp.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.thetesting.musicapp.MainActivity
import com.thetesting.musicapp.R
import com.thetesting.musicapp.data.MusicRepository
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
    private var currentCoverBitmap: Bitmap? = null
    private lateinit var repository: MusicRepository

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        repository = MusicRepository(applicationContext)
        createNotificationChannel()
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
        serviceScope.launch {
            currentCoverBitmap = repository.loadCoverBitmap(newCurrent, 256)
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
        updateNotification()
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        val clamped = positionMs.coerceIn(0L, _playbackState.value.durationMs.coerceAtLeast(1L))
        try {
            player.seekTo(clamped.toInt())
            _playbackState.update { it.copy(positionMs = clamped) }
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

        if (state.positionMs > 3000L) {
            seekTo(0L)
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
                updateNotification()
                true
            }
        }

        try {
            player.setDataSource(applicationContext, Uri.parse(song.uri))
            player.prepare()
            player.start()
            mediaPlayer = player

            val duration = player.duration.toLong().takeIf { it > 0L } ?: song.durationMs
            _playbackState.update {
                it.copy(
                    currentSong = song,
                    currentIndex = index,
                    isPlaying = true,
                    positionMs = 0L,
                    durationMs = duration,
                    errorMessage = null
                )
            }
            startProgressTicker()

            serviceScope.launch {
                currentCoverBitmap = repository.loadCoverBitmap(song, 256)
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

    private fun handleTrackCompleted() {
        val state = _playbackState.value
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
                    _playbackState.update { it.copy(positionMs = player.currentPosition.toLong()) }
                }
                delay(300L)
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
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayer() {
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(song.title)
            .setContentText("${song.artist} • ${song.album}")
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
        stopProgressTicker()
        releasePlayer()
        serviceScope.cancel()
        super.onDestroy()
    }
}
