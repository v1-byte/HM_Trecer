# HM Tracker

Aplikasi Android untuk mencatat jam meter (Engine Hours) per unit/alat, menghitung pemakaian sejak servis, watermark GPS, dan notifikasi peringatan servis.

**Versi aplikasi: 1.4.0 — kamera langsung di aplikasi, popup pengguna, target HM wajib servis**

## Build release (produksi)

Kredensial signing tidak disimpan di repo. Build release membaca environment variable berikut, dan di GitHub Actions diisi dari Secrets:

| Secret | Isi |
|---|---|
| `HM_KEYSTORE_BASE64` | Isi file keystore dalam format base64 |
| `HM_KEYSTORE_PASSWORD` | Password keystore |
| `HM_KEY_ALIAS` | Alias kunci |
| `HM_KEY_PASSWORD` | Password kunci |

Untuk membuat nilai base64 dari keystore di komputer: `base64 -w 0 hmtracker-release.jks`. Simpan keystore asli di tempat yang aman dan buat cadangannya. Jangan commit file `.jks` ke repo.

Workflow memvalidasi keystore sebelum build. Jika secret belum diisi, base64 rusak, atau password salah, workflow tetap membuat APK release dengan **debug key** dan memberi peringatan agar dapat dipasang untuk testing; APK produksi harus dibangun ulang setelah secret signing yang valid dipasang.

Tes unit untuk logika (status, pemakaian, validasi, watermark) dijalankan dengan `gradle :app:testDebugUnitTest`.

## Tampilan terbaru

Layar utama kini mengikuti konsep **camera-first photo proof** seperti aplikasi Timemark: kamera menjadi fokus layar, dengan toolbar ringkas, tombol shutter bundar, area watermark langsung di atas preview foto, status **Saved**, serta navigasi **Preview / Files** dan mode **Video / Photo / Attendance**. Data HM dan servis tetap ditampilkan sebagai strip ringkas agar tidak mengganggu fungsi utama kamera.

Konsep ini digunakan untuk dokumentasi lapangan: setiap foto menjadi bukti kerja yang memuat waktu, unit, lokasi, GPS, HM terakhir, pemakaian sejak servis, sisa interval, dan status servis. Fitur galeri, input manual, OCR, ekspor, dan backup tetap tersedia.

Perubahan ini hanya pada lapisan presentasi. Rumus pemakaian sejak titik servis, ambang peringatan 20 jam, status wajib servis, validasi HM, dan notifikasi tetap menggunakan sistem yang sudah ada.

Catatan perubahan lengkap tersedia di [`docs/CATATAN-PERUBAHAN.md`](docs/CATATAN-PERUBAHAN.md).

## Fitur

- **Multi unit / alat** — banyak excavator, dozer, dll dalam satu app
- Foto HM + OCR otomatis (ML Kit)
- Input manual & pilih dari galeri
- Watermark berwarna: tanggal, jam, **nama unit**, operator, HM sekarang, HM terpakai, HM awal servis, sisa menuju HM wajib servis, status, tempat, **GPS**
- Tombol **⚙ Pengaturan** tersedia langsung di layar kamera
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

## Pemetaan layar camera-first

Layar utama dirancang untuk dipakai di lapangan, bukan sebagai dashboard administrasi. Area terbesar adalah preview kamera. Toolbar atas berisi menu, **Bagikan**, dan kontrol kamera. Informasi watermark ditampilkan di atas preview, lalu tombol shutter bundar menjadi aksi utama.

- **Preview** — kembali ke area preview kamera.
- **Files** — memilih foto dari galeri atau melihat bukti foto yang tersimpan.
- **Shutter** — mengambil foto HM; tekan lama digunakan untuk foto titik servis baru.
- **Video / Photo / Attendance** — pola mode kamera yang mengikuti konsep aplikasi dokumentasi lapangan; mode **Photo** tetap menjadi mode aktif untuk pencatatan HM.
- **HM & Servis** — strip ringkas di bawah kamera yang menampilkan pemakaian, sisa interval, dan status tanpa mengambil alih layar kamera.

Setelah foto disimpan, watermark foto berisi waktu, unit, HM, lokasi/GPS, pemakaian, sisa interval, dan status servis. Fungsi **Saved** pada layar menandai bahwa bukti telah tersimpan.

## Pemakaian pertama kali

1. Aplikasi membuka **Pengaturan**. Tambah unit (contoh: `Excavator 01`), isi tempat & batas servis, Simpan.
2. Tekan **Foto HM Servis (titik awal)**, cek angka, Simpan.
3. Pembacaan berikutnya: **Foto HM**.

## Cara kerja sistem

### Pencatatan HM harian

Pilih unit yang benar, lalu tekan **Ambil foto HM sekarang**. Foto meter diproses OCR untuk membaca angka HM. Periksa hasil bacaannya, koreksi bila perlu, isi nama operator secara opsional, kemudian tekan **Simpan**. Jika tidak menggunakan kamera, angka juga dapat dimasukkan melalui **Input manual** atau foto dari **Galeri**.

Setiap catatan menyimpan nilai HM, waktu, operator, foto watermark, dan koordinat GPS bila izin lokasi tersedia. Watermark memuat tanggal, jam, unit, HM, pemakaian sejak servis, sisa interval, status, tempat kerja, dan GPS.

### Dasar perhitungan servis

Pembacaan yang ditandai sebagai titik servis terbaru menjadi dasar perhitungan aktif. Rumusnya:

```text
Pemakaian = HM terakhir - HM titik servis
Sisa = Batas servis - Pemakaian
HM wajib servis = HM titik servis + Batas servis
```

Contoh: titik servis `984,5 jam`, batas servis `200 jam`, dan pembacaan terakhir `988,6 jam` menghasilkan pemakaian `4,1 jam` serta sisa `195,9 jam`.

### Status dan perkiraan servis

- **Lancar**: sisa lebih dari 20 jam.
- **Peringatan**: sisa 20 jam atau kurang.
- **Wajib servis**: pemakaian sudah mencapai atau melewati batas.

Jika tersedia pembacaan dengan jarak waktu minimal satu hari, aplikasi menghitung rata-rata pemakaian per hari dan menampilkan perkiraan tanggal servis. Notifikasi dikirim saat unit masuk status Peringatan atau Wajib servis, jika izin notifikasi diberikan.

### Validasi angka

Pembacaan HM baru yang lebih kecil dari pembacaan terakhir akan ditolak karena dianggap tidak valid. Kenaikan lebih dari 100 jam akan diberi peringatan agar angka diperiksa kembali, tetapi tetap dapat disimpan bila memang benar.

### Setelah servis baru

Setelah alat diservis, tekan **Reset titik awal servis (foto baru)** dan simpan foto HM sebagai titik servis baru. Riwayat lama tetap ada, tetapi perhitungan aktif dimulai dari titik servis terbaru.

### Riwayat, banyak unit, dan cadangan

Setiap unit memiliki riwayat, batas servis, status, dan perhitungan sendiri. Tekan dan tahan catatan pada bagian **Bukti kerja terbaru** untuk menghapusnya. Menu **Pengaturan** menyediakan Export CSV, Backup JSON, dan Restore JSON. Restore dapat menimpa data yang ada, jadi buat backup terlebih dahulu.

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

- Build release menggunakan Gradle 8.9 melalui GitHub Actions pada `.github/workflows/build-apk.yml`.
- Keystore release berada di `keystore/hmtracker-release.jks`; password dan alias tidak dicantumkan di dokumentasi. Untuk produksi serius, ganti keystore dengan milik sendiri dan simpan kredensial di GitHub Secrets.
- Data hanya di HP. Backup secara berkala.

## Status build terakhir

- Commit UI camera-first: `6206590`
- Preview visual: [`preview/hm-tracker-camera-first-preview.png`](preview/hm-tracker-camera-first-preview.png)
- Commit dokumentasi terakhir: `1123bed`
- Workflow release berhasil: [Build APK #43171806350](https://github.com/v1-byte/HM_Trecer/actions/runs/43171806350)
- Artifact: `HMTracker-release/app-release.apk`
