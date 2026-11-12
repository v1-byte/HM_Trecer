# Catatan Perubahan HM Tracker

## 1.5.1 — Alur foto HM otomatis

- Setelah SMR awal tersimpan, foto HM harian yang berhasil dibaca OCR langsung disimpan otomatis.
- Input manual hanya menjadi fallback saat OCR gagal atau angka perlu dikoreksi.
- Setelah Next Maintenance tercapai, pembacaan berikutnya dikunci sampai titik servis baru dibuat.
- Permission kamera tidak lagi diminta massal saat aplikasi dibuka; izin diminta saat tombol foto digunakan.

## 1.5.0 — Panduan awal, animasi, dan update APK

- Panduan penggunaan pertama menjelaskan urutan profil, tambah unit, simpan SMR, dan pencatatan rutin.
- Animasi layout ditambahkan pada onboarding dan kartu status servis.
- Aplikasi memeriksa GitHub Release terbaru, menampilkan catatan versi, mengunduh APK setelah persetujuan, dan membuka installer Android.
- Instalasi tetap memerlukan konfirmasi pengguna sesuai keamanan Android; tidak ada pemasangan diam-diam.

Dokumen ini mencatat perubahan yang diterapkan pada repository `v1-byte/HM_Trecer` sampai build release camera-first terakhir.

## 1.4.0 — Produksi (versionCode 6)

- Popup saat aplikasi dibuka: nama pengguna (wajib) dan jabatan/jenis alat, dengan tombol **Selesai!!**.
- Kamera langsung di aplikasi (CameraX): preview, tombol flash, HD, perbesar, dan perkecil. Deteksi HM mengambil foto dari preview ini.
- Izin kamera diminta saat aplikasi dibuka. Jika ditolak permanen, muncul popup yang membuka pengaturan aplikasi.
- Watermark: tanggal dan jam, nama pengguna, jabatan/alat, unit, operator, HM sekarang, HM terpakai, SMR, Next Maintenance dengan persen, sisa, status, dan GPS. Teks putih rata kanan.
- Status: merah jika sisa 10 jam atau kurang (kuning 20 jam atau kurang). Notifikasi mengikuti status ini.
- Pengaturan: input Next Maintenance dalam angka HM meter, dan keterangan arti warna.
- Tombol Templates dihapus dari layar kamera.
- Signing dipindah ke environment variable dan GitHub Secrets. Keystore dikeluarkan dari repo.
- Tambahan tes unit untuk logika status, pemakaian, validasi, dan watermark.

## 2026-11-12 — Camera-first photo proof

### Perubahan konsep UI

Layar utama diubah dari dashboard kartu HM menjadi layar **camera-first photo proof**. Fokus utama sekarang adalah preview kamera dan pengambilan bukti foto lapangan. Pola ini mengikuti konsep aplikasi dokumentasi kerja seperti Timemark yang dirujuk pengguna: foto menjadi bukti utama, sedangkan data pekerjaan muncul sebagai watermark dan informasi ringkas.

Elemen yang diterapkan:

- Toolbar atas dengan menu, tombol Bagikan, dan kontrol kamera.
- Area preview kamera besar dengan overlay timestamp, tanggal, unit, site, GPS, HM, dan status servis.
- Tombol shutter bundar sebagai aksi utama.
- Status `Saved` untuk menandai bukti yang tersimpan.
- Navigasi bawah `Preview` dan `Files`. Tombol `Templates` dihapus dari layar utama.
- Mode `Video`, `Photo`, dan `Attendance`, dengan `Photo` sebagai mode aktif untuk pencatatan HM.
- Strip `HM & Servis` di bawah kamera untuk pemakaian, sisa interval, dan status servis.
- Tombol akses cepat untuk galeri, input manual, dan foto titik servis baru.

### Sistem HM yang tetap dipertahankan

Redesign tidak mengubah `Logic.kt`, model data, OCR, watermark generator, GPS, notifikasi, export, backup, atau restore.

Rumus aktif tetap:

```text
Pemakaian = HM terakhir - HM titik servis terbaru
Sisa = Batas servis - Pemakaian
HM wajib servis = HM titik servis terbaru + Batas servis
```

Status tetap:

- `Lancar` jika sisa lebih dari 20 jam.
- `Peringatan` jika sisa 20 jam atau kurang.
- `Wajib servis` jika pemakaian sudah mencapai atau melewati batas.

Validasi tetap menolak HM baru yang lebih kecil dari pembacaan terakhir dan memberi peringatan untuk lompatan lebih dari 100 jam. Setelah servis baru, pengguna dapat membuat titik servis baru tanpa menghapus riwayat lama.

### Bukti foto dan metadata

Setiap foto yang diproses dapat menyimpan:

- Tanggal dan waktu.
- Nama unit dan lokasi/site.
- Nilai HM.
- Nama operator jika diisi.
- Pemakaian sejak servis.
- Sisa interval servis.
- Status servis.
- Latitude dan longitude jika izin lokasi tersedia.
- Foto yang telah diberi watermark.

Foto dapat berasal dari kamera, galeri, atau tidak digunakan pada input manual. OCR tetap dapat dikoreksi sebelum penyimpanan.

### Dokumentasi

README diperbarui untuk mencakup:

- Konsep camera-first.
- Pemetaan elemen layar dan navigasi.
- Cara pemakaian pertama kali.
- Alur pencatatan HM harian.
- Rumus dan status servis.
- Validasi pembacaan.
- Reset titik servis.
- Multi-unit.
- GPS, watermark, notifikasi, export, backup, dan restore.
- Proses build release melalui GitHub Actions.
- Status build terakhir.

### Build dan release

Workflow release berada di `.github/workflows/build-apk.yml` dan menjalankan Gradle 8.9. Build terakhir yang berhasil:

- Workflow: `43171806350`
- Commit UI: `6206590d5738d631c8041970244043cb49541110`
- Artifact: `HMTracker-release/app-release.apk`
- Hasil: release signed APK dan debug APK berhasil dibuat.

Commit dokumentasi terbaru setelah build UI:

- `4a5cc2e` — Document camera-first photo proof UI

## Catatan batasan

Preview PNG pada folder `preview/` adalah render deterministik dari layout final untuk inspeksi visual. Sandbox tidak memiliki emulator Android, sehingga screenshot runtime langsung dari perangkat belum diambil. APK release sudah berhasil dikompilasi oleh GitHub Actions.

Kredensial signing dan token GitHub tidak dicantumkan dalam catatan ini. Untuk produksi, kredensial harus dikelola melalui GitHub Secrets atau secret manager, bukan ditulis di source atau dokumentasi.
