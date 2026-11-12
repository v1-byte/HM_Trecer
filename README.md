# HM Tracker

Aplikasi Android untuk mencatat jam meter (Engine Hours), menghitung pemakaian sejak servis, dan memberi status warna. Setiap foto diberi watermark berisi tanggal, jam, HM, pemakaian, sisa jam, status, dan tempat.

**Versi 1.1.0**

## Fitur

- Foto HM + OCR otomatis (ML Kit)
- Input manual & pilih dari galeri
- Watermark berwarna di foto (tanggal, jam, operator, status, tempat)
- Status Lancar / Peringatan / Wajib servis
- Perkiraan tanggal wajib servis
- **Export CSV** (laporan)
- **Backup & Restore** (JSON)
- Hapus catatan (tahan lama pada baris riwayat)
- Icon aplikasi

## Tampilan utama

![Tampilan utama](docs/tampilan-utama.png)

## Contoh watermark

![Contoh watermark](docs/watermark-contoh.png)

## Cara memasang

1. Buka tab **Actions** → workflow **Build APK** → **Run workflow**.
2. Setelah selesai, unduh artifact **HMTracker-debug** → ekstrak `app-debug.apk`.
3. Pasang di HP (izinkan sumber tidak dikenal jika diminta).

## Pemakaian pertama kali

1. Aplikasi langsung membuka **Pengaturan**. Isi **Tempat** dan **Batas servis** (default 200 jam), lalu Simpan.
2. Tekan **Foto HM Servis (titik awal)**, periksa angka HM, lalu **Simpan**.
3. Pembacaan berikutnya: **Foto HM**. Angka dari OCR selalu ditampilkan dulu untuk dikonfirmasi.

## Export & Backup

Di **Pengaturan**:

- **Export CSV** → share file laporan (bisa dibuka di Excel / Sheets).
- **Backup (JSON)** → cadangan lengkap (pengaturan + semua catatan).
- **Restore dari file…** → pilih file backup JSON (menimpa data saat ini).

## Hapus catatan

Tahan lama (long-press) pada baris di **Riwayat**, lalu konfirmasi.

## Status

| Warna | Label | Kondisi |
|-------|--------|---------|
| Hijau | Lancar | sisa > 20 jam |
| Kuning | Peringatan | sisa ≤ 20 jam |
| Merah | Wajib servis | pemakaian ≥ batas |

## Catatan

- OCR bisa keliru (silau, angka kotor). Selalu konfirmasi sebelum simpan.
- Watermark ditulis langsung ke foto yang disimpan.
- Data hanya di HP. Gunakan Backup secara berkala.
