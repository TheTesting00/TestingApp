package com.thetesting.musicapp.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.thetesting.musicapp.model.AppAppearanceSettings
import com.thetesting.musicapp.model.AppThemeId
import com.thetesting.musicapp.model.EarphoneSettings
import com.thetesting.musicapp.model.EqPreset
import com.thetesting.musicapp.model.NavBarStyleId
import com.thetesting.musicapp.model.Song
import com.thetesting.musicapp.model.SongOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class MusicRepository(private val context: Context) {

    private val overridesFile = File(context.filesDir, "song_overrides.json")
    private val customSongsFile = File(context.filesDir, "custom_songs.json")
    private val appearanceFile = File(context.filesDir, "appearance_settings.json")
    private val earphoneFile = File(context.filesDir, "earphone_settings.json")
    private val coversDir = File(context.filesDir, "covers").apply { mkdirs() }

    suspend fun loadSongs(hasStoragePermission: Boolean): List<Song> = withContext(Dispatchers.IO) {
        val overrides = readOverrides()
        val songs = mutableListOf<Song>()

        if (hasStoragePermission) {
            songs.addAll(queryMediaStoreSongs(overrides))
        }

        val existingUris = songs.mapTo(HashSet()) { it.uri }
        val customSongs = readCustomSongs(overrides).filterNot { it.uri in existingUris }
        songs.addAll(customSongs)

        songs.sortedBy { it.title.lowercase() }
    }

    private fun queryMediaStoreSongs(overrides: Map<String, SongOverride>): List<Song> {
        val list = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.IS_MUSIC
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 3000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val songId = "ms:$mediaId"
                    val contentUri = ContentUris.withAppendedId(collection, mediaId).toString()
                    val baseTitle = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Lagu Tanpa Judul"
                    val rawArtist = cursor.getString(artistCol)
                    val baseArtist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") "Artis Tidak Diketahui" else rawArtist
                    val rawAlbum = cursor.getString(albumCol)
                    val baseAlbum = if (rawAlbum.isNullOrBlank() || rawAlbum == "<unknown>") "Album Umum" else rawAlbum
                    val durationMs = cursor.getLong(durationCol).coerceAtLeast(0L)
                    val albumId = cursor.getLong(albumIdCol)

                    val override = overrides[songId]
                    list.add(
                        applyOverride(
                            base = Song(
                                id = songId,
                                uri = contentUri,
                                title = baseTitle,
                                artist = baseArtist,
                                album = baseAlbum,
                                durationMs = durationMs,
                                albumId = albumId
                            ),
                            override = override
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    suspend fun addCustomAudioUri(uri: Uri): Song? = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        val songId = "doc:${sha256Short(uriString)}"

        var title = queryDisplayName(uri)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Lagu Pilihan"
        var artist = "Artis Tidak Diketahui"
        var album = "Koleksi Sendiri"
        var durationMs = 0L

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }?.let { album = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.let { durationMs = it }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        val entry = JSONObject().apply {
            put("id", songId)
            put("uri", uriString)
            put("title", title)
            put("artist", artist)
            put("album", album)
            put("durationMs", durationMs)
        }

        val currentArray = readCustomSongsArray()
        val updatedArray = JSONArray()
        for (i in 0 until currentArray.length()) {
            val item = currentArray.optJSONObject(i) ?: continue
            if (item.optString("uri") != uriString) {
                updatedArray.put(item)
            }
        }
        updatedArray.put(entry)
        customSongsFile.writeText(updatedArray.toString())

        val override = readOverrides()[songId]
        applyOverride(
            base = Song(
                id = songId,
                uri = uriString,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                isCustomAdded = true
            ),
            override = override
        )
    }

    suspend fun saveSongEdit(
        songId: String,
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
    ): SongOverride = withContext(Dispatchers.IO) {
        val allOverrides = readOverrides().toMutableMap()
        val existing = allOverrides[songId]

        var coverPath = existing?.customCoverPath
        if (removeCover) {
            coverPath?.let { File(it).delete() }
            coverPath = null
        }
        if (newCoverUri != null) {
            val savedPath = saveResizedCover(songId, newCoverUri)
            if (savedPath != null) {
                coverPath = savedPath
            }
        }

        val updated = SongOverride(
            title = title.trim().takeIf { it.isNotEmpty() },
            artist = artist.trim().takeIf { it.isNotEmpty() },
            album = album.trim().takeIf { it.isNotEmpty() },
            customCoverPath = coverPath,
            lyrics = lyrics.trimEnd(),
            trimStartMs = trimStartMs.coerceAtLeast(0L),
            trimEndMs = trimEndMs.coerceAtLeast(0L),
            playbackSpeed = playbackSpeed.coerceIn(0.5f, 2.0f),
            playbackPitch = playbackPitch.coerceIn(0.75f, 1.5f)
        )
        allOverrides[songId] = updated
        writeOverrides(allOverrides)
        updated
    }

    suspend fun resetSongEdit(songId: String) = withContext(Dispatchers.IO) {
        val allOverrides = readOverrides().toMutableMap()
        val removed = allOverrides.remove(songId)
        removed?.customCoverPath?.let { File(it).delete() }
        writeOverrides(allOverrides)
    }

    suspend fun loadAppearanceSettings(): AppAppearanceSettings = withContext(Dispatchers.IO) {
        if (!appearanceFile.exists()) return@withContext AppAppearanceSettings()
        try {
            val obj = JSONObject(appearanceFile.readText())
            val theme = AppThemeId.entries.find { it.name == obj.optString("themeId") }
                ?: AppThemeId.MIDNIGHT_EMERALD
            val navStyle = NavBarStyleId.entries.find { it.name == obj.optString("navBarStyleId") }
                ?: NavBarStyleId.FLOATING_DETACHED
            AppAppearanceSettings(themeId = theme, navBarStyleId = navStyle)
        } catch (_: Exception) {
            AppAppearanceSettings()
        }
    }

    suspend fun saveAppearanceSettings(settings: AppAppearanceSettings) = withContext(Dispatchers.IO) {
        val obj = JSONObject().apply {
            put("themeId", settings.themeId.name)
            put("navBarStyleId", settings.navBarStyleId.name)
        }
        appearanceFile.writeText(obj.toString())
    }

    fun loadEarphoneSettingsSync(): EarphoneSettings {
        if (!earphoneFile.exists()) return EarphoneSettings()
        return try {
            val obj = JSONObject(earphoneFile.readText())
            val preset = EqPreset.entries.find { it.name == obj.optString("preset") }
                ?: EqPreset.BASS_BOOST
            val bandsArr = obj.optJSONArray("bandValues")
            val bands = if (bandsArr != null && bandsArr.length() == 5) {
                List(5) { idx -> bandsArr.optInt(idx, 0).coerceIn(-100, 100) }
            } else {
                preset.bandLevels
            }
            EarphoneSettings(
                enabled = obj.optBoolean("enabled", true),
                autoActivateOnConnect = obj.optBoolean("autoActivateOnConnect", true),
                autoPauseOnDisconnect = obj.optBoolean("autoPauseOnDisconnect", true),
                autoPlayOnConnect = obj.optBoolean("autoPlayOnConnect", false),
                preset = preset,
                bandValues = bands,
                bassBoostPercent = obj.optInt("bassBoostPercent", 45).coerceIn(0, 100),
                virtualizerPercent = obj.optInt("virtualizerPercent", 35).coerceIn(0, 100),
                loudnessGainPercent = obj.optInt("loudnessGainPercent", 20).coerceIn(0, 100),
                doubleTapNextTrack = obj.optBoolean("doubleTapNextTrack", true)
            )
        } catch (_: Exception) {
            EarphoneSettings()
        }
    }

    suspend fun saveEarphoneSettings(settings: EarphoneSettings) = withContext(Dispatchers.IO) {
        val obj = JSONObject().apply {
            put("enabled", settings.enabled)
            put("autoActivateOnConnect", settings.autoActivateOnConnect)
            put("autoPauseOnDisconnect", settings.autoPauseOnDisconnect)
            put("autoPlayOnConnect", settings.autoPlayOnConnect)
            put("preset", settings.preset.name)
            put("bandValues", JSONArray(settings.bandValues))
            put("bassBoostPercent", settings.bassBoostPercent)
            put("virtualizerPercent", settings.virtualizerPercent)
            put("loudnessGainPercent", settings.loudnessGainPercent)
            put("doubleTapNextTrack", settings.doubleTapNextTrack)
        }
        earphoneFile.writeText(obj.toString())
    }

    suspend fun readLyricsFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun loadCoverBitmap(song: Song, maxSizePx: Int = 600): Bitmap? = withContext(Dispatchers.IO) {
        song.customCoverPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                decodeSampledBitmapFromFile(file, maxSizePx)?.let { return@withContext it }
            }
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(song.uri))
            val embedded = retriever.embeddedPicture
            if (embedded != null) {
                decodeSampledBitmapFromBytes(embedded, maxSizePx)?.let { return@withContext it }
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        song.albumId?.let { albumId ->
            try {
                val artUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
                context.contentResolver.openInputStream(artUri)?.use { stream ->
                    val bytes = stream.readBytes()
                    decodeSampledBitmapFromBytes(bytes, maxSizePx)?.let { return@withContext it }
                }
            } catch (_: Exception) {
            }
        }

        null
    }

    private fun saveResizedCover(songId: String, imageUri: Uri): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() } ?: return null
            val bitmap = decodeSampledBitmapFromBytes(bytes, 1024) ?: return null
            val targetFile = File(coversDir, "${sha256Short(songId)}.jpg")
            FileOutputStream(targetFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            targetFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun applyOverride(base: Song, override: SongOverride?): Song {
        if (override == null) return base
        val hasValidCover = override.customCoverPath?.let { File(it).exists() } == true
        val hasAudioTweaks = override.trimStartMs > 0L ||
            override.trimEndMs > 0L ||
            kotlin.math.abs(override.playbackSpeed - 1.0f) > 0.01f ||
            kotlin.math.abs(override.playbackPitch - 1.0f) > 0.01f
        val edited = (override.title != null || override.artist != null || override.album != null ||
            hasValidCover || override.lyrics.isNotBlank() || hasAudioTweaks)
        return base.copy(
            title = override.title ?: base.title,
            artist = override.artist ?: base.artist,
            album = override.album ?: base.album,
            customCoverPath = if (hasValidCover) override.customCoverPath else null,
            lyrics = override.lyrics,
            trimStartMs = override.trimStartMs,
            trimEndMs = override.trimEndMs,
            playbackSpeed = override.playbackSpeed,
            playbackPitch = override.playbackPitch,
            isEdited = edited
        )
    }

    private fun readOverrides(): Map<String, SongOverride> {
        if (!overridesFile.exists()) return emptyMap()
        return try {
            val root = JSONObject(overridesFile.readText())
            val map = mutableMapOf<String, SongOverride>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.optJSONObject(key) ?: continue
                map[key] = SongOverride(
                    title = obj.optString("title").takeIf { it.isNotEmpty() },
                    artist = obj.optString("artist").takeIf { it.isNotEmpty() },
                    album = obj.optString("album").takeIf { it.isNotEmpty() },
                    customCoverPath = obj.optString("customCoverPath").takeIf { it.isNotEmpty() },
                    lyrics = obj.optString("lyrics", ""),
                    trimStartMs = obj.optLong("trimStartMs", 0L),
                    trimEndMs = obj.optLong("trimEndMs", 0L),
                    playbackSpeed = obj.optDouble("playbackSpeed", 1.0).toFloat().coerceIn(0.5f, 2.0f),
                    playbackPitch = obj.optDouble("playbackPitch", 1.0).toFloat().coerceIn(0.75f, 1.5f)
                )
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeOverrides(map: Map<String, SongOverride>) {
        val root = JSONObject()
        map.forEach { (id, item) ->
            val obj = JSONObject().apply {
                if (item.title != null) put("title", item.title)
                if (item.artist != null) put("artist", item.artist)
                if (item.album != null) put("album", item.album)
                if (item.customCoverPath != null) put("customCoverPath", item.customCoverPath)
                if (item.lyrics.isNotEmpty()) put("lyrics", item.lyrics)
                if (item.trimStartMs > 0L) put("trimStartMs", item.trimStartMs)
                if (item.trimEndMs > 0L) put("trimEndMs", item.trimEndMs)
                if (kotlin.math.abs(item.playbackSpeed - 1.0f) > 0.01f) put("playbackSpeed", item.playbackSpeed.toDouble())
                if (kotlin.math.abs(item.playbackPitch - 1.0f) > 0.01f) put("playbackPitch", item.playbackPitch.toDouble())
            }
            root.put(id, obj)
        }
        overridesFile.writeText(root.toString())
    }

    private fun readCustomSongsArray(): JSONArray {
        if (!customSongsFile.exists()) return JSONArray()
        return try {
            JSONArray(customSongsFile.readText())
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun readCustomSongs(overrides: Map<String, SongOverride>): List<Song> {
        val array = readCustomSongsArray()
        val result = mutableListOf<Song>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val uri = obj.optString("uri")
            if (id.isBlank() || uri.isBlank()) continue
            val base = Song(
                id = id,
                uri = uri,
                title = obj.optString("title", "Lagu Pilihan"),
                artist = obj.optString("artist", "Artis Tidak Diketahui"),
                album = obj.optString("album", "Koleksi Sendiri"),
                durationMs = obj.optLong("durationMs", 0L),
                isCustomAdded = true
            )
            result.add(applyOverride(base, overrides[id]))
        }
        return result
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampledBitmapFromBytes(bytes: ByteArray, reqSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds, reqSize, reqSize)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun decodeSampledBitmapFromFile(file: File, reqSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds, reqSize, reqSize)
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun sha256Short(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
