# MusikKu (TestingApp)

Aplikasi pemutar musik Android yang mudah diakses langsung di HP, lengkap dengan Studio Edit Musik, pilihan Tema & Gaya Bottom Navigation Bar keren, Studio Earphone otomatis saat terhubung ke HP, serta Timer Pemutar Musik (Sleep Timer).

## Fitur Utama
- **Akses Musik HP:** Otomatis memindai file audio bawaan HP (`MediaStore`) dan tombol `+` di navigasi bawah untuk menambahkan file musik dari folder mana pun.
- **Timer Pemutar Musik (Sleep Timer):**
  - Berhenti otomatis sesuai durasi waktu (preset: 5, 10, 15, 30, 45, 60 menit atau kustom bebas).
  - Opsi berhenti otomatis tepat saat lagu yang sedang diputar selesai (*until track end*).
  - Efek audio *fade-out* bertahap agar lagu mengecil lembut sebelum berhenti.
  - Tampilan sisa waktu *live* di bar atas, layar Now Playing, dan notifikasi Android.
  - Tombol perpanjang waktu cepat (`+5m`) atau batalkan timer sewaktu-waktu.
- **Studio Edit Musik Bawaan:**
  - Edit judul lagu, nama artis, dan nama album.
  - Ganti foto cover/album dari galeri HP.
  - Tambah/edit lirik lagu atau impor file `.txt` / `.lrc` (dengan highlight lirik otomatis mengikuti waktu lagu).
  - Potong bagian putar lagu (*trim* awal & akhir), atur kecepatan/tempo (`0.5x`–`2.0x`), dan nada suara (*pitch*).
- **Tema Keren & Gaya Bottom Navigation Bar:**
  - 6 pilihan tema warna (*Midnight Emerald, Neon Cyber, Glass Aurora, Sunset Bold, Rose Velvet, Minimal Clean*).
  - 9 gaya *Bottom Navigation Bar* (*Floating Detached, Glassmorphism, Pill Highlight, Center FAB, Gradient Bold, Tab Indicator, Curved Background, Dock Style iOS, Minimal Clean*).
- **Studio Earphone (Otomatis Saat Konek):**
  - Deteksi real-time earphone kabel maupun TWS/Bluetooth.
  - Equalizer 5-band dengan preset (*Normal, Bass Mantap, Vokal Jernih, Pop, Rock, Lo-Fi Chill, Custom*).
  - Penguat *Bass Boost*, *Surround 3D Virtualizer*, dan *Loudness Gain*.
  - Otomatisasi: auto-aktif saat earphone konek, auto-jeda saat earphone dilepas, auto-putar saat tersambung, dan ketuk 2x tombol headset untuk lanjut lagu.

## Cara Mengunduh File `.apk` dari GitHub
1. Buka tab **Actions** di repositori GitHub: [TestingApp](https://github.com/TheTesting00/TestingApp).
2. Klik workflow **Build Android APK** terbaru yang berstatus hijau.
3. Gulir ke **Artifacts**, unduh **MusikKu-debug-apk**, ekstrak `.zip`-nya, lalu install `app-debug.apk` di HP Android.
