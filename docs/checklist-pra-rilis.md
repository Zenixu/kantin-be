# ✅ Checklist Pra-Rilis Produksi — kantin-be

> Issue: **#173** · Referensi: PRD §8.2 & §11.10 · `architecture/SECURITY.md` §5.1 & §7
>
> **Tujuan:** memastikan **tidak ada rahasia / konfigurasi / env yang terlewat**
> saat rilis produksi — item-item ini konfigurasi, bukan kode, jadi mudah
> terlewat. Salin checklist ini ke PR rilis / tiket deploy, isi nilainya, lalu
> centang tiap item.

**Cara pakai:**

```bash
cp .env.example .env    # lalu isi nilai <GANTI_...>
# Jalankan daftar periksa di bawah, centang satu per satu.
```

Keterangan penanda:

- 🔴 **Fail-closed** — bila kosong, fitur mati / endpoint balas **503**. **Wajib** diisi.
- 🟡 **Keputusan** — perlu ditetapkan tim (tidak ada nilai default yang benar).
- 🟠 **Terblokir pihak lain** — menunggu kredensial/endpoint tim lain (lihat #164).

---

## A. Profil & flag dev — WAJIB nonaktif di produksi

- [ ] `SPRING_PROFILES_ACTIVE` **bukan** `local` / `demo` / `loadtest`
- [ ] `KANTIN_DEV_LOGIN_ENABLED=false` (shim login dev)
- [ ] `KANTIN_DEV_LOGIN_PRIVATE_KEY` **kosong**
- [ ] `KANTIN_DEMO_ENABLED=false` (jalur demo E2E)
- [ ] `KANTIN_LOADTEST_ENABLED=false`
- [ ] `KANTIN_OPENAPI_PUBLIC=false` (atau dibatasi di balik JWT/gateway)
- [ ] `KANTIN_METRICS_PUBLIC=false` (atau allowlist scraper Prometheus)
- [ ] Semua `KANTIN_INTEGRASI_*_ENABLED=false` kecuali endpoint nyatanya **sudah siap** (lihat #164)

## B. Rahasia & kunci 🔴

- [ ] `KANTIN_WEBHOOK_SECRET` terisi — acak ≥ 32 byte. Kosong ⇒ `/api/webhook/**` **503**
- [ ] `KANTIN_INTERNAL_SECRET` terisi — acak ≥ 32 byte, **BEDA** dari webhook. Kosong ⇒ `/api/internal/**` **503**
- [ ] `KANTIN_INTERNAL_ENABLED=true` bila admin-be memakai endpoint internal
- [ ] `JWT_ADMIN_PUBLIC_KEY` = kunci **RS256 produksi** admin-be 🟠 (#164)
- [ ] `JWT_MOBILE_PUBLIC_KEY` = kunci **RS256 produksi** mobile-be 🟠 (#164)
- [ ] Password **PostgreSQL / Redis / MinIO** terisi & kuat (bukan default dev)

## C. CORS & akses jaringan

- [ ] `KANTIN_CORS_ORIGINS` = origin FE produksi, **eksplisit**, **TANPA `*`** 🟡
      (saat kredensial aktif, wildcard tidak diizinkan)
- [ ] `KANTIN_WEBHOOK_ALLOWED_IPS` = IP sumber webhook (bila arsitektur memungkinkan) 🟡
- [ ] `KANTIN_INTERNAL_ALLOWED_IPS` = IP admin-be 🟡
- [ ] `KANTIN_WEBHOOK_TRUSTED_PROXIES` / `KANTIN_INTERNAL_TRUSTED_PROXIES` = proxy di depan app
      (agar `X-Forwarded-For` dibaca benar oleh allowlist)

## D. Rate limit — keputusan produksi 🟡

- [ ] Postur diputuskan & **dicatat di sini**:
      - **fail-open** (`KANTIN_RATE_LIMIT_FAIL_CLOSED=false`, default) — Redis mati ⇒
        request tetap dilayani (kantin tidak lumpuh), tapi proteksi rate-limit hilang saat itu.
      - **fail-closed** (`=true`) — Redis mati ⇒ semua request **429**; lebih aman,
        tapi kantin berhenti saat Redis down.
- [ ] `KANTIN_RATE_LIMIT_ENABLED=true`
- [ ] Ambang ditinjau: `KANTIN_RATE_LIMIT_AUTH` (20/mnt), `KANTIN_RATE_LIMIT_SENSITIVE` (60/mnt),
      `KANTIN_RATE_LIMIT_DEFAULT` (600/mnt), `KANTIN_RATE_LIMIT_WINDOW` (60 dtk)
- [ ] `KANTIN_RATE_LIMIT_TRUSTED_PROXIES` diisi bila ada reverse proxy

> **Rekomendasi (perlu sign-off tim):** pertahankan **fail-open** untuk ketersediaan
> kasir (outage lebih merugikan), **kecuali** bila kebijakan keamanan sekolah
> mensyaratkan fail-closed. Apa pun pilihannya, catat alasannya di baris di atas.

## E. Database & migrasi

- [ ] Seluruh migrasi Flyway **V1 … V22** diterapkan (22 migrasi)
- [ ] **Backup** database sebelum deploy
- [ ] Ukuran pool Hikari sesuai kapasitas host (lihat `docs/uji-beban-tap.md`)

## F. Observability

- [ ] `/actuator/health` hijau
- [ ] Metrik Prometheus terekspos & ter-scrape (`docs/OBSERVABILITY.md`)
- [ ] Log **tidak** memuat rahasia (token, secret, PII)

## G. Verifikasi pasca-deploy

- [ ] Login admin/TU berhasil
- [ ] Tap kartu kasir berhasil (SLO p95 ≤ 1 dtk — `docs/uji-beban-tap.md`)
- [ ] Webhook top-up online terverifikasi **E2E** dengan secret produksi 🟠 (#164)
- [ ] Endpoint internal (admin-be cek UID Kartu Tamu) berhasil dengan signature sah

## H. Sign-off

- [ ] Tanggal rilis: `____`
- [ ] Penanggung jawab: `____`
- [ ] Catatan / pengecualian: `____`

---

> **Status item terblokir (🟠)** menunggu #164 (kunci RS256 produksi & endpoint
> tim lain). Checklist ini **dapat dipakai sekarang** untuk item non-blokir;
> lengkapi item 🟠 saat kredensial tersedia.
