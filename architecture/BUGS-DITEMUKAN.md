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

## Ringkasan untuk tim

Saat menyalin kode dari `admin-be`, **selalu periksa**:
1. Spring Boot 4: nama starter berubah (`-aop` → `-aspectj`).
2. Jackson 3 (`tools.jackson`), bukan `com.fasterxml`.
3. Kembalikan **HTTP status** yang benar di `CommonResponse`, bukan selalu `ok()`.
4. Bersihkan `ThreadLocal` (tenant) di `finally`.
