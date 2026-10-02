# ADR-0004 — Pola Lookup Kartu RFID

- **Status:** Diusulkan
- **Tanggal:** 2026-10-02
- **Pengusul:** BE-1 / BE-2
- **Terkait:** PRD §4.3, §6.1, §11.4, §11.11, INTEGRATIONS §4, Q7

## Konteks

Saat tap, kantin-be harus memetakan UID kartu → siswa (atau Kartu Tamu) dengan latency total ≤1 dtk (p95). Data kartu siswa ada di `admin-be` (`siswa.rfid_uid`), saldo & Kartu Tamu ada di kantin-be. Ditemukan bahwa `SiswaRepository.findByRfidUid` **tidak** memfilter sekolah & status aktif, dan **tidak** tahu soal Kartu Tamu.

## Keputusan

1. Lookup kartu siswa dilakukan lewat **API internal admin-be** (bukan akses DB langsung).
2. kantin-be **menambah sendiri**: validasi tenant (sekolah cocok → jika beda **404**), status siswa aktif.
3. **Anti-tabrakan UID**: kantin-be menolak UID yang sudah terdaftar sebagai Kartu Tamu (dan sebaliknya) saat registrasi.
4. **Caching:** kartu/siswa boleh di-cache **untuk performa**, tetapi:
   - cache **WAJIB di-invalidate** oleh webhook perubahan kartu/status siswa dari admin-be,
   - **status blokir kartu TIDAK boleh di-cache** — diperiksa di server setiap tap (§11.11).
5. Perubahan di admin-be yang memengaruhi kartu dikirim **saat itu juga** (webhook + retry).

## Alasan

- Menjaga batas modul (tidak sharing DB).
- Mengatasi keterbatasan `findByRfidUid` tanpa menyalahgunakan repo admin-be.
- Cache (tanpa blokir) dibutuhkan untuk capai p95<1dtk, asalkan konsisten via invalidasi.

## Alternatif yang Dipertimbangkan

| Alternatif | Kenapa tidak dipilih |
|---|---|
| Akses langsung DB `siswa` | Melanggar batas modul, rapuh terhadap perubahan admin-be |
| Cache status blokir dengan TTL | Bertentangan dengan "blokir instan" (§11.11) — kartu blokir tetap bisa dipakai |
| Tanpa cache, query tiap tap | Berisiko gagal p95<1dtk pada jam sibuk |

## Konsekuensi

**Positif:** batas modul terjaga, blokir benar-benar instan, performa terkendali.

**Negatif / risiko:** bergantung webhook invalidasi admin-be (harus andal + retry); kompleksitas cache.

## Tindak Lanjut

- [ ] Konfirmasi API internal lookup + SLA (Q7)
- [ ] Kontrak webhook invalidasi kartu dengan admin-be
- [ ] Uji: blokir lalu tap di detik yang sama → ditolak
