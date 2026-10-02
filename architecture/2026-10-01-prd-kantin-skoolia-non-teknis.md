# PRD Non-Teknis — Aplikasi Kantin Sekolah SKOOLIA

| | |
|---|---|
| **Versi** | 1.0 (turunan dari PRD teknis v4) |
| **Tanggal** | 1 Oktober 2026 |
| **Cakupan** | **Aplikasi Kantin** (kantin-fe & kantin-be): layar kasir, back office kantin, dan aturan yang dijalankan sistem kantin |
| **Pembaca** | Tim produk, engineer kantin-fe/kantin-be, pihak sekolah, penguji UAT |
| **Dokumen acuan** | `2026-10-01-prd-kantin-skoolia.md` (PRD teknis lengkap) |

> Dokumen ini menjelaskan **apa yang dilakukan aplikasi kantin dan bagaimana proses bisnisnya berjalan**, dalam bahasa sehari-hari. Detail teknis (arsitektur, keamanan, integrasi) ada di PRD teknis.
>
> **Di luar dokumen ini:** layar orang tua di aplikasi sekolah (mobile-fe). Dokumen ini hanya menyebut aksi orang tua yang **berdampak** ke kantin (top-up, limit, blokir).

---

## 1. Ringkasan

Kantin sekolah menjadi **100% tanpa uang tunai**. Siswa cukup **menempelkan kartu** di kasir untuk membayar. Guru, staf, dan tamu memakai **Kartu Tamu**. Orang tua mengisi saldo anak lewat aplikasi sekolah atau titip uang di TU.

Aplikasi Kantin terdiri dari dua bagian:

| Bagian | Dipakai oleh | Fungsi |
|---|---|---|
| **Layar Kasir** | Petugas kantin | Melayani pembeli: pilih menu → tempel kartu → beres |
| **Back Office Kantin** | Pengelola kantin, TU, bendahara, admin, kepala sekolah | Mengelola menu, stok, saldo tunai, Kartu Tamu, dan laporan |

**Manfaat untuk sekolah:**
- Tidak ada uang tunai di kantin → tidak ada selisih kas atau uang hilang di kasir.
- Setiap penjualan, stok, dan laba tercatat otomatis.
- Orang tua tenang karena bisa memantau dan membatasi jajan anak.

---

## 2. Prinsip Utama

1. **Satu kantin, milik sekolah.** Seluruh hasil penjualan adalah pendapatan sekolah.
2. **Kasir tidak menerima uang tunai sama sekali.** Satu-satunya tempat menerima uang tunai adalah **TU** (untuk isi saldo).
3. **Kartu hanya "kunci", bukan dompet.** Kartu tidak menyimpan uang. Saldo tersimpan aman di sistem. Kartu siswa hilang → blokir & ganti kartu, **saldo tidak ikut hilang**.
4. **Saldo hanya untuk jajan di kantin sekolah ini.** Tidak bisa ditarik tunai, tidak bisa dikirim ke siswa lain. Sisa saldo hanya dikembalikan saat siswa lulus/pindah atau Kartu Tamu dikembalikan.
5. **Saldo siswa adalah uang titipan orang tua**, bukan pendapatan sekolah — baru menjadi pendapatan saat dibelanjakan.
6. **Antrean harus cepat.** Satu transaksi normal: tempel kartu → bunyi "beep" → selesai, tanpa petugas menyentuh layar.
7. **Tidak ada data yang dihapus atau diedit diam-diam.** Kesalahan diperbaiki dengan catatan pembetulan yang jelas siapa, kapan, dan kenapa.

---

## 3. Siapa Saja yang Terlibat

| Peran | Siapa | Yang dilakukan di Aplikasi Kantin |
|---|---|---|
| **Petugas Kantin** | Staf yang jaga kasir | Melayani transaksi, membatalkan transaksi, tutup kasir |
| **Pengelola Kantin** | Penanggung jawab kantin | Kelola menu & harga, catat barang masuk, stok opname, lihat laporan penjualan/stok/laba |
| **TU** | Staf tata usaha | Terima uang tunai isi saldo, kelola Kartu Tamu |
| **Bendahara** | Bendahara sekolah | Terima setoran kas TU, pembetulan transaksi, pengembalian saldo, laporan keuangan |
| **Admin Sekolah** | Admin SKOOLIA sekolah | Pengaturan kantin, titik kasir, atur limit/blokir untuk siswa yang ortunya tidak pakai aplikasi |
| **Kepala Sekolah** | Kepsek | Melihat laporan |
| **Pembeli — Siswa** | Siswa | Tempel kartu siswa |
| **Pembeli — Guru/Staf/Tamu** | Non-siswa | Tempel Kartu Tamu |
| **Orang Tua** *(di luar aplikasi kantin)* | Ortu | Isi saldo, atur limit & larangan menu, blokir kartu lewat aplikasi sekolah — **langsung berlaku di kasir** |

Semua akun memakai **akun SKOOLIA yang sudah ada**. Ada dua peran baru: **Petugas Kantin** dan **Pengelola Kantin**.

---

## 4. Gambaran Alur Uang

```
   Orang tua isi saldo              Orang tua / guru titip tunai
   lewat aplikasi sekolah                    di TU
            │                                  │
            └──────────────┬───────────────────┘
                           ▼
          SALDO (milik siswa / Kartu Tamu) — uang titipan
                           │
                           │  tempel kartu di kasir
                           ▼
              PENJUALAN KANTIN — pendapatan sekolah
                           │
                           │  tutup kasir setiap hari
                           ▼
           Dicatat di Buku Kas: "Pendapatan Kantin"

   Belanja barang dagangan ──► Buku Kas: "Belanja Stok Kantin"
```

**Rumus yang harus selalu cocok setiap hari:**

> Total uang isi saldo − total saldo yang dikembalikan
> **=** sisa saldo semua siswa & Kartu Tamu **+** total penjualan kantin

Jika tidak cocok, laporan menampilkan **peringatan merah**.

---

## 5. Siklus Harian Kantin

```
 PAGI                     JAM ISTIRAHAT                 SORE
 ─────────────────────    ─────────────────────────     ─────────────────────────
 Pengelola cek stok   →   Petugas melayani pembeli  →   Petugas tutup kasir
 Catat barang masuk       (tempel kartu, beep)          Pengelola catat barang rusak/basi
 Tandai menu habis        Batalkan bila ada salah       TU setor uang tunai ke bendahara
                                                        Bendahara cek laporan rekonsiliasi
```

---

## 6. Proses Bisnis

### 6.1 Melayani pembeli (proses utama)

**Pelaku:** Petugas Kantin · **Tempat:** Layar Kasir

1. Petugas memilih menu yang dibeli. Menu yang stoknya habis tampil **"Habis"** dan tidak bisa dipilih.
2. Pembeli menempelkan kartu.
3. Sistem memeriksa secara otomatis, berurutan:

   | Urutan | Pemeriksaan | Jika gagal, layar menampilkan |
   |---|---|---|
   | 1 | Kartu dikenal & terdaftar di sekolah ini | "Kartu tidak dikenal" |
   | 2 | Kartu tidak diblokir | "Kartu diblokir" |
   | 3 | Tidak ada menu yang dilarang orang tua *(siswa saja)* | "Item [nama] diblokir oleh orang tua" |
   | 4 | Stok cukup | "Stok [nama] tidak cukup (sisa X)" |
   | 5 | Belum melewati batas jajan harian *(siswa saja)* | "Melebihi limit harian (sisa Rp X)" |
   | 6 | Saldo cukup | **"Saldo kurang Rp X"** |

4. **Jika semua lolos:** transaksi langsung tercatat, terdengar **beep sukses**, saldo terpotong, stok berkurang, dan orang tua mendapat notifikasi.
   **Jika gagal:** terdengar **beep gagal** (nada berbeda) dan pesan merah tampil besar. Petugas bisa mengurangi/mengganti menu lalu minta pembeli tempel ulang.
5. Setelah sukses, **foto, nama, dan kelas siswa** tampil besar selama 3 detik. Jika wajah **tidak cocok** dengan pembeli, petugas menekan **Batalkan** → transaksi dibatalkan, saldo kembali, dan kartu ditahan untuk diserahkan ke TU.
6. Layar otomatis kosong, siap untuk pembeli berikutnya.

**Aturan:**
- Satu tempelan kartu = satu kali potong. Tempel dua kali tidak memotong dua kali.
- Saldo dan stok tidak pernah bisa minus.
- Angka saldo lengkap tidak ditampilkan di layar (privasi di depan antrean); hanya kekurangannya bila tidak cukup.
- Semua penjualan wajib dipilih dari daftar menu — **tidak ada input nominal bebas**.
- Siswa tanpa foto tetap bisa membeli, dengan tanda peringatan di layar.

### 6.2 Membatalkan transaksi (void)

**Pelaku:** Petugas Kantin

- Hanya untuk transaksi **hari ini** yang kasirnya **belum ditutup**.
- Wajib memilih/menulis alasan.
- Saldo kembali penuh, stok kembali, jatah limit harian kembali, orang tua mendapat notifikasi.
- Transaksi yang kasirnya sudah ditutup hanya bisa dibetulkan oleh **Bendahara** (§6.10).

### 6.3 Tutup kasir

**Pelaku:** Petugas Kantin · **Waktu:** akhir jam jualan

1. Petugas menekan **Tutup Kasir**.
2. Sistem menampilkan ringkasan: jumlah transaksi, total penjualan, total pembatalan, **total bersih**.
3. Setelah ditutup, transaksi hari itu dikunci dan total bersih otomatis tercatat di **Buku Kas sekolah** sebagai "Pendapatan Kantin".
4. Jika petugas lupa, sistem **menutup otomatis** pada jam yang ditentukan sekolah (bawaan 23:59).

### 6.4 Mengelola menu

**Pelaku:** Pengelola Kantin · **Tempat:** Back Office

- Data menu: nama, harga jual, kategori, satuan (pcs/porsi/botol), foto (opsional), batas stok minimum.
- Kategori (mis. Makanan Berat, Snack, Minuman Manis, Minuman Non-Manis) dipakai orang tua untuk melarang jenis jajanan tertentu.
- Mengubah harga tidak mengubah transaksi yang sudah terjadi. Setiap perubahan harga tercatat.
- Menu yang tidak dijual lagi dinonaktifkan, bukan dihapus, agar riwayat tetap utuh.

### 6.5 Mencatat barang masuk

**Pelaku:** Pengelola Kantin

1. Isi tanggal, nama pemasok (opsional), daftar barang: jumlah dan **harga beli per satuan**, foto nota (opsional).
2. Stok bertambah otomatis.
3. **Harga pokok (modal) per barang dihitung ulang otomatis** dengan cara rata-rata (lihat §7).
4. Total belanja otomatis tercatat di Buku Kas sebagai "Belanja Stok Kantin".
5. Salah input → dibetulkan dengan **catatan pembatalan barang masuk** beserta alasan, bukan diedit.

### 6.6 Stok opname & barang rusak

**Pelaku:** Pengelola Kantin

- **Stok opname (berkala):** hitung stok fisik → masukkan ke sistem → sistem menunjukkan selisih → setiap selisih **wajib diberi alasan**: rusak, kedaluwarsa, hilang, salah hitung, atau lainnya.
- **Barang rusak/basi harian** (mis. nasi kotak tidak laku) bisa langsung dicatat dengan alasan yang sama.
- Nilai kerugian otomatis muncul di laporan.
- Menu dengan stok di bawah batas minimum muncul sebagai **"Stok menipis"** di dashboard.

### 6.7 Isi saldo tunai di TU

**Pelaku:** TU

1. Cari siswa (nama/NIS atau tempel kartunya) — atau tempel Kartu Tamu.
2. Masukkan nominal dan siapa yang menyetor (ortu/wali/siswa/pemegang Kartu Tamu).
3. Simpan → keluar **bukti setor bernomor** (bisa dicetak).
4. Saldo langsung bertambah; orang tua mendapat notifikasi (untuk kartu siswa).

**Aturan:** ada batas saldo maksimal per siswa dan per Kartu Tamu (diatur sekolah, mis. Rp1.000.000).

### 6.8 Setoran kas TU ke bendahara

**Pelaku:** TU → Bendahara · **Waktu:** setiap akhir hari

1. Sistem menyiapkan rekap uang tunai yang diterima tiap petugas TU hari itu.
2. TU menyerahkan uang fisik ke bendahara.
3. Bendahara mengonfirmasi di sistem. Jika ada selisih, **selisih dicatat** (tidak dihapus/ditutupi).

### 6.9 Kartu Tamu (guru, staf, tamu)

**Pelaku:** TU

| Proses | Langkah |
|---|---|
| **Daftar kartu baru** | Tempel kartu kosong → sistem memberi nomor (mis. KT-012) → tempel stiker nomor di kartu → isi nama pemegang (opsional, bisa "Tamu") |
| **Isi saldo** | Sama dengan isi saldo tunai (§6.7) |
| **Belanja** | Sama dengan siswa, tetapi **tanpa** batas harian, larangan menu, dan notifikasi |
| **Pinjamkan** | Kartu boleh dipinjamkan, mis. untuk tamu acara |
| **Kembalikan** | Sisa saldo diserahkan tunai ke pemegang → saldo jadi 0 → kartu siap dipakai lagi |
| **Hilang** | Pemegang lapor nomor kartu → TU blokir (langsung berlaku) → sisa saldo dipindah ke kartu baru |

### 6.10 Pembetulan oleh bendahara

**Pelaku:** Bendahara

- Untuk kesalahan yang tidak bisa dibatalkan petugas: salah input isi saldo, atau transaksi yang kasirnya sudah ditutup.
- Dilakukan dengan **catatan pembetulan** + alasan. Data lama tetap ada, sehingga jejaknya bisa diperiksa.

### 6.11 Pengembalian saldo siswa lulus/pindah

**Pelaku:** Bendahara

1. Sistem otomatis menampilkan daftar **siswa yang sudah tidak aktif tapi masih punya saldo**.
2. Bendahara memilih salah satu:
   - **Kembalikan ke orang tua** (tunai/transfer, unggah bukti), atau
   - **Pindahkan ke kakak/adik** yang masih bersekolah di sekolah yang sama.
3. Saldo menjadi 0 dan kartu siswa otomatis diblokir.

### 6.12 Kartu diblokir (oleh orang tua, admin, atau TU)

- Begitu blokir disimpan, **tempelan berikutnya di kasir langsung ditolak** — tanpa jeda.
- Kartu siswa pengganti dipasang oleh admin sekolah lewat fitur kartu SKOOLIA yang sudah ada; **saldo otomatis ikut** karena tersimpan di data siswa.

### 6.13 Siswa yang orang tuanya tidak memakai aplikasi

- Tetap bisa jajan; isi saldo lewat TU.
- Atas permintaan orang tua, **Admin Sekolah** dapat mengatur batas jajan harian dan larangan menu dari back office.

---

## 7. Cara Menghitung Modal & Laba

**Harga pokok (modal) per barang — metode rata-rata:**

> Modal baru = (stok lama × modal lama + jumlah masuk × harga beli) ÷ (stok lama + jumlah masuk)

*Contoh:* stok Roti 10 pcs dengan modal Rp3.000. Masuk 20 pcs dengan harga beli Rp3.300.
Modal baru = (10 × 3.000 + 20 × 3.300) ÷ 30 = **Rp3.200/pcs**.

- Setiap penjualan mencatat modal yang berlaku **saat itu**, sehingga laba masa lalu tidak berubah walau harga beli naik.
- **Laba kotor** = penjualan bersih − total modal barang terjual.

---

## 8. Layar yang Dibutuhkan

### 8.1 Layar Kasir

| Layar | Isi pokok |
|---|---|
| Transaksi | Daftar menu (dengan foto/kategori/tanda habis), keranjang & total, area hasil tempel kartu (sukses/gagal, foto, tombol Batalkan) |
| Riwayat hari ini | Transaksi sesi ini + tombol Batalkan (void) |
| Tutup kasir | Ringkasan sesi + tombol Tutup |
| Tanda offline | Banner "Offline — transaksi tidak tersedia" bila internet putus |

### 8.2 Back Office

| Menu | Pengguna |
|---|---|
| Dashboard (penjualan hari ini, stok menipis, peringatan rekonsiliasi) | Semua peran back office |
| Menu & Kategori | Pengelola |
| Barang Masuk | Pengelola |
| Stok Opname & Barang Rusak | Pengelola |
| Isi Saldo Tunai | TU, Bendahara |
| Kartu Tamu | TU, Bendahara |
| Setoran Kas TU | TU, Bendahara |
| Pembetulan & Pengembalian Saldo | Bendahara |
| Kontrol Siswa (atas nama ortu) | Admin |
| Pengaturan Kantin & Titik Kasir | Admin |
| Laporan | Sesuai §9 |

---

## 9. Laporan

| Laporan | Untuk menjawab | Dilihat oleh |
|---|---|---|
| **Rekonsiliasi harian** | "Apakah uang & saldo hari ini cocok?" | Bendahara, Kepsek, Admin |
| **Saldo mengendap** | "Berapa uang titipan yang masih kami pegang?" | Bendahara, Kepsek, Admin |
| **Penjualan** | "Apa yang paling laku, kapan, di kasir mana, oleh petugas siapa?" | Bendahara, Kepsek, Admin, Pengelola |
| **Laba kotor** | "Berapa untung kantin?" | Bendahara, Kepsek, Pengelola |
| **Stok & nilai persediaan** | "Barang apa yang tersisa dan berapa nilainya?" | Pengelola, Bendahara |
| **Kartu stok per barang** | "Kenapa stok barang X sekian?" | Pengelola, Bendahara |
| **Kerugian stok** | "Berapa yang rusak/hilang/basi?" | Pengelola, Bendahara, Kepsek |
| **Barang masuk** | "Berapa belanja ke pemasok?" | Pengelola, Bendahara |
| **Per siswa** | "Anak saya beli apa saja?" (menjawab komplain ortu) | Bendahara, Admin |
| **Kartu Tamu** | "Siapa pegang kartu mana, saldonya berapa?" | TU, Bendahara |
| **Pembatalan karena kartu dipakai orang lain** | "Siswa mana yang kartunya sering dipinjam?" | Bendahara, Admin |
| **Setoran kas TU** | "Apakah setoran TU cocok?" | Bendahara |

Semua laporan dapat diunduh ke **Excel**.

---

## 10. Pengaturan yang Bisa Diubah Sekolah

| Pengaturan | Bawaan |
|---|---|
| Nama kantin | — |
| Jam tutup kasir otomatis | 23:59 |
| Lama foto tampil setelah transaksi | 3 detik |
| Konfirmasi manual sebelum transaksi tercatat | Nonaktif |
| Minimal & maksimal sekali isi saldo | Ditentukan sekolah |
| Batas saldo maksimal per siswa / per Kartu Tamu | Ditentukan sekolah |
| Daftar titik kasir | 1 |

---

## 11. Situasi Khusus (Tanya-Jawab)

| Situasi | Yang terjadi |
|---|---|
| Kartu siswa hilang | Ortu blokir dari aplikasi (atau lapor admin) → langsung tidak bisa dipakai → admin pasang kartu baru → saldo tetap |
| Ada yang memakai kartu milik orang lain | Petugas lihat foto tidak cocok → tekan Batalkan → kartu ditahan & diserahkan ke TU |
| Saldo kurang | Layar menulis "Saldo kurang Rp X" → petugas kurangi menu atau pembeli batal |
| Kartu ditempel dua kali | Hanya terpotong sekali |
| Petugas salah pilih menu | Batalkan transaksi selama kasir belum ditutup |
| Lupa tutup kasir | Ditutup otomatis oleh sistem |
| Barang habis di tengah jam istirahat | Menu otomatis tampil "Habis" di kasir |
| Internet putus | Kasir tidak bisa bertransaksi (kantin tidak menerima tunai) — lihat §13 |
| Ortu komplain "anak saya tidak beli itu" | Bendahara/admin buka laporan per siswa (ada waktu, kasir, petugas, dan daftar menu) |
| Siswa lulus masih punya saldo | Bendahara kembalikan ke ortu atau pindahkan ke saudara |

---

## 12. Ukuran Keberhasilan (3 bulan setelah pilot)

| Ukuran | Target |
|---|---|
| Siswa aktif yang jajan pakai kartu minimal 1×/minggu | ≥ 60% |
| Rekonsiliasi harian selalu cocok | 100% hari |
| Selisih stok saat opname | < 2% nilai persediaan per bulan |
| Komplain potong ganda / terpotong tanpa dapat barang | < 1 per 1.000 transaksi |
| Waktu dari tempel kartu sampai beep | ≤ 1 detik |

---

## 13. Hal yang Masih Perlu Diputuskan

| # | Topik | Usulan |
|---|---|---|
| 1 | **Internet kantin mati** → kantin tidak bisa berjualan sama sekali | Sediakan modem/hotspot cadangan; mode offline dibuat di fase berikutnya bila gangguan sering |
| 2 | **Aturan Bank Indonesia** soal dana titipan | Wajib konfirmasi ke legal sebelum rilis |
| 3 | **Saldo yang tidak pernah diambil** setelah siswa lulus | Perlu kebijakan sekolah (batas waktu klaim, dialihkan ke mana) |

---

## 14. Tidak Termasuk Versi Pertama

- Menerima uang tunai di kasir kantin
- Banyak pedagang/lapak sewa, foodcourt
- Mode offline kasir
- Kasir pakai HP (NFC)
- Kartu pegawai pribadi (terikat akun guru/staf, isi saldo online)
- Resep/bahan baku, metode modal FIFO
- Pre-order/katering, isi saldo otomatis
- Komplain transaksi di dalam aplikasi
