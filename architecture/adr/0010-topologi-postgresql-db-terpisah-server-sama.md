# ADR-0010 — Topologi PostgreSQL: DB terpisah, server boleh sama (co-located) untuk MVP

- **Status:** Diterima (2026-10-07)
- **Tanggal:** 2026-10-07
- **Pengusul:** Tim kantin-be
- **Terkait:** PRD §4, ADR-0001, Q11 di OPEN-QUESTIONS; issue #27

---

## Konteks

Q11 (🟡) menanyakan: PostgreSQL kantin = **DB terpisah** (sudah disarankan), tetapi
apakah **server** fisik boleh **sama** dengan `admin-be`?

Batasan yang ada:

- ADR-0001 & PRD §4: kantin-be adalah **repo terpisah** dengan **kepemilikan data
  sendiri** (ledger saldo, transaksi, menu, stok, Kartu Tamu). Tidak boleh berbagi DB
  dengan admin-be.
- PRD §4.1: integrasi ke admin-be lewat **JWT + API internal**, bukan akses DB langsung.
- MVP = **pilot 1 sekolah** (PRD §14) → beban kecil; biaya & kesederhanaan ops penting.

## Keputusan

1. **Database: TERPISAH** — `kantin_db`, bukan skema di DB admin-be. Sudah diimplementasi
   (`spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/kantin_db}`).
2. **Server/cluster: boleh SAMA (co-located) untuk MVP**, asalkan:
   - **user/role DB terpisah** (`kantin_user`) tanpa grant lintas-DB ke DB admin-be,
   - **tidak ada** foreign key / join / `dblink` ke tabel admin-be,
   - **backup & restore terpisah** (dump `kantin_db` sendiri),
   - resource dibatasi (connection pool, memori) agar tak saling mengganggu.
3. **Produksi**: topologi ditentukan tim infra lewat env var `DB_URL` — **tanpa ubah kode**.
   Bila pilot dinilai berisiko, pindah ke instance/klaster PostgreSQL terpisah cukup dengan
   mengganti `DB_URL`.

## Alasan

- Isolasi data (kebutuhan keras) **dijamin oleh DB terpisah + user terpisah**, bukan oleh
  lokasi server. Co-locate tidak melanggar batas modul.
- Untuk pilot 1 sekolah, instance terpisah menambah biaya ops tanpa manfaat nyata.
- Config lewat `DB_URL` membuat keputusan ini **reversibel** dan murah diubah.

## Alternatif yang Dipertimbangkan

| Alternatif | Kenapa tidak dipilih |
|---|---|
| Satu DB, skema terpisah (`kantin.*`) di DB admin-be | Melanggar kepemilikan data & batas modul (PRD §4); migrasi Flyway tercampur |
| Instance PostgreSQL terpisah sejak MVP | Aman, tapi biaya & beban ops berlebih untuk pilot 1 sekolah |
| Akses DB admin-be langsung (tanpa API) | Dilarang PRD §4; rapuh terhadap perubahan admin-be |

## Konsekuensi

**Positif:** hemat biaya untuk pilot; isolasi tetap terjaga; mudah dipindah ke instance sendiri.

**Negatif / risiko:** bila co-located, satu gangguan server memengaruhi keduanya — dimitigasi
dengan backup terpisah + batas resource; **wajib ditinjau ulang sebelum skala multi-sekolah**.

## Tindak Lanjut

- [ ] Infra menyiapkan role `kantin_user` tanpa grant lintas-DB
- [ ] Dokumentasikan `DB_URL` produksi di `ENVIRONMENT.md`
- [ ] Tinjau ulang topologi saat naik dari pilot 1 sekolah
