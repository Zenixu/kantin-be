# ADR-0009 — Postur regulasi: dana titipan closed-loop (bukan uang elektronik)

- **Status:** Diusulkan (postur DEMO — wajib konfirmasi legal sebelum produksi)
- **Tanggal:** 2026-10-07
- **Pengusul:** Tim kantin-be
- **Terkait:** PRD §5.1, §9.5, §13 poin 7; Q15 di OPEN-QUESTIONS; issue #24

---

## Konteks

Q15 (🔴 **wajib konfirmasi legal sebelum rilis**) bertanya apakah dana yang
dihimpun kantin-be aman dari ketentuan **uang elektronik** menurut regulasi BI.
Saldo siswa adalah **dana titipan** (uang ortu dititipkan untuk pembelian di
kantin), bukan uang elektronik yang bisa dipakai/ditarik bebas.

Kantin-be belum rilis penuh. Perlu **postur default yang aman** agar desain
sistem (mis. tidak ada tarik tunai/transfer bebas) selaras dengan asumsi
closed-loop sampai legal menjawab.

## Keputusan

**Untuk demo, sistem diperlakukan sebagai DANA TITIPAN CLOSED-LOOP**, dengan
postur (dapat diubah lewat konfigurasi):

| Sifat | Nilai demo | Kunci konfigurasi |
|---|---|---|
| Model dana | `DANA_TITIPAN_CLOSED_LOOP` | `kantin.profil.model-dana` |
| Uang elektronik | `false` | `kantin.profil.uang-elektronik` |
| Tarik tunai | `false` (dilarang) | `kantin.profil.tarik-tunai` |
| Transfer bebas antar siswa/sekolah | `false` (dilarang) | `kantin.profil.transfer-bebas` |
| Perlu konfirmasi legal | `true` | `kantin.profil.perlu-konfirmasi-legal` |

Konsekuensi desain yang **sudah** berlaku di kode: saldo hanya bisa dipakai
untuk pembelian di kantin (`PENJUALAN`); pengembalian saldo siswa keluar
memakai jalur **refund terkontrol** (issue #38 / `RefundSaldoService`, PRD §9.3),
bukan penarikan tunai bebas. Postur ini disajikan lewat
`GET /api/konfigurasi/profil` untuk tim legal.

## Alasan

- Closed-loop = **ruang lingkup MVP** (PRD §14): saldo hanya untuk belanja
  kantin, tanpa fitur e-money penuh.
- Postur konservatif (tanpa tarik tunai/transfer bebas) **meminimalkan risiko
  regulasi** sampai legal memutuskan.
- Nilai konfigurabel ⇒ bila legal memutuskan lain, penyesuaian tanpa ubah kode.

## Alternatif yang Dipertimbangkan

| Alternatif | Kenapa belum dipilih |
|---|---|
| Menunggu legal sebelum merancang apa pun | Menahan modul saldo padahal closed-loop sudah cukup untuk demo |
| Mengizinkan tarik tunai/transfer sekarang | Justru **meningkatkan** risiko ketentuan uang elektronik |
| Menganggap sistem sebagai uang elektronik | Menambah beban kepatuhan tanpa dasar keputusan legal |

## Konsekuensi

**Positif:** desain aman & selaras closed-loop; asumsi terdokumentasi.

**Negatif / risiko:** bila legal menyimpulkan perlu lisensi/penyesuaian,
perubahan bisa menyentuh alur refund & limit. **Wajib ditinjau ulang.**

## Tindak Lanjut

- [ ] Konfirmasi Q15 ke tim legal (kepatuhan regulasi BI)
- [ ] Bila perlu: sesuaikan alur refund/limit & `kantin.profil.*`
- [ ] Simpan bukti kepatuhan (dokumen legal) sebelum rilis produksi
