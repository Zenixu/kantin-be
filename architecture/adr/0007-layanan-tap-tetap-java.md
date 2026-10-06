# ADR-0007 — Layanan Tap Tetap di Java (tanpa split ke service ringan)

- **Status:** Diusulkan
- **Tanggal:** 2026-10-06
- **Pengusul:** Tim kantin-be
- **Terkait:** PRD §6.1, §6.2, §11.2, §11.11, §12; ADR-0001, ADR-0003, ADR-0004; Q13 di OPEN-QUESTIONS; issue #28

---

## Konteks

Target performa keras dari PRD: **satu tap (validasi + debit) ≤ 1 detik (p95)** pada
jam istirahat, **termasuk** lookup kartu ke data SKOOLIA (PRD §11.8, §12). Alur tap
(`TapService`, PRD §6.1–6.2) bukan sekadar baca-saja — ia menjalankan **satu transaksi
DB** berlapis:

1. lookup kartu (port `KartuLookupPort`),
2. validasi 6 tahap (`TapValidator`),
3. potong stok `FOR UPDATE` (`LedgerStokService`),
4. catat transaksi + item (snapshot HPP),
5. debit saldo `FOR UPDATE` (`LedgerSaldoService`).

Semua langkah itu **wajib atomik** (PRD §11.2) dan menjaga invariant keras:
ledger **append-only** (ADR-0003), **saldo & stok tak pernah minus**, dan **status
blokir kartu diperiksa server tiap tap tanpa cache** (PRD §11.11).

Q13 (🟡) bertanya apakah endpoint tap perlu **dipisah ke service ringan** (mis.
Go/Node/Python) yang lebih berlatensi rendah daripada Java/Spring. ADR-0001 sudah
menyisipkan opsi "Hybrid (Java + service Go/Python) untuk endpoint tap" sebagai
**ditunda** — hanya bila Java gagal SLO.

## Keputusan

**Layanan tap tetap di dalam `kantin-be` (Java 25 + Spring Boot 4).** Tidak ada
service terpisah untuk tap di MVP. **Pemisahan hanya dilakukan bila** hasil uji
beban nyata menunjukkan Java **gagal** target **p95 ≤ 1 dtk** pada jam sibuk —
dan itu pun harus lewat ADR baru yang menggantikan ADR ini.

Sebagai ganti "microservice", optimasi tetap dilakukan **di dalam** Java lewat jalur
yang sudah direncanakan (lihat Tindak Lanjut): cache lookup kartu yang **aman**
(kecuali status blokir), index `rfid_uid`, koneksi DB yang di-pool, dan pengukuran
p95 nyata via uji beban.

## Alasan

- **P95 ≤ 1 dtk realistis di Java.** Targetnya 1 **detik**, bukan 10 ms. Sebagian
  besar anggaran waktu justru habis di **round-trip DB** (transaksi berlapis) dan
  **lookup kartu ke SKOOLIA** — bukan di bahasa/JVM. Memindah bahasa tak menghapus
  biaya I/O ini.
- **Integritas lebih penting daripada kecepatan ekstrem.** Debit saldo + potong stok
  wajib satu transaksi ACID dengan `FOR UPDATE` (ADR-0003). Ini paling aman
  dikerjakan oleh **satu service yang memegang transaksi DB** — bukan dipecah lintas
  proses yang menuntut 2-phase/saga dan justru menambah latensi + titik gagal.
- **Blokir instan (PRD §11.11)** mensyaratkan cek server tiap tap tanpa cache TTL.
  Ini menuntut akses data langsung — lebih sederhana di dalam service yang sama.
- **Konsistensi & SDM.** ADR-0001 mengunci stack SKOOLIA (Java/Spring). Menambah
  runtime kedua berarti duplikasi verifikasi JWT RS256, tenant scoping, adaptor Buku
  Kas, CI/CD, dan observability — beban ops nyata demi keuntungan yang belum terbukti.
- **YAGNI.** Tanpa bukti gagal SLO, memisahkan service = menebak masalah yang belum
  ada. Keputusan optimalisasi sebaiknya **berbasis data pengukuran**, bukan asumsi.

## Alternatif yang Dipertimbangkan

| Alternatif | Kenapa tidak dipilih (sekarang) |
|---|---|
| **Service ringan (Go/Node/Python) khusus endpoint tap** | Menambah kompleksitas deploy, duplikasi auth (RS256), tenant scoping & adaptor; memecah transaksi DB ACID lintas proses; keuntungan latensi belum terbukti (target 1 dtk, bukan ms). Ditunda → hanya bila Java gagal SLO (ADR baru). |
| **Native/Quarkus image di Java** | Optimasi startup/memori, **bukan** latensi per-request yang jadi masalah utama; tak menyentuh biaya I/O DB. Bisa ditinjau nanti bila perlu. |
| **Cache saldo/blokir agresif untuk mempercepat** | **Dilarang** untuk status blokir (PRD §11.11) & berisiko pada saldo (ADR-0003). Optimasi yang diizinkan: cache lookup kartu **tanpa** status blokir + index `rfid_uid`. |
| **Baca saldo dari cache Redis saja tiap tap** | Saldo harus otoritatif & konsisten saat debit; cache basi → risiko saldo minus. Cache hanya boleh untuk mempercepat baca, bukan menggantikan sumber kebenaran. |

## Konsekuensi

**Positif:**
- Satu service = satu transaksi ACID; invariant saldo/stok/blokir terjaga tanpa saga.
- Tak ada duplikasi auth/integrasi/ops → pengembangan & pemeliharaan lebih murah.
- Jalur optimasi jelas & terukur (cache aman, index, pooling) tanpa memecah sistem.

**Negatif / risiko:**
- Bila Java **memang** gagal p95 ≤ 1 dtk, keputusan ini harus ditinjau ulang (ADR baru).
- Spring/JVM lebih "berat" daripada service ringan — **tidak relevan** untuk target
  1 dtk, tetapi wajib dibuktikan dengan uji beban, bukan diasumsikan.

## Tindak Lanjut

- [ ] **Ukur dulu, baru putuskan:** uji beban tap (k6/JMeter) → catat **p95 nyata** pada
      jam sibuk tiruan; jadikan dasar keputusan lanjutan. *(belum ada skrip beban di repo)*
- [ ] Implementasi optimasi dalam Java: **index `rfid_uid`**, **cache lookup kartu**
      (kecuali status blokir — PRD §11.11), koneksi DB ter-pool.
- [ ] Pastikan lookup kartu ke SKOOLIA punya **timeout + fallback** agar satu
      dependensi lambat tak menjatuhkan seluruh anggaran 1 dtk (lihat Q7).
- [ ] Bila **p95 > 1 dtk** setelah optimasi: buka **ADR baru** (mis. "Service tap
      terpisah") yang menggantikan ADR ini, dengan data pengukuran sebagai lampiran.
- [ ] Update **Q13** di `OPEN-QUESTIONS.md` → 🟢 Terjawab (tautan ke ADR ini).
