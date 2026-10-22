<p align="center">
  <img src="docs/logo.svg" width="120" alt="Logo VibeMovie">
</p>
<h1 align="center">VibeMovie</h1>

Pemutar video offline untuk Android dengan dukungan subtitle lokal dan pengaturan delay.

![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)

## Fitur

- Daftar video dari penyimpanan (filter folder, cari, urut terbaru/nama/durasi), thumbnail, dan penanda "lanjut menonton"
- Buka video dari aplikasi lain (file manager/galeri) atau lewat tombol **Buka berkas**
- Pemutar layar penuh: play/pause, geser posisi, ±10 detik, video sebelumnya/berikutnya, kecepatan 0.5x–2x, rasio layar (sesuai/zoom/penuh), rotasi, kunci layar, pilih trek audio
- Gestur: ketuk 2x kiri/kanan = ±10 dtk, ketuk 2x tengah = play/pause, geser vertikal kiri = kecerahan, kanan = volume
- **Subtitle lokal**: muat berkas `.srt`, `.vtt`, `.ass`/`.ssa` dari penyimpanan
- **Delay subtitle**: ±0,1 / ±0,5 / ±1 detik dengan tampilan langsung (panel tidak menutupi video), tombol reset
- Ukuran teks subtitle bisa diatur
- Subtitle, delay, dan posisi terakhir diingat per video
- Material You, tema sistem / terang / gelap AMOLED

## Subtitle

Buka pemutar, ketuk ikon **CC** di kanan atas. Pilih **Muat subtitle**; file picker langsung membuka folder video. Encoding UTF-8, UTF-16, atau Windows-1252 dikenali otomatis.

Delay positif = subtitle muncul lebih lambat, negatif = lebih cepat. Subtitle digambar oleh aplikasi sendiri (bukan lewat ExoPlayer) sehingga delay berlaku seketika tanpa memuat ulang.

## Build

Butuh JDK 17 dan Gradle 8.9.

```bash
gradle assembleRelease
```

APK ada di `app/build/outputs/apk/release/`. Bisa juga lewat Android Studio, atau workflow **Release APK** di GitHub Actions.

> APK ditandatangani dengan debug key, jadi update dari build lain perlu uninstall dulu.

## Izin

| Izin | Fungsi |
|---|---|
| `READ_MEDIA_VIDEO` | Membaca daftar video |

Tidak ada izin internet; tidak ada data yang keluar dari perangkat.
