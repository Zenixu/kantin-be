# 📁 docs/ — Spesifikasi, Plan & SQL Manual

Folder ini menampung dokumen kerja **non-Flyway** (AGENTS.md §4):

| Subfolder / pola | Isi |
|---|---|
| `spesifikasi-*.md` | Spesifikasi fitur sebelum implementasi |
| `plan-*.md` | Rencana perbaikan / rencana kerja |
| `integrasi-*.md` | Catatan integrasi lintas repo (admin-be, mobile-be, FE) |
| `sql/` | Query SQL **manual** (ad-hoc, eksplorasi, data perbaikan) — **bukan** migrasi |

> ⚠️ **Semua perubahan skema tetap lewat Flyway** di
> `src/main/resources/db/migration/`. File di `docs/sql/` hanya untuk keperluan
> manual/eksplorasi dan **tidak** dijalankan otomatis oleh aplikasi
> (CONVENTIONS.md §8).

Dokumen arsitektur keputusan (ADR) ada di `architecture/adr/`, bukan di sini.
