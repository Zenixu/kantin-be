# ADR-0001 — Ikuti Stack SKOOLIA

- **Status:** Diterima (2026-10-02) — diimplementasi: Java 25, Spring Boot 4.0.2 webmvc, PostgreSQL, Flyway, Redis, JWT RS256, Docker multi-stage
- **Tanggal:** 2026-10-02
- **Pengusul:** Tim kantin-be
- **Terkait:** PRD §4, Q9, Q11

## Konteks

`kantin-be` adalah modul baru (repo terpisah) yang harus terintegrasi erat dengan SKOOLIA yang sudah mapan: verifikasi JWT RS256, lookup kartu RFID, posting Buku Kas, notifikasi, aktivasi modul. Tim SKOOLIA sudah punya `admin-be` (Java 25 + Spring Boot 4.0.2, 250 migrasi Flyway, 1000+ file) dan `admin-fe` (React 19 + TS + Vite + TanStack + shadcn/ui).

## Keputusan

`kantin-be` **mengikuti stack SKOOLIA**: **Java 25 + Spring Boot 4.0.2 (webmvc) + Maven + PostgreSQL + Flyway + Spring Data JPA/JDBC + Redis + jjwt RS256 + MinIO + Apache POI**, dengan kontainerisasi Docker multi-stage + Jenkins mengikuti pola `admin-be`.

## Alasan

- **Integrasi native:** verifikasi JWT, adaptor Buku Kas, lookup RFID bisa reuse pola & library yang sama → risiko integrasi minimum.
- **Tim familiar:** engineer yang mengerjakan SKOOLIA bisa langsung produktif.
- **Operasional seragam:** CI/CD, Docker, deployment sama → tidak menambah beban ops.
- Menulis ulang di stack lain = mengulang verifikasi RS256, adaptor, tenant scoping, dsb.

## Alternatif yang Dipertimbangkan

| Alternatif | Kenapa tidak dipilih |
|---|---|
| NestJS/Node + React | Duplikasi logic integrasi, risiko salah saat UAT, tim backend SKOOLIA tak bisa bantu |
| Hybrid (Java + service Go/Python) untuk endpoint tap | Menambah kompleksitas deploy & duplikasi auth; **ditunda** — hanya bila Java gagal SLO p95<1dtk (lihat Q13, kini diputuskan di **ADR-0007**) |
| Monolith di dalam admin-be | Melanggar keputusan PRD "repo terpisah" |

## Konsekuensi

**Positif:** integrasi cepat, kode konsisten, tim bisa saling bantu.

**Negatif / risiko:** Java/Spring lebih verbose untuk ledger → butuh disiplin + Testcontainers; lokal butuh JDK 25 (tim mungkin punya 21).

## Tindak Lanjut

- [ ] Semua anggota install JDK 25 (lihat `ONBOARDING.md`)
- [ ] `pom.xml` meniru `admin-be`
