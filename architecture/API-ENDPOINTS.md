# 🌐 API-ENDPOINTS.md — Daftar Endpoint HTTP

> **Single source of truth endpoint kantin-be.** Semua path berprefix `/api`.
> Respons selalu dibungkus `Response<T>` (`{ responseCode, message, data, ... }`).
>
> **Sinkron terakhir:** 2026-10-09 · **Total: 87 endpoint** (86 di `controller/` + 1 shim dev)
> di **14 controller**. Dijaga otomatis oleh `ApiEndpointsDocTest` — uji ini
> **gagal** bila daftar di bawah tidak lagi sama persis dengan anotasi
> `@…Mapping` di kode. Bila menambah/mengubah endpoint, perbarui dokumen ini.
>
> **Aturan wajib (AGENTS.md §10):** controller tipis; tenant dari `TenantContext`
> (bukan body); aktor dari token (`identitas.aktorIdWajib()`); RBAC `@PerluPeran`.
>
> **Legenda peran:** `petugas`=PETUGAS_KANTIN · `pengelola`=PENGELOLA_KANTIN ·
> `TU`=TU_SEKOLAH · `admin`=ADMIN_SEKOLAH · `kepsek`=KEPSEK (read-only) ·
> `orang tua`=ORANG_TUA (mobile).

---

## 1. Autentikasi (AuthController) — `/api/auth`

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `GET` | `/api/auth/me` | semua (terautentikasi) | Identitas + tenant + peran + sumber dari token (diagnostik sesi FE) |
| `POST` | `/api/auth/cabut` | admin, TU | Cabut token pemanggil (blacklist Redis, TTL = sisa umur token) |

> kantin-be **tanpa login sendiri** (ADR-0002) — token RS256 dari SKOOLIA.
> Endpoint `cabut` mencabut token yang dikelola platform (akun nonaktif, token bocor).

## 2. Kasir (KasirController) — `/api/kasir`

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `POST` | `/api/kasir/tap` | petugas, pengelola, admin | Proses tap: validasi 6 tahap, potong saldo, kurangi stok, catat transaksi (atomik, idempoten). Body `TapRequest` (`rfidUid`, `items[]`, idempotency key) |
| `POST` | `/api/kasir/tap/{pendingId}/konfirmasi` | petugas, pengelola, admin | Konfirmasi tap "menunggu konfirmasi" (PRD §6.1) — di sini saldo/stok dipotong. Idempoten |
| `POST` | `/api/kasir/tap/{pendingId}/batal` | petugas, pengelola, admin | Batalkan tap yang menunggu konfirmasi. Idempoten |
| `GET` | `/api/kasir/tap/menunggu` | petugas, pengelola, admin | Daftar tap yang masih menunggu konfirmasi (tenant-scoped) |
| `POST` | `/api/kasir/transaksi/{transaksiId}/void` | petugas, pengelola, admin | Void transaksi (PRD §6.3): kembalikan saldo & stok; wajib `alasan`; hanya sesi belum ditutup |
| `POST` | `/api/kasir/transaksi/{transaksiId}/koreksi` | TU, admin, pengelola | Koreksi transaksi pada **sesi tertutup** (PRD §6.3/§9.2) — mutasi pembalik beralasan + audit; wajib `alasan` |
| `POST` | `/api/kasir/sesi/buka` | petugas, pengelola, admin | Ambil sesi terbuka hari ini atau buka baru (idempoten per sekolah+titik+hari). Body `BukaSesiRequest` (`titikKasirId`) |
| `GET` | `/api/kasir/sesi/{sesiId}/rekap` | petugas, pengelola, admin | Pratinjau rekap (bruto/void/bersih) |
| `POST` | `/api/kasir/sesi/{sesiId}/tutup` | petugas, pengelola, admin | Tutup sesi: hitung rekap, tandai DITUTUP, kunci transaksi |
| `GET` | `/api/kasir/sesi/{sesiId}` | petugas, pengelola, admin | Detail sesi kasir |
| `POST` | `/api/kasir/sesi/{sesiId}/posting-buku-kas` | TU, pengelola, admin | Posting ulang rekap sesi DITUTUP ke Buku Kas (idempoten; untuk integrasi yang tertunda) |

## 3. Saldo (SaldoController) — `/api/saldo`

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `POST` | `/api/saldo/topup` | TU, admin, pengelola | Top-up tunai (PRD §9.1); `referensiId` = kunci idempotensi |
| `POST` | `/api/saldo/koreksi` | TU, admin, pengelola | Koreksi saldo (arah KREDIT/DEBIT, alasan + berita acara wajib) |
| `GET` | `/api/saldo` | petugas, TU, pengelola, admin | Saldo berjalan + belanja hari ini + mutasi terbaru. Query: `subjekTipe`, `subjekId`, `batasMutasi` |
| `GET` | `/api/saldo/rekonsiliasi` | TU, admin, pengelola | Hitung ulang saldo dari ledger. Query: `subjekTipe`, `subjekId` |
| `GET` | `/api/saldo/refund/kandidat` | TU, admin, pengelola, **kepsek (read)** | Daftar siswa bersisa saldo (kandidat refund/pindah). Query: `hanyaTidakAktif`. Kepsek **hanya baca** (§9.6) |
| `POST` | `/api/saldo/refund` | TU, admin, pengelola | Refund **seluruh** sisa saldo siswa keluar ke ortu; saldo → 0 & kartu diblokir |
| `POST` | `/api/saldo/pindah-saldo` | TU, admin, pengelola | Pindah **seluruh** sisa saldo ke saudara kandung (aktif, sekolah sama); saldo sumber → 0 |
| `GET` | `/api/saldo/setoran-tu/rekap` | TU, admin, pengelola | Rekap top-up tunai per petugas per hari + selisih. Query: `tanggal` (kosong = hari ini) |
| `POST` | `/api/saldo/setoran-tu` | TU, admin, pengelola | Konfirmasi setoran kas TU (selisih dicatat, tidak dihapus; `referensiId` = idempotensi) |

## 4. Stok (StokController) — `/api/stok`

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `POST` | `/api/stok/barang-masuk` | TU, pengelola, admin | Barang masuk/stok awal (PRD §7.2); perbarui HPP rata-rata tertimbang. `referensiId` = idempotensi |
| `POST` | `/api/stok/barang-masuk-pembalik` | TU, pengelola, admin | **Koreksi barang masuk salah input** (PRD §7.2) — catat mutasi pembalik baru (wajib alasan). Body `{ mutasiId, qty?, alasan, referensiId }` |
| `POST` | `/api/stok/opname` | TU, pengelola, admin | Penyesuaian stok hasil opname fisik (PRD §7.3); alasan wajib |
| `POST` | `/api/stok/opname-batch` | TU, pengelola, admin | **Opname batch** (PRD §7.3) — banyak menu dalam **satu** transaksi (all-or-nothing). Body `{ referensiId, items: [{ menuId, qtyFisik, alasan, rusak? }] }` |
| `GET` | `/api/stok/riwayat` | petugas, TU, pengelola, admin | **Riwayat mutasi stok** (PRD §9.5). Query: `menuId?`, `jenis?`, `dari?`, `sampai?`, `halaman=0`, `ukuran=20` |
| `GET` | `/api/stok/{menuId}` | petugas, TU, pengelola, admin | Stok + HPP + nilai persediaan satu menu |
| `GET` | `/api/stok/menipis` | petugas, TU, pengelola, admin | Daftar menu dengan stok ≤ minimum |
| `GET` | `/api/stok/{menuId}/rekonsiliasi` | TU, pengelola, admin | Hitung ulang stok dari ledger |

> **Barang masuk pembalik (PRD §7.2).** Ledger stok **append-only** — koreksi
> **tidak** mengedit/menghapus baris asal, melainkan mencatat mutasi baru
> ber-`jenis=BARANG_MASUK_PEMBALIK` (arah `KELUAR`) yang menunjuk baris asal
> lewat `mutasiAsalId`. Stok berkurang & **HPP rata-rata dihitung ulang** memakai
> harga beli asli (`harga_beli_satuan` disimpan saat barang masuk). Wajib
> `alasan`; `referensiId` = nomor bukti pembalik (idempotency). `qty` opsional:
> kosong = batalkan seluruh sisa; diisi = koreksi sebagian. Bila stok saat ini <
> qty yang dibalik (sebagian sudah terjual) ⇒ **409**, arahkan ke opname.

> **Riwayat stok (`GET /api/stok/riwayat`).** Terbaru dulu, berhalaman
> (`items`, `total`, `halaman`, `ukuran`, `totalHalaman`). Tiap item memuat
> `id` (dipakai sebagai `mutasiId` saat membalik), `menuId`, `menuNama`, `jenis`,
> `qty`, `hargaBeliSatuan`, `totalNilai`, `stokSetelah`, `referensiId`, `waktu`,
> `aktorId`, **`aktorNama`**, serta untuk `BARANG_MASUK`: `sudahDibalik`,
> `sisaDapatDibalik`, `dapatDibalik`. Tenant-scoped.

> **Opname batch (`POST /api/stok/opname-batch`).** Satu `referensiId` untuk
> seluruh `items`. Selisih kurang ber-`rusak=true` dicatat **`BARANG_RUSAK`**,
> sisanya **`OPNAME_KELUAR`**. **Atomik all-or-nothing**; **idempoten** per
> (sekolah, `referensiId`, `menuId`).

## 5. Katalog (KatalogController) — `/api/katalog`

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `GET` | `/api/katalog/kategori` | petugas, pengelola, TU, admin | Daftar kategori. Query: `hanyaAktif` |
| `POST` | `/api/katalog/kategori` | pengelola, TU, admin | Buat kategori |
| `PUT` | `/api/katalog/kategori/{kategoriId}` | pengelola, TU, admin | Ubah kategori |
| `DELETE` | `/api/katalog/kategori/{kategoriId}` | pengelola, TU, admin | Nonaktifkan (soft delete) — gagal bila masih dipakai item aktif |
| `GET` | `/api/katalog/menu` | petugas, pengelola, TU, admin | Daftar item (+ `stokBerjalan`, satu query batch anti-N+1). Query: `kategoriId?`, `hanyaAktif` |
| `GET` | `/api/katalog/menu/{menuId}` | petugas, pengelola, TU, admin | Detail item (+ `stokBerjalan`) |
| `POST` | `/api/katalog/menu` | pengelola, TU, admin | Buat item |
| `PUT` | `/api/katalog/menu/{menuId}` | pengelola, TU, admin | Ubah item (perubahan harga jual → audit `UBAH_HARGA_JUAL`) |
| `DELETE` | `/api/katalog/menu/{menuId}` | pengelola, TU, admin | Nonaktifkan item (soft delete) |

> **`stokBerjalan`.** `MenuResponse` memuat `stokBerjalan` (integer) sehingga FE
> menampilkan total stok per menu **tanpa** memanggil `GET /api/stok/{menuId}`
> per item. Diambil dari `stok_cache` via satu query batch, tenant-scoped; menu
> tanpa baris stok ⇒ `0`.

## 6. Kartu Tamu (KartuTamuController) — `/api/kartu-tamu`

> Kartu RFID untuk non-siswa (guru/staf/tamu) — PRD §9.4. Saldo terikat ke
> nomor kartu (bukan orang). Detail tambahan: `docs/API-KARTU-TAMU.md`.

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `GET` | `/api/kartu-tamu` | petugas, pengelola, TU, admin | Daftar kartu tamu sekolah. Query: `hanyaAktif` |
| `GET` | `/api/kartu-tamu/{kartuId}` | petugas, pengelola, TU, admin | Detail satu kartu tamu |
| `POST` | `/api/kartu-tamu` | pengelola, TU, admin | Buat kartu (nomor digenerate otomatis `KT-<urut>` bila kosong; `labelPemegang` opsional) |
| `GET` | `/api/kartu-tamu/nomor-berikutnya` | pengelola, TU, admin | Pratinjau nomor kartu berikutnya (tanpa menyimpan) |
| `PUT` | `/api/kartu-tamu/{kartuId}` | pengelola, TU, admin | Ubah kartu (nomor, `rfidUid` rebind, catatan, label, status) |
| `DELETE` | `/api/kartu-tamu/{kartuId}` | pengelola, TU, admin | Nonaktifkan kartu (soft delete) |
| `POST` | `/api/kartu-tamu/refund` | TU, admin, pengelola | Refund sisa saldo saat **pengembalian kartu** (tunai, saldo → 0, label dikosongkan). Idempoten |
| `POST` | `/api/kartu-tamu/pindah-saldo` | TU, admin, pengelola | Pindah sisa saldo dari Kartu Tamu **hilang** ke kartu baru; kartu lama diblokir. Idempoten |
| `GET` | `/api/kartu-tamu/{kartuId}/riwayat` | petugas, TU, pengelola, admin | Riwayat transaksi & mutasi saldo satu kartu (PRD §9.4/§9.5). Query: `batasMutasi=20`, `halaman=0`, `ukuran=20` |

## 7. Kontrol Kartu — Blokir & Limit (KontrolKartuController) — `/api/kontrol-kartu`

> **#40** — blokir kartu **instan**, limit harian, dan blokir item/kategori.
> Kontrol dibaca **tanpa cache** saat tap (PRD §11.11). RBAC: admin mengatur
> atas nama ortu (§9.6); ortu (mobile) mengatur anaknya sendiri.

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `POST` | `/api/kontrol-kartu/blokir` | admin, TU, orang tua | Blokir / buka blokir kartu (flag `diblokir`); berlaku instan di tap berikutnya |
| `PUT` | `/api/kontrol-kartu/limit-harian` | admin, TU, orang tua | Set/ubah limit belanja harian (nominal null = tanpa limit) |
| `POST` | `/api/kontrol-kartu/blokir-item` | admin, TU, orang tua | Blokir / buka blokir item atau kategori untuk satu kartu |
| `GET` | `/api/kontrol-kartu/{subjekTipe}/{subjekId}` | petugas, pengelola, TU, admin, orang tua | Ringkasan kontrol satu subjek (blokir, limit, item diblokir) |

> **Q7** (anti-tabrakan `rfid_uid` dengan admin-be) tidak menghalangi penegakan
> kontrol: penegakan dilakukan kantin-be atas data kartu yang sudah dikenali
> (`TapService` → `KontrolKartuService.terapkan`). Kontrak lookup eksternal
> masuk lewat `KartuLookupPort`.

## 8. Laporan & Ekspor Excel (LaporanController) — `/api/laporan`

> Semua laporan **tenant-scoped** dari token. Periode: kirim `tanggal`
> (YYYY-MM-DD, default hari ini zona kantin) **atau** rentang `dari`/`sampai`
> (ISO date-time). Keduanya kosong ⇒ hari ini. Peran: TU, admin, pengelola,
> **kepsek** (PRD §9.5 — kepsek **read-only**, hanya laporan yang menyebutnya;
> lihat ADR-0012 & issue #123).

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `GET` | `/api/laporan/penjualan` | TU, admin, pengelola, kepsek | Ringkasan: jumlah transaksi, bruto, HPP, **laba kotor**, void |
| `GET` | `/api/laporan/penjualan/item` | TU, admin, pengelola, kepsek | Penjualan per item (terlaris dulu): qty & nilai |
| `GET` | `/api/laporan/penjualan/kategori` | TU, admin, pengelola, kepsek | Penjualan per kategori |
| `GET` | `/api/laporan/saldo-mengendap` | TU, admin, pengelola, kepsek | Dana titipan: Σ saldo siswa + Kartu Tamu (kewajiban sekolah) |
| `GET` | `/api/laporan/rekonsiliasi` | TU, admin, pengelola, kepsek | Arus kas per jenis + cek invariant `seimbang`/`selisih` (PRD §5) |
| `GET` | `/api/laporan/stok` | TU, admin, pengelola | Stok + nilai persediaan; `menipis` dari `menu.stok_minimum`. Query: `hanyaMenipis`. **Tanpa kepsek** (§9.5) |
| `GET` | `/api/laporan/kerugian-stok` | TU, admin, pengelola, kepsek | Opname keluar & barang rusak: qty + nilai kerugian |
| `GET` | `/api/laporan/pembatalan` | TU, admin, pengelola | **Pembatalan kasir** (#114): daftar transaksi di-void per subjek pada periode |
| `GET` | `/api/laporan/kartu-tamu` | TU, admin, pengelola | **Kartu Tamu** (#115): daftar kartu + pemegang + saldo + status |
| `GET` | `/api/laporan/penjualan/titik` | TU, admin, pengelola | **Penjualan per titik kasir** (#116): transaksi, nilai, HPP, laba kotor per titik |
| `GET` | `/api/laporan/penjualan/petugas` | TU, admin, pengelola | **Penjualan per petugas** (#116): transaksi, nilai, HPP, laba kotor per petugas |
| `GET` | `/api/laporan/per-siswa` | TU, admin, pengelola | **Per siswa** (#117): riwayat lengkap satu subjek. Query: `subjekTipe=SISWA`, `subjekId`, periode |
| `GET` | `/api/laporan/setoran-tu` | TU, admin, pengelola | **Setoran kas TU** (#143): rekap top-up tunai per petugas per hari + disetor + **selisih**. Query: `tanggal?`. **Tanpa kepsek** (§9.5: hanya Bendahara) |
| `GET` | `/api/laporan/kartu-stok/{menuId}` | TU, admin, pengelola | **Kartu stok per item** (#118): riwayat mutasi satu menu + saldo berjalan. Query: `batas?` |
| `GET` | `/api/laporan/ekspor` | TU, admin, pengelola, kepsek* | Unduh **Excel `.xlsx`** (bukan JSON). Query: `jenis` ∈ `JenisLaporan`, `tanggal`/`dari`/`sampai`, `menuId?`. *Kepsek hanya jenis yang boleh dibacanya — `STOK`/`BARANG_MASUK`/`SETORAN_TU` → **403** |

> **`JenisLaporan` (14 jenis ekspor):** `PENJUALAN`, `PENJUALAN_ITEM`,
> `PENJUALAN_KATEGORI`, `SALDO_MENGENDAP`, `REKONSILIASI`, `STOK`,
> `KERUGIAN_STOK`, `BARANG_MASUK`, `PEMBATALAN`, `KARTU_TAMU`,
> `PENJUALAN_TITIK`, `PENJUALAN_PETUGAS`, `KARTU_STOK`, `SETORAN_TU`.
> Kepsek hanya boleh mengekspor: `PENJUALAN`, `PENJUALAN_ITEM`,
> `PENJUALAN_KATEGORI`, `SALDO_MENGENDAP`, `REKONSILIASI`, `KERUGIAN_STOK`.

> **Invariant rekonsiliasi (PRD §5).** `selisih = (Σ KREDIT − Σ DEBIT) − saldo
> mengendap` **harus 0**; `seimbang=false` menandakan ledger & cache tidak
> sinkron. Laba kotor = penjualan bersih − Σ HPP snapshot item terjual;
> transaksi **void dipisah** (tidak masuk bruto).

## 9. Konfigurasi Kantin & Solusi Demo (KonfigurasiKantinController) — `/api/konfigurasi`

> Solusi "seadanya" untuk pertanyaan terblokir tim lain (#21–#25) selama
> kantin-be belum rilis penuh. Semua **tenant-scoped** dari token. Lihat
> `OPEN-QUESTIONS.md` Q8/Q14–Q17.

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `POST` | `/api/konfigurasi/pos-buku-kas/aktivasi` | admin, pengelola, TU | **#21/Q8** — buat pos Buku Kas standar (Pendapatan/Belanja Stok/Penyesuaian), **idempoten** |
| `GET` | `/api/konfigurasi/pos-buku-kas` | admin, pengelola, TU, petugas | Daftar pos Buku Kas kantin |
| `POST` | `/api/konfigurasi/insiden-offline` | petugas, pengelola, TU, admin | **#23/Q14** — catat insiden offline (prosedur darurat ADR-0006), append-only |
| `GET` | `/api/konfigurasi/insiden-offline` | pengelola, TU, admin | Daftar insiden offline, terbaru dulu |
| `GET` | `/api/konfigurasi/kebijakan` | admin, pengelola, TU | **#25/Q16** — ambil kebijakan kantin (default `REFUND` bila belum diisi) |
| `PUT` | `/api/konfigurasi/kebijakan` | admin | Ubah kebijakan saldo mengendap (`REFUND`/`PINDAH_SAUDARA`/`TETAP_MENGENDAP`) |
| `GET` | `/api/konfigurasi/profil` | admin, pengelola, TU, petugas | **#22/Q17 & #24/Q15** — profil reader RFID & postur regulasi (asumsi demo) |

## 10. Pengaturan Kantin & Titik Kasir + Aktivasi Modul (PengaturanKantinController) — `/api/pengaturan-kantin`

> **#42** — pengaturan kantin per sekolah, CRUD titik kasir, dan status
> aktivasi modul/fee. Semua **tenant-scoped** dari token.

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `GET` | `/api/pengaturan-kantin` | admin, pengelola, TU | Ambil pengaturan (default aman bila belum diatur) |
| `PUT` | `/api/pengaturan-kantin` | admin | Ubah nama, jam tutup otomatis, konfirmasi manual, durasi foto, min/maks top-up, batas saldo siswa/Kartu Tamu |
| `GET` | `/api/pengaturan-kantin/titik-kasir` | petugas, pengelola, TU, admin | Daftar titik kasir. Query: `hanyaAktif` |
| `GET` | `/api/pengaturan-kantin/titik-kasir/{titikId}` | petugas, pengelola, TU, admin | Detail titik kasir |
| `POST` | `/api/pengaturan-kantin/titik-kasir` | admin | Buat titik kasir (kode unik per sekolah) |
| `PUT` | `/api/pengaturan-kantin/titik-kasir/{titikId}` | admin | Ubah titik kasir (nama, kode, aktif) |
| `DELETE` | `/api/pengaturan-kantin/titik-kasir/{titikId}` | admin | Nonaktifkan titik kasir (soft delete; tolak bila ada sesi terbuka) |
| `GET` | `/api/pengaturan-kantin/aktivasi-modul` | admin, pengelola, TU | **#42/Q6** — status aktivasi modul & fee (fail-open via `AktivasiModulPort`) |

> **Jam tutup otomatis** dipakai penjadwal `JamTutupKasirScheduler` yang menutup
> sesi **hari ini** begitu jam tutup sekolah terlewati. **Aktivasi/fee platform
> (§10)** dimiliki internal-be (Q6) — kantin-be hanya menanyakan statusnya.

## 11. Storage — Unggah/Tampil Berkas (StorageController) — `/api/storage`

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `POST` | `/api/storage/upload` | pengelola, TU, admin | Unggah berkas `multipart/form-data` (field `file`), `folder` opsional (`menu`/`nota`/`kartu-tamu`/`lain`). Mengembalikan `{ path, url, namaAsli, ukuran }` |
| `GET` | `/api/storage/file` | petugas, pengelola, TU, admin | Tampilkan berkas milik tenant (bila URL publik belum dikonfigurasi). Query: `path` |

> **Isolasi tenant wajib (B19, PRD §11.4).** Prefix `sekolah-<id>/` dibentuk
> **server-side** dari tenant token — bukan input klien. Berkas sekolah lain ⇒
> **404**. `folder` dibatasi allowlist.

## 12. Webhook Masuk (WebhookController) — `/api/webhook`

| Method | Path | Auth | Keterangan |
|---|---|---|---|
| `POST` | `/api/webhook/{sumber}` | **HMAC-SHA256 + anti-replay** (bukan token) | Terima event dari SKOOLIA/callback-be. `{sumber}` mis. `skoolia`. Idempotent per `(sumber, eventId)` |

> **Autentikasi webhook (B34, SECURITY.md §5.1).** `/api/webhook/**` dibuka
> `permitAll` (tanpa token user), **tetapi** setiap request wajib membawa:
>
> | Header | Isi |
> |---|---|
> | `X-Webhook-Timestamp` | epoch detik penandatanganan |
> | `X-Webhook-Signature` | `sha256=<hex HMAC-SHA256(rahasia, timestamp + "." + body_mentah)>` |
> | `X-Webhook-Id` | id event unik (kunci idempotency; boleh dari field body `eventId`) |
>
> `WebhookSignatureFilter` menolak **fail-closed**: signature salah / timestamp
> kedaluwarsa / header kurang ⇒ **401**; IP di luar `kantin.webhook.allowed-ips`
> ⇒ **403**; badan > batas ⇒ **413**; `KANTIN_WEBHOOK_SECRET` kosong ⇒ **503**.
> Respons sukses memuat `{ sumber, eventId, eventType, status, replay }`.

## 13. Endpoint Internal (mesin-ke-mesin) — `/api/internal`

> Dipanggil SKOOLIA (mis. admin-be) **bukan** oleh user. Autentikasi:
> **HMAC-SHA256 + anti-replay** (`InternalSignatureFilter`), rahasia **terpisah**
> `KANTIN_INTERNAL_SECRET`. Kosong/disabled ⇒ **503** (fail-closed). Lihat
> `docs/integrasi-anti-tabrakan-uid.md`.

| Method | Path | Keterangan |
|---|---|---|
| `GET` | `/api/internal/kartu-tamu/cek-uid` | **#29** — cek apakah `rfidUid` sudah dipakai Kartu Tamu (anti-tabrakan UID ↔ siswa). Query: `rfidUid`, `sekolahId?` |

> **Header:** `X-Internal-Timestamp` (epoch detik) + `X-Internal-Signature`
> (`sha256=<hex HMAC-SHA256(rahasia, timestamp + "." + body)>`). GET tanpa body.

## 14. Shim Dev — Login (DevLoginController) — `/api/v1/auth`

> **Hanya untuk dev/demo.** Hidup bila profil `local` **dan**
> `kantin.dev-login.enabled=true`. Meniru `POST /api/v1/auth/login` admin-be agar
> FE dapat token RS256 nyata yang diterima kantin-be. Password **tidak**
> diverifikasi. **Tidak boleh aktif di staging/produksi.**

| Method | Path | Auth | Keterangan |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | — (profil `local`) | Terbitkan token RS256 dummy untuk FE dev. Body `{ email/username, sekolah_id }` |

---

## Hal yang perlu diperhatikan tim

1. **Tenant dari token, bukan query/body.** Sekolah lain → **404** (bukan 403) agar tidak membocorkan keberadaan data (PRD §11.4).
2. **Semua mutasi wajib idempotency key** (`referensiId`), agar retry jaringan tidak menggandakan efek (Aturan Emas §3.3).
3. **Operasi tulis stok** harus lewat `StokOperasiService` (pembuka transaksi), karena `LedgerStokService` `propagation = MANDATORY`.
4. **Endpoint file upload/view** (`/api/storage/*`) — prefix tenant `sekolah-<id>/` dipaksa di `StorageService` (B19).
5. **Rate limit & cabut token** (Redis) **sudah aktif** (B27 & B28, `SECURITY.md` §7). Rate limit **fail-open**: Redis mati ⇒ request tetap dilayani.
6. **Menjaga dokumen ini tetap segar:** `ApiEndpointsDocTest` membandingkan daftar di sini dengan anotasi `@…Mapping` di kode. Jalankan `./mvnw test -Dtest=ApiEndpointsDocTest` setelah mengubah controller.

---

## Endpoint yang BELUM dibuat (menunggu modul/fase)

| Modul | Endpoint (rencana) | Blocker |
|---|---|---|
| Laporan ekspor PDF | ekspor PDF (saat ini hanya Excel) | Q3, Q5 |
| Ekspor "Per siswa" | `case PER_SISWA` di ekspor | issue #144 (belum dikerjakan) |
| Aktivasi & fee | toggle modul, fee platform | status dibaca (#42); toggle tetap milik internal-be (Q6) |
| Top-up online | (dari callback-be) | Q4 — handler webhook sudah siap |

> Lihat `MODULE-MAP.md` §1 untuk urutan fase & `OPEN-QUESTIONS.md` untuk Q1–Q7.
