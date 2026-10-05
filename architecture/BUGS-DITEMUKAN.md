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

## B21 — (bukan bug) Audit top-up & void SUDAH ada

- **Status:** ✅ Terverifikasi saat audit lanjutan — `SaldoTopUpService` (`TOPUP_TUNAI`, `KOREKSI_SALDO`) dan `VoidService` (`VOID_TRANSAKSI`) **sudah** memanggil `AuditLogger.catat`, hanya saat mutasi baru (`!isIdempotentReplay()`) agar replay tidak menggandakan jejak. Tidak ada perbaikan diperlukan.

## B22 — `CommonResponse.serverError(Exception)` / `databaseError(Exception)` membocorkan pesan internal

- **Gejala:** kedua method memakai `e.getMessage()` sebagai pesan respons ke klien — bisa memuat detail SQL/stack internal (info-leak).
- **Akar:** warisan pola `admin-be`. Keduanya **tidak dipakai** di kantin-be (`GlobalExceptionHandler` selalu memakai pesan generik), tetapi berisiko disalin tim.
- **Perbaikan:** ✅ `serverError(Exception)` kini mengembalikan pesan generik (`Terjadi kesalahan pada server`); `databaseError(Exception)` ditandai `@Deprecated(forRemoval=true)` + pesan generik. Detail asli tetap dicatat server-side via `ErrorLogger`.

## B23 — Redis dead config (belum dipakai) & health check menyesatkan

- **Gejala:** bean `RedisTemplate` ada & terkonfigurasi, tetapi **tidak ada pemakai** di kode bisnis. Health check Redis bawaan bisa melaporkan `DOWN` di dev yang sengaja tanpa Redis.
- **Akar:** Redis disiapkan untuk rate-limit/blacklist token yang belum diimplementasi (`SECURITY.md` §7).
- **Perbaikan:** ✅ `management.health.redis.enabled=${REDIS_HEALTH_ENABLED:false}` (dev tanpa Redis tidak DOWN palsu) + `spring.data.redis.timeout` + dokumentasi status di `SECURITY.md`. **Keputusan**: pertahankan (parity admin-be, siap dipakai) alih-alih hapus — tetapi **jangan** jadikan dependency wajib sampai benar-benar dipakai.

## B24 — (perbaikan) Endpoint tap stub + duplikasi konversi aktor id

- **Gejala:** (a) `KasirController.tap()` masih `throw InvalidOperationException` — `TapService` yang sudah lengkap & teruji **tidak terpanggil**, fitur inti kasir tak bisa dipakai; (b) konversi `userId` (String) → `Long` aktor id diduplikasi di tiap pemanggil (`TapService.idPetugas`), rawan inkonsistensi pesan error.
- **Akar:** controller sengaja di-stub menunggu Q1/Q2/Q7 (sudah usang karena service selesai).
- **Perbaikan:** ✅
  - `KasirController` kini memanggil service nyata: `POST tap`, `POST transaksi/{id}/void`, `POST sesi/buka`, `GET sesi/{id}/rekap`, `POST sesi/{id}/tutup`, `GET sesi/{id}`. Tenant dari `TenantContext` (bukan body).
  - Konversi aktor dipusatkan di `IdentitasKantin.aktorIdWajib()` (single source of truth); `TapService` memakainya.
  - DTO baru: `VoidRequest`, `BukaSesiRequest`.
  - Tes: `IdentitasKantinTest` (5), `KasirControllerTest` (3, MockMvc standalone).

## B25 — (perbaikan) Endpoint saldo & stok belum di-expose

- **Gejala:** `SaldoTopUpService`, `LedgerSaldoService`, `LedgerStokService` lengkap & teruji, tetapi **tidak ada controller** — fitur top-up, koreksi, barang masuk, opname, baca saldo/stok tak bisa dipakai via HTTP.
- **Akar:** controller belum dibuat; plus operasi tulis stok bertanda `@Transactional(MANDATORY)` sehingga controller tak boleh memanggil service langsung.
- **Perbaikan:** ✅
  - `SaldoController` (`/api/saldo`): topup, koreksi, lihat, rekonsiliasi.
  - `StokController` (`/api/stok`): barang-masuk, opname, lihat, menipis, rekonsiliasi.
  - Facade **transaksi-owning** `StokOperasiService` (membuka transaksi lalu mendelegasikan ke `LedgerStokService`) + `SaldoOperasiService` (baca-agregat).
  - DTO: `TopUpRequest`, `KoreksiSaldoRequest`, `BarangMasukRequest`, `OpnameRequest`, `SaldoResponse`, `MutasiSaldoItem`, `StokResponse`.
  - Dokumen baru `architecture/API-ENDPOINTS.md` (single source of truth endpoint).
  - Tes: `SaldoControllerTest` (3), `StokControllerTest` (3).

## B26 — (fitur) Modul Katalog Menu & Kategori

- **Gejala:** `MenuLookupPort` masih dilayani `MenuLookupFallback` yang selalu `null` → kasir tak bisa menjual menu apa pun; barang masuk/opname tak punya master item.
- **Perbaikan:** ✅
  - Migrasi `V6__CreateKatalog.sql`: tabel `kategori_menu` & `menu` (soft delete `is_active`, `stok_minimum`, FK `ON DELETE RESTRICT`, CHECK satuan `PCS/PORSI/BOTOL`).
  - Entity `KategoriMenu`, `Menu` (`Persistable`, id dari `IdGenerator`) + enum `SatuanMenu`.
  - Repository `KategoriMenuRepository`, `MenuRepository` (semua query **tenant-scoped**).
  - `KatalogService`: CRUD + soft delete; **ubah harga jual → audit `UBAH_HARGA_JUAL`** (PRD §7.1); kategori terpakai tak bisa dinonaktifkan.
  - `MenuLookupKatalogAdapter`: implementasi **nyata** `MenuLookupPort` (menggantikan fallback) → kasir kini dapat snapshot harga/kategori/status aktif.
  - `KatalogController` (`/api/katalog`): 8 endpoint kategori/menu.
  - Tes: `KatalogServiceIT` (7, Testcontainers), `KatalogControllerTest` (3).
- **Belajar (`/bug-hunter`):** dua bean `@Primary` untuk `MenuLookupPort` (adapter + fake test) menyebabkan `NoUniqueBeanDefinitionException`. Solusi: adapter **tanpa `@Primary`** (di produksi hanya satu bean); fake test tetap `@Primary` sehingga menang. `@ConditionalOnMissingBean` pada `@Service` component-scan **tidak andal** — hindari.

## B27 — (fitur) Rate limit Redis (SECURITY.md §7)

- **Gejala:** Redis terpasang tapi **belum dipakai** (dead config, B23) → tidak ada proteksi banjir request / brute force.
- **Perbaikan:** ✅
  - `RateLimitProperties` (`kantin.rate-limit.*`) — saklar, jendela, batas per kategori, mode gagal.
  - `RateLimiterRedis` — fixed-window **atomik via Lua** (INCR+EXPIRE), kunci `rl:{kategori}:{identitas}`.
  - `RateLimitFilter` — kategori dari path; **429** + `Retry-After` + `X-RateLimit-Limit`; identitas `sekolahId:userId` atau IP.
  - `CommonResponse.tooManyRequests` + `ResponseCode.TOO_MANY_REQUESTS`.
  - Terdaftar di chain **sebelum** JWT filter (tolak banjir sebelum verifikasi kripto mahal).
  - Tes: `RateLimiterRedisTest` (6), `RateLimitFilterTest` (6).
- **Keputusan desain — FAIL-OPEN default:** Redis mati ⇒ request tetap dilayani. Alasannya: melumpuhkan seluruh transaksi kantin karena infra cache rusak lebih merugikan daripada melewatkan sebagian limit. Bisa diubah ke fail-closed via `KANTIN_RATE_LIMIT_FAIL_CLOSED=true` bila kebijakan menuntut.
- **Belajar (`/bug-hunter`):** hitung pakai `INCR` + `EXPIRE` **harus atomik** (Lua) — bila dipisah, request paralel bisa lolos tanpa TTL → kunci bocor permanen.
- ⚠️ **DILARANG** memakai Redis untuk cache status blokir kartu (PRD §11.11) — tetap diperiksa di DB setiap tap.

## B28 — (fitur) Cabut token / blacklist Redis (SECURITY.md §7)

- **Gejala:** token yang dikelola platform tidak bisa dicabut sebelum kedaluwarsa — akun dinonaktifkan / token bocor tetap bisa dipakai sampai `exp`.
- **Perbaikan:** ✅
  - `TokenBlacklistPort` + `TokenBlacklistRedis` — kunci `bl:<sha256(token)>`, TTL = sisa umur token.
  - `SidikJari` — SHA-256 heksadesimal (token mentah **tidak** disimpan di Redis).
  - `JwtAuthTokenFilter` menolak token tercabut (401) **setelah** signature valid.
  - `AuthController.cabut` — `POST /api/auth/cabut` (`@PerluPeran` ADMIN_SEKOLAH/TU_SEKOLAH).
  - Tes: `SidikJariTest` (4), `TokenBlacklistRedisTest` (7), `AuthControllerTest` (4).
- **Konteks penting (ADR-0002):** kantin-be tidak punya login/logout sendiri; endpoint `/cabut` untuk mencabut token yang **dikelola platform** (akun nonaktif, token bocor, sesi dipaksa berakhir).
- **Fail-open:** Redis mati ⇒ token dianggap belum dicabut. Konsisten dengan kebijakan rate limit (B27).
- **Belajar (`/bug-hunter`):** (1) simpan **hash**, bukan token mentah — bila Redis bocor, penyerang tak langsung dapat token yang bisa dipakai. (2) Periksa blacklist **setelah** verifikasi signature, agar token palsu tidak menghabiskan query Redis.

## B29 — (audit) Dokumen arsitektur basi vs kondisi kode

- **Gejala:** audit menyeluruh menemukan **tidak ada bug kode baru**, tetapi **dokumen/ADR tertinggal** dari kenyataan — berisiko menyesatkan tim (manusia & agen AI).
- **Rincian yang diperbaiki:** ✅
  1. **ADR-0001, 0002, 0003 masih "Diusulkan"** padahal sudah diimplementasi & teruji → diubah jadi **"Diterima"** dengan bukti implementasi.
  2. **`OPEN-QUESTIONS.md` Q9 & Q12 masih 🟡** padahal sudah diputuskan (Java 25; locking pessimistic ADR-0003) → **🟢**.
  3. **`API-ENDPOINTS.md` §6.5** masih menulis "rate limit & blacklist token belum aktif" → **sudah aktif** (B27 & B28).
- **Hasil audit kode (bersih):** tidak ada TODO/FIXME; semua request DTO `@Valid`; semua 11 repository tenant-scoped; indeks DB lengkap untuk query panas; `ddl-auto=none` + `open-in-view=false`; tanpa secret hardcoded; `VoidService` memvalidasi tenant + status sesi + audit (diperiksa manual).
- **Catatan:** `SesiKasirService.tutupSesi` **tidak** mengubah status transaksi individual — "mengunci" ditegakkan lewat status **sesi** (`VoidService` menolak void bila sesi tidak terbuka). Javadoc diringkas agar tidak menyesatkan.
- **Pelajaran:** dokumentasi adalah bagian dari DoD. Setiap commit fitur **wajib** memperbarui ADR/OPEN-QUESTIONS/API-ENDPOINTS sekaligus — bukan menyusul.

## B30 — (fitur) Penjadwal auto-tutup sesi kasir (PRD §6.4)

- **Gejala:** `SesiKasirService.tutupOtomatis()` sudah ada & teruji, tetapi **tidak ada `@Scheduled`** yang memanggilnya — sesi kasir yang lupa ditutup bisa tertinggal `TERBUKA` selamanya (total bersih tak terkunci, Buku Kas tak lengkap).
- **Perbaikan:** ✅
  - `SesiKasirScheduler` (`@Scheduled` cron `0 59 23 * * *`, zona kantin) — aktif via `@EnableScheduling`.
  - `SesiKasirService.tutupOtomatisLintasTenant()` — proses **semua sekolah** secara terpisah (satu tenant gagal ≠ menggagalkan yang lain).
  - Repository: `daftarSekolahIdDenganStatus`, `findBySekolahIdAndStatusAndTanggalBefore`.
  - Bisa dimatikan: `kantin.scheduler.sesi.enabled=false` (test/dev).
  - Tes: `SesiKasirSchedulerTest` (2), skenario IT tertinggal (1) di `SesiKasirServiceIT`.
- **Desain kunci — hanya tutup sesi bertanggal `< hari ini`:** aman bila penjadwal tergeser/terlambat; sesi yang baru dibuka lewat tengah malam tidak ikut tertutup keliru. Idempoten.
- **Catatan multi-tenant:** kantin-be tidak menyimpan tabel `sekolah` (milik admin-be) — daftar tenant diturunkan dari `SELECT DISTINCT sekolah_id` pada `sesi_kasir`.

## B31 — (perbaikan FE) `stokBerjalan` di katalog & endpoint unggah berkas

- **Gejala (dilaporkan FE):**
  1. Tabel katalog menu perlu menampilkan **total stok berjalan** per item, tetapi `GET /api/katalog/menu` hanya mengirim `stokMinimum`. Memanggil `GET /api/stok/{menuId}` per item = **N+1 query** (lambat).
  2. Form buat menu butuh **unggah foto**, tetapi belum ada endpoint upload (`API-ENDPOINTS.md §6.4` lama).
- **Perbaikan:** ✅
  1. **`stokBerjalan` pada `MenuResponse`.** Field baru `int stokBerjalan` diisi dari `stok_cache` lewat **satu query batch** `StokCacheRepository.findBySekolahIdAndMenuIdIn(sekolahId, menuIds)` (tenant-scoped) — bukan per-item. `KatalogService.stokBerjalan(sekolahId, menuIds)` mengembalikan peta `menuId → stok`; controller memakai `getOrDefault(id, 0)`. `GET /api/katalog/menu/{id}` juga menyertakan `stokBerjalan` (satu menu).
  2. **Endpoint unggah** `POST /api/storage/upload` (`multipart/form-data`, field `file`, `folder` opsional) → mengembalikan `{ path, url, namaAsli, ukuran }`; FE menyimpan `url`/`path` ke `fotoUrl` pada `MenuRequest`. Plus `GET /api/storage/file?path=` untuk menampilkan (bila URL publik belum diset).
- **Keamanan (B19, PRD §11.4):** prefix objek `sekolah-<id>/` dibentuk **server-side** dari tenant token (bukan input klien); `folder` dibatasi allowlist (`menu`/`nota`/`kartu-tamu`/`lain`); berkas sekolah lain ⇒ **404** (`NotFoundEntity`), bukan 403. Error validasi ekstensi → **400**; error infra MinIO → pesan generik (detail hanya di log server).
- **Konfigurasi:** `minio.url-final` (opsional) menjadi basis URL publik; bila kosong, `url` = `path`.
- **Tes:** `StorageServiceTest` (9), `StorageControllerTest` (1), `KatalogControllerTest` (+3), `KatalogServiceIT` (+2).
- **Catatan:** tak ada migrasi Flyway baru — `stok_cache` sudah ada (V3) dan `menu.foto_url` sudah ada (V6).

## Ringkasan untuk tim

Saat menyalin kode dari `admin-be`, **selalu periksa**:
1. Spring Boot 4: nama starter berubah (`-aop` → `-aspectj`).
2. Jackson 3 (`tools.jackson`), bukan `com.fasterxml`.
3. Kembalikan **HTTP status** yang benar di `CommonResponse`, bukan selalu `ok()`.
4. Bersihkan `ThreadLocal` (tenant) di `finally`.
