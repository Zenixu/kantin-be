# ❓ OPEN-QUESTIONS.md — Pertanyaan Terbuka & Blocking Issues

> Daftar hal yang **belum diputuskan** dan **menghambat** pekerjaan. Update status di sini setiap kali ada jawaban. Tautkan ke ADR bila keputusan sudah diambil.

Legenda status: 🔴 Blocking · 🟡 Perlu dijawab (tidak memblokir sekarang) · 🟢 Terjawab

---

## A. Blocking Teknis (harus dijawab sebelum coding)

| # | Pertanyaan | Untuk siapa | Status | Catatan |
|---|---|---|---|---|
| **Q1** | Format klaim JWT staf (nama field `user_id`/`sekolah_id`/`role`) + lokasi **public key RS256** admin-be? | Tim admin-be | 🟡 TEMPORARY | ⚠️ **WORKAROUND aktif (2026-10-05):** Temporary keypair di `.env.jwt-temporary` (admin-be) + `application-local.properties` (kantin-be). Token sudah include `sekolah_id`/`role`/`user_id`/`nama`. **WAJIB ganti production key sebelum staging/prod** |
| **Q2** | **Public key RS256 + format klaim JWT ortu** dari mobile-be? (repo belum ada di clone) | Tim mobile-be | 🔴 | Idem Q1 — decoder sudah siap 2-issuer |
| **Q3** | Tambah `refModul` kantin ke `migrateBukuKas()` admin-be, atau pakai `refModul=null`? | Tim admin-be | 🔴 (ada mitigasi) | Menentukan cara posting Buku Kas. ⚠️ **Mitigasi aktif (2026-10-06):** posting kantin sudah dibangun di balik `BukuKasPort` + fallback `DILEWATI`; `refModul` dikirim **null** (properti `kantin.bukukas.ref-modul` kosong) agar entri masuk `remainingBks` & tidak dihapus `migrateBukuKas`. Isi `KANTIN_BUKUKAS_REF_MODUL` hanya setelah admin-be menambah case kantin |
| **Q4** | Kontrak payload **callback top-up** dari callback-be (field `refId` PG)? | Tim callback-be | 🔴 | Blokir fitur top-up online |
| **Q5** | Endpoint & format **push notification** mobile-be? | Tim mobile-be | 🔴 | Blokir notifikasi ortu |
| **Q6** | Kontrak API **aktivasi modul & fee platform** (internal-be)? | Tim internal-be | 🟡 | Blokir pengecekan aktivasi |
| **Q7** | **Lookup kartu**: REST API internal vs akses data; SLA latency? | Tim admin-be | 🔴 | Menentukan `SiswaKartuClient` |
| **Q8** | Pos Buku Kas **"Pendapatan Kantin" & "Belanja Stok Kantin"** dibuat otomatis saat modul diaktifkan? | Tim admin-be | 🟡 | PRD §13 poin 7 |

---

## B. Keputusan Arsitektur (tim kantin-be)

| # | Pertanyaan | Saran | Status |
|---|---|---|---|
| **Q9** | Java 21 (lokal) atau 25 (parity admin-be)? | **Java 25** | 🟢 Diputuskan & dipakai — toolchain dikunci ke Java 25 (ADR-0001) |
| **Q10** | **RFID USB bridge** — browser tak bisa baca USB/serial langsung. Opsi: WebHID, WebSerial, atau agent lokal (Node/Electron)? | Perlu spike; **belum di PRD §13 poin 5** | 🔴 |
| **Q11** | PostgreSQL kantin: DB terpisah, tapi server sama dengan admin-be? | DB terpisah | 🟡 |
| **Q12** | Strategi locking ledger: pessimistic (`FOR UPDATE`) vs optimistic? | Pessimistic untuk debit | 🟢 Diputuskan (ADR-0003): pessimistic `FOR UPDATE` untuk debit |
| **Q13** | Pemisahan layanan tap berlatensi rendah (Java vs service ringan)? | **Java dulu**; split hanya bila gagal SLO p95<1dtk | 🟡 |

---

## C. Risiko Produk / Legal (dari PRD §13)

| # | Topik | Status | Pemilik |
|---|---|---|---|
| **Q14** | **Internet/server mati → kantin tak bisa jualan.** Butuh prosedur darurat? | 🔴 Perlu keputusan | Produk/Sekolah — usulan di [ADR-0006](./adr/0006-prosedur-darurat-offline.md) |
| **Q15** | **Regulasi BI** soal dana titipan closed-loop — aman dari ketentuan uang elektronik? | 🔴 **Wajib konfirmasi legal sebelum rilis** | Legal |
| **Q16** | **Kebijakan saldo mengendap** yang tak diklaim setelah siswa lulus? | 🟡 Perlu keputusan | Sekolah/Legal |
| **Q17** | Spesifikasi **RFID reader USB** kasir = reader Kiosk Presensi? | 🟡 Cek tim RFID | Tim RFID |
| **Q18** | Siswa tanpa foto boleh transaksi (dengan peringatan)? | 🟢 Diputuskan (v4), ditinjau pasca-pilot | — |
| **Q19** | Guru/karyawan/tamu bayar pakai apa? | 🟢 Diputuskan: **Kartu Tamu** | — |

---

## D. Cara Menggunakan Dokumen Ini

1. Saat kick-off, tim menandai Q mana yang mereka bisa jawab sendiri vs butuh pihak luar.
2. Setiap pertanyaan yang terjawab → ubah status jadi 🟢 dan **catat keputusan** (tanggal + siapa + jawaban). Bila keputusan besar → buat ADR di [`adr/`](./adr/).
3. Q 🔴 = **blocking**; jangan mulai modul terkait sebelum terjawab. Kerjakan modul yang tidak terblokir lebih dulu (mis. katalog menu, ledgernya sendiri).
