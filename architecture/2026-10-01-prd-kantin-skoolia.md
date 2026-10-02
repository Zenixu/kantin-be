# PRD — Modul Kantin Cashless SKOOLIA

| | |
|---|---|
| **Status** | Draft v4 |
| **Tanggal** | 1 Oktober 2026 |
| **Pelaksana** | Tim kelompok lain (diserahterimakan) |
| **Repo** | Repo baru **kantin-be** + **kantin-fe**; integrasi ke admin-be, mobile-be, mobile-fe, callback-be, internal-be/fe |

> **Riwayat perubahan**
> - **v2:** lingkup dipersempit menjadi **satu kantin milik sekolah**. Multi-pedagang, bagi hasil, dan pencairan ke pedagang dihapus.
> - **v3:** kantin **100% cashless**; ditambah **manajemen stok barang jadi & HPP rata-rata tertimbang**; kantin dibangun di **repo terpisah** dengan SKOOLIA sebagai sumber identitas; penjualan masuk Buku Kas pos **"Pendapatan Kantin"**.
> - **v4:** kasir **otomatis tanpa klik konfirmasi** (beep + foto + tombol Batalkan); layar menampilkan **kekurangan saldo**; ditambah **Kartu Tamu** untuk guru, staf & tamu; **blokir kartu berlaku instan** (tanpa jeda sinkronisasi).

---

## 1. Latar Belakang & Tujuan

Sekolah pelanggan SKOOLIA ingin kantin sekolahnya tanpa uang tunai. SKOOLIA sudah memiliki aset yang dapat dipakai ulang:

- **Kartu RFID siswa** — sudah terdaftar per siswa dan dipakai di Kiosk Presensi (`scan-kartu`, `cek-kartu`, `rfid-list`).
- **Payment gateway & callback-be** — dipakai untuk pembayaran tagihan.
- **Saldo sekolah** (`SekolahBalance`) dan **Buku Kas** di admin-be.
- **mobile-fe** — aplikasi orang tua (white-label per sekolah) dengan push notification.
- **JWT RS256** di admin-be dan mobile-be — token dapat diverifikasi layanan lain cukup dengan public key.

**Tujuan produk:**

1. Siswa jajan di kantin sekolah cukup dengan **tap kartu RFID**, tanpa uang tunai.
2. Orang tua dapat **top-up, mengontrol, dan memantau** jajan anak dari aplikasi sekolah.
3. Sekolah memperoleh **pencatatan penjualan, stok, dan laba kantin yang akurat, terekonsiliasi, dan dapat diaudit**.

## 2. Konsep Inti

- **Satu kantin milik sekolah.** Kantin dikelola sekolah; seluruh hasil penjualan adalah pendapatan sekolah. Petugas kantin adalah staf sekolah.
- **100% cashless.** Kantin **tidak menerima uang tunai sama sekali**. Seluruh penjualan wajib melalui sistem dengan kartu. Ini menjamin stok, penjualan, dan laba selalu sinkron. Guru, staf, dan tamu membeli dengan **Kartu Tamu** (§9.4).
- **Kartu RFID hanya identitas.** Kartu tidak menyimpan uang maupun nominal. Saldo adalah **angka di ledger server**. Saldo siswa terikat ke **siswa** (bukan ke kartu): kartu hilang → blokir & ganti kartu, saldo tetap aman. Saldo Kartu Tamu terikat ke **nomor kartu tamu** (§9.4).
- **Blokir kartu berlaku instan.** Tap pertama setelah blokir berhasil disimpan **pasti ditolak** — tidak ada jeda sinkronisasi.
- **Closed-loop.** Saldo hanya dapat dibelanjakan di kantin sekolah yang sama. Saldo **tidak dapat** ditarik tunai, **tidak dapat** ditransfer antar siswa, dan hanya dapat dikembalikan (refund) atau dipindahkan ke saudara kandung saat siswa lulus/pindah/keluar.
- **Saldo siswa = dana titipan, bukan pendapatan.** Uang top-up baru menjadi pendapatan sekolah saat dibelanjakan di kantin.
- **Barang jadi.** Stok dihitung per item jual (snack kemasan, minuman botol, nasi kotak per porsi). Satu terjual = stok item berkurang satu. Tidak ada bahan baku/resep di MVP.
- **Wajib online.** Kasir harus terhubung internet. Karena 100% cashless, **saat offline kantin tidak dapat berjualan** (lihat Risiko, §13).

### 2.1 Glosarium

| Istilah | Arti |
|---|---|
| **Saldo siswa** | Dana titipan ortu milik siswa, tercatat di ledger |
| **Kartu Tamu** | Kartu RFID milik kantin bernomor (mis. KT-012) untuk guru, staf, dan tamu; saldonya terikat ke nomor kartu dan diisi tunai di TU |
| **Petugas kantin** | Staf yang mengoperasikan kasir kantin |
| **Pengelola kantin** | Staf yang mengelola menu, harga, stok, dan barang masuk |
| **Titik kasir** | Satu perangkat kasir (tablet/laptop + reader). Satu kantin dapat memiliki lebih dari satu |
| **Sesi kasir** | Periode transaksi satu titik kasir dalam satu hari, diakhiri dengan tutup kasir |
| **Mutasi** | Satu baris pencatatan perubahan saldo atau stok |
| **Void** | Pembatalan transaksi belanja oleh petugas pada sesi yang masih terbuka |
| **HPP** | Harga Pokok Penjualan — biaya perolehan per unit item yang terjual |
| **Stok opname** | Penghitungan fisik stok untuk menyesuaikan stok sistem |

## 3. Aktor & Aplikasi

| Aktor | Aplikasi | Peran utama |
|---|---|---|
| Orang tua | mobile-fe (layar kantin memanggil kantin-be) | Top-up, limit harian, blokir item, blokir kartu, riwayat & notifikasi |
| Siswa | Kartu RFID (sudah ada) | Tap untuk bayar; tidak membutuhkan aplikasi |
| Guru, staf & tamu | Kartu Tamu | Tap untuk bayar; isi saldo tunai di TU |
| Petugas kantin | **kantin-fe — Web kasir** + RFID reader USB | Transaksi, void, tutup kasir |
| Pengelola kantin | kantin-fe — Back office | Menu, kategori, harga, barang masuk, stok opname, laporan stok & laba |
| Bendahara / TU | kantin-fe — Back office | Top-up tunai, kelola Kartu Tamu, koreksi, refund saldo, laporan keuangan |
| Admin sekolah | kantin-fe — Back office | Pengaturan kantin, titik kasir, kontrol siswa atas nama ortu |
| Kepala sekolah | kantin-fe — Back office | Melihat laporan |
| SKOOLIA internal | internal-fe/be | Aktivasi modul per sekolah, konfigurasi fee platform |

Akun seluruh staf (petugas, pengelola, bendahara, admin, kepsek) adalah **user SKOOLIA** (admin-be). Petugas dan pengelola kantin mendapat **role baru** di role management SKOOLIA; boleh dari data karyawan maupun user non-karyawan (mis. tenaga harian).

## 4. Arsitektur & Integrasi

```
                ┌──────────── SKOOLIA (sumber kebenaran identitas) ────────────┐
                │ admin-be : user staf, sekolah, siswa, kartu RFID, RBAC, Buku Kas│
                │ mobile-be: user ortu, relasi ortu–anak, push notification       │
                │ callback-be: callback payment gateway                            │
                │ internal-be: aktivasi modul & fee platform                       │
                └──────────────────────────────────────────────────────────────────┘
                        ▲ verifikasi JWT (public key RS256) + API internal
                        │
       ┌────────────────┴────────────────┐
       │ kantin-be (repo baru)           │  pemilik data: ledger saldo, transaksi,
       │                                  │  menu, kategori, stok, HPP, sesi kasir,
       │                                  │  limit/blokir, laporan kantin
       └────────────────┬────────────────┘
          ▲                         ▲
   kantin-fe (kasir +        mobile-fe (layar kantin ortu)
   back office)
```

**Pembagian kepemilikan data:**

| Data | Pemilik | Diakses kantin-be via |
|---|---|---|
| User staf, role & hak akses | admin-be | JWT (klaim user, sekolah, role) + API cek hak akses |
| User ortu & relasi ortu–anak | mobile-be | JWT ortu + API |
| Sekolah, siswa (nama, kelas, foto, status aktif) | admin-be | API internal |
| Kartu RFID (UID → siswa, status) | admin-be | API internal (lookup UID) |
| Buku Kas | admin-be | API internal (posting pemasukan/pengeluaran) |
| Push notification | mobile-be | API internal |
| Aktivasi modul & fee platform | internal-be | API internal |
| Ledger saldo, transaksi, menu, stok, HPP, sesi kasir, limit, blokir item | **kantin-be** | — |
| **Status blokir kartu untuk kantin** (oleh ortu / admin / TU) | **kantin-be** | — |
| **Kartu Tamu** (nomor, UID, label pemegang, saldo) | **kantin-be** | — |

**Kebutuhan integrasi:**

1. **Autentikasi:** kantin-be **tidak memiliki sistem login sendiri**. kantin-be menerima dan memverifikasi JWT dari admin-be (staf) dan mobile-be (ortu) menggunakan public key RS256 masing-masing. Production wajib memakai RS256; fallback HS256 dengan secret di file properties tidak boleh dipakai.
2. **Hak akses** tetap dikelola di role management SKOOLIA (menu baru di §9.6). kantin-be wajib mengecek hak akses di setiap endpoint.
3. **Lookup kartu & blokir instan.** Tap harus cepat (target total ≤ 1 detik) **dan** blokir harus instan:
   - Blokir kartu dari app ortu, admin, atau TU disimpan langsung di kantin-be dan diperiksa **di server pada setiap tap**. Respons "blokir berhasil" ke ortu baru dikirim setelah status tersimpan, sehingga tap berikutnya pasti ditolak.
   - Web kasir **tidak boleh** menyimpan status kartu/saldo secara lokal; setiap tap divalidasi server.
   - Perubahan di admin-be yang memengaruhi kartu (lepas/ganti kartu, siswa nonaktif) wajib **dikirim ke kantin-be saat itu juga** (notifikasi sinkron/webhook dengan retry). Bila kantin-be memakai cache data kartu & siswa, cache wajib di-invalidate oleh notifikasi tersebut — **tidak boleh mengandalkan jeda kedaluwarsa**.
   - Pengikatan kartu siswa di admin-be wajib menolak UID yang sudah terdaftar sebagai Kartu Tamu, dan sebaliknya.
4. **Top-up online:** callback PG untuk top-up kantin diteruskan callback-be ke kantin-be. Pemisahan dari callback tagihan sekolah ditentukan berdasarkan referensi transaksi.
5. **Buku Kas:** kantin-be memposting ke Buku Kas admin-be (§5.1 dan §7.2). Posting harus idempoten (posting ulang tidak menggandakan entri).
6. **Aktivasi modul:** kantin-be menolak seluruh request dari sekolah yang modul kantinnya nonaktif.

---

## 5. Alur Uang & Akuntansi

```
Ortu ──top-up online (PG, fee ditanggung ortu)──┐
TU  ──top-up tunai siswa & Kartu Tamu──────────┤
                                                ▼
                 [Saldo Siswa + Saldo Kartu Tamu]  ← dana titipan (kewajiban sekolah)
                                                │ tap kartu di kasir kantin
                                                ▼
                 [Penjualan Kantin]  ← pendapatan sekolah
                                                │ tutup kasir
                                                ▼
                 Buku Kas: pemasukan pos "Pendapatan Kantin"

Pembelian stok (barang masuk) ──► Buku Kas: pengeluaran pos "Belanja Stok Kantin"
Dana fisik top-up mengendap di Saldo Sekolah (SekolahBalance) / kas TU.
```

**Invariant saldo (wajib selalu benar):**

```
Σ top-up masuk − Σ refund (siswa keluar & Kartu Tamu dikembalikan)
  = Σ saldo siswa + Σ saldo Kartu Tamu + Σ penjualan kantin (bersih setelah void & koreksi)
```

**Invariant stok (per item):**

```
Stok sistem = Σ barang masuk − Σ terjual + Σ void − Σ penyesuaian opname (keluar) + Σ penyesuaian opname (masuk)
```

**Laba kotor** = penjualan bersih − Σ HPP item terjual.

### 5.1 Posting ke Buku Kas

- **Pendapatan:** saat tutup kasir, total bersih sesi diposting sebagai **pemasukan** pos **"Pendapatan Kantin"** (satu entri per sesi).
- **Belanja stok:** setiap barang masuk diposting sebagai **pengeluaran** pos **"Belanja Stok Kantin"**.
- Top-up **tidak** diposting sebagai pendapatan; top-up adalah dana titipan.
- Koreksi bendahara pada sesi yang sudah ditutup diposting sebagai entri penyesuaian (bukan mengubah entri lama).

---

## 6. Kebutuhan Fungsional — Transaksi di Kasir

### 6.1 Alur tap normal

1. Petugas memilih item dari katalog. Item dengan stok 0 tampil sebagai **"Habis"** dan tidak dapat ditambahkan. Keranjang dan total tampil di layar.
2. Pembeli tap kartu RFID (kartu siswa atau Kartu Tamu) pada reader USB.
3. Sistem memvalidasi **secara berurutan**; kegagalan pertama dihentikan dengan pesan berikut:

   | # | Validasi | Pesan di layar kasir |
   |---|---|---|
   | 1 | Kartu terdaftar ke siswa aktif atau Kartu Tamu aktif di sekolah ini | "Kartu tidak dikenal" |
   | 2 | Kartu tidak diblokir | "Kartu diblokir" (kartu siswa: "Kartu diblokir, hubungi orang tua") |
   | 3 | Tidak ada item/kategori yang diblokir ortu *(kartu siswa saja)* | "Item [nama] diblokir oleh orang tua" |
   | 4 | Stok setiap item mencukupi | "Stok [nama] tidak cukup (sisa X)" |
   | 5 | Total + belanja hari ini ≤ limit harian *(kartu siswa saja)* | "Melebihi limit harian (sisa Rp X)" |
   | 6 | Saldo ≥ total | **"Saldo kurang Rp X"** (X = total − saldo) |

4. **Bila lolos, transaksi langsung tercatat tanpa klik konfirmasi:** saldo terpotong, stok berkurang, kasir berbunyi **beep sukses**, dan notifikasi dikirim ke ortu. Bila gagal, kasir berbunyi **beep gagal** (nada berbeda) dan pesan tampil besar berwarna merah.
5. Setelah sukses, layar menampilkan **foto, nama, dan kelas siswa** dalam ukuran besar selama **3 detik** (dapat dikonfigurasi) beserta tombol **Batalkan**. Jika wajah tidak cocok dengan pembeli, petugas menekan Batalkan → transaksi di-void dengan alasan otomatis "Kartu dipakai bukan pemiliknya" dan kartu ditahan untuk dikembalikan ke TU. Untuk Kartu Tamu, layar menampilkan nomor kartu dan label pemegang.
6. Keranjang dikosongkan otomatis, siap untuk pembeli berikutnya.

Sekolah dapat mengaktifkan kembali **langkah konfirmasi manual** (petugas menekan "Konfirmasi" sebelum transaksi tercatat) di pengaturan — default **nonaktif**.

**Acceptance criteria:**
- Validasi gagal tidak memotong saldo, tidak mengurangi stok, dan tidak membuat transaksi.
- Pesan "Saldo kurang Rp X" menampilkan nominal kekurangan yang tepat, sehingga petugas dapat langsung mengurangi item tanpa menebak. **Angka saldo penuh tidak ditampilkan.** *(Catatan: saldo dapat disimpulkan dari total − kekurangan; ini diterima demi kecepatan antrean.)*
- Pada validasi #3, #4, dan #6, petugas dapat menghapus/mengurangi item lalu meminta pembeli tap ulang.
- Dari tap hingga beep sukses ≤ 1 detik (p95). Petugas tidak perlu menyentuh layar untuk transaksi normal.
- Siswa tanpa foto tetap dapat bertransaksi; layar menampilkan peringatan "Foto siswa belum tersedia".
- Kartu milik siswa sekolah lain diperlakukan sama dengan kartu tidak terdaftar (pesan #1).
- Kartu yang diblokir ortu **sedetik sebelumnya** tetap ditolak (pesan #2).

### 6.2 Aturan transaksi

- Setiap transaksi memiliki **ID unik yang dibuat oleh klien kasir** (idempotency key). Tap ganda atau retry jaringan dengan ID yang sama **tidak boleh** memotong saldo atau stok dua kali; respons kedua mengembalikan hasil transaksi pertama.
- Saldo dan stok **tidak boleh minus** dalam kondisi apa pun, termasuk saat dua titik kasir memproses kartu atau item yang sama secara bersamaan.
- Setiap transaksi menyimpan: siswa atau Kartu Tamu, kartu yang dipakai, titik kasir, akun petugas, sesi kasir, daftar item (nama, harga jual, kategori, qty, **HPP per unit saat itu** — *snapshot*), total, dan waktu.
- **Tidak ada item manual / nominal bebas.** Semua penjualan wajib dari katalog agar stok dan HPP terlacak.

### 6.3 Void

- Hanya untuk transaksi pada **sesi yang belum ditutup** (hari yang sama).
- Wajib alasan. Saldo dikembalikan penuh, **stok dikembalikan**, dan belanja hari ini (untuk limit) ikut berkurang.
- Ortu menerima notifikasi void.
- Koreksi transaksi pada sesi yang sudah ditutup hanya dapat dilakukan bendahara (§9.2) dan tercatat di audit log.

### 6.4 Tutup kasir

- Petugas menutup sesi harian per titik kasir; sistem menampilkan rekap: jumlah transaksi, total bruto, total void, **total bersih**.
- Setelah ditutup, transaksi pada sesi tersebut terkunci dan total bersih diposting ke Buku Kas (§5.1).
- Sesi yang tidak ditutup akan **ditutup otomatis** pada jam yang dikonfigurasi sekolah (default 23:59).
- Sesi baru terbuka otomatis pada transaksi pertama hari berikutnya.

### 6.5 Kondisi offline

- Bila kasir kehilangan koneksi, tampilkan banner **"Offline — transaksi tidak tersedia"**. Tidak ada antrean transaksi lokal. Kantin tidak berjualan sampai koneksi pulih.

### 6.6 Titik kasir & akun petugas

- Satu kantin dapat memiliki lebih dari satu titik kasir.
- Beberapa petugas dapat bergantian di satu titik kasir; setiap transaksi tercatat atas akun petugas yang memprosesnya.
- Petugas kantin hanya dapat mengakses web kasir dan rekap sesinya.

---

## 7. Kebutuhan Fungsional — Menu, Stok & HPP (Pengelola kantin)

### 7.1 Katalog menu & kategori

- Satu katalog untuk kantin sekolah.
- Field item: nama, **harga jual** (rupiah, integer), kategori, satuan (pcs/porsi/botol), foto (opsional), status aktif/nonaktif, **stok minimum** (ambang peringatan).
- Kategori (mis. Makanan Berat, Snack, Minuman Manis, Minuman Non-Manis) dikelola pengelola kantin dan menjadi dasar blokir per kategori oleh ortu.
- Mengubah harga jual tidak mengubah transaksi lama (snapshot). Perubahan harga tercatat di audit log.
- Item dan kategori yang dihapus tidak menghapus riwayat (soft delete). Kategori yang masih dipakai item hanya dapat dinonaktifkan.

### 7.2 Barang masuk (pembelian stok)

- Input: tanggal, pemasok (teks bebas, opsional), daftar item (qty, **harga beli per unit**), bukti/nota (upload, opsional).
- Stok bertambah sesuai qty. **HPP item diperbarui dengan rata-rata tertimbang** (§7.4).
- Total pembelian diposting sebagai pengeluaran Buku Kas pos "Belanja Stok Kantin" (§5.1).
- Barang masuk yang salah input dikoreksi dengan **barang masuk pembalik** (wajib alasan), bukan diedit/dihapus.

### 7.3 Stok opname & penyesuaian

- Pengelola menginput **stok fisik** per item; sistem menampilkan selisih terhadap stok sistem.
- Setiap selisih wajib diberi alasan: **rusak, kedaluwarsa, hilang, salah hitung, lainnya**.
- Penyesuaian dicatat sebagai mutasi stok (tidak mengubah HPP rata-rata). Nilai kerugian (qty × HPP) tampil di laporan.
- Penyesuaian di luar opname (mis. nasi kotak basi di akhir hari) dapat dicatat langsung dengan alasan yang sama.

### 7.4 Perhitungan HPP — rata-rata tertimbang

```
HPP baru = (stok sekarang × HPP sekarang + qty masuk × harga beli) ÷ (stok sekarang + qty masuk)
```

- Dihitung ulang setiap ada barang masuk; dibulatkan ke rupiah terdekat.
- Penjualan memakai HPP yang berlaku **saat transaksi** dan menyimpannya sebagai snapshot.
- Void mengembalikan stok dengan HPP snapshot transaksi tersebut.
- Bila stok sekarang 0, HPP baru = harga beli barang masuk.

### 7.5 Peringatan stok

- Item dengan stok ≤ stok minimum tampil di dashboard back office sebagai **"Stok menipis"**.
- Item dengan stok 0 otomatis tampil **"Habis"** di kasir dan di daftar menu yang dilihat ortu.

---

## 8. Kebutuhan Fungsional — Orang Tua (mobile-fe)

### 8.1 Dashboard kantin per anak

- Ortu dengan lebih dari satu anak dapat memilih anak.
- Menampilkan: saldo, belanja hari ini vs limit, status kartu, 5 transaksi terakhir.

### 8.2 Top-up online

- Pilihan nominal preset (Rp20.000 / 50.000 / 100.000 / 200.000) atau bebas.
- **Minimum & maksimum per top-up** serta **batas saldo maksimum per siswa** (mis. Rp1.000.000) dikonfigurasi sekolah. Top-up yang membuat saldo melebihi batas ditolak sebelum pembayaran.
- **Biaya admin PG ditampilkan sebelum bayar**, contoh: "Top-up Rp50.000 + biaya Rp2.500 = Rp52.500". Biaya ditanggung ortu.
- Saldo bertambah **hanya setelah callback PG sukses**. Callback duplikat tidak boleh menambah saldo dua kali.
- Status top-up: Menunggu → Berhasil / Kedaluwarsa / Gagal. Ortu menerima notifikasi saat berhasil.

### 8.3 Kontrol

- **Limit harian:** nominal per hari atau "tanpa limit". Perubahan berlaku seketika. Hari direset pukul 00:00 waktu lokal sekolah.
- **Blokir item:** per kategori dan/atau per item spesifik. Ortu dapat melihat daftar kategori dan menu kantin (termasuk status habis).
- **Blokir kartu:** satu tombol, **berlaku instan** — app baru menampilkan "Kartu diblokir" setelah server menyimpan status, dan tap berikutnya di kasir pasti ditolak (§4 poin 3). Buka blokir juga dari app. Penggantian kartu fisik tetap melalui admin sekolah (bind kartu baru dengan alur RFID yang ada di admin-be). Saldo tetap karena terikat ke siswa.

### 8.4 Riwayat & notifikasi

- Push notification untuk: belanja (contoh: "Budi belanja Rp8.000 di Kantin: Nasi Uduk, Es Jeruk"), void, top-up online berhasil, top-up tunai oleh TU, refund/pindah saldo.
- Riwayat dengan filter tanggal dan jenis (belanja / top-up / void / refund / koreksi), detail item per transaksi.

### 8.5 Batasan

- Ortu **tidak dapat**: menarik saldo, mentransfer saldo antar anak, atau membatalkan transaksi.
- Komplain transaksi disampaikan ke sekolah lewat jalur yang sudah ada (di luar sistem untuk MVP).

### 8.6 Siswa tanpa ortu terdaftar di app

- Tetap dapat bertransaksi. Top-up melalui TU (tunai).
- Admin sekolah dapat mengatur limit harian dan blokir item **atas nama ortu** (atas permintaan ortu), tercatat di audit log.

---

## 9. Kebutuhan Fungsional — Sekolah (kantin-fe Back office)

### 9.1 Pengaturan kantin (Admin)

- Pengaturan modul: nama kantin, jam tutup kasir otomatis, langkah konfirmasi manual aktif/nonaktif (default nonaktif), durasi tampil foto setelah transaksi (default 3 detik), min/maks top-up, batas saldo maksimum per siswa dan per Kartu Tamu.
- Kelola titik kasir (nama, aktif/nonaktif).

### 9.2 Top-up tunai & koreksi (TU / Bendahara)

- Top-up tunai adalah **satu-satunya** penerimaan uang tunai dalam modul kantin, dan dilakukan di TU, bukan di kasir kantin.
- Alur: cari siswa (nama/NIS atau tap kartu) → nominal → penyetor (ortu/wali/siswa) → simpan → bukti dengan nomor referensi (dapat dicetak).
- Batas saldo maksimum per siswa tetap berlaku.
- Tercatat atas nama petugas; ortu menerima notifikasi.
- **Setoran kas TU harian:** rekap top-up tunai per petugas per hari → dikonfirmasi bendahara saat uang disetor. Selisih kas dicatat, tidak dihapus.
- **Koreksi** (top-up salah input, atau transaksi pada sesi yang sudah ditutup) dilakukan bendahara sebagai **mutasi pembalik** dengan alasan. Data tidak pernah diedit atau dihapus.

### 9.3 Refund saldo siswa keluar (Bendahara)

- Daftar otomatis **"siswa nonaktif (lulus/pindah/keluar) dengan sisa saldo"**.
- Dua opsi:
  - **Refund ke ortu** — tunai/transfer, dengan bukti.
  - **Pindahkan ke saudara kandung** yang masih aktif di sekolah yang sama.
- Setelah diproses, saldo siswa menjadi 0 dan kartunya otomatis diblokir.

### 9.4 Kartu Tamu — guru, staf & tamu (TU / Bendahara)

Kartu Tamu adalah kartu RFID fisik milik kantin yang dipakai siapa pun yang bukan siswa.

- **Registrasi:** TU mendaftarkan kartu dengan tap pada reader → sistem memberi **nomor kartu** (mis. KT-012, dicetak/ditempel di kartu) → isi **label pemegang** opsional (nama guru/staf, atau "Tamu").
- **Saldo terikat ke nomor kartu**, bukan ke orang. Diisi **tunai di TU** (alur sama dengan top-up tunai siswa, §9.2). Batas saldo maksimum per Kartu Tamu dikonfigurasi sekolah.
- **Di kasir:** berlaku validasi kartu, blokir, stok, dan saldo. **Tidak ada** limit harian, blokir item, atau notifikasi (tidak ada ortu).
- **Pinjam pakai:** Kartu Tamu boleh dipinjamkan (mis. untuk tamu acara) dan dikembalikan ke TU.
- **Pengembalian kartu:** sisa saldo di-refund **tunai** oleh TU kepada pemegang, saldo menjadi 0, lalu kartu dapat dipakai ulang (label pemegang dikosongkan).
- **Kartu hilang:** pemegang lapor ke TU dengan menyebut nomor kartu → TU memblokir (berlaku instan) → sisa saldo dapat dipindahkan ke Kartu Tamu baru.
- Riwayat transaksi per Kartu Tamu dapat dilihat/dicetak oleh TU atas permintaan pemegang.

### 9.5 Laporan

| Laporan | Isi | Akses |
|---|---|---|
| Rekonsiliasi harian | Invariant saldo §5: top-up (online + tunai), penjualan, void, koreksi, refund, saldo mengendap. **Peringatan mencolok** bila tidak seimbang | Bendahara, Kepsek, Admin |
| Saldo mengendap | Total dana titipan siswa + Kartu Tamu (kewajiban sekolah) | Bendahara, Kepsek, Admin |
| Kartu Tamu | Daftar kartu, pemegang, saldo, status, riwayat per kartu | TU, Bendahara |
| Pembatalan kasir | Transaksi yang dibatalkan lewat tombol Batalkan ("Kartu dipakai bukan pemiliknya") per siswa | Bendahara, Admin |
| Penjualan | Per item, kategori, titik kasir, petugas, periode | Bendahara, Kepsek, Admin, Pengelola |
| **Laba kotor** | Penjualan bersih − HPP, per item/kategori/periode | Bendahara, Kepsek, Pengelola |
| **Stok** | Stok sekarang, nilai persediaan (stok × HPP), stok menipis | Pengelola, Bendahara |
| **Kartu stok per item** | Riwayat mutasi: masuk, terjual, void, penyesuaian, saldo berjalan | Pengelola, Bendahara |
| **Kerugian stok** | Penyesuaian opname per alasan beserta nilainya | Pengelola, Bendahara, Kepsek |
| Barang masuk | Pembelian per periode/pemasok | Pengelola, Bendahara |
| Per siswa | Riwayat lengkap satu siswa (untuk komplain ortu) | Bendahara, Admin |
| Setoran kas TU | Rekap top-up tunai per petugas per hari + selisih | Bendahara |

Semua laporan dapat diekspor ke Excel.

### 9.6 RBAC

Menu baru di role management SKOOLIA:

| Menu | Role & aksi |
|---|---|
| Kantin – Pengaturan & Titik Kasir | Admin (CRUD) |
| Kantin – Menu & Kategori | Pengelola kantin (CRUD) |
| Kantin – Barang Masuk & Stok Opname | Pengelola kantin (create, read), Bendahara (read) |
| Kantin – Kasir | Petugas kantin (create, read) |
| Kantin – Top-up Tunai | TU, Bendahara (create, read) |
| Kantin – Kartu Tamu | TU, Bendahara (CRUD, blokir, refund) |
| Kantin – Refund & Koreksi | Bendahara (create, read), Kepsek (read) |
| Kantin – Laporan | Sesuai kolom akses §9.5 (read) |
| Kantin – Kontrol Siswa (atas nama ortu) | Admin (update) |

Role baru: **Petugas Kantin**, **Pengelola Kantin**.

---

## 10. Kebutuhan Fungsional — SKOOLIA Internal (internal-fe/be)

- Toggle **aktivasi modul kantin per sekolah**. Bila nonaktif, seluruh menu dan endpoint kantin untuk sekolah tersebut tidak tersedia.
- Konfigurasi **fee platform** per sekolah: per top-up (nominal/persentase) dan/atau biaya langganan modul.
- Laporan fee platform per sekolah per periode untuk penagihan.

---

## 11. Batasan Teknis Wajib

Implementasi bebas dirancang tim pelaksana, **tetapi poin berikut tidak boleh ditawar**:

1. **Ledger append-only** untuk saldo **dan** stok. Setiap perubahan adalah baris mutasi. Saldo/stok adalah turunan/cache yang selalu dapat dihitung ulang dari mutasi. Tidak ada perubahan tanpa mutasi; tidak ada hard delete.
2. **Atomik & bebas race condition.** Pemotongan saldo, pengurangan stok, dan pencatatan transaksi terjadi dalam satu transaksi database dengan locking yang tepat. Saldo atau stok minus harus mustahil, termasuk pada tap bersamaan.
3. **Idempotency** pada transaksi kasir (key dari klien), callback PG (berbasis ID referensi PG), dan posting Buku Kas.
4. **Tenant scoping** pada setiap query: kartu, siswa, transaksi, menu, stok wajib divalidasi milik sekolah user (dari klaim JWT). Data sekolah lain dijawab **404**, bukan 403. *(Pelajaran dari review endpoint RFID Absensi, 28 Sep 2026.)*
5. **RBAC dicek di backend** pada setiap endpoint — menyembunyikan tombol di FE bukan pengaman.
6. **Nominal uang disimpan sebagai integer rupiah** (`BIGINT`), bukan float/double. Qty stok berupa integer.
7. **Audit log** untuk: void, koreksi, refund/pindah saldo, perubahan harga jual, barang masuk & pembaliknya, penyesuaian stok, perubahan limit/blokir (siapa, kapan, nilai lama → baru).
8. **Performa tap:** validasi + debit **≤ 1 detik (p95)** pada jam istirahat, termasuk lookup kartu ke data SKOOLIA.
9. **Zona waktu** mengikuti zona waktu sekolah untuk reset limit harian, tutup kasir otomatis, dan laporan harian.
10. **JWT RS256 wajib** di production untuk token yang diterima kantin-be.
11. **Blokir instan.** Status blokir kartu diperiksa di server pada setiap tap; tidak ada cache berbasis waktu kedaluwarsa untuk status kartu. Uji wajib: blokir lalu tap dalam detik yang sama → ditolak.

## 12. Metrik Keberhasilan

Diukur 3 bulan setelah rilis di sekolah pilot:

| Metrik | Target |
|---|---|
| Siswa aktif yang bertransaksi ≥ 1×/minggu | ≥ 60% |
| Ortu siswa aktif yang pernah top-up via app | ≥ 50% |
| Laporan rekonsiliasi harian seimbang | Selalu (selisih = 0) |
| Selisih stok saat opname (nilai) | < 2% nilai persediaan per bulan |
| Komplain saldo terpotong tanpa barang / terpotong ganda | < 0,1% transaksi |
| Waktu respons tap (p95) | ≤ 1 detik |

## 13. Keputusan Terbuka & Risiko

| # | Topik | Usulan default | Status |
|---|---|---|---|
| 1 | **Guru, karyawan & tamu** membeli dengan apa? | Kartu Tamu (§9.4) | Diputuskan (v4). Kartu pegawai personal → fase berikutnya |
| 2 | **Kantin tidak bisa berjualan saat internet/server mati** (konsekuensi 100% cashless tanpa mode offline). Perlu prosedur darurat. | Koneksi cadangan (modem/hotspot) di kantin + mode offline dimajukan bila insiden sering | **Perlu keputusan** |
| 3 | **Regulasi:** apakah skema closed-loop dana titipan ini aman dari ketentuan uang elektronik Bank Indonesia? | — | **Wajib dikonfirmasi legal sebelum rilis** |
| 4 | Kebijakan saldo mengendap bila ortu tidak mengklaim refund setelah anak lulus. | — | Perlu keputusan sekolah/legal |
| 5 | Spesifikasi RFID reader USB untuk kasir — sama dengan reader Kiosk Presensi? | Sama | Cek dengan tim RFID |
| 6 | Siswa tanpa foto dapat bertransaksi dengan peringatan (§6.1). | — | Diputuskan; ditinjau ulang setelah pilot |
| 7 | Pos Buku Kas "Pendapatan Kantin" & "Belanja Stok Kantin" — perlu dibuat otomatis saat modul diaktifkan? | Ya | Konfirmasi tim admin-be |

## 14. Ruang Lingkup Rilis

**Fase 1 — MVP (pilot 1 sekolah):** seluruh kebutuhan §4–§11.

**Fase berikutnya:**
- Notifikasi saldo menipis
- Mode offline kasir
- Kasir via HP Android (NFC)
- Kartu pegawai personal (terikat akun guru/staf, top-up online, riwayat di aplikasi)
- Stok bahan baku + resep
- HPP metode FIFO
- Pre-order / katering
- Auto top-up

**Di luar lingkup:** penerimaan tunai di kasir kantin, komplain transaksi di dalam sistem, transfer saldo antar siswa oleh ortu, penarikan tunai saldo.
