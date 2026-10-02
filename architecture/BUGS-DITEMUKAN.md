# 🐞 BUGS-DITEMUKAN.md — Catatan Bug & Perbaikan

> Daftar bug nyata yang ditemukan saat membangun `kantin-be`, terutama dari
> mewarisi pola `admin-be`. Setiap entri: gejala → akar masalah → perbaikan.
>
> **Tujuan:** mencegah tim mengulang bug yang sama (terutama saat copy-paste
> dari `admin-be`).

Status: ✅ diperbaiki · ⚠️ perilaku disengaja (didokumentasikan) · 🔲 menunggu

---

## B1 — `spring-boot-starter-aop` tidak ada di Spring Boot 4

- **Gejala:** `mvn compile` gagal: `'dependencies.dependency.version' for org.springframework.boot:spring-boot-starter-aop:jar is missing`.
- **Akar:** Spring Boot 4 **menghapus** artifact `spring-boot-starter-aop` dari BOM; diganti **`spring-boot-starter-aspectj`**.
- **Perbaikan:** ✅ pakai `spring-boot-starter-aspectj` (`pom.xml`).
- **Catatan:** perubahan breaking tak terdokumentasi jelas — hati-hati saat menyalin dependency dari proyek lama.

## B2 — `@ConfigurationProperties` prefix + nama field salah

- **Gejala:** `cannot find symbol: method getCookieName()`.
- **Akar:** field bernama `name` + `@ConfigurationProperties(prefix="jwt.cookie")` → getter jadi `getName()`, bukan `getCookieName()`.
- **Perbaikan:** ✅ rename field ke `cookieName`, properti jadi `jwt.cookie.name` (`JwtConfigValues.java`).

## B3 — PostgreSQL 18 restart-loop di Docker

- **Gejala:** container `kantin-postgres` restart terus; log: *"there appears to be PostgreSQL data in /var/lib/postgresql/data (unused mount/volume)"*.
- **Akar:** PostgreSQL 18 mengubah tata letak data. Mount di `/var/lib/postgresql` (tanpa `/data`), bukan cara lama.
- **Perbaikan:** ✅ ubah volume di `docker-compose.yml` → `kantin_pgdata:/var/lib/postgresql`.
- **Catatan:** menghapus volume lama wajib (`docker compose down -v`) agar tidak bentrok.

## B4 — Spring Boot membuat user default ("generated security password")

- **Gejala:** log WARN `Using generated security password: <uuid>`.
- **Akar:** `UserDetailsServiceAutoConfiguration` aktif karena tak ada `UserDetailsService` — padahal `kantin-be` tak punya login sendiri (ADR-0002).
- **Perbaikan:** ✅ kecualikan autoconfig di `application.properties`.

## B5 — Jackson 3 di Spring Boot 4 (bukan Jackson 2)

- **Gejala:** (potensial) `NoClassDefFoundError: com/fasterxml/jackson/databind/ObjectMapper` saat menyalin kode.
- **Akar:** Spring Boot 4 memakai **Jackson 3** (`tools.jackson.*`). Import `com.fasterxml.jackson.databind.*` **tidak berlaku** untuk `ObjectMapper`/`JsonMapper`.
- **Perbaikan:** ✅ gunakan `tools.jackson.databind.json.JsonMapper` di filter & entry point.
- **Catatan:** anotasi (`com.fasterxml.jackson.annotation.*`) masih di Jackson 2 *annotations*; jangan tertukar.

## B6 — `CommonResponse.forbidden()` membalas HTTP 200

- **Gejala:** RBAC gagal → body `{"code":403,...}` tapi status HTTP **200**. FE sulit membedakan sukses/gagal.
- **Akar:** warisan `admin-be` — `forbidden()` memakai `ResponseEntity.ok(...)`.
- **Perbaikan:** ✅ ubah ke `ResponseEntity.status(HttpStatus.FORBIDDEN)` + set `code=403`.

## B7 — Validasi dijawab HTTP 200 + `code=100`

- **Gejala:** input tidak valid → HTTP 200, `code=100`, field `validation`.
- **Akar:** kontrak FE SKOOLIA lama (warisan `admin-be`).
- **Keputusan:** ⚠️ **sengaja dipertahankan** (kompatibel FE). Tersedia saklar `kantin.validation.http-400=true` untuk memakai HTTP 400. Lihat `ValidasiContractConfig`.

## B8 — Endpoint tak dikenal membalas 401, bukan 404

- **Gejala:** `GET /api/tidak-ada` tanpa token → **401** (bukan 404).
- **Akar:** filter security menolak sebelum routing; request tanpa token ke path apa pun → 401. Setelah token valid, path tak dikenal → 404.
- **Keputusan:** ⚠️ **diterima** — ini praktik aman (tidak membocorkan endpoint mana yang ada). 404 tetap muncul untuk path tak dikenal **dengan token valid**.

## B9 — Filter JWT berstate (race condition)

- **Gejala:** (potensial) token salah issuer terverifikasi saat request konkuren.
- **Akar:** draft awal menyimpan hasil verifikasi di field instance filter (di-share antar thread).
- **Perbaikan:** ✅ hasil verifikasi dikembalikan sebagai `record HasilVerifikasi` lokal — tanpa state bersama.

## B10 — TenantContext bocor antar request

- **Gejala:** (potensial) request tanpa token bisa memakai sekolah dari request sebelumnya.
- **Akar:** `ThreadLocal` (dipakai Tomcat) tidak dibersihkan.
- **Perbaikan:** ✅ `TenantContext.clear()` di blok `finally` filter.

---

## 🔍 Audit Keamanan Lanjutan (Fase 3+) — B11–B18

Ditemukan pada audit menyeluruh (skill *backend-security-coder* + *bug-hunter*)
terhadap kriteria **Security & Controller / Service & Repository / Database &
Integration**. Semua sudah ditindaklanjuti kecuali yang ditandai menunggu.

## B11 — CORS `*` + `allowCredentials(true)` (celah lintas-origin)

- **Gejala:** kombinasi origin wildcard **dan** kredensial aktif.
- **Akar:** `WebSecurityConfig` memakai `setAllowedOriginPatterns(List.of("*"))` **bersamaan dengan** `setAllowCredentials(true)`. Ini membuat **setiap situs** dapat mengirim request ber-kredensial (cookie `skoolia-cookies`) dan membaca respons — isolasi origin praktis hilang.
- **Perbaikan:** ✅ origin eksplisit dari properti `kantin.cors.allowed-origins` (default dev `http://localhost:5173,http://localhost:3000`), header di-allowlist, wildcard **dibuang** meski ada di config.
- **Catatan:** wajib diset eksplisit sebelum production (via env `KANTIN_CORS_ORIGINS`).

## B12 — Verifikasi `alg` dilakukan di *claims*, bukan *header* (proteksi palsu)

- **Gejala:** blok kode "menolak token ber-header alg lain" tidak pernah berjalan.
- **Akar:** `KantinJwtDecoder.verifikasi` memeriksa `claims.get("alg")`. `alg` adalah **header** JWT, bukan klaim — nilainya selalu `null` → blok terlewati (komentar menyesatkan).
- **Perbaikan:** ✅ periksa `parsed.getHeader().getAlgorithm()` dan tolak bila ≠ `RS256`.

## B13 — Issuer JWT tidak pernah diverifikasi (token lintas-issuer lolos)

- **Gejala:** token dari issuer lain (walau signature sah dengan key yang sama) diterima.
- **Akar:** `JwtProperties.verifyIssuer` default `false` dan `KantinJwtDecoder` **tidak pernah** memanggil `requireIssuer(...)`. Janji `SECURITY.md` ("verifikasi terhadap 2 issuer") tidak terwujud.
- **Perbaikan:** ✅ `verifyIssuer` default **`true`**; decoder memanggil `requireIssuer(...)` sesuai `SumberToken` (admin vs mobile). Bisa dimatikan sementara via `jwt.verify-issuer=false` bila token uji belum memuat issuer yang sesuai (Q1/Q2).

## B14 — Replay idempotency tap tidak memeriksa tenant (kebocoran lintas-sekolah)

- **Gejala:** idempotency key yang sama dari sekolah lain dapat mengembalikan detail transaksi sekolah pemilik key.
- **Akar:** `TapService.bangunReplay` memakai `findByIdempotencyKey` (tanpa filter `sekolah_id`) dan **tidak** membandingkan `trx.getSekolahId()` dengan sekolah pemanggil. Key dibuat klien → bisa bentrok/berulang.
- **Perbaikan:** ✅ guard tenant di `bangunReplay` → `NotFoundEntity` (HTTP 404, bukan bocor) bila transaksi bukan milik sekolah pemanggil.

## B15 — Koreksi saldo tanpa idempotency key (double-apply)

- **Gejala:** retry jaringan / double-submit koreksi bendahara menambah/mengurangi saldo **dua kali**.
- **Akar:** `SaldoTopUpService.koreksi` membangun `PerintahMutasiSaldo` **tanpa** `.idempotencyKey(...)` (bandingkan `topUpTunai` yang benar). Jalur penanganan balapan `DataIntegrityViolationException` di `LedgerSaldoService` hanya bekerja bila key ada → tanpa key, retry jadi error 500, bukan idempoten.
- **Perbaikan:** ✅ `koreksi` kini **wajib** `referensiId` (nomor berita acara) → idempotency key `KOREKSI-<referensiId>`; validasi menolak bila kosong.

## B16 — Audit koreksi mengisi `nilai_lama` dengan saldo *sesudah*

- **Gejala:** audit log koreksi kehilangan saldo sebelum mutasi.
- **Akar:** `SaldoTopUpService.koreksi` melempar `hasil.getSaldoSetelah()` ke slot `nilai_lama` (harusnya saldo sebelumnya).
- **Perbaikan:** ✅ hitung saldo sebelum (`saldoSetelah ∓ nominal` sesuai arah) → `nilai_lama = saldoSebelum`, `nilai_baru = saldoSetelah`; audit hanya untuk mutasi baru (bukan replay).

## B17 — Lock/read ledger tidak ter-scope tenant pada `WHERE`

- **Gejala:** baris `saldo_cache`/`stok_cache`/`sesi_kasir` sekolah lain ikut terkunci/serialisasi (blast radius bila cek pasca-lock terlewat).
- **Akar:** `SaldoCacheRepository.kunciUntukUpdate(subjekTipe, subjekId)`, `StokCacheRepository.kunciUntukUpdate(menuId)`, `SesiKasirRepository.kunciBerdasarkanTitikTanggal(...)` tidak menyertakan `sekolah_id` di `WHERE` (PK cache juga tidak memuat `sekolah_id`). Tenant baru dicek **setelah** lock di service.
- **Perbaikan:** ✅ `sekolah_id` kini **wajib** ikut di `WHERE` ketiga query kunci (`SaldoCacheRepository`, `StokCacheRepository`, `SesiKasirRepository.kunciUntukUpdate` & `kunciBerdasarkanTitikTanggal`); pemanggil di `LedgerSaldoService`/`LedgerStokService`/`SesiKasirService` diperbarui. Cek tenant pasca-lock **tetap** dipertahankan sebagai pertahanan berlapis. Diuji oleh `IsolasiTenantLockIT`.
- **Catatan:** query read (`findBySekolahIdAnd…`) sudah tenant-scoped sebelumnya.

## B18 — Audit logger baru placeholder; opname & barang masuk belum diaudit

- **Gejala:** audit §11.7 tidak tahan-restart & tidak bisa di-query (hanya `log.info`).
- **Akar:** `AuditLogger.catat` masih placeholder (tabel `audit_log` belum ada). Selain itu `LedgerStokService.masukBarang` & `sesuaikanOpname` **belum** memanggil audit sama sekali, padahal "barang masuk & pembalik" + "opname" **wajib** diaudit (`CONVENTIONS.md` §6).
- **Perbaikan:** ✅ dibuat migrasi `V5__CreateAuditLog.sql` (tabel `audit_log` append-only + trigger + indeks), model `AuditLog` (`@Immutable`, `Persistable`), repositori `AuditLogRepository`, dan `AuditLogger` kini **menulis baris DB nyata** (`Propagation.MANDATORY` → audit ikut transaksi aksi). `masukBarang` & `sesuaikanOpname` memanggil audit (`BARANG_MASUK` / `OPNAME_STOK` + alasan + `nilai_lama`→`nilai_baru`). Diuji oleh `AuditLoggerIT`.

## B19 — `S3Storage.viewFile`/`deleteFile` tanpa validasi nama objek (path-traversal / IDOR)

- **Gejala:** pemanggil bisa meminta objek di luar prefix tenant-nya (mis. menyisipkan `../`) atau membaca objek internal mana pun di bucket.
- **Akar:** `viewFile`/`deleteFile` langsung meneruskan `objectName` ke MinIO tanpa validasi; hanya `uploadFile` yang memvalidasi **ekstensi**.
- **Perbaikan:** ✅ ditambahkan `validasiObjectName(...)` (tolak absolut/`..`/`\`/NUL, allowlist karakter `[A-Za-z0-9._-]`) yang dipanggil di `uploadFile`, `viewFile`, dan `deleteFile`.
- **Catatan:** endpoint HTTP file belum dibuat; bila nanti dibuat, **wajib** juga memaksa prefix tenant (`sekolah-<id>/`) sebelum memanggil helper ini.

## B20 — Artefak generator ID tak-aman di `Constants` berisiko diwarisi

- **Gejala:** `Constants.idGenerator()` (epoch-millis + 3 digit acak) & `sortableIdGenerator()` berpotensi **tabrakan PK** pada trafik tinggi bila disalin dari `admin-be`.
- **Akar:** method statis lama tetap ada meski seluruh entitas sudah memakai bean `IdGenerator` (monoton + offset per-JVM).
- **Perbaikan:** ✅ `sortableIdGenerator()` ditandai `@Deprecated(forRemoval=true)`; dokumentasi `idGenerator()` diberi peringatan tegas "JANGAN dipakai"; semua pemakaian internal sudah memakai bean `IdGenerator`.

---

## Ringkasan untuk tim

Saat menyalin kode dari `admin-be`, **selalu periksa**:
1. Spring Boot 4: nama starter berubah (`-aop` → `-aspectj`).
2. Jackson 3 (`tools.jackson`), bukan `com.fasterxml`.
3. Kembalikan **HTTP status** yang benar di `CommonResponse`, bukan selalu `ok()`.
4. Bersihkan `ThreadLocal` (tenant) di `finally`.
