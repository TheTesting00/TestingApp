package com.thetesting.musicapp.model

data class Song(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumId: Long? = null,
    val customCoverPath: String? = null,
    val lyrics: String = "",
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val isEdited: Boolean = false,
    val isCustomAdded: Boolean = false
)

data class SongOverride(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val customCoverPath: String? = null,
    val lyrics: String = "",
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f
)

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

data class LrcLine(
    val timeMs: Long,
    val text: String
)

private val LRC_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?](.*)""")

fun parseLrcLyrics(raw: String): List<LrcLine> {
    if (raw.isBlank()) return emptyList()
    val result = mutableListOf<LrcLine>()
    raw.lineSequence().forEach { line ->
        val trimmed = line.trim()
        val match = LRC_REGEX.find(trimmed)
        if (match != null) {
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fracStr = match.groupValues[3]
            val millis = when (fracStr.length) {
                0 -> 0L
                1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                else -> fracStr.take(3).toLongOrNull() ?: 0L
            }
            val lyricText = match.groupValues[4].trim()
            if (lyricText.isNotEmpty()) {
                result.add(LrcLine(timeMs = minutes * 60_000L + seconds * 1_000L + millis, text = lyricText))
            }
        }
    }
    return result.sortedBy { it.timeMs }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
