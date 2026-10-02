# ADR-0002 — JWT RS256, kantin-be Tanpa Login Sendiri

- **Status:** Diusulkan
- **Tanggal:** 2026-10-02
- **Pengusul:** BE-1
- **Terkait:** PRD §4.1, §11.10, Q1, Q2

## Konteks

kantin-be melayani dua jenis pengguna: **staf sekolah** (petugas/pengelola/TU/bendahara/admin/kepsek) dan **orang tua**. Keduanya sudah punya identitas di SKOOLIA (admin-be & mobile-be). Membangun sistem login terpisah akan menduplikasi otentikasi & memperbesar permukaan serangan.

## Keputusan

kantin-be **tidak memiliki sistem login sendiri**. kantin-be memverifikasi **JWT RS256** yang diterbitkan:
- `admin-be` → untuk staf (klaim sekolah & role),
- `mobile-be` → untuk orang tua.

Verifikasi memakai **public key** masing-masing penerbit via `jjwt 0.12.6`. **Fallback HS256 dilarang** di production.

## Alasan

- PRD §4.1 & §11.10 eksplisit.
- RS256 memungkinkan verifikasi cukup dengan public key tanpa berbagi secret.
- Hak akses tetap terpusat di role management SKOOLIA → RBAC konsisten lintas modul.

## Alternatif yang Dipertimbangkan

| Alternatif | Kenapa tidak dipilih |
|---|---|
| Session/login sendiri di kantin-be | Duplikasi identitas, sinkronisasi role rumit, permukaan serangan lebih besar |
| HS256 dengan secret bersama | PRD melarang; secret bocor = semua token bisa dipalsukan |
| OAuth2 introspection tiap request | Menambah latency, tak sesuai target tap p95<1dtk |

## Konsekuensi

**Positif:** identitas tunggal, RBAC terpusat, aman.

**Negatif / risiko:** bergantung pada ketersediaan public key kedua penerbit (blocking Q1/Q2); perlu mekanisme rotasi key.

## Tindak Lanjut

- [ ] Konfirmasi format klaim & lokasi public key (Q1, Q2)
- [ ] Implementasi `AdminJwtAuthTokenFilter` & `MobileJwtAuthTokenFilter`
- [ ] Uji: token invalid/expired/sekolah berbeda → 401/404
