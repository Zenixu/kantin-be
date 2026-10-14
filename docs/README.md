# 📁 docs/ — Spesifikasi, Plan & SQL Manual

Folder ini menampung dokumen kerja **non-Flyway** (AGENTS.md §4).

## Daftar dokumen

| Dokumen | Isi |
|---|---|
| [`spesifikasi-fase4-ledger.md`](spesifikasi-fase4-ledger.md) | Spesifikasi skema Fase 4 — Ledger (saldo & stok) · **DIIMPLEMENTASI** |
| [`spesifikasi-service-repository.md`](spesifikasi-service-repository.md) | Spesifikasi layer Service & Repository (Fase 4–5) · **DIIMPLEMENTASI** |
| [`spesifikasi-rfid-usb-bridge.md`](spesifikasi-rfid-usb-bridge.md) | Spike RFID USB bridge untuk layar kasir (Q10 / issue #26) |
| [`laporan-service-repository.md`](laporan-service-repository.md) | Laporan layer Service & Repository (Fase 4–5) — snapshot hasil pekerjaan |
| [`integrasi-adapter-rest.md`](integrasi-adapter-rest.md) | Adapter REST integrasi nyata pengganti `*Fallback` (issue #160) |
| [`integrasi-anti-tabrakan-uid.md`](integrasi-anti-tabrakan-uid.md) | Integrasi anti-tabrakan UID kartu tamu ↔ siswa (issue #29) |
| [`API-KARTU-TAMU.md`](API-KARTU-TAMU.md) | Kontrak endpoint kartu tamu (`/api/kartu-tamu`) |
| [`dev-jwt-dummy.md`](dev-jwt-dummy.md) | Harness JWT **DUMMY** untuk dev/test — ⚠️ jangan di staging/produksi |
| [`OBSERVABILITY.md`](OBSERVABILITY.md) | Metrik yang diekspos & cara memantau SLO (issue #146) |
| [`uji-beban-tap.md`](uji-beban-tap.md) | Cara menjalankan & ambang SLO uji beban tap (issue #145) |
| [`demo-e2e-kasir.md`](demo-e2e-kasir.md) | Cara menjalankan alur kasir **end-to-end** di lokal tanpa data SKOOLIA (issue #163) |
| [`plan-branch-protection.md`](plan-branch-protection.md) | Plan & status proteksi branch `main` — **AKTIF** (issue #171) |
| [`checklist-pra-rilis.md`](checklist-pra-rilis.md) | **Checklist pra-rilis produksi** — daftar env/rahasia/flag yang wajib dicek sebelum rilis (issue #173) |
| [`uat-dan-dod-rilis.md`](uat-dan-dod-rilis.md) | **UAT, DoD rilis & cakupan test (JaCoCo)** — skenario end-to-end, angka cakupan, checklist kesiapan rilis (issue #174) |
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
