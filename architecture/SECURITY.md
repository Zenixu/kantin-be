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
| `POST /api/webhook/**` | signature (bukan token) | — |
| `GET /api/auth/me` | token | apa pun yang valid |
| `POST /api/kasir/tap` | token | PETUGAS/PENGELOLA/ADMIN |

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
- ⚠️ **CORS** masih longgar (`*`) untuk pengembangan. **Persempit sebelum production.**
- 🔲 Pertimbangkan **rate limit** login/tap di Redis (blacklist token) — parity admin-be belum diimplementasi.

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
