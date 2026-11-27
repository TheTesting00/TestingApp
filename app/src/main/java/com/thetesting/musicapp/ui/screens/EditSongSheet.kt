package com.thetesting.musicapp.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.thetesting.musicapp.model.Song
import com.thetesting.musicapp.model.formatDuration
import com.thetesting.musicapp.ui.components.AlbumArtImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSongSheet(
    song: Song,
    pendingCoverUri: Uri?,
    importedLyricsText: String?,
    onConsumeImportedLyrics: () -> Unit,
    onPickCoverPhoto: () -> Unit,
    onImportLyricsFile: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        artist: String,
        album: String,
        lyrics: String,
        trimStartMs: Long,
        trimEndMs: Long,
        playbackSpeed: Float,
        playbackPitch: Float,
        removeCover: Boolean
    ) -> Unit,
    onResetToOriginal: () -> Unit,
    loadCoverBitmap: suspend (Song, Int) -> Bitmap?
) {
    var title by remember(song.id) { mutableStateOf(song.title) }
    var artist by remember(song.id) { mutableStateOf(song.artist) }
    var album by remember(song.id) { mutableStateOf(song.album) }
    var lyrics by remember(song.id) { mutableStateOf(song.lyrics) }
    var removeCover by remember(song.id) { mutableStateOf(false) }

    val totalDuration = song.durationMs.coerceAtLeast(0L)
    var trimStartMs by remember(song.id) { mutableLongStateOf(song.trimStartMs.coerceIn(0L, totalDuration)) }
    var trimEndMs by remember(song.id) {
        val initialEnd = if (song.trimEndMs > song.trimStartMs && song.trimEndMs <= totalDuration) {
            song.trimEndMs
        } else {
            totalDuration
        }
        mutableLongStateOf(initialEnd)
    }
    var playbackSpeed by remember(song.id) { mutableFloatStateOf(song.playbackSpeed.coerceIn(0.5f, 2.0f)) }
    var playbackPitch by remember(song.id) { mutableFloatStateOf(song.playbackPitch.coerceIn(0.75f, 1.5f)) }

    if (importedLyricsText != null) {
        lyrics = importedLyricsText
        onConsumeImportedLyrics()
    }

    val context = LocalContext.current
    val previewNewCoverBitmap by produceState<Bitmap?>(initialValue = null, key1 = pendingCoverUri) {
        value = if (pendingCoverUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(pendingCoverUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } catch (_: Exception) {
                    null
                }
            }
        } else null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Batal")
                        }
                    },
                    title = {
                        Text(
                            text = "Edit Musik, Cover & Lirik",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                val normalizedEnd = if (totalDuration > 0L && trimEndMs >= totalDuration - 500L) 0L else trimEndMs
                                onSave(
                                    title,
                                    artist,
                                    album,
                                    lyrics,
                                    trimStartMs,
                                    normalizedEnd,
                                    playbackSpeed,
                                    playbackPitch,
                                    removeCover
                                )
                            }
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simpan", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Cover Editor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        val pickedBitmap = previewNewCoverBitmap
                        when {
                            pickedBitmap != null -> {
                                Image(
                                    bitmap = pickedBitmap.asImageBitmap(),
                                    contentDescription = "Foto album baru",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            removeCover -> {
                                AlbumArtImage(
                                    song = song.copy(customCoverPath = null, albumId = null),
                                    loadBitmap = { _, _ -> null },
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 16.dp
                                )
                            }
                            else -> {
                                AlbumArtImage(
                                    song = song,
                                    loadBitmap = loadCoverBitmap,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 16.dp,
                                    maxSizePx = 300,
                                    iconSize = 40.dp
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Foto Album / Cover",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        FilledTonalButton(
                            onClick = {
                                removeCover = false
                                onPickCoverPhoto()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pilih Foto dari Galeri")
                        }
                        if (song.customCoverPath != null || pendingCoverUri != null) {
                            OutlinedButton(
                                onClick = { removeCover = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Hapus Cover Custom")
                            }
                        }
                    }
                }

                // Metadata Fields
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Judul Lagu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Nama Artis") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Nama Album") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Audio Trim & Tempo / Pitch Editor
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCut,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Edit Audio Lagu (Potong, Tempo & Pitch)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (totalDuration > 3000L) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Potong Bagian Putar: ${formatDuration(trimStartMs)} - ${formatDuration(trimEndMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = {
                                        trimStartMs = 0L
                                        trimEndMs = totalDuration
                                    }
                                ) {
                                    Text("Full")
                                }
                            }
                            RangeSlider(
                                value = trimStartMs.toFloat()..trimEndMs.coerceAtLeast(trimStartMs + 1000L).toFloat(),
                                onValueChange = { range ->
                                    trimStartMs = range.start.toLong()
                                    trimEndMs = range.endInclusive.toLong().coerceAtLeast(trimStartMs + 1000L)
                                },
                                valueRange = 0f..totalDuration.toFloat()
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Kecepatan (Tempo)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                text = String.format(Locale.US, "%.2fx", playbackSpeed),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = playbackSpeed,
                            onValueChange = { playbackSpeed = it },
                            valueRange = 0.5f..2.0f
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { presetSpeed ->
                                FilterChip(
                                    selected = kotlin.math.abs(playbackSpeed - presetSpeed) < 0.03f,
                                    onClick = { playbackSpeed = presetSpeed },
                                    label = { Text("${presetSpeed}x") }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Nada Suara (Pitch)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = String.format(Locale.US, "%.2fx", playbackPitch),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = playbackPitch,
                            onValueChange = { playbackPitch = it },
                            valueRange = 0.75f..1.5f
                        )
                    }
                }

                // Lyrics Editor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lirik Lagu",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(onClick = onImportLyricsFile) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Impor .txt / .lrc")
                    }
                }

                OutlinedTextField(
                    value = lyrics,
                    onValueChange = { lyrics = it },
                    label = { Text("Tulis atau tempel lirik di sini...") },
                    placeholder = { Text("Mendukung teks biasa atau timestamp LRC seperti:\n[00:15.00] Baris lirik pertama...") },
                    minLines = 7,
                    maxLines = 14,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (song.isEdited) {
                        OutlinedButton(
                            onClick = onResetToOriginal,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Asli")
                        }
                    }
                    Button(
                        onClick = {
                            val normalizedEnd = if (totalDuration > 0L && trimEndMs >= totalDuration - 500L) 0L else trimEndMs
                            onSave(
                                title,
                                artist,
                                album,
                                lyrics,
                                trimStartMs,
                                normalizedEnd,
                                playbackSpeed,
                                playbackPitch,
                                removeCover
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simpan Perubahan")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
