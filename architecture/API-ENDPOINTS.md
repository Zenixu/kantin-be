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

## 4. Stok (StokController)

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `POST` | `/api/stok/barang-masuk` | TU, pengelola, admin | Barang masuk/stok awal; perbarui HPP rata-rata tertimbang |
| `POST` | `/api/stok/opname` | TU, pengelola, admin | Penyesuaian stok hasil opname fisik (alasan wajib) |
| `GET` | `/api/stok/{menuId}` | petugas, TU, pengelola, admin | Stok + HPP + nilai persediaan satu menu |
| `GET` | `/api/stok/menipis` | petugas, TU, pengelola, admin | Daftar menu dengan stok ≤ minimum |
| `GET` | `/api/stok/{menuId}/rekonsiliasi` | TU, pengelola, admin | Hitung ulang stok dari ledger |

---

## 5. Katalog (KatalogController) — 🆕

| Method | Path | Peran | Keterangan |
|---|---|---|---|
| `GET` | `/api/katalog/kategori?hanyaAktif=` | petugas, pengelola, TU, admin | Daftar kategori |
| `POST` | `/api/katalog/kategori` | pengelola, TU, admin | Buat kategori |
| `PUT` | `/api/katalog/kategori/{id}` | pengelola, TU, admin | Ubah kategori |
| `DELETE` | `/api/katalog/kategori/{id}` | pengelola, TU, admin | Nonaktifkan (soft delete) — gagal bila masih dipakai item aktif |
| `GET` | `/api/katalog/menu?kategoriId=&hanyaAktif=` | petugas, pengelola, TU, admin | Daftar item |
| `GET` | `/api/katalog/menu/{id}` | petugas, pengelola, TU, admin | Detail item |
| `POST` | `/api/katalog/menu` | pengelola, TU, admin | Buat item |
| `PUT` | `/api/katalog/menu/{id}` | pengelola, TU, admin | Ubah item (perubahan harga tercatat audit `UBAH_HARGA_JUAL`) |
| `DELETE` | `/api/katalog/menu/{id}` | pengelola, TU, admin | Nonaktifkan item (soft delete) |

---

## 6. Hal yang perlu diperhatikan tim

1. **Tenant dari token, bukan query/body.** Sekolah lain → **404** (bukan 403) agar tidak membocorkan keberadaan data (PRD §11.4).
2. **Semua mutasi wajib idempotency key** (`referensiId`), agar retry jaringan tidak menggandakan efek (Aturan Emas §3.3).
3. **Operasi tulis stok** harus lewat `StokOperasiService` (pembuka transaksi), karena `LedgerStokService` `propagation = MANDATORY`.
4. **Endpoint file upload/view** belum ada — saat dibuat wajib paksa prefix tenant `sekolah-<id>/` (lihat B19).
5. **Rate limit & cabut token** (Redis) **sudah aktif** (B27 & B28, `SECURITY.md` §7). Rate limit **fail-open**: Redis mati ⇒ request tetap dilayani.

---

## 7. Endpoint yang BELUM dibuat (menunggu modul/fase)

| Modul | Endpoint (rencana) | Blocker |
|---|---|---|
| Kartu Tamu | daftar/kartu & saldo | Q7 |
| Blokir kartu | blokir/buka blokir | Q7 |
| Limit & blokir item | set limit harian, blokir item | Q7 |
| Laporan | ekspor Excel/PDF | Q3, Q5 |
| Aktivasi & fee | toggle modul, fee platform | Q6 |
| Top-up online | (dari mobile-be) | Q4 |

> Lihat `MODULE-MAP.md` §1 untuk urutan fase & `OPEN-QUESTIONS.md` untuk Q1–Q7.
