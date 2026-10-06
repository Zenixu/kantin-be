# 🌐 API-ENDPOINTS.md — Daftar Endpoint HTTP

> **Single source of truth endpoint kantin-be.** Diperbarui manual setiap kali
> controller berubah. Semua path berprefix `/api`. Respons selalu dibungkus
> `Response<T>` (`{ responseCode, message, data, ... }`).
>
> **Aturan wajib (AGENTS.md §10):** controller tipis; tenant dari `TenantContext`
> (bukan body); aktor dari token (`identitas.aktorIdWajib()`); RBAC `@PerluPeran`.

---

## 1. Autentikasi (AuthController)

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `GET` | `/api/auth/me` | semua | Identitas + tenant + peran dari token (diagnostik) |
| `POST` | `/api/auth/cabut` | admin, TU | Cabut token pemanggil (blacklist Redis, TTL = sisa umur) |

> kantin-be **tanpa login sendiri** (ADR-0002) — token RS256 dari SKOOLIA.

## 2. Kasir (KasirController)

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `POST` | `/api/kasir/tap` | petugas, pengelola, admin | Proses tap: validasi 6 tahap, potong saldo, kurangi stok, catat transaksi (atomik, idempoten) |
| `POST` | `/api/kasir/transaksi/{id}/void` | petugas, pengelola, admin | Void transaksi (alasan wajib); kembalikan saldo & stok |
| `POST` | `/api/kasir/sesi/buka` | petugas, pengelola, admin | Ambil sesi terbuka hari ini atau buka baru (idempoten per sekolah+titik+hari) |
| `GET` | `/api/kasir/sesi/{id}/rekap` | petugas, pengelola, admin | Pratinjau rekap (bruto/void/bersih) |
| `POST` | `/api/kasir/sesi/{id}/tutup` | petugas, pengelola, admin | Tutup sesi: hitung rekap, kunci transaksi |
| `GET` | `/api/kasir/sesi/{id}` | petugas, pengelola, admin | Detail sesi kasir |

## 3. Saldo (SaldoController)

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `POST` | `/api/saldo/topup` | TU, admin, pengelola | Top-up tunai; `referensiId` = kunci idempotensi |
| `POST` | `/api/saldo/koreksi` | TU, admin, pengelola | Koreksi saldo (arah KREDIT/DEBIT, alasan + berita acara wajib) |
| `GET` | `/api/saldo?subjekTipe=&subjekId=&batasMutasi=` | petugas, TU, pengelola, admin | Saldo berjalan + belanja hari ini + mutasi terbaru |
| `GET` | `/api/saldo/rekonsiliasi?subjekTipe=&subjekId=` | TU, admin, pengelola | Hitung ulang saldo dari ledger |
| `GET` | `/api/saldo/refund/kandidat?hanyaTidakAktif=` | TU, admin, pengelola | Daftar siswa bersisa saldo (kandidat refund/pindah); filter siswa nonaktif |
| `POST` | `/api/saldo/refund` | TU, admin, pengelola | Refund **seluruh** sisa saldo siswa keluar ke ortu; saldo → 0 & kartu diblokir |
| `POST` | `/api/saldo/pindah-saldo` | TU, admin, pengelola | Pindah **seluruh** sisa saldo ke saudara kandung (aktif, sekolah sama); saldo sumber → 0 |

## 4. Stok (StokController)

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `POST` | `/api/stok/barang-masuk` | TU, pengelola, admin | Barang masuk/stok awal; perbarui HPP rata-rata tertimbang |
| `POST` | `/api/stok/barang-masuk-pembalik` | TU, pengelola, admin | **Koreksi barang masuk salah input** (PRD §7.2) — catat mutasi pembalik baru (wajib alasan); data asal tak diubah. Body: `{ mutasiId, qty?, alasan, referensiId }` |
| `POST` | `/api/stok/opname` | TU, pengelola, admin | Penyesuaian stok hasil opname fisik (alasan wajib) |
| `POST` | `/api/stok/opname-batch` | TU, pengelola, admin | **Opname batch** (PRD §7.3) — sesuaikan banyak menu dalam **satu** transaksi (all-or-nothing). Body: `{ referensiId, items: [{ menuId, qtyFisik, alasan, rusak? }] }` |
| `GET` | `/api/stok/riwayat` | petugas, TU, pengelola, admin | **Riwayat mutasi stok** (PRD §9.5) — daftar restock sebelum memilih baris untuk dibalik. Query: `menuId?`, `jenis?`, `dari?`, `sampai?`, `halaman=0`, `ukuran=20` |
| `GET` | `/api/stok/{menuId}` | petugas, TU, pengelola, admin | Stok + HPP + nilai persediaan satu menu |
| `GET` | `/api/stok/menipis` | petugas, TU, pengelola, admin | Daftar menu dengan stok ≤ minimum |
| `GET` | `/api/stok/{menuId}/rekonsiliasi` | TU, pengelola, admin | Hitung ulang stok dari ledger |

> **Barang masuk pembalik (PRD §7.2).** Ledger stok **append-only** — koreksi
> **tidak** mengedit/menghapus baris asal, melainkan mencatat mutasi baru
> ber-`jenis=BARANG_MASUK_PEMBALIK` (arah `KELUAR`) yang menunjuk baris asal
> lewat `mutasiAsalId`. Stok berkurang & **HPP rata-rata dihitung ulang** memakai
> harga beli asli (`harga_beli_satuan` kini disimpan saat barang masuk). Wajib
> `alasan`; `referensiId` = nomor bukti pembalik (idempotency). `qty` opsional:
> kosong = batalkan seluruh sisa; diisi = koreksi sebagian (mis. salah input qty).
> Bila stok saat ini < qty yang dibalik (sebagian sudah terjual) ⇒ **409**, arahkan
> ke opname. Gagal bila baris bukan `BARANG_MASUK` atau sudah dibalik penuh.

> **Riwayat stok (`GET /api/stok/riwayat`).** Terbaru dulu, berhalaman
> (`items`, `total`, `halaman`, `ukuran`, `totalHalaman`). Tiap item memuat
> `id` (dipakai sebagai `mutasiId` saat membalik), `menuId`, `menuNama`, `jenis`,
> `qty`, `hargaBeliSatuan`, `totalNilai`, `stokSetelah`, `referensiId`, `waktu`,
> serta untuk `BARANG_MASUK`: `sudahDibalik`, `sisaDapatDibalik`, `dapatDibalik`.
> Tenant-scoped (sekolah lain ⇒ tidak tampil).

> **Opname batch (`POST /api/stok/opname-batch`).** Satu `referensiId` (nomor
> berita acara, mis. `OPN-20261006-001`) untuk seluruh `items`. Server menghitung
> selisih per menu (`qtyFisik − stok sistem`); selisih 0 ⇒ tak ada mutasi.
> Selisih kurang ber-`rusak=true` dicatat **`BARANG_RUSAK`** (rusak/basi), sisanya
> **`OPNAME_KELUAR`** (selisih audit) — riwayat kerugian harian bisa difilter
> terpisah. **Atomik all-or-nothing**: bila satu item gagal (menu tak dikenal,
> alasan kosong, menu ganda) seluruh batch di-rollback. **Idempotent** per
> (sekolah, `referensiId`, `menuId`): retry nomor sama mengembalikan ringkasan
> lama tanpa menerapkan dua kali. Respons: `{ referensiId, jumlahBerubah,
> jumlahTanpaSelisih, items: [{ menuId, stokSebelum, stokFisik, selisih, jenis,
> mutasiId, stokSetelah }] }`.

---

## 5. Katalog (KatalogController) — 🆕

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `GET` | `/api/katalog/kategori?hanyaAktif=` | petugas, pengelola, TU, admin | Daftar kategori |
| `POST` | `/api/katalog/kategori` | pengelola, TU, admin | Buat kategori |
| `PUT` | `/api/katalog/kategori/{id}` | pengelola, TU, admin | Ubah kategori |
| `DELETE` | `/api/katalog/kategori/{id}` | pengelola, TU, admin | Nonaktifkan (soft delete) — gagal bila masih dipakai item aktif |
| `GET` | `/api/katalog/menu?kategoriId=&hanyaAktif=` | petugas, pengelola, TU, admin | Daftar item — menyertakan **`stokBerjalan`** dari `stok_cache` (satu query, hindari N+1) |
| `GET` | `/api/katalog/menu/{id}` | petugas, pengelola, TU, admin | Detail item (+ `stokBerjalan`) |
| `POST` | `/api/katalog/menu` | pengelola, TU, admin | Buat item |
| `PUT` | `/api/katalog/menu/{id}` | pengelola, TU, admin | Ubah item (perubahan harga tercatat audit `UBAH_HARGA_JUAL`) |
| `DELETE` | `/api/katalog/menu/{id}` | pengelola, TU, admin | Nonaktifkan item (soft delete) |

> **`stokBerjalan` (baru).** `MenuResponse` kini memuat `stokBerjalan` (integer) sehingga
> FE menampilkan total stok per menu **tanpa** memanggil `GET /api/stok/{menuId}` satu per satu.
> Nilai diambil dari `stok_cache` via satu query batch (`StokCacheRepository.findBySekolahIdAndMenuIdIn`),
> tenant-scoped; menu tanpa baris stok ⇒ `0`.

---

## 5b. Webhook masuk (WebhookController) — 🆕

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
> `WebhookSignatureFilter` menolak, **fail-closed**: signature salah / timestamp
> kedaluwarsa (replay) / header kurang ⇒ **401**; IP di luar
> `kantin.webhook.allowed-ips` (bila diisi) ⇒ **403**; badan > batas ⇒ **413**;
> `KANTIN_WEBHOOK_SECRET` kosong ⇒ **503**. Respons sukses memuat
> `{ sumber, eventId, eventType, status, replay }` — `replay=true` berarti retry
> event yang sama **tidak diproses ulang**. Body: `{ eventId?, eventType?,
> sekolahId?, data? }` (bentuk generik; handler per jenis event menunggu Q4/Q7).

---

## 6. Storage — Unggah/Tampil Berkas (StorageController) — 🆕

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `POST` | `/api/storage/upload` | pengelola, TU, admin | Unggah berkas `multipart/form-data` (field `file`), `folder` opsional (`menu`/`nota`/`kartu-tamu`; default `menu`). Mengembalikan `{ path, url, namaAsli, ukuran }` — simpan `url`/`path` ke `fotoUrl` |
| `GET` | `/api/storage/file?path=` | petugas, pengelola, TU, admin | Tampilkan berkas milik tenant (bila URL publik belum dikonfigurasi) |

> **Isolasi tenant wajib (B19, PRD §11.4).** Prefix `sekolah-<id>/` dibentuk **server-side**
> dari tenant token — bukan dari input klien. Berkas sekolah lain ⇒ **404**.
> `folder` dibatasi allowlist agar klien tak membuat struktur folder sembarang.

---

## 6b. Laporan & Ekspor Excel (LaporanController) — 🆕

> Semua laporan **tenant-scoped** dari token. Periode: kirim `tanggal`
> (YYYY-MM-DD, default hari ini zona kantin) **atau** rentang `dari`/`sampai`
> (ISO date-time). Keduanya kosong ⇒ hari ini. Peran: TU, admin, pengelola
> (PRD §9.5).

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `GET` | `/api/laporan/penjualan` | TU, admin, pengelola | Ringkasan: jumlah transaksi, bruto, HPP, **laba kotor**, void |
| `GET` | `/api/laporan/penjualan/item` | TU, admin, pengelola | Penjualan per item (terlaris dulu): qty & nilai |
| `GET` | `/api/laporan/penjualan/kategori` | TU, admin, pengelola | Penjualan per kategori |
| `GET` | `/api/laporan/saldo-mengendap` | TU, admin, pengelola | Dana titipan: Σ saldo siswa + Kartu Tamu (kewajiban sekolah) |
| `GET` | `/api/laporan/rekonsiliasi` | TU, admin, pengelola | Arus kas per jenis + cek invariant `seimbang`/`selisih` (PRD §5) |
| `GET` | `/api/laporan/stok?hanyaMenipis=` | TU, admin, pengelola | Stok + nilai persediaan (stok × HPP); `menipis` dari `menu.stok_minimum` |
| `GET` | `/api/laporan/kerugian-stok` | TU, admin, pengelola | Opname keluar & barang rusak: qty + nilai kerugian |
| `GET` | `/api/laporan/ekspor?jenis=&tanggal=&dari=&sampai=` | TU, admin, pengelola | Unduh **Excel `.xlsx`** (bukan JSON); `jenis` ∈ `JenisLaporan` |

> **Invariant rekonsiliasi (PRD §5).** `selisih = (Σ KREDIT − Σ DEBIT) − saldo
> mengendap` **harus 0**; `seimbang=false` menandakan ledger & cache tidak
> sinkron (perlu diselidiki). Laba kotor = penjualan bersih − Σ HPP snapshot
> item terjual; transaksi **void dipisah** (tidak masuk bruto).

---

## 7. Hal yang perlu diperhatikan tim

1. **Tenant dari token, bukan query/body.** Sekolah lain → **404** (bukan 403) agar tidak membocorkan keberadaan data (PRD §11.4).
2. **Semua mutasi wajib idempotency key** (`referensiId`), agar retry jaringan tidak menggandakan efek (Aturan Emas §3.3).
3. **Operasi tulis stok** harus lewat `StokOperasiService` (pembuka transaksi), karena `LedgerStokService` `propagation = MANDATORY`.
4. **Endpoint file upload/view** kini ada (`/api/storage/*`) — prefix tenant `sekolah-<id>/` dipaksa di `StorageService` (lihat B19).
5. **Rate limit & cabut token** (Redis) **sudah aktif** (B27 & B28, `SECURITY.md` §7). Rate limit **fail-open**: Redis mati ⇒ request tetap dilayani.

---

## 8. Endpoint yang BELUM dibuat (menunggu modul/fase)

> **Kartu Tamu sudah dibuat** (`KartuTamuController`, `/api/kartu-tamu`) —
> lihat `docs/API-KARTU-TAMU.md` & `KartuTamuServiceIT` (20 uji integrasi).
> Blocker Q7 yang tersisa hanyalah **anti-tabrakan `rfid_uid` dengan siswa admin-be**.

| Modul | Endpoint (rencana) | Blocker |
|---|---|---|
| Blokir kartu | blokir/buka blokir | Q7 |
| Limit & blokir item | set limit harian, blokir item | Q7 |
| Laporan | ekspor Excel/PDF | Q3, Q5 |
| Aktivasi & fee | toggle modul, fee platform | Q6 |
| Top-up online | (dari mobile-be) | Q4 |

> Lihat `MODULE-MAP.md` §1 untuk urutan fase & `OPEN-QUESTIONS.md` untuk Q1–Q7.
