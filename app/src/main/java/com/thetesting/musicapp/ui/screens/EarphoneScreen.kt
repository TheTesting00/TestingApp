package com.thetesting.musicapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thetesting.musicapp.model.EarphoneConnectionType
import com.thetesting.musicapp.model.EarphoneSettings
import com.thetesting.musicapp.model.EarphoneStatus
import com.thetesting.musicapp.model.EqPreset

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EarphoneScreen(
    status: EarphoneStatus,
    settings: EarphoneSettings,
    bandLabels: List<String>,
    onBack: () -> Unit,
    onRefreshStatus: () -> Unit,
    onUpdateSettings: (EarphoneSettings) -> Unit,
    onSelectPreset: (EqPreset) -> Unit,
    onBandChange: (Int, Int) -> Unit,
    bottomPadding: PaddingValues = PaddingValues(bottom = 100.dp)
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Studio Earphone",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Atur audio otomatis saat terhubung ke HP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshStatus) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Periksa status audio")
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
                .padding(horizontal = 16.dp)
                .padding(bottom = bottomPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (status.isConnected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (status.isConnected) Color(0xFF22C55E).copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (status.type) {
                                EarphoneConnectionType.BLUETOOTH -> Icons.Rounded.BluetoothAudio
                                EarphoneConnectionType.WIRED -> Icons.Rounded.Headphones
                                EarphoneConnectionType.SPEAKER -> Icons.Rounded.Speaker
                            },
                            contentDescription = null,
                            tint = if (status.isConnected) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (status.isConnected) "Earphone Terhubung" else "Tidak Ada Earphone",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = status.deviceName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (settings.enabled && (!settings.autoActivateOnConnect || status.isConnected)) {
                                "Profil audio earphone sedang AKTIF"
                            } else if (settings.autoActivateOnConnect && !status.isConnected) {
                                "Menunggu earphone terhubung untuk aktif otomatis"
                            } else {
                                "Efek audio earphone nonaktif"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Connection Behavior Switches
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Otomatisasi Koneksi & Tombol Earphone",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    SettingToggleRow(
                        title = "Aktifkan Tuning Suara Earphone",
                        subtitle = "Terapkan Equalizer, Bass, & Surround",
                        checked = settings.enabled,
                        onCheckedChange = { onUpdateSettings(settings.copy(enabled = it)) }
                    )
                    SettingToggleRow(
                        title = "Aktif Otomatis Saat Earphone Konek",
                        subtitle = "Suara tuning hanya aktif saat headset/TWS tersambung",
                        checked = settings.autoActivateOnConnect,
                        onCheckedChange = { onUpdateSettings(settings.copy(autoActivateOnConnect = it)) }
                    )
                    SettingToggleRow(
                        title = "Auto-Jeda Saat Earphone Dilepas",
                        subtitle = "Musik otomatis berhenti jika kabel tercabut atau TWS putus",
                        checked = settings.autoPauseOnDisconnect,
                        onCheckedChange = { onUpdateSettings(settings.copy(autoPauseOnDisconnect = it)) }
                    )
                    SettingToggleRow(
                        title = "Auto-Putar Saat Earphone Tersambung",
                        subtitle = "Lanjutkan lagu otomatis saat earphone baru dicolok/konek",
                        checked = settings.autoPlayOnConnect,
                        onCheckedChange = { onUpdateSettings(settings.copy(autoPlayOnConnect = it)) }
                    )
                    SettingToggleRow(
                        title = "Ketuk 2x Tombol Earphone = Lagu Berikutnya",
                        subtitle = "Ketuk 1x untuk Putar/Jeda, ketuk cepat 2x untuk Next",
                        checked = settings.doubleTapNextTrack,
                        onCheckedChange = { onUpdateSettings(settings.copy(doubleTapNextTrack = it)) }
                    )
                }
            }

            // Equalizer Presets
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Preset Equalizer Earphone",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        EqPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = settings.preset == preset,
                                onClick = { onSelectPreset(preset) },
                                label = { Text(preset.displayName) }
                            )
                        }
                    }

                    settings.bandValues.forEachIndexed { index, value ->
                        val label = bandLabels.getOrElse(index) { "Band ${index + 1}" }
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${if (value > 0) "+" else ""}$value%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = value.toFloat(),
                                onValueChange = { onBandChange(index, it.toInt()) },
                                valueRange = -100f..100f
                            )
                        }
                    }
                }
            }

            // Bass, Virtualizer, Loudness
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Penguat Efek Audio",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    EffectSliderRow(
                        title = "Bass Boost",
                        value = settings.bassBoostPercent,
                        onValueChange = { onUpdateSettings(settings.copy(bassBoostPercent = it)) }
                    )
                    EffectSliderRow(
                        title = "Surround / 3D Virtualizer",
                        value = settings.virtualizerPercent,
                        onValueChange = { onUpdateSettings(settings.copy(virtualizerPercent = it)) }
                    )
                    EffectSliderRow(
                        title = "Penguat Volume (Loudness Gain)",
                        value = settings.loudnessGainPercent,
                        onValueChange = { onUpdateSettings(settings.copy(loudnessGainPercent = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EffectSliderRow(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "$value%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f
        )
    }
}
