# Water Supply

Aplikasi Android offline untuk mencatat minum dalam satuan gelas. Dibangun dengan Jetpack Compose, Material 3, Room, DataStore, dan Jetpack Glance. Android minimum 7.0 / API 24.

## Menjalankan

Buka proyek di Android Studio, sinkronkan Gradle, lalu jalankan konfigurasi `app`. Gunakan JDK bawaan Android Studio dan SDK yang ditentukan proyek. Font Manrope tersedia di APK; lisensinya disertakan pada `app/src/main/assets/Manrope-OFL.txt`.

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew.bat :app:connectedDebugAndroidTest
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

## Perilaku

- Tombol aplikasi dan widget mencatat 1 atau ½ gelas melalui repository yang sama. Riwayat mendukung tambah catatan lampau, ubah, dan hapus.
- Jumlah disimpan dalam unit setengah gelas berbentuk integer. Target awal 8 gelas; perubahan target berlaku untuk hari ini dan hari berikutnya. Target hari sebelumnya tetap menggunakan nilai yang berlaku pada tanggal itu.
- Catatan menyimpan waktu absolut serta tanggal lokal saat dicatat. Perubahan zona waktu tidak memindahkan catatan yang sudah tersimpan ke tanggal lain. Koreksi waktu menghitung ulang tanggal lokal catatan tersebut.
- Pengingat awalnya nonaktif. Pilih interval 15–240 menit atau daftar jam khusus. Interval dimulai pada jam aktif pertama dan tidak bergeser setelah minum.
- Saat target tercapai, pengingat selanjutnya dijadwalkan untuk hari berikutnya. Menghapus atau mengoreksi catatan sehingga progres di bawah target mengaktifkan jadwal berikutnya hari ini.
- Izin notifikasi dan akses alarm tepat waktu dikelola di Pengaturan. Jika akses alarm tidak tersedia, aplikasi menggunakan alarm biasa yang dapat terlambat. Android dapat membatasi alarm saat perangkat mati, di-force-stop, atau karena kebijakan baterai produsen.
- Pergantian hari menggunakan alarm pemeliharaan biasa; pembaruan widget di latar belakang bisa tertunda mengikuti pembatasan Android. Interaksi widget selalu membaca tanggal terbaru.
- Tidak ada akun, internet, analitik, atau pencadangan cloud aplikasi. Menghapus data aplikasi atau uninstall akan menghapus catatan.

## Verifikasi perangkat

Selain tes otomatis, periksa izin notifikasi ditolak/diberikan, akses alarm dicabut, reboot, perubahan waktu/zona, dan notifikasi saat layar terkunci. Pasang widget pada ukuran kecil dan besar, hentikan proses aplikasi tanpa force-stop, lalu catat dari widget. Uji layar kecil, font 200%, TalkBack, serta pengaturan animasi nonaktif. Perilaku penghematan baterai perlu diperiksa pada perangkat fisik tujuan.

## Hasil verifikasi implementasi

- Build debug, 12 unit test, dan 9 tes instrumentasi lulus pada emulator Android 17 / API 37.
- Tes instrumentasi mencakup penulisan bersamaan, penyimpanan setelah database dibuka ulang, koreksi tanggal, target historis, tambah/urungkan, navigasi, teks 200%, aksi widget, serta pengiriman dan penghentian notifikasi berdasarkan target.
- Lint: 0 error; 15 peringatan ketersediaan versi dependensi lebih baru.
- Widget diperiksa langsung pada ukuran dua dan tiga kolom. Setelah proses aplikasi dimatikan, pencatatan dari widget memulai proses baru dan memperbarui progres; pencatatan berikutnya pada sesi aktif serta koreksi dari aplikasi juga tersinkron.
- Tampilan diperiksa pada lebar sekitar 320 dp. Pengujian TalkBack langsung, reboot, pencabutan akses alarm, dan pembatasan baterai pada perangkat fisik masih perlu dilakukan.

Jika runner tes Windows gagal dengan argumen PATH bertanda kutip, jalankan dari PowerShell dengan PATH yang dibersihkan untuk proses tersebut dan daemon baru:

```powershell
$env:PATH = $env:PATH.Replace('"', '')
./gradlew.bat :app:testDebugUnitTest --no-daemon
```
