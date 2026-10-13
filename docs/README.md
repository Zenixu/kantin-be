# 📁 docs/ — Spesifikasi, Plan & SQL Manual

Folder ini menampung dokumen kerja **non-Flyway** (AGENTS.md §4):

| Subfolder / pola | Isi |
|---|---|
| `spesifikasi-*.md` | Spesifikasi fitur sebelum implementasi |
| `plan-*.md` | Rencana perbaikan / rencana kerja |
| `integrasi-*.md` | Catatan integrasi lintas repo (admin-be, mobile-be, FE) |
| `uji-beban-tap.md` | Cara menjalankan & ambang SLO uji beban tap (issue #145) |
| `demo-e2e-kasir.md` | Cara menjalankan alur kasir **end-to-end** di lokal tanpa data SKOOLIA (issue #163) |
| `checklist-pra-rilis.md` | **Checklist pra-rilis produksi** — daftar env/rahasia/flag yang wajib dicek sebelum rilis (issue #173) |
| `uat-dan-dod-rilis.md` | **UAT, DoD rilis & cakupan test (JaCoCo)** — skenario end-to-end, angka cakupan, checklist kesiapan rilis (issue #174) |
| `sql/` | Query SQL **manual** (ad-hoc, eksplorasi, data perbaikan) — **bukan** migrasi |

> Skrip & data uji beban (k6) ada di [`load/`](../load/) — lihat
> [`docs/uji-beban-tap.md`](uji-beban-tap.md).
> Data seed **demo E2E** (`load/seed/seed-demo.sql`) & panduannya ada di
> [`docs/demo-e2e-kasir.md`](demo-e2e-kasir.md).

> ⚠️ **Semua perubahan skema tetap lewat Flyway** di
> `src/main/resources/db/migration/`. File di `docs/sql/` hanya untuk keperluan
> manual/eksplorasi dan **tidak** dijalankan otomatis oleh aplikasi
> (CONVENTIONS.md §8).

Dokumen arsitektur keputusan (ADR) ada di `architecture/adr/`, bukan di sini.
