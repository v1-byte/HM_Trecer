# HM Tracker

Aplikasi Android untuk mencatat jam meter (Engine Hours) per unit/alat, menghitung pemakaian sejak servis, watermark GPS, dan notifikasi peringatan servis.

**Versi 1.2.0**

## Fitur

- **Multi unit / alat** — banyak excavator, dozer, dll dalam satu app
- Foto HM + OCR otomatis (ML Kit)
- Input manual & pilih dari galeri
- Watermark berwarna: tanggal, jam, unit, operator, status, tempat, **GPS**
- Status Lancar / Peringatan / Wajib servis
- **Notifikasi** saat mendekati atau melewati batas servis
- Export CSV & Backup/Restore JSON
- Hapus catatan (long-press)
- **APK signed release** (siap instalasi tanpa debug)

## Cara memasang

1. Buka tab **Actions** → **Build APK** → **Run workflow** (atau tunggu auto-build setelah push).
2. Download artifact **HMTracker-release** → ekstrak `app-release.apk`.
3. Pasang di HP.

> Artifact debug juga tersedia jika diperlukan.

## Pemakaian pertama kali

1. Aplikasi membuka **Pengaturan**. Tambah unit (contoh: `Excavator 01`), isi tempat & batas servis, Simpan.
2. Tekan **Foto HM Servis (titik awal)**, cek angka, Simpan.
3. Pembacaan berikutnya: **Foto HM**.

## Multi unit

- Di Pengaturan → **+ Tambah unit / alat**
- Di layar utama muncul dropdown unit jika ada lebih dari 1
- Setiap unit punya riwayat, batas servis, dan status sendiri

## Notifikasi

Saat status unit menjadi **Peringatan** (sisa ≤ 20 jam) atau **Wajib servis**, notifikasi muncul.  
Izinkan notifikasi saat diminta sistem.

## GPS

Koordinat diambil otomatis saat foto (jika izin lokasi diberikan) dan ditulis di watermark + riwayat.

## Export & Backup

Di **Pengaturan**:
- **Export CSV** — laporan semua unit
- **Backup (JSON)** — cadangan penuh
- **Restore** — pilih file backup (menimpa data)

## Status

| Warna | Label | Kondisi |
|-------|--------|---------|
| Hijau | Lancar | sisa > 20 jam |
| Kuning | Peringatan | sisa ≤ 20 jam |
| Merah | Wajib servis | pemakaian ≥ batas |

## Catatan teknis

- Keystore release ada di `keystore/hmtracker-release.jks` (password: `hmtracker2026`, alias: `hmtracker`).
- Untuk produksi serius, ganti keystore dengan milik sendiri dan simpan password di GitHub Secrets.
- Data hanya di HP. Backup secara berkala.
