# 📚 architecture/ — Pusat Dokumentasi Teknis `kantin-be`

> **Mulai dari sini.** Semua keputusan, konvensi, dan peta integrasi proyek ada di folder ini. Dibuat untuk **tim berkelompok** (manusia & agen AI) agar satu sumber kebenaran.

---

## 🧭 Baca dalam Urutan Ini

| # | Dokumen | Untuk apa |
|---|---|---|
| 1 | [**AGENTS.md**](./AGENTS.md) | **KONTRAK KERJA** — aturan emas, stack, DoD, larangan. Baca pertama. |
| 2 | [**ONBOARDING.md**](./ONBOARDING.md) | Anggota baru: alat, setup, checklist hari pertama. |
| 3 | [**ENVIRONMENT.md**](./ENVIRONMENT.md) | 🖥️ Toolchain & versi teruji, cara pasang JDK 25, fix shell fish, troubleshooting. |
| 4 | [**GLOSSARY.md**](./GLOSSARY.md) | Istilah domain (saldo, void, HPP, dsb). |
| 5 | [**MODULE-MAP.md**](./MODULE-MAP.md) | PRD → kode: peta modul + urutan fase pengerjaan. |
| 6 | [**INTEGRATIONS.md**](./INTEGRATIONS.md) | Cara nyambung ke SKOOLIA (JWT, Buku Kas, RFID, callback). |
| 7 | [**CONVENTIONS.md**](./CONVENTIONS.md) | Gaya kode, penamaan, response, ledger, testing, toolchain. |
| 8 | [**WORKFLOW.md**](./WORKFLOW.md) | Branching, commit, PR, review. |
| 9 | [**SECURITY.md**](./SECURITY.md) | 🔐 Cara kerja auth: JWT RS256, RBAC, tenant guard, status HTTP. |
| 10 | [**BUGS-DITEMUKAN.md**](./BUGS-DITEMUKAN.md) | 🐞 Bug nyata ditemukan + perbaikan (terutama warisan admin-be). |
| 11 | [**OPEN-QUESTIONS.md**](./OPEN-QUESTIONS.md) | Pertanyaan belum terjawab & yang **memblokir**. |
| 12 | [**adr/**](./adr/) | Architecture Decision Records — keputusan + alasannya. |
| 13 | [**diagrams/**](./diagrams/) | Diagram alur (mis. alur tap kasir). |

---

## 📌 Ringkasan Cepat Proyek

- **Apa:** backend modul kantin cashless SKOOLIA (100% non-tunai, tap kartu RFID).
- **Stack:** Java 25 · Spring Boot 4.0.2 (webmvc) · PostgreSQL · Flyway · JPA/JDBC · Redis · JWT RS256 · MinIO · Apache POI · Maven · Docker + Jenkins.
- **Prinsip inti:** ledger append-only · saldo/stok tak boleh minus · idempotency · tenant scoping (404) · RBAC backend · blokir kartu instan (tanpa cache) · tap ≤1 dtk.
- **Repo:** `git@github.com:Zenixu/kantin-be.git`
- **Dokumen produk:** `../2026-10-01-prd-kantin-skoolia.md` (teknis v4) & `../2026-10-01-prd-kantin-skoolia-non-teknis.md`.

---

## ⚠️ Sebelum Coding: Cek Blocking

Lihat [OPEN-QUESTIONS.md](./OPEN-QUESTIONS.md). Pertanyaan berstatus 🔴 **harus** terjawab sebelum mengerjakan modul terkait. Modul yang tidak terblokir (ledger, katalog, infra) bisa jalan lebih dulu — lihat [MODULE-MAP.md](./MODULE-MAP.md) §Konsekuensi.

---

## 🤝 Untuk Agen AI

Baca [AGENTS.md](./AGENTS.md) (§3 aturan emas, §10 daftar merah) + [CONVENTIONS.md](./CONVENTIONS.md) + [WORKFLOW.md](./WORKFLOW.md) §8 **sebelum menulis kode**. Jangan commit/push tanpa diminta manusia.

---

*Terakhir diperbarui: 2026-10-02 · Versi 1.0*
