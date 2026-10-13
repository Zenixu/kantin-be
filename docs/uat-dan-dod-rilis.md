# 🧪 UAT, Definition of Done Rilis & Cakupan Test — kantin-be

> Issue: **#174** · Referensi: PRD §12 (metrik keberhasilan), PRD §4–§11,
> [`AGENTS.md`](../architecture/AGENTS.md) §9, [`README.md`](../README.md).
>
> **Tujuan:** menyediakan **bukti kesiapan rilis** — skenario UAT end-to-end,
> angka cakupan test, dan checklist DoD tingkat rilis (bukan hanya DoD per fitur).

---

## 1. Cakupan Test (JaCoCo)

Cakupan diukur dengan **JaCoCo 0.8.13** (ditambahkan di `pom.xml`, issue #174).
Laporan dihasilkan saat fase `verify`:

```bash
./mvnw verify          # menjalankan unit (*Test) + integrasi (*IT) + laporan JaCoCo
# Hasil: target/site/jacoco/index.html (HTML), jacoco.xml (XML), jacoco.csv (CSV)
```

- Unit test (Surefire) **dan** integration test (Failsafe/Testcontainers) digabung
  ke satu `jacoco.exec` (`prepare-agent` + `prepare-agent-integration`).
- Laporan diunggah sebagai artefak CI (`ci.yml` → `laporan-coverage`).
- **Tanpa gate `check` yang menggagalkan build** — angka dicatat sebagai bukti;
  gerbang keras (ambang minimum) ditetapkan menyusul bila cakupan sudah stabil.

### 1.1 Cara membaca angka

| Konteks | Angka cakupan | Catatan |
|---|---|---|
| `./mvnw test` (unit saja, tanpa Docker) | rendah (mis. ~27% baris) | IT di-skip → kelas service/repository belum terukur |
| `./mvnw verify` (unit + IT, butuh Docker) | **angka sebenarnya** | dipakai sebagai bukti rilis |

> **Catatan:** angka unit-saja **bukan** cakupan sebenarnya. Jalankan `verify`
> (atau lihat artefak CI) untuk angka lengkap. Catat angka rilis di tabel di bawah.

### 1.2 Angka tercatat

| Tanggal | Lingkungan | Baris | Instruksi | Cabang | Sumber |
|---|---|---|---|---|---|
| 2026-10-13 | laptop dev (unit saja) | 27,8% | 26,9% | 25,9% | `target/site/jacoco/jacoco.csv` |
| _isi setelah CI_ | runner CI (unit + IT) | _tbd_ | _tbd_ | _tbd_ | artefak `laporan-coverage` |

---

## 2. Skenario UAT End-to-End

Dijalankan pada lingkungan uji (staging / lokal `demo`). Kolom **Status** diisi
saat eksekusi: ✅ lulus · ❌ gagal · ⬜ belum.

**Prasyarat:** profil `demo` + seed demo (lihat [`demo-e2e-kasir.md`](demo-e2e-kasir.md)),
atau lingkungan staging dengan kredensial SKOOLIA.

### 2.1 Kasir & Penjualan

| # | Skenario | Langkah inti | Ekspektasi | Bukti otomatis | Status |
|---|---|---|---|---|---|
| U1 | Tap normal (Kartu Tamu) | pilih item → tap → (beep) | saldo & stok terpotong, transaksi tercatat, notifikasi terkirim | `TapServiceIT`, `DemoE2EIT` | ⬜ |
| U2 | Saldo kurang | tap dengan total > saldo | pesan "Saldo kurang Rp X", **tidak** potong saldo/stok | `TapServiceIT.saldoKurang*` | ⬜ |
| U3 | Stok tidak cukup | tap qty > stok | pesan "Stok … tidak cukup", tidak ada transaksi | `TapServiceIT.stokKurang*` | ⬜ |
| U4 | Tap ganda (idempotency) | kirim ulang `idempotencyKey` sama | hasil sama, saldo/stok **tidak** dobel | `TapServiceIT.tapGandaIdempotent` | ⬜ |
| U5 | Kartu diblokir instan | blokir → tap sedetik kemudian | ditolak "Kartu diblokir" | `KontrolKartuServiceIT` | ⬜ |
| U6 | Limit harian | belanja melebihi limit | ditolak "Melebihi limit harian (sisa Rp X)" | `KontrolKartuServiceIT` | ⬜ |
| U7 | Blokir item/kategori | ortu blokir item → tap item itu | ditolak "Item … diblokir oleh orang tua" | `KontrolKartuServiceIT` | ⬜ |
| U8 | Void | batalkan transaksi sesi terbuka + alasan | saldo & stok kembali, belanja harian berkurang, notifikasi void | `VoidServiceIT` | ⬜ |
| U9 | Tutup kasir | tutup sesi | rekap bruto/void/bersih, sesi terkunci, posting Buku Kas | `SesiKasirServiceIT`, `DemoE2EIT` | ⬜ |
| U10 | Tutup otomatis | lewati jam tutup | sesi tertutup otomatis + posting | `JamTutupKasirSchedulerTest`, `SesiKasirSchedulerTest` | ⬜ |

### 2.2 Saldo, Top-up & Kartu Tamu

| # | Skenario | Langkah inti | Ekspektasi | Bukti otomatis | Status |
|---|---|---|---|---|---|
| U11 | Top-up tunai (TU) | cari siswa → nominal → simpan | saldo bertambah, bukti bernomor, notifikasi, teraudit | `SaldoTopUpService` IT | ⬜ |
| U12 | Top-up online (webhook) | callback PG sukses | saldo bertambah **hanya** setelah callback; duplikat idempoten | `TopUpOnlineWebhookIT` | ⬜ |
| U13 | Batas saldo & min/maks | top-up melebihi batas | ditolak sebelum pembayaran | `BatasSaldoTopUpIT` | ⬜ |
| U14 | Kartu Tamu: registrasi | daftar kartu + tap UID | nomor kartu otomatis, label pemegang, anti-tabrakan UID | `KartuTamuServiceIT` | ⬜ |
| U15 | Kartu Tamu: refund tunai | kembalikan kartu | saldo di-refund, saldo jadi 0, kartu bisa dipakai ulang | `RefundKartuTamuService` IT | ⬜ |
| U16 | Refund/pindah siswa keluar | proses siswa nonaktif | saldo → 0, kartu diblokir, opsi pindah ke saudara | `RefundSaldoServiceIT` | ⬜ |
| U17 | Setoran kas TU | rekap top-up tunai per petugas | rekap + selisih tercatat | `SetoranTuServiceIT` | ⬜ |

### 2.3 Katalog, Stok & HPP

| # | Skenario | Langkah inti | Ekspektasi | Bukti otomatis | Status |
|---|---|---|---|---|---|
| U18 | Katalog menu | buat/ubah menu & kategori | tersimpan, soft delete, ubah harga teraudit | `KatalogServiceIT` | ⬜ |
| U19 | Barang masuk & HPP | input pembelian | stok bertambah, HPP rata-rata tertimbang diperbarui, posting "Belanja Stok Kantin" | `PostingBukuKasStokIT`, `HppServiceTest` | ⬜ |
| U20 | Opname & penyesuaian | input stok fisik + alasan | selisih tercatat sebagai mutasi, nilai kerugian muncul | `OpnameBatchIT` | ⬜ |
| U21 | Peringatan stok menipis | stok ≤ minimum | tampil "Stok menipis"; stok 0 = "Habis" | `LaporanServiceIT` | ⬜ |

### 2.4 Laporan & Rekonsiliasi (bukti metrik PRD §12)

| # | Skenario | Langkah inti | Ekspektasi | Bukti otomatis | Status |
|---|---|---|---|---|---|
| U22 | Rekonsiliasi harian | `GET /api/laporan/rekonsiliasi` | `seimbang=true`, `selisih=0` (invariant PRD §5) | `LaporanServiceIT` | ⬜ |
| U23 | Laporan penjualan & laba | penjualan/item/kategori/titik/petugas | angka sesuai transaksi; laba = bersih − HPP | `LaporanServiceIT` | ⬜ |
| U24 | Laporan stok & kartu stok | stok, nilai persediaan, kartu stok per item | saldo berjalan konsisten | `LaporanServiceIT` | ⬜ |
| U25 | Ekspor Excel | `GET /api/laporan/ekspor?jenis=…` | berkas `.xlsx` valid untuk **semua** jenis | `LaporanServiceIT` | ⬜ |

### 2.5 Keamanan & Tenant (PRD §11)

| # | Skenario | Langkah inti | Ekspektasi | Bukti otomatis | Status |
|---|---|---|---|---|---|
| U26 | Tenant scoping | akses data sekolah lain | **404** (bukan 403) | `IsolasiTenantLockIT`, `KontrolKartuServiceIT` | ⬜ |
| U27 | RBAC backend | peran tanpa hak memanggil endpoint | ditolak (403) | `PeranAspectTest`, `RbacKepsekGuardTest` | ⬜ |
| U28 | Webhook tanpa signature | POST tanpa HMAC sah | **401** (fail-closed) | `WebhookSignatureFilterTest` | ⬜ |
| U29 | Cabut token | `POST /api/auth/cabut` | token tercabut ditolak | `TokenBlacklistRedisTest` | ⬜ |

> **Menjalankan bukti otomatis:** `./mvnw verify` (butuh Docker) menjalankan
> seluruh IT di atas. Tanpa Docker, IT di-skip (bukan lulus).

---

## 3. Definition of Done — Tingkat Rilis

Pelengkap DoD per fitur ([`AGENTS.md`](../architecture/AGENTS.md) §9). Sebuah
**rilis** dianggap siap hanya bila **semua** tercentang.

### 3.1 Mutu kode & test
- [ ] `./mvnw verify` **hijau** (unit + integration) di runner CI.
- [ ] Cakupan JaCoCo tercatat di §1.2 (artefak CI terunggah).
- [ ] Tidak ada `TODO`/`FIXME` di `src/main` (cek: `grep`).
- [ ] Migrasi Flyway lolos `flyway:validate`; jumlah migrasi sesuai `README`.

### 3.2 Fungsional (PRD)
- [ ] Seluruh skenario UAT §2 **✅ lulus** pada lingkungan uji.
- [ ] Metrik PRD §12 terukur & tercatat (tabel §4).

### 3.3 Integrasi eksternal
- [ ] Enam adapter integrasi tersambung ke endpoint/kredensial produksi (#164).
- [ ] `kantin.integrasi.wajib-aktif=true` & aplikasi **start sukses** (tidak ada `*Fallback`).
- [ ] Public key RS256 admin-be & mobile-be produksi terpasang.

### 3.4 Keamanan & konfigurasi
- [ ] Checklist pra-rilis produksi tercentang semua ([`checklist-pra-rilis.md`](checklist-pra-rilis.md)).
- [ ] Rahasia (webhook/internal/JWT) terisi; profil dev **nonaktif**.
- [ ] Proteksi branch `main`/`develop` **aktif** (#171).

### 3.5 Performa & operasional
- [ ] SLO tap p95 ≤ 1 dtk pada runner target (steady state) — tercatat di [`uji-beban-tap.md`](uji-beban-tap.md).
- [ ] Cold-start (JVM dingin 100 tap/dtk) ditangani atau diterima (#180).
- [ ] Observability aktif (metrik `kantin_tap_duration_seconds`, alert) — [`OBSERVABILITY.md`](OBSERVABILITY.md).

### 3.6 Dokumentasi
- [ ] `README.md` (status, jumlah test, migrasi) sinkron dengan kode.
- [ ] `architecture/API-ENDPOINTS.md` sinkron (dijaga `ApiEndpointsDocTest`).
- [ ] Dokumen keputusan (ADR) diperbarui bila ada perubahan arsitektur.

---

## 4. Metrik Keberhasilan (PRD §12) — lembar ukur

Diukur pada lingkungan uji / 3 bulan setelah rilis pilot. Isi kolom **Hasil**.

| Metrik (PRD §12) | Target | Cara ukur | Hasil |
|---|---|---|---|
| Siswa aktif bertransaksi ≥ 1×/minggu | ≥ 60% | query `transaksi` per `subjek_id` unik/minggu | _tbd_ |
| Ortu siswa aktif pernah top-up via app | ≥ 50% | `saldo_ledger` jenis `TOPUP_ONLINE` unik per siswa | _tbd_ |
| Laporan rekonsiliasi harian seimbang | **selalu** (selisih = 0) | `GET /api/laporan/rekonsiliasi` → `seimbang=true` | _tbd_ |
| Selisih stok saat opname | < 2% nilai persediaan / bulan | `GET /api/laporan/kerugian-stok` vs nilai persediaan | _tbd_ |
| Komplain saldo terpotong ganda | < 0,1% transaksi | audit `void` + laporan pembatalan | _tbd_ |
| Waktu respons tap (p95) | ≤ 1 dtk | k6 / metrik Prometheus (runner target) | _tbd_ |

> Saat dijalankan di lokal `demo`, U22 (rekonsiliasi) & U25 (ekspor) dapat dipakai
> sebagai bukti cepat "invariant seimbang" & "semua laporan bisa diekspor".
