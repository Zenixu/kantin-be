# ADR-0012 — Peran KEPSEK dipisah dari ADMIN_SEKOLAH (read-only laporan)

- **Status:** Diterima (2026-10-08)
- **Tanggal:** 2026-10-08
- **Pengusul:** Tim kantin-be
- **Terkait:** PRD §9.5, §9.6, §11.5; ADR-0002; issue #123, #41, #44

---

## Konteks

PRD §9.5 memberi **Kepala Sekolah (Kepsek)** hak **baca** pada sebagian laporan
(rekonsiliasi harian, saldo mengendap, penjualan/laba kotor, kerugian stok).
PRD §9.6 mengatur menu RBAC SKOOLIA: "Kantin – Pengaturan & Titik Kasir →
**Admin (CRUD)**"; "Kantin – Laporan → sesuai kolom akses §9.5 (**read**)";
"Kantin – Refund & Koreksi → Bendahara (create, read), **Kepsek (read)**".

Namun `KlaimResolver.petakanPeran` melebur peran yang memuat `KEPSEK`/
`KEPALA_SEKOLAH` ke `AktorKantin.ADMIN_SEKOLAH`. Karena `ADMIN_SEKOLAH` memegang
**CRUD** (pengaturan kantin & titik kasir §9.6, blokir kartu §9.6, cabut token),
kepsek **mewarisi hak tulis** yang tidak seharusnya — **eskalasi hak** dan
pelanggaran least-privilege (PRD §11.5).

## Keputusan

Tambah nilai enum **`AktorKantin.KEPSEK`** dan petakan `KEPSEK`/
`KEPALA_SEKOLAH` ke situ (bukan `ADMIN_SEKOLAH`). Peran `KEPSEK` bersifat
**read-only**:

1. **Dibuka** pada laporan §9.5 yang menyebut Kepsek: penjualan/laba kotor,
   saldo mengendap, rekonsiliasi, kerugian stok — termasuk **ekspor** jenis
   tersebut.
2. **Ditutup** pada: laporan stok & barang masuk (§9.5 hanya Pengelola &
   Bendahara), seluruh endpoint tulis pengaturan/titik kasir/kebijakan
   (§9.6 = Admin CRUD), kontrol kartu (blokir/limit), dan cabut token.
3. **Ekspor** (`GET /api/laporan/ekspor`) menegakkan batas per jenis: kepsek
   yang meminta `STOK`/`BARANG_MASUK` → **403** meski anotasi mengizinkan peran.
4. **Refund/koreksi**: kepsek boleh **membaca** daftar kandidat refund §9.6;
   eksekusi refund/pindah-saldo tetap hanya TU/admin/pengelola (tulis).

## Alasan

- **Least-privilege & PRD §11.5.** RBAC ditegakkan di backend per endpoint;
  memisahkan peran di enum adalah cara paling langsung mencegah pewarisan hak.
- **Enum sebagai kontrak.** `AktorKantin` dipakai bersama oleh `@PerluPeran`,
  `KonteksResponse`, dan (potensial) FE — menambah `KEPSEK` membuat hak terlihat
  eksplisit di anotasi, bukan tersembunyi di dalam peleburan string.
- **Sesuai tabel §9.6.** Kepsek memang punya entri sendiri (read) pada
  "Refund & Koreksi" dan "Laporan", jadi peran tersendiri konsisten dengan PRD.

## Alternatif yang Dipertimbangkan

| Alternatif | Kenapa tidak dipilih |
|---|---|
| Biarkan kepsek = ADMIN_SEKOLAH, batasi di tiap controller | Rapuh: tiap endpoint baru harus ingat mengecualikan kepsek; mudah bocor |
| Flag boolean `readOnly` pada `IdentitasKantin` | Tidak terlihat di `@PerluPeran`; perlu logika tambahan di aspect; enum lebih jelas |
| Pertahankan enum, tapi kepsek = TIDAK_DIKENAL | Menghilangkan hak baca sah kepsek (§9.5) — over-restrictive |

## Konsekuensi

**Positif:** eskalasi hak tertutup; hak kepsek eksplisit & teruji; laporan tetap
bisa dibaca/diekspor kepsek sesuai §9.5.

**Negatif / risiko:** perlu memastikan tiap endpoint laporan baru memutuskan
apakah kepsek berhak (disengaja — keputusan eksplisit). Nilai enum baru
mengubah `KonteksResponse.peran` untuk token kepsek (dari `ADMIN_SEKOLAH` →
`KEPSEK`); FE yang memeriksa peran harus menyesuaikan (dokumentasikan ke tim FE).

## Tindak Lanjut

- [x] `AktorKantin.KEPSEK` + pemetaan di `KlaimResolver`
- [x] `@PerluPeran` laporan §9.5 menyertakan kepsek pada yang berhak
- [x] Ekspor per-jenis menolak kepsek untuk STOK/BARANG_MASUK
- [x] Uji regresi `RbacKepsekGuardTest` + `KlaimResolverPeranTest`
- [ ] Kabari tim FE: `KonteksResponse.peran` kini bisa bernilai `KEPSEK`
- [ ] Terapkan menu RBAC §9.6 di role management SKOOLIA (pihak admin-be)
