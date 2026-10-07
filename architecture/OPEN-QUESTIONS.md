# ❓ OPEN-QUESTIONS.md — Pertanyaan Terbuka & Blocking Issues

> Daftar hal yang **belum diputuskan** dan **menghambat** pekerjaan. Update status di sini setiap kali ada jawaban. Tautkan ke ADR bila keputusan sudah diambil.

Legenda status: 🔴 Blocking · 🟡 Perlu dijawab (tidak memblokir sekarang) · 🟢 Terjawab

---

## A. Blocking Teknis (harus dijawab sebelum coding)

| # | Pertanyaan | Untuk siapa | Status | Catatan |
|---|---|---|---|---|
| **Q1** | Format klaim JWT staf (nama field `user_id`/`sekolah_id`/`role`) + lokasi **public key RS256** admin-be? | Tim admin-be | 🟡 SEBAGIAN TERJAWAB | ✅ **Format klaim TERKONFIRMASI (2026-10-06)** dari sumber `admin-be/JwtUtils.buildToken()`: `sub`(username), `typ`(access/refresh), `user_id`(Long), `nama`(String), `role`(String nama role), `sekolah_id`(Long), `jti`/`iat`/`exp`. **TIDAK menyetel `iss`.** 2 bug integrasi ditemukan & diperbaiki (lihat #14 / `KompatibilitasTokenStafAdminTest`): (a) `user_id` numerik terbaca `"42.0"` (jjwt-gson → Double) → `aktorIdWajib()` gagal; (b) `iss` absen → token staf sah ditolak. ⚠️ **Public key MASIH temporary** (`.env.jwt-temporary`) — WAJIB ganti production key sebelum staging/prod |
| **Q2** | **Public key RS256 + format klaim JWT ortu** dari mobile-be? (repo belum ada di clone) | Tim mobile-be | 🟡 SEBAGIAN TERJAWAB | ✅ **Harness dummy tersedia (2026-10-06):** `scripts/dev/gen-jwt-dummy.sh` + `mint-jwt-dummy.sh` (lihat `docs/dev-jwt-dummy.md`) — token ortu dummy (`role=ORANG_TUA`, `siswa_id`) lolos verifikasi RS256 asli, teruji `DummyTokenDevTest`. ⏳ **Public key produksi mobile-be tetap dibutuhkan** sebelum staging/prod |
| **Q3** | Tambah `refModul` kantin ke `migrateBukuKas()` admin-be, atau pakai `refModul=null`? | Tim admin-be | 🔴 (ada mitigasi) | Menentukan cara posting Buku Kas. ⚠️ **Mitigasi aktif (2026-10-06):** posting kantin sudah dibangun di balik `BukuKasPort` + fallback `DILEWATI`; `refModul` dikirim **null** (properti `kantin.bukukas.ref-modul` kosong) agar entri masuk `remainingBks` & tidak dihapus `migrateBukuKas`. Isi `KANTIN_BUKUKAS_REF_MODUL` hanya setelah admin-be menambah case kantin |
| **Q4** | Kontrak payload **callback top-up** dari callback-be (field `refId` PG)? | Tim callback-be | 🟡 (ada mitigasi) | Blokir fitur top-up online. ⚠️ **Mitigasi aktif (2026-10-07):** handler `TopUpOnlineWebhookHandler` sudah terpasang di pipa webhook (signature HMAC + anti-replay + idempotency) → `SaldoTopUpService.topUpOnline(...)` (jenis `TOPUP_ONLINE`, idempoten per `refId` PG, tenant-scoped). Kontrak belum final ⇒ nama jenis event & tipe subjek default **konfigurabel** (`kantin.webhook.topup.event-types`, `kantin.webhook.topup.subjek-tipe-default`) dan field payload dibaca via **alias** (`refId`/`orderId`/`trxId`, `nominal`/`amount`, dst); jenis tak dikenal → `DIABAIKAN` (fail-safe). Teruji `TopUpOnlineWebhookIT`. Sesuaikan via konfigurasi begitu kontrak dikonfirmasi — kode keamanan tak berubah |
| **Q5** | Endpoint & format **push notification** mobile-be? | Tim mobile-be | 🔴 | Blokir notifikasi ortu |
| **Q6** | Kontrak API **aktivasi modul & fee platform** (internal-be)? | Tim internal-be | 🟡 | Blokir pengecekan aktivasi |
| **Q7** | **Lookup kartu**: REST API internal vs akses data; SLA latency? | Tim admin-be | 🔴 | Menentukan `SiswaKartuClient` |
| **Q8** | Pos Buku Kas **"Pendapatan Kantin" & "Belanja Stok Kantin"** dibuat otomatis saat modul diaktifkan? | Tim admin-be | 🟢 (solusi demo) | ✅ **Seeding otomatis sisi kantin-be (2026-10-07, #21):** `PosBukuKasService.pastikanPosStandar()` membuat pos standar ("Pendapatan Kantin"/MASUK, "Belanja Stok Kantin"/KELUAR, "Penyesuaian Kantin"/MASUK) per sekolah, **idempoten** (UNIQUE `(sekolah_id, nama)`); endpoint `POST /api/konfigurasi/pos-buku-kas/aktivasi`. Fallback lokal sampai admin-be konfirmasi — posting tetap lewat `BukuKasPort` |

---

## B. Keputusan Arsitektur (tim kantin-be)

| # | Pertanyaan | Saran | Status |
|---|---|---|---|
| **Q9** | Java 21 (lokal) atau 25 (parity admin-be)? | **Java 25** | 🟢 Diputuskan & dipakai — toolchain dikunci ke Java 25 (ADR-0001) |
| **Q10** | **RFID USB bridge** — browser tak bisa baca USB/serial langsung. Opsi: WebHID, WebSerial, atau agent lokal (Node/Electron)? | Perlu spike; **belum di PRD §13 poin 5** | 🟢 (spike selesai) | ✅ **Spike selesai (2026-10-08, #26)** → keputusan bertingkat di [ADR-0005](./adr/0005-rfid-usb-bridge.md): demo = **keyboard-wedge** (reader Kiosk, ADR-0008); produksi = **WebHID/WebSerial** (bila browser boleh dikunci Chromium) **atau agent lokal** (bila reader vendor-SDK/bebas browser). Perbandingan & pola aman FE: `docs/spesifikasi-rfid-usb-bridge.md`. ⏳ Tetap butuh **Q17** (spesifikasi reader fisik) untuk memilih opsi produksi |
| **Q11** | PostgreSQL kantin: DB terpisah, tapi server sama dengan admin-be? | DB terpisah | 🟡 |
| **Q12** | Strategi locking ledger: pessimistic (`FOR UPDATE`) vs optimistic? | Pessimistic untuk debit | 🟢 Diputuskan (ADR-0003): pessimistic `FOR UPDATE` untuk debit |
| **Q13** | Pemisahan layanan tap berlatensi rendah (Java vs service ringan)? | **Java dulu**; split hanya bila gagal SLO p95<1dtk | 🟢 Diputuskan (ADR-0007): tap **tetap di Java**; split hanya bila uji beban buktikan p95>1dtk → ADR baru |

---

## C. Risiko Produk / Legal (dari PRD §13)

| # | Topik | Status | Pemilik |
|---|---|---|---|
| **Q14** | **Internet/server mati → kantin tak bisa jualan.** Butuh prosedur darurat? | 🟢 (solusi demo) | Produk/Sekolah — [ADR-0006](./adr/0006-prosedur-darurat-offline.md) + pencatatan insiden `InsidenOfflineService` (2026-10-07, #23); `POST/GET /api/konfigurasi/insiden-offline` |
| **Q15** | **Regulasi BI** soal dana titipan closed-loop — aman dari ketentuan uang elektronik? | 🟢 (postur demo, ⚠️ legal sebelum prod) | Legal — postur `DANA_TITIPAN_CLOSED_LOOP` [ADR-0009](./adr/0009-postur-regulasi-dana-titipan-closed-loop.md) (2026-10-07, #24); lihat `GET /api/konfigurasi/profil` |
| **Q16** | **Kebijakan saldo mengendap** yang tak diklaim setelah siswa lulus? | 🟢 (solusi demo) | Sekolah/Legal — default `REFUND`, konfigurabel per sekolah `KebijakanKantinService` (2026-10-07, #25); `GET/PUT /api/konfigurasi/kebijakan` |
| **Q17** | Spesifikasi **RFID reader USB** kasir = reader Kiosk Presensi? | 🟢 (asumsi demo) | Tim RFID — asumsi sama Kiosk (HID keyboard-wedge) [ADR-0008](./adr/0008-reader-rfid-samakan-kiosk-demo.md) (2026-10-07, #22); lihat `GET /api/konfigurasi/profil` |
| **Q18** | Siswa tanpa foto boleh transaksi (dengan peringatan)? | 🟢 Diputuskan (v4), ditinjau pasca-pilot | — |
| **Q19** | Guru/karyawan/tamu bayar pakai apa? | 🟢 Diputuskan: **Kartu Tamu** | — |

---

## D. Cara Menggunakan Dokumen Ini

1. Saat kick-off, tim menandai Q mana yang mereka bisa jawab sendiri vs butuh pihak luar.
2. Setiap pertanyaan yang terjawab → ubah status jadi 🟢 dan **catat keputusan** (tanggal + siapa + jawaban). Bila keputusan besar → buat ADR di [`adr/`](./adr/).
3. Q 🔴 = **blocking**; jangan mulai modul terkait sebelum terjawab. Kerjakan modul yang tidak terblokir lebih dulu (mis. katalog menu, ledgernya sendiri).
