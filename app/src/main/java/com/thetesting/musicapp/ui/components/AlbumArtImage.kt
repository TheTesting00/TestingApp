package com.thetesting.musicapp.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thetesting.musicapp.model.Song

@Composable
fun AlbumArtImage(
    song: Song,
    loadBitmap: suspend (Song, Int) -> Bitmap?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    maxSizePx: Int = 512,
    iconSize: Dp = 28.dp
) {
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = song.id,
        key2 = song.customCoverPath,
        key3 = song.uri
    ) {
        value = loadBitmap(song, maxSizePx)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF065F46),
                        Color(0xFF1E293B),
                        Color(0xFF0E7490)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color(0xFFA7F3D0),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
