# 📁 adr/ — Architecture Decision Records

> Setiap keputusan arsitektur besar ditulis sebagai satu ADR. Format: `NNNN-judul-singkat.md`. Jangan hapus ADR lama — bila berubah, buat ADR baru yang menggantikan.

**Template:** [`0000-template.md`](./0000-template.md)

## Indeks ADR

| No | Judul | Status | Tanggal |
|---|---|---|---|
| [0001](./0001-ikuti-stack-skoolia.md) | Ikuti stack SKOOLIA (Java 25 + Spring Boot 4 + React 19) | Diusulkan | 2026-10-02 |
| [0002](./0002-jwt-rs256-tanpa-login-sendiri.md) | JWT RS256, kantin-be tanpa login sendiri | Diusulkan | 2026-10-02 |
| [0003](./0003-ledger-append-only-locking.md) | Ledger append-only + locking pessimistic | Diusulkan | 2026-10-02 |
| [0004](./0004-pola-lookup-kartu.md) | Pola lookup kartu RFID | Diusulkan | 2026-10-02 |
| [0005](./0005-rfid-usb-bridge.md) | RFID USB bridge untuk kasir | **Diterima (bertahap)** — hasil spike #26 | 2026-10-02 (↑ 2026-10-08) |
| [0006](./0006-prosedur-darurat-offline.md) | Prosedur darurat saat kantin kehilangan koneksi | Diusulkan | 2026-10-06 |
| [0007](./0007-layanan-tap-tetap-java.md) | Layanan tap tetap di Java (tanpa split ke service ringan) | Diusulkan | 2026-10-06 |
| [0008](./0008-reader-rfid-samakan-kiosk-demo.md) | Reader RFID USB kasir = reader Kiosk Presensi (asumsi demo) | Diusulkan | 2026-10-07 |
| [0009](./0009-postur-regulasi-dana-titipan-closed-loop.md) | Postur regulasi: dana titipan closed-loop (bukan uang elektronik) | Diusulkan | 2026-10-07 |
| [0010](./0010-topologi-postgresql-db-terpisah-server-sama.md) | Topologi PostgreSQL: DB terpisah, server boleh sama untuk MVP | Diterima | 2026-10-07 |
| [0011](./0011-rfid-usb-bridge-keyboard-wedge-mvp.md) | RFID USB bridge: keyboard-wedge untuk MVP, WebHID/agent menuju produksi | Diterima | 2026-10-07 |

> Status: `Diusulkan` → `Diterima` → (`Ditolak` / `Digantikan oleh NNNN`).
