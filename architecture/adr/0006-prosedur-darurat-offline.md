# ADR-0006 — Prosedur Darurat Saat Kantin Kehilangan Koneksi

- **Status:** Diusulkan
- **Tanggal:** 2026-10-06
- **Pengusul:** Tim kantin-be
- **Terkait:** PRD §6.5, §11.11, §13 poin 2, §14; Q14 di OPEN-QUESTIONS

---

## Konteks

Kantin **100% cashless** (PRD §6): setiap transaksi memotong saldo kartu. Transaksi
adalah operasi database berlapis (`TapService`): lookup kartu → validasi 6 tahap →
potong stok (`FOR UPDATE`) → catat transaksi → debit saldo (`FOR UPDATE`), **semua
dalam satu transaksi DB** (PRD §11.2). Artinya: **tanpa backend + DB, tidak ada
transaksi** — dan karena tak ada uang tunai, kantin benar-benar berhenti berjualan.

Dua batasan keras yang membingkai keputusan ini:

1. **PRD §6.5 (sudah diputuskan):** saat offline tampilkan banner *"Offline —
   transaksi tidak tersedia"*, **tanpa antrean transaksi lokal**. Kantin berhenti
   sampai koneksi pulih.
2. **PRD §11.11 (sudah diputuskan):** status **blokir kartu diperiksa server setiap
   tap, tanpa cache**, agar blokir berlaku **instan** (mis. kartu hilang/disalahgunakan).
   Mode offline penuh akan melanggar jaminan ini.

PRD §13 poin 2 & §14 menempatkan **mode offline kasir** di **fase berikutnya**,
dan meminta **prosedur darurat** untuk sekarang. Q14 (🔴) meminta keputusan
produk/sekolah.

## Keputusan

_(Diusulkan — butuh persetujuan Produk/Sekolah.)_ Untuk MVP, prosedur darurat
**berlapis**, **tanpa** mengaktifkan mode offline penuh:

1. **Pencegahan (utama) — koneksi cadangan.** Kantin menyediakan **modem/hotspot
   cadangan** (mis. tethering HP petugas) + UPS kecil untuk router. Ini
   menghilangkan mayoritas insiden tanpa perubahan perangkat lunak.
2. **Deteksi & komunikasi.** kasir menampilkan banner offline (sudah di PRD §6.5)
   + tombol **"Lapor gangguan"** yang mencatat insiden (waktu, titik kasir,
   durasi) untuk memutuskan apakah mode offline perlu dimajukan.
3. **Rencana B operasional (manual, terjaga integritas):** bila koneksi tak kunjung
   pulih, kantin boleh jualan **terbatas** dengan **catat manual** (kertas/lembar
   darurat): waktu, UID/nomor kartu, item, total, tanda tangan petugas. **Saldo
   TIDAK dipotong saat itu.** Setelah koneksi pulih, petugas memasukkan transaksi
   darurat ke sistem **sebagai transaksi normal dengan waktu asli** (bila saldo
   cukup) atau ke **opname/koreksi** (bila tidak) — dengan alasan "mode darurat".
   Semua tercatat sebagai baris ledger baru (append-only, PRD §11.1).
4. **Eskalasi.** Bila insiden sering (>ambang tertentu), **mode offline penuh
   dimajukan** dari fase berikutnya — dengan desain yang sadar-konflik (lihat
   "Konsekuensi").

**Yang TIDAK dilakukan di MVP:** antrean transaksi lokal / sinkronisasi otomatis
(melanggar §6.5 & §11.11 tanpa desain khusus).

## Alasan

- **Integritas ledger lebih penting daripada ketersediaan jualan.** Debit saldo
  offline tanpa cek blokir berisiko kartu hilang tetap bisa dipakai — bertentangan
  dengan janji keamanan §11.11. Untuk sistem yang memegang **dana titipan siswa**,
  lebih baik berhenti sebentar daripada mencatat transaksi yang mungkin curang.
- **Koneksi cadangan = solusi 90%.** Insiden jaringan sekolah biasanya singkat;
  hotspot cadangan memulihkan operasi tanpa kompleksitas offline-sync.
- **Catat manual menjaga operasi tetap jalan** tanpa mengubah invariant sistem:
  transaksi darurat **baru** masuk ledger saat online, jadi tak ada saldo negatif
  atau kartu-blokir-terlewat.
- **Selaras PRD:** §6.5 (tanpa antrean lokal) & §14 (offline = fase berikutnya).

## Alternatif yang Dipertimbangkan

| Alternatif | Kenapa belum dipilih |
|---|---|
| **Mode offline penuh** (antrean lokal + sync) | Melanggar §6.5 & §11.11; butuh desain anti-konflik (blokir, saldo, double-spend) yang mahal; sudah dijadwalkan fase berikutnya |
| **Terima kartu tanpa cek saldo** (IOU offline) | Risiko saldo negatif & tak tertagih; rumit direkonsiliasi |
| **Kantin tutup total saat offline** | Paling aman, tapi kerugian penjualan nyata; opsi catat-manual lebih baik sebagai jaring |
| **Mode offline "baca-saja"** (cek saldo cache, tetap butuh online untuk commit) | Hampir sama dengan berhenti; cache saldo berisiko basi |

## Konsekuensi

**Positif:** operasi tetap berjalan lewat catat-manual; integritas saldo & blokir
terjaga; tak ada kompleksitas offline-sync di MVP.

**Negatif / risiko:**
- Catat-manual **membebani petugas** & berpotensi salah tulis → wajib form baku + tanda tangan.
- Bila saldo saat rekonsiliasi **tidak cukup** → transaksi darurat jadi **piutang/koreksi** (kebijakan sekolah).
- Bila nanti mode offline penuh dibuat, wajib desain ulang §11.11 (blokir tak bisa instan saat offline) → **ADR baru**.

## Tindak Lanjut

- [ ] **Produk/Sekolah** setujui prosedur (atau pilih alternatif lain) → ubah status ADR jadi *Diterima*.
- [ ] Sediakan **form lembar darurat** (template cetak) + kolom lapor gangguan di kasir.
- [ ] Tentukan **ambang eskalasi** (mis. >X insiden/bulan → majukan mode offline).
- [ ] Tentukan **kebijakan saldo tidak cukup** saat rekonsiliasi darurat (piutang vs koreksi).
- [ ] Bila mode offline penuh dikerjakan: tulis ADR baru + rancang ulang jaminan blokir §11.11.
