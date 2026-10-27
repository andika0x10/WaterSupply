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
- Saat pertama dibuka, aplikasi menawarkan aktivasi pengingat dan meminta izin notifikasi, dilanjutkan panduan akses Alarm & pengingat. Pilih Nanti untuk tetap menonaktifkannya; aktivasi bisa dilakukan lagi dari Pengaturan. Pilih interval 15–240 menit atau daftar jam khusus. Interval dimulai pada jam aktif pertama dan tidak bergeser setelah minum.
- Saat target tercapai, pengingat selanjutnya dijadwalkan untuk hari berikutnya. Menghapus atau mengoreksi catatan sehingga progres di bawah target mengaktifkan jadwal berikutnya hari ini.
- Pengingat menggunakan `AlarmManager.setExactAndAllowWhileIdle` jika akses tersedia. Jika akses alarm tidak tersedia, aplikasi menggunakan alarm biasa yang dapat terlambat. Izin yang ditolak dan kanal notifikasi yang diblokir bisa diaktifkan kembali dari Pengaturan. Android dapat membatasi alarm saat perangkat mati, di-force-stop, atau karena kebijakan baterai produsen. Rujukan: [izin notifikasi Android](https://developer.android.com/develop/ui/compose/notifications/notification-permission) dan [penjadwalan alarm](https://developer.android.com/develop/background-work/services/alarms).
- Pergantian hari menggunakan alarm pemeliharaan yang tidak membangunkan perangkat; pembaruan widget di latar belakang bisa tertunda mengikuti pembatasan Android. Interaksi widget selalu membaca tanggal terbaru.
- Aplikasi dan sesi widget menunggu tengah malam serta merespons perubahan waktu/zona, tanpa polling setiap 15 detik. Widget membaca agregat SQL untuk tanggal aktif, dan halaman riwayat memakai hasil pengelompokan tanggal yang dihitung sekali per state.
- Tidak ada akun, internet, analitik, atau pencadangan cloud aplikasi. Menghapus data aplikasi atau uninstall akan menghapus catatan.

## Verifikasi perangkat

Selain tes otomatis, periksa izin notifikasi ditolak/diberikan, akses alarm dicabut, reboot, perubahan waktu/zona, dan notifikasi saat layar terkunci. Pasang widget pada ukuran kecil dan besar, hentikan proses aplikasi tanpa force-stop, lalu catat dari widget. Uji layar kecil, font 200%, TalkBack, serta pengaturan animasi nonaktif. Perilaku penghematan baterai perlu diperiksa pada perangkat fisik tujuan.

## Hasil verifikasi implementasi

- Build debug, 12 unit test, dan 15 tes instrumentasi lulus pada emulator Android 17 / API 37.
- Tes instrumentasi mencakup penulisan bersamaan, penyimpanan setelah database dibuka ulang, koreksi tanggal, target historis, tambah/urungkan, navigasi, teks 200%, aksi dan layout widget, agregat progres per tanggal, penundaan panduan pengingat, serta pengiriman dan penghentian notifikasi berdasarkan target.
- Lint: 0 error; 13 peringatan ketersediaan versi dependensi lebih baru.
- Alur izin diperiksa pada instalasi baru: Aktifkan → dialog izin notifikasi Android → panduan alarm → halaman Alarm & pengingat. Setelah akses diberikan, `dumpsys alarm` mengonfirmasi pengingat `RTC_WAKEUP` dengan jendela 0 dan akses exact alarm.
- Setelah proses aplikasi dihentikan melalui `am kill` (tanpa force-stop), alarm pukul 20.00 menghidupkan proses baru, mengirim notifikasi, dan menjadwalkan pengingat berikutnya pukul 21.00.
- Widget diperiksa langsung pada ukuran dua dan tiga kolom. Setelah proses aplikasi dimatikan, pencatatan dari widget memulai proses baru dan memperbarui progres; pencatatan berikutnya pada sesi aktif serta koreksi dari aplikasi juga tersinkron.
- Tampilan diperiksa pada lebar sekitar 320 dp. Pengujian TalkBack langsung, reboot, pencabutan akses alarm, dan pembatasan baterai pada perangkat fisik masih perlu dilakukan.

Jika runner tes Windows gagal dengan argumen PATH bertanda kutip, jalankan dari PowerShell dengan PATH yang dibersihkan untuk proses tersebut dan daemon baru:

```powershell
$env:PATH = $env:PATH.Replace('"', '')
./gradlew.bat :app:testDebugUnitTest --no-daemon
```
