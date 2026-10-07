# ADR-0010 — Topologi PostgreSQL kantin: DB terpisah, server sama atau terpisah?

- **Status:** Diusulkan (rekomendasi tim kantin-be; butuh konfirmasi tim DB/admin-be)
- **Tanggal:** 2026-10-08
- **Pengusul:** Tim kantin-be (koordinasi: jobdesk Database & Integrasi)
- **Terkait:** PRD §11.4; ADR-0001 (DB terpisah dari admin-be); INTEGRATIONS.md §1 & pembuka;
  ENVIRONMENT.md §6; Q11 di OPEN-QUESTIONS; issue #27

---

## Konteks

Q11 (🟡, saran: **DB terpisah**) meminta konfirmasi: PostgreSQL kantin = **DB
terpisah** (sudah diputuskan di ADR-0001), tetapi **apakah servernya sama dengan
`admin-be`?**

Ini menentukan:
- **Isolasi data antar-tenant/modul** (PRD §11.4): kantin-be **tidak** boleh membaca
  tabel milik admin-be (`siswa`, `buku_kas`, dst) secara langsung.
- **Isolasi failure domain & resource**: DB kantin memegang **ledger append-only**
  (`saldo_ledger`, `mutasi_stok`) yang tumbuh terus; DB admin-be sudah besar
  (250+ migrasi Flyway, 1000+ file). Bercampur satu server menyatukan risiko.
- **Keamanan & least privilege**: user DB, hak akses, dan jalur backup berbeda.
- **Topologi deploy** (Docker/Jenkins) dan cara tim menjalankan dev/CI.

Batasan yang sudah berlaku:
- **ADR-0001:** kantin-be memakai PostgreSQL **DB terpisah** dari admin-be.
- **INTEGRATIONS.md pembuka:** semua panggilan lintas-sistem **wajib** lewat
  `service/integrasi/` (REST), **tidak** ada akses DB bersama.
- **ENVIRONMENT.md §6:** dev memakai `docker-compose` (PostgreSQL 18 + Redis).

## Keputusan

1. **DB kantin TERPISAH** dari admin-be (menegaskan ADR-0001). Kantin-be **tidak
   pernah** menunjuk DB admin-be, dan tidak pernah membaca tabelnya langsung —
   interaksi hanya lewat REST (`service/integrasi/`).
2. **Produksi: server PostgreSQL TERPISAH** dari admin-be. Alasan: isolasi failure
   domain, resource, keamanan, dan backup/restore independen. DB kantin tumbuh
   cepat (ledger append-only) dan tak boleh berbagi I/O dengan DB admin-be.
3. **Dev/CI: boleh satu host** (docker-compose lokal / container Testcontainers).
   Yang penting **nama database & kredensial berbeda**; keterpisahan *server*
   adalah syarat **produksi**, bukan syarat dev.
4. **Penjaga konfigurasi (guard).** Kantin-be memeriksa datasource saat start:
   menunjuk **DB admin-be** (nama DB yang dilarang) = **pelanggaran**; menunjuk
   **host yang sama** dengan admin-be (bila dikonfigurasi) = **peringatan**.
   Pelanggaran menggagalkan start **hanya bila** `kantin.topologi.enforce=true`
   (disarankan di produksi); default `false` (peringatan) agar dev/CI tetap lancar.

## Alasan

- **Integritas & isolasi modul** (PRD §11.4): memisahkan DB mencegah kantin-be
  menyalahgunakan repo admin-be dan menjaga batas kepemilikan data (kantin-be
  *memiliki* ledger; admin-be *memiliki* identitas).
- **Failure domain:** server yang sama berarti satu gangguan/replikasi/restore
  admin-be ikut melumpuhkan kantin (dan sebaliknya). Kantin adalah jalur uang.
- **Karakter beban berbeda:** ledger append-only + locking `FOR UPDATE` per tap
  menghasilkan pola I/O berbeda dari admin-be. Berbagi server menimbulkan
  *resource contention* yang sulit diprediksi.
- **Operasional:** backup, tuning, dan hak akses DB bisa disesuaikan per modul.
- **Tetap praktis:** untuk dev/CI cukup satu host — guard hanya menegakkan
  pemisahan **nama DB** (yang memang wajib), bukan pemisahan host.

## Alternatif yang Dipertimbangkan

| Alternatif | Kenapa tidak dipilih |
|---|---|
| **Satu server & satu DB untuk admin-be + kantin** | Melanggar isolasi data (ADR-0001); menyatukan failure domain & resource; sulit least privilege |
| **Satu server, DB terpisah** | Dapat diterima untuk **dev**, tetapi **produksi** menyatukan failure domain & I/O; dilarang sebagai default produksi |
| **DB terpisah + server terpisah (dipilih)** | Isolasi penuh; sedikit menambah biaya/ops server |
| **Akses langsung DB admin-be (tanpa REST)** | Melanggar INTEGRATIONS.md & batas modul |

## Konsekuensi

**Positif:** isolasi data & failure domain; keamanan/least privilege lebih baik;
backup/tuning independen; guard mencegah salah konfigurasi menunjuk DB admin-be.

**Negatif / risiko:** biaya & beban ops server produksi bertambah; butuh
koordinasi tim DB untuk menyediakan instance terpisah. Bila nanti diputuskan
berbagi server produksi, ADR ini **wajib ditinjau ulang**.

## Tindak Lanjut

- [ ] **Tim DB (M-Arkan):** konfirmasi server produksi terpisah (atau alasan
      eksplisit bila berbagi) → ubah status ADR jadi *Diterima*.
- [ ] Set `kantin.topologi.enforce=true` di staging/produksi + isi
      `kantin.topologi.host-db-admin-be` bila diketahui.
- [ ] Dokumentasikan topologi final (diagram) di ENVIRONMENT.md.
- [ ] Pastikan kredensial DB produksi memakai *least privilege* (hanya DB kantin).
