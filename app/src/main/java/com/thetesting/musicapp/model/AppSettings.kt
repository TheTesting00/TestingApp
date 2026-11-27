package com.thetesting.musicapp.model

enum class AppThemeId(
    val displayName: String,
    val subtitle: String,
    val isLight: Boolean = false
) {
    MIDNIGHT_EMERALD("Midnight Emerald", "Gelap elegan dengan aksen zamrud"),
    CYBER_VIOLET("Neon Cyber", "Ungu futuristik dengan cahaya neon"),
    GLASS_AURORA("Glass Aurora", "Gradasi malam biru-ungu estetik"),
    SUNSET_BOLD("Sunset Bold", "Hangat oranye-pink penuh energi"),
    ROSE_VELVET("Rose Velvet", "Mewah gelap dengan sentuhan rose gold"),
    MINIMAL_LIGHT("Minimal Clean", "Putih bersih cerah dan fokus", isLight = true)
}

enum class NavBarStyleId(
    val number: String,
    val displayName: String,
    val description: String
) {
    FLOATING_DETACHED("03", "Floating (Detached)", "Melayang terpisah dengan sudut membulat"),
    GLASSMORPHISM("02", "Glassmorphism", "Efek kaca transparan modern"),
    PILL_HIGHLIGHT("05", "Pill Highlight", "Sorotan kapsul lembut pada tab aktif"),
    CENTER_FAB("06", "Center FAB", "Tombol tambah (+) menonjol di tengah"),
    GRADIENT_BOLD("07", "Gradient Bold", "Latar gradasi cerah penuh karakter"),
    TAB_INDICATOR("09", "Tab Indicator", "Garis indikator tegas di atas tab aktif"),
    CURVED_PILL("10", "Curved Background", "Kapsul melengkung penuh warna"),
    IOS_DOCK("11", "Dock Style (iOS)", "Dock melayang ala iOS yang halus"),
    MINIMAL_CLEAN("01", "Minimal Clean", "Bersih, sederhana, dan fokus")
}

enum class EqPreset(
    val displayName: String,
    val bandLevels: List<Int> // 5 bands in normalized -100..100 scale
) {
    NORMAL("Normal", listOf(0, 0, 0, 0, 0)),
    BASS_BOOST("Bass Mantap", listOf(80, 55, 10, 0, 15)),
    VOCAL_CLEAR("Vokal Jernih", listOf(-15, 0, 50, 65, 35)),
    POP("Pop", listOf(20, 45, 25, 35, 45)),
    ROCK("Rock", listOf(65, 35, -10, 40, 65)),
    LOFI("Lo-Fi Chill", listOf(45, 25, 0, -25, -40)),
    CUSTOM("Custom", listOf(0, 0, 0, 0, 0))
}

enum class EarphoneConnectionType(val label: String) {
    SPEAKER("Speaker HP"),
    WIRED("Earphone Kabel"),
    BLUETOOTH("Earphone / TWS Bluetooth")
}

data class EarphoneStatus(
    val isConnected: Boolean = false,
    val type: EarphoneConnectionType = EarphoneConnectionType.SPEAKER,
    val deviceName: String = "Speaker HP"
)

data class EarphoneSettings(
    val enabled: Boolean = true,
    val autoActivateOnConnect: Boolean = true,
    val autoPauseOnDisconnect: Boolean = true,
    val autoPlayOnConnect: Boolean = false,
    val preset: EqPreset = EqPreset.BASS_BOOST,
    val bandValues: List<Int> = EqPreset.BASS_BOOST.bandLevels, // 5 bands, each -100..100
    val bassBoostPercent: Int = 45,
    val virtualizerPercent: Int = 35,
    val loudnessGainPercent: Int = 20,
    val doubleTapNextTrack: Boolean = true
)

data class AppAppearanceSettings(
    val themeId: AppThemeId = AppThemeId.MIDNIGHT_EMERALD,
    val navBarStyleId: NavBarStyleId = NavBarStyleId.FLOATING_DETACHED
)
