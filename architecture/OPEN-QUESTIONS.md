# ❓ OPEN-QUESTIONS.md — Pertanyaan Terbuka & Blocking Issues

> Daftar hal yang **belum diputuskan** dan **menghambat** pekerjaan. Update status di sini setiap kali ada jawaban. Tautkan ke ADR bila keputusan sudah diambil.

Legenda status: 🔴 Blocking · 🟡 Perlu dijawab (tidak memblokir sekarang) · 🟢 Terjawab

---

## A. Blocking Teknis (harus dijawab sebelum coding)

| # | Pertanyaan | Untuk siapa | Status | Catatan |
|---|---|---|---|---|
| **Q1** | Format klaim JWT staf (nama field `user_id`/`sekolah_id`/`role`) + lokasi **public key RS256** admin-be? | Tim admin-be | 🟢 TERJAWAB (ADAPTASI) | ⚠️ **KOREKSI FAKTUAL (2026-10-07):** Catatan sebelumnya SALAH. Diverifikasi langsung dari `admin-be/JwtUtils.buildToken()` baris 87-100: token admin-be **HANYA** berisi `sub`(username), `typ`(access/refresh), `jti`/`iat`/`exp`. **TIDAK menyetel** `user_id`, `nama`, `role`, `sekolah_id`, maupun `iss`. Info tersebut hanya ada di **response body login** (`JwtResponse`: id, name, roles, sekolah). **Keputusan tim BE (menyesuaikan admin-be, tidak mengubah):** `KlaimResolver` sudah toleran (`sub` → `userId` via fallback list); untuk `sekolah_id`/`role`/`nama` yang absen di JWT, kantin-be akan **memanggil `GET /api/auth/me` admin-be** sekali per session (cache in-memory/Redis, TTL = TTL token) untuk mengisi `IdentitasKantin`. `jwt.verify-issuer` tetap `false` untuk admin hingga admin-be menambah `iss`. Public key RS256: admin-be `application.properties` baris 8-10 (`jwt.privateKey`/`jwt.publicKey` base64 PKCS8/X509) — saat ini kosong → fallback HS256. Tim BE pakai keypair RS256 temporary (`.env.jwt-temporary`) untuk dev; produksi menunggu admin-be set env var. |
| **Q2** | **Public key RS256 + format klaim JWT ortu** dari mobile-be? (repo belum ada di clone) | Tim mobile-be | 🟡 SEBAGIAN TERJAWAB | ✅ **Harness dummy tersedia (2026-10-06):** `scripts/dev/gen-jwt-dummy.sh` + `mint-jwt-dummy.sh` (lihat `docs/dev-jwt-dummy.md`) — token ortu dummy (`role=ORANG_TUA`, `siswa_id`) lolos verifikasi RS256 asli, teruji `DummyTokenDevTest`. ⏳ **Public key produksi mobile-be tetap dibutuhkan** sebelum staging/prod |
| **Q3** | Tambah `refModul` kantin ke `migrateBukuKas()` admin-be, atau pakai `refModul=null`? | Tim admin-be | 🟢 TERJAWAB (ADAPTASI) | **Keputusan tim BE (2026-10-07, tidak mengubah admin-be):** Pakai `refModul="KANTIN"`. Diverifikasi dari `admin-be/BukuKasService.migrateBukuKas()` baris 387-425: `switch(refModul)` `default:` → `isOrphan=false` → entri masuk `remainingBks` & **tidak dihapus**. Tidak perlu admin-be tambah case kantin. `refModul="KANTIN"` (bukan `null`) agar entri terlacak & bisa di-query. Config `kantin.bukukas.ref-modul` diupdate dari kosong → `KANTIN`. |
| **Q4** | Kontrak payload **callback top-up** dari callback-be (field `refId` PG)? | Tim callback-be | 🟢 TERJAWAB (ADAPTASI) | **Kontrak ditetapkan (2026-10-07):** `POST /api/webhook/topup` dengan body `{refId, siswaId, sekolahId, nominal, status, paymentGateway, timestamp}` + header `X-Webhook-Signature` (HMAC-SHA256) + `X-Webhook-Timestamp`. `refId` = `order_id` Midtrans di-prefix `KANTIN-TOP-`. Handler `TopUpOnlineWebhookHandler` sudah terpasang (PR #87 merged): signature HMAC + anti-replay + idempotency per `refId` PG, tenant-scoped. Field dibaca via alias (`refId`/`orderId`/`trxId`, dst); jenis tak dikenal → `DIABAIKAN`. Teruji `TopUpOnlineWebhookIT`. Konfigurabel via `kantin.webhook.topup.*`. Kode keamanan tak perlu berubah. |
| **Q5** | Endpoint & format **push notification** mobile-be? | Tim mobile-be | 🟢 TERJAWAB (ADAPTASI) | **Kontrak ditetapkan (2026-10-07):** `POST /api/internal/notifikasi` ke mobile-be dengan body `{siswaId, sekolahId, jenis, judul, pesan, nominal, timestamp}`. Enum `jenis`: `BELANJA`/`VOID`/`TOPUP_TUNAI`/`TOPUP_ONLINE`/`REFUND`/`PINDAH_SALDO`. Fail-open sudah terpasang (PR #91 merged): `NotifikasiClientFallback` log + lanjut — notifikasi gagal ≠ transaksi gagal. Config: `kantin.notifikasi.enabled`, `kantin.notifikasi.base-url`. Admin-be pakai XMPP (`XmppService.sendHeadlineMessage`) → mobile-be kemungkinan teruskan via XMPP; kantin-be tidak perlu tahu transport, cukup REST. |
| **Q6** | Kontrak API **aktivasi modul & fee platform** (internal-be)? | Tim internal-be | 🟢 TERJAWAB (ADAPTASI) | **Keputusan tim BE (2026-10-07, tidak mengubah internal-be):** Kontrak ditetapkan `GET /api/internal/modul-kantin/status?sekolahId={id}` → `{aktif, fee:{perTopupNominal,perTopupPersen,biayaLangganan}}`. Penegakan PRD §10 ("bila nonaktif, seluruh endpoint kantin tidak tersedia") **diimplementasikan** lewat `AktivasiModulFilter` (jalur request, setelah JWT): sekolah dengan `aktif=false` (diketahui) ditolak **409**; status tidak diketahui/integrasi gagal/Redis down → **fail-open** (kantin tetap jalan). Cache Redis TTL pendek (`kantin.aktivasi-modul.cache-seconds`, default 60). Saklar `kantin.aktivasi-modul.enabled`. Endpoint diagnostik `GET /api/pengaturan-kantin/aktivasi-modul` tetap dapat dibaca walau modul nonaktif. Teruji `AktivasiModulFilterTest` (8 kasus). Sisa pekerjaan **tim internal-be**: menyediakan endpoint sesuai kontrak; kantin-be tinggal menambah `AktivasiModulRestClient` `@Primary` tanpa ubah alur. |
| **Q7** | **Lookup kartu**: REST API internal vs akses data; SLA latency? | Tim admin-be | 🟢 TERJAWAB (ADAPTASI) | **Keputusan tim BE (2026-10-07, tidak mengubah admin-be):** Diverifikasi: admin-be **tidak punya endpoint RFID lookup** — `SiswaRepository.findByRfidUid()` ada di repository tapi tidak terexpose ke controller. `SiswaController` hanya punya `get-by-id/{id}`, `get-by-nik/{id}`, `get` (search pagination). **Solusi adaptasi:** kantin-be akan buat `SiswaKartuClient` REST yang memanggil `POST /api/siswa/get` admin-be dengan `search={rfidUid}` — endpoint sudah ada, mengembalikan `Page<Siswa>`. Filter tenant dilakukan sisi kantin-be (cek `sekolahId` response == `sekolahId` JWT). SLA: query indexed `findByRfidUid` ~<50ms, within PRD §11 p95<1dtk. Cache data siswa (nama/foto/kelas) Redis TTL 5 menit; **status blokir selalu fresh** (PRD §11.11). Untuk dev/demo: `KartuLookupFallback` tetap sebagai fallback. |
| **Q8** | Pos Buku Kas **"Pendapatan Kantin" & "Belanja Stok Kantin"** dibuat otomatis saat modul diaktifkan? | Tim admin-be | 🟢 (solusi demo) | ✅ **Seeding otomatis sisi kantin-be (2026-10-07, #21):** `PosBukuKasService.pastikanPosStandar()` membuat pos standar ("Pendapatan Kantin"/MASUK, "Belanja Stok Kantin"/KELUAR, "Penyesuaian Kantin"/MASUK) per sekolah, **idempoten** (UNIQUE `(sekolah_id, nama)`); endpoint `POST /api/konfigurasi/pos-buku-kas/aktivasi`. Fallback lokal sampai admin-be konfirmasi — posting tetap lewat `BukuKasPort` |

---

## B. Keputusan Arsitektur (tim kantin-be)

| # | Pertanyaan | Saran | Status |
|---|---|---|---|
| **Q9** | Java 21 (lokal) atau 25 (parity admin-be)? | **Java 25** | 🟢 Diputuskan & dipakai — toolchain dikunci ke Java 25 (ADR-0001) |
 feat/spike-rfid-usb-bridge-adr
| **Q10** | **RFID USB bridge** — browser tak bisa baca USB/serial langsung. Opsi: WebHID, WebSerial, atau agent lokal (Node/Electron)? | Perlu spike; **belum di PRD §13 poin 5** | 🟢 (spike selesai) | ✅ **Spike selesai (2026-10-08, #26)** → keputusan bertingkat di [ADR-0005](./adr/0005-rfid-usb-bridge.md): demo = **keyboard-wedge** (reader Kiosk, ADR-0008); produksi = **WebHID/WebSerial** (bila browser boleh dikunci Chromium) **atau agent lokal** (bila reader vendor-SDK/bebas browser). Perbandingan & pola aman FE: `docs/spesifikasi-rfid-usb-bridge.md`. ⏳ Tetap butuh **Q17** (spesifikasi reader fisik) untuk memilih opsi produksi |
| **Q11** | PostgreSQL kantin: DB terpisah, tapi server sama dengan admin-be? | DB terpisah | 🟡 |

| **Q10** | **RFID USB bridge** — browser tak bisa baca USB/serial langsung. Opsi: WebHID, WebSerial, atau agent lokal (Node/Electron)? | Perlu spike; **belum di PRD §13 poin 5** | 🟢 Diputuskan (ADR-0011, 2026-10-07) | **Keputusan tim kantin-be:** berjenjang — **MVP/demo pakai keyboard-wedge (HID)** (selaras asumsi reader = Kiosk Presensi, ADR-0008; tanpa komponen tambahan), **target produksi pakai WebHID/WebSerial** bila reader mendukung, **fallback agent lokal** untuk reader non-standar. Transport reader **tidak mengubah** kontrak backend (`TapRequest.rfidUid` divalidasi `@UidKartuValid`). Pemilihan final menunggu konfirmasi Q17. ADR-0005 digantikan ADR-0011. Sisa: spike kompatibilitas reader (issue #26). |
| **Q11** | PostgreSQL kantin: DB terpisah, tapi server sama dengan admin-be? | DB terpisah | 🟢 Diputuskan (ADR-0010, 2026-10-07) | **Keputusan tim kantin-be:** **DB TERPISAH** (`kantin_db`, sudah diimplementasi), **server boleh sama (co-located) untuk MVP** dengan syarat: user/role DB terpisah tanpa grant lintas-DB, tanpa FK/join/dblink ke tabel admin-be, backup terpisah, resource dibatasi. Produksi ditentukan infra lewat `DB_URL` — tanpa ubah kode; pindah ke instance terpisah cukup ganti `DB_URL`. Wajib ditinjau ulang sebelum skala multi-sekolah. |
 main
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
