# 🔐 SECURITY.md — Autentikasi & Otorisasi `kantin-be`

> Sumber tunggal cara kerja keamanan. Baca bersama `INTEGRATIONS.md` &
> `ADR-0002-jwt-rs256-tanpa-login-sendiri.md`.

---

## 1. Prinsip

1. **Tanpa login sendiri** (ADR-0002). `kantin-be` tidak punya `/login`,
   `/refresh`, `/logout`, tabel user, atau password. Identitas **hanya** dari
   JWT yang diterbitkan `admin-be` (staf) & `mobile-be` (ortu).
2. **Fail-closed.** Ragu = tolak. Token tanpa identitas valid → 401.
   Peran tak dikenal → `TIDAK_DIKENAL` (tak punya hak apa pun).
3. **Keamanan di backend** (PRD §11.5). Menyembunyikan tombol di FE **bukan**
   pengaman. Setiap endpoint wajib dijaga di server.
4. **Tenant terpisah.** Data sekolah lain dijawab **404**, bukan 403
   (PRD §11.4) — agar keberadaan data sekolah lain tidak bocor.

---

## 2. Alur Verifikasi Token

```
Request (Authorization: Bearer <jwt>  atau  Cookie: skoolia-cookies)
   │
   ▼
JwtAuthTokenFilter  ──►  KantinJwtDecoder
   │                        ├─ coba verify pakai public key ADMIN (RS256)
   │                        └─ gagal? coba public key MOBILE (RS256)
   │
   ├─ token kadaluwarsa / signature salah  → 401
   ├─ token typ=refresh dipakai sebagai access → 401
   │
   ▼
KlaimResolver  ──►  IdentitasKantin { userId, nama, sekolahId, peran, sumber, siswaId }
   │                (toleran nama klaim; normalisasi peran)
   │
   ├─ userId kosong → 401
   │
   ▼
SecurityContext + TenantContext diisi
   │
   ▼
@PerluPeran (RBAC) ──► 403 bila peran tak berhak
   │
   ▼
SekolahGuard ──► 404 bila data milik sekolah lain
   │
   ▼
Controller (tipis) ──► Service (aturan bisnis) ──► DB
```

---

## 3. Kelas Kunci

| Kelas | Peran |
|---|---|
| `config/security/KantinJwtDecoder` | Verifikasi RS256 terhadap 2 issuer (admin & mobile) |
| `security/KlaimResolver` | Petakan klaim JWT → `IdentitasKantin` (toleran nama klaim) |
| `security/IdentitasKantin` | Principal terverifikasi (pengganti `User`) |
| `security/TenantContext` | `ThreadLocal` sekolah pemanggil; **wajib** di-clear |
| `security/SekolahGuard` | Pastikan data milik tenant (404 bila bukan) |
| `security/PerluPeran` + `PeranAspect` | RBAC deklaratif per method |
| `config/webhook/WebhookSignatureFilter` + `WebhookSignatureVerifier` | Verifikasi HMAC webhook + anti-replay + allowlist IP (B34) |
| `config/security/WebSecurityConfig` | Rantai filter, endpoint publik, CORS |

---

## 4. Peran (AktorKantin)

| Peran | Deskripsi | Hak umum |
|---|---|---|
| `PETUGAS_KANTIN` | Operator titik kasir | Tap, lihat transaksi sendiri |
| `PENGELOLA_KANTIN` | Pengelola menu/stok | Semua petugas + katalog, stok, opname |
| `TU_SEKOLAH` | Tata usaha | Top-up tunai, Buku Kas, laporan |
| `ADMIN_SEKOLAH` | Admin sekolah | Semua + sesi kasir, konfigurasi |
| `ORANG_TUA` | Dari mobile-be | **Hanya baca** saldo/riwayat anak sendiri |
| `TIDAK_DIKENAL` | Fallback | Tidak ada hak |

> ⚠️ Pemetaan string peran SKOOLIA → enum ini di `KlaimResolver.petakanPeran`.
> **Persempit setelah Q1/Q2 terjawab** (saat ini toleran banyak variasi).

---

## 5. Kontrak Endpoint

| Endpoint | Auth | Peran |
|---|---|---|
| `GET /actuator/health` | publik | — |
| `POST /api/webhook/{sumber}` | **HMAC-SHA256 + anti-replay** (bukan token) | — |
| `GET /api/internal/**` | **HMAC-SHA256 + anti-replay** (rahasia terpisah) | — (mesin-ke-mesin) |
| `GET /api/auth/me` | token | apa pun yang valid |
| `POST /api/kasir/tap` | token | PETUGAS/PENGELOLA/ADMIN |

### 5.1 Webhook masuk — verifikasi signature (B34)

`permitAll` di `/api/webhook/**` **bukan** berarti tanpa autentikasi: token user
memang tidak dipakai (SKOOLIA tidak login), tetapi setiap request **wajib**
membawa HMAC sah. `WebhookSignatureFilter` menegakkan, **fail-closed**:

```
POST /api/webhook/{sumber}
  X-Webhook-Timestamp: <epoch detik>
  X-Webhook-Signature: sha256=<hex HMAC-SHA256(rahasia, timestamp + "." + body_mentah)>
  X-Webhook-Id:        <id event unik>            (atau field body "eventId")
```

| Kondisi | HTTP |
|---|---|
| Signature sah & timestamp dalam jendela `±tolerance-seconds` | diteruskan |
| Signature salah / header kurang / timestamp kedaluwarsa (replay) | **401** |
| IP di luar `kantin.webhook.allowed-ips` (bila diisi) | **403** |
| Badan melebihi `kantin.webhook.max-body-bytes` | **413** |
| `kantin.webhook.secret` kosong (belum dikonfigurasi) | **503** |

- **Rahasia dari environment** (`KANTIN_WEBHOOK_SECRET`), bukan hardcode.
- **Idempotency** per `(sumber, eventId)` (tabel `webhook_event`, migrasi V12):
  retry event sama ⇒ tidak diproses ulang (respons `replay=true`); event id sama
  dengan payload berbeda ⇒ **409**.
- Perbandingan signature **konstan-waktu**; timestamp ikut ditandatangani.
- Handler per jenis event lewat `WebhookHandlerPort` (kontrak payload menunggu
  Q4/Q7) — event yang belum ditangani dicatat `DIABAIKAN`.

---

## 6. Status HTTP yang Benar

| Situasi | HTTP | `code` body |
|---|---|---|
| Sukses | 200 | 200 |
| Validasi input | **200** (default; bisa 400 via saklar) | 100 |
| Belum autentikasi | 401 | 401 |
| Tidak berhak (RBAC) | **403** | 403 |
| Data/tenant lain | **404** | 404 |
| Konflik (idempotency, saldo) | **409** | 409 |
| Error server | 500 | 500 |

Lihat `BUGS-DITEMUKAN.md` B6–B8 tentang perbaikan status 403 dari `admin-be`.

---

## 7. Yang Belum & Menunggu

- 🔴 **Q1/Q2**: public key RS256 + format klaim JWT (admin & mobile). Decoder
  saat ini **fail-closed** bila key kosong — semua request → 401.
- ✅ **CORS** — sudah diperbaiki (B11): origin eksplisit dari
  `kantin.cors.allowed-origins` (tanpa wildcard saat kredensial aktif). Set
  daftar origin production sebelum rilis.
- ✅ **Rate limit Redis** (B27, SECURITY.md §7) — fixed-window atomik (Lua),
  per kategori: `auth` 20/menit, `sensitif` (kasir/saldo/stok/katalog) 60/menit,
  `umum` 600/menit. **Fail-open** default (`kantin.rate-limit.fail-closed=false`):
  Redis mati ⇒ request tetap dilayani agar kantin tidak lumpuh. Balas **429** +
  `Retry-After` + `X-RateLimit-Limit`.
- ✅ **Cabut token (blacklist Redis)** (B28) — `POST /api/auth/cabut` (admin/TU).
  Token disimpan sebagai SHA-256 (bukan mentah) dengan TTL = sisa umur token.
  Filter menolak token tercabut **setelah** signature valid. **Fail-open**: Redis
  mati ⇒ token dianggap belum dicabut (gangguan infra tidak melumpuhkan kantin).
- ✅ **Webhook masuk diamankan (B34)** — `/api/webhook/{sumber}` wajib HMAC-SHA256
  sah + anti-replay (timestamp) + allowlist IP opsional; rahasia dari
  `KANTIN_WEBHOOK_SECRET` (kosong ⇒ 503, fail-closed). Idempotency per event id
  (tabel `webhook_event`, V12). **Isi `KANTIN_WEBHOOK_SECRET` sebelum endpoint
  webhook dipakai di produksi.**
- ✅ **Endpoint internal (mesin-ke-mesin, #29)** — `/api/internal/**` (mis. admin-be
  cek UID Kartu Tamu untuk anti-tabrakan) diamankan `InternalSignatureFilter`
  dengan **rahasia TERPISAH** `KANTIN_INTERNAL_SECRET` (kosong/disabled ⇒ 503,
  fail-closed) + anti-replay + allowlist IP opsional. Lihat
  `docs/integrasi-anti-tabrakan-uid.md`.
- ⚠️ **Audit top-up/void/barang-masuk/opname** sudah menulis `audit_log` (B18).
  Aksi lain (ubah harga jual, blokir item, ubah limit) menunggu service terkait
  dibuat — pastikan tiap service baru memanggil `AuditLogger.catat`.

---

## 8. Cara Menguji Lokal

Lihat `ENVIRONMENT.md` untuk generate keypair & token uji. Ringkas:

```bash
# 1. Generate keypair
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out priv.pem
openssl rsa -pubout -in priv.pem -out pub.pem
# 2. Public key base64 (X.509 DER) → JWT_ADMIN_PUBLIC_KEY
openssl rsa -pubin -in pub.pem -outform DER | base64 -w0
# 3. Jalankan dengan env, lalu uji
curl -H "Authorization: Bearer <token>" http://localhost:8082/api/auth/me
```
