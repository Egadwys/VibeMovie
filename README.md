# VibeMovie - Modern Android Local Video Player

VibeMovie adalah aplikasi pemutar video lokal modern untuk platform Android dengan tampilan antarmuka **iOS Glassmorphism**, mendukung pemutaran video H.265/HDR/4K, manajemen subtitle presisi tinggi (step ±50ms & speed scaling 0.5x–2.0x), kontrol gestur usap intuitif, dan mode Picture-in-Picture (PiP).

## Fitur Utama
- **UI iOS Glassmorphism:** Efek kaca buram transparan (*frosted glass blur*) dan sudut membulat.
- **Media Engine Media3/ExoPlayer:** Dukungan H.265/HEVC, HDR10, Dolby Vision, dan 4K UHD.
- **Subtitle System:** Subtitle internal & eksternal, pencarian cloud API (Bahasa Indonesia & Inggris), serta penyesuaian delay ±50ms dan kecepatan subtitle.
- **Gestur Usap:** Swipe kiri untuk Kecerahan, Swipe kanan untuk Volume.
- **Lock Mode & PiP:** Pengunci layar dan pemutaran melayang saat keluar aplikasi.
- **Automasi CI/CD:** GitHub Actions Workflow untuk rilis APK otomatis.

## Struktur Proyek
`com.ega.project.vibemovie`
- `data/`: Local storage scraper, Preferences, Remote Subtitle API, Models
- `player/`: ExoPlayer wrapper, Gesture detector, Subtitle sync manager
- `ui/`: Glassmorphic components, Theme (Light/Dark/System), Screens (Gallery, Player, Settings)
