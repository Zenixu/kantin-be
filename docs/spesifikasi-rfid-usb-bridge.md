# 🔬 Spike — RFID USB Bridge untuk Layar Kasir (Q10 / issue #26)

- **Tanggal:** 2026-10-08
- **Pengusul:** Tim kantin-be
- **Terkait:** PRD §6.1, §13 poin 5; ADR-0005 (keputusan), ADR-0008 (asumsi reader demo);
  `OPEN-QUESTIONS.md` Q10 & Q17; issue #26 (spike) & #22 (profil reader).
- **Status:** Spike **selesai** (rekomendasi) → keputusan di **ADR-0005** (diperbarui).

> Dokumen ini **bukan** spesifikasi implementasi FE. Ia adalah **hasil spike** yang
> membandingkan tiga opsi jembatan reader RFID USB → browser, lalu merekomendasikan
> satu arah dengan alasan, risiko, dan rencana tindak lanjut.

---

## 1. Masalah

Layar kasir (`kantin-fe`) berjalan di **browser web**. Reader RFID kasir adalah
perangkat **USB**. **Browser tidak dapat membaca perangkat USB/serial sembarang
secara langsung** — ia harus lewat API khusus (WebHID/WebSerial/WebUSB) **atau**
perantara (agent lokal). Tanpa keputusan ini, tim FE tidak tahu cara membaca UID
kartu non-siswa / Kartu Tamu, dan SLO **tap ≤ 1 dtk (p95)** (PRD §11.8) sulit dijamin.

Konteks tambahan:
- **ADR-0008 (demo):** untuk sementara reader kasir **diasumsikan = reader Kiosk
  Presensi** (mode **keyboard-wedge/HID**), nilai konfigurabel
  (`kantin.profil.reader-*`). Asumsi ini **bukan** pilihan produksi — wajib ditinjau
  setelah Q17 (spesifikasi reader fisik) terjawab.
- **Q17 (🟢 asumsi demo):** spesifikasi reader belum dikonfirmasi tim RFID.
- UID yang sampai ke backend divalidasi `@UidKartuValid` pada `TapRequest.rfidUid`.

---

## 2. Kriteria Evaluasi

| # | Kriteria | Bobot | Alasan |
|---|---|---|---|
| K1 | **Dukungan browser** (Chrome/Edge/Firefox/Safari) | Tinggi | Kantin memakai browser apa pun; jangan kunci ke satu vendor |
| K2 | **Keamanan** (permukaan serangan, izin, sidik jari) | Tinggi | Kasir memegang dana titipan; API periferal memperluas attack surface |
| K3 | **Kemudahan deploy** (instalasi/pemeliharaan per mesin kasir) | Tinggi | Sekolah punya sedikit staf IT; komponen baru = beban ops |
| K4 | **Pengalaman kasir** (latensi, fokus, feedback/beep) | Tinggi | Target tap ≤ 1 dtk; petugas tak boleh kehilangan fokus |
| K5 | **Ketahanan** (reader tanpa HID/serial, kabel, driver) | Sedang | Reader murah sering tak expose HID/serial standar |
| K6 | **Kompleksitas integrasi** (kode FE/BE) | Sedang | Tim FE terbatas |

---

## 3. Perbandingan Opsi

### Opsi A — Keyboard-wedge (baseline, dipakai ADR-0008 untuk demo)

Reader mengirim UID **sebagai keystroke** ke field yang sedang fokus, diakhiri
terminator (Enter). Tidak butuh API khusus.

| Kriteria | Nilai |
|---|---|
| K1 Dukungan browser | ✅ **Universal** (semua browser) |
| K2 Keamanan | ✅ Tidak ada API periferal baru; UID masuk lewat input biasa |
| K3 Deploy | ✅ **Tanpa instalasi** (reader USB HID standar) |
| K4 Pengalaman | ⚠️ **Rapuh**: bergantung fokus field, burst cepat bisa tercampur input manual, tanpa ack perangkat |
| K5 Ketahanan | ❌ Reader yang tak expose HID keyboard **tidak jalan** |
| K6 Kompleksitas | ✅ Rendah (buffer keystroke + deteksi terminator) |

### Opsi B — WebHID / WebSerial API (langsung dari browser)

Browser membuka perangkat HID/serial dengan izin pengguna, JS membaca stream UID.

| Kriteria | Nilai |
|---|---|
| K1 Dukungan browser | ❌ **Chromium-centric**: Chrome/Edge desktop (WebHID ≥ 89, WebSerial ≥ 89). **Firefox & Safari TIDAK mendukung** WebHID/WebSerial (WebKit menolak karena fingerprinting/keamanan; Firefox baru menambah WebSerial di Nightly, belum rilis) |
| K2 Keamanan | ⚠️ **Memperluas attack surface**: riset (Peripheral Instinct, ACM Web 2025) menunjukkan WebHID/WebSerial bisa dipakai mereprogram firmware periferal → potensi escape sandbox. Butuh izin eksplisit + HTTPS (secure context) |
| K3 Deploy | ✅ Tanpa instalasi; tapi butuh **izin perangkat** tiap sesi/mesin + HTTPS |
| K4 Pengalaman | ✅ **Bagus**: baca stream terstruktur, bisa beep/ack; tak bergantung fokus |
| K5 Ketahanan | ⚠️ Hanya reader yang expose HID/serial; reader vendor-SDK **tidak jalan** |
| K6 Kompleksitas | ❌ Tinggi: parsing protokol perangkat, siklus koneksi, penanganan stream |

### Opsi C — Agent/bridge lokal (Node/Electron atau service native)

Aplikasi kecil di mesin kasir membaca reader (native SDK/USB/serial) lalu
mengekspos **WebSocket/HTTP localhost** yang disubscribe `kantin-fe`. Browser
tak menyentuh hardware.

| Kriteria | Nilai |
|---|---|
| K1 Dukungan browser | ✅ **Universal** (browser hanya buka `ws://localhost`) |
| K2 Keamanan | ⚠️ Komponen lokal = permukaan baru; **wajib** bind ke `127.0.0.1`, verifikasi Origin, token/kunci per mesin, tanpa akses jaringan luas |
| K3 Deploy | ❌ **Ada instalasi & pemeliharaan per mesin** (auto-start, update) |
| K4 Pengalaman | ✅ **Terbaik**: ack, retry, beep, kontrol penuh reader |
| K5 Ketahanan | ✅ **Paling andal**: bisa pakai SDK vendor, reader apa pun |
| K6 Kompleksitas | ❌ Tinggi: bangun + distribusi + update agent per OS |

---

## 4. Matriks Ringkas

| Kriteria | A. Keyboard-wedge | B. WebHID/WebSerial | C. Agent lokal |
|---|---|---|---|
| Dukungan browser | ✅ Universal | ❌ Chromium saja | ✅ Universal |
| Keamanan | ✅ Rendah risiko | ⚠️ Attack surface lebih luas | ⚠️ Komponen lokal |
| Deploy | ✅ Nol instalasi | ✅ Tanpa instalasi (+izin) | ❌ Instalasi/mesin |
| Pengalaman kasir | ⚠️ Rapuh (fokus) | ✅ Baik | ✅ Terbaik |
| Ketahanan reader | ❌ Terbatas HID | ⚠️ HID/serial saja | ✅ Apa pun |
| Kompleksitas | ✅ Rendah | ❌ Tinggi | ❌ Tinggi |

---

## 5. Rekomendasi

**Strategi bertingkat (fallback berlapis), selaras ADR-0005 & ADR-0008:**

1. **MVP/demo (sekarang):** **keyboard-wedge** (Opsi A) — reader Kiosk Presensi
   mode HID (ADR-0008). Cukup untuk membuktikan alur tap end-to-end; **nol
   instalasi**. FE wajib menerapkan pola **buffer keystroke + deteksi
   burst-terminator** di level dokumen agar tak kehilangan fokus & tak tercampur
   input manual (lihat §6).
2. **Produksi (setelah Q17 terjawab):** pilih **satu** dari:
   - **WebHID/WebSerial** bila reader final **mengekspose HID/serial** dan sekolah
     memakai **Chromium desktop** (mis. Chrome kiosk). Ini menghilangkan instalasi
     tapi mengunci browser → **butuh keputusan tim** apakah browser kasir boleh
     dikunci ke Chromium.
   - **Agent lokal** bila reader final **vendor-SDK only**, butuh reader apa pun,
     atau browser kasir harus bebas. Paling andal, tapi menambah komponen per mesin.
3. **Jembatan universal:** bila ingin **bebas browser & bebas reader sekaligus**,
   **agent lokal** adalah jawabannya — dengan syarat keamanan di §6.

**Rekomendasi tegas untuk tim:** bila tim RFID mengonfirmasi reader kasir =
reader Kiosk Presensi (HID keyboard-wedge, Q17 = ya), **pertahankan keyboard-wedge
+ buffer dokumen** (paling murah, universal) dan **tidak** menambah komponen.
Naikkan ke WebHID/agent **hanya bila** reader final tak bisa mode wedge.

---

## 6. Persyaratan Keamanan & Implementasi (untuk FE/tim RFID)

**Semua opsi — wajib:**
- HTTPS (secure context) di produksi; token akses tap tetap lewat JWT RS256.
- Validasi UID di backend (`@UidKartuValid`) — jangan percaya klien.
- Anti-duplikat: 1 tap = 1 transaksi via **idempotency key** (PRD §11.3), bukan
  asumsi "UID tak terkirim dua kali".

**Keyboard-wedge (Opsi A) — pola aman FE:**
- Listener **level dokumen** (bukan per-field) yang membuffer keystroke cepat
  (mis. gap < 50 ms antar karakter) dan mengenali terminator (Enter) sebagai
  "selesai scan" → membedakan scan dari ketikan manusia.
- Setelah terminator: kirim UID, lalu **reset buffer**; jangan biarkan fokus
  berpindah di tengah burst.
- Beri feedback **beep** lokal segera (≤ 150 ms) agar petugas tahu UID terbaca.

**WebHID/WebSerial (Opsi B) — bila dipilih:**
- Batasi ke **vendor/product ID reader** yang diizinkan (filter device).
- Minta izin sekali di awal shift; simpan preferensi perangkat (bukan per request).
- Tangani `disconnect`/`connect` (kabel lepas) dengan pesan jelas + retry.

**Agent lokal (Opsi C) — bila dipilih:**
- Bind **hanya** `127.0.0.1` (bukan `0.0.0.0`).
- Verifikasi header **`Origin`** (hanya origin `kantin-fe`) **dan** token/kunci
  per mesin; tolak request lain.
- Auto-start saat login; sediakan jalur **update** & **fallback** bila agent mati
  (mis. tampilkan "reader tidak aktif").
- Agent **tidak** boleh menyimpan saldo/status kartu (AGENTS.md §10).

---

## 7. Risiko & Mitigasi

| Risiko | Opsi | Mitigasi |
|---|---|---|
| Fokus hilang / UID tercampur input manual | A | Buffer keystroke level dokumen + deteksi burst |
| Reader final tak expose HID/serial | B | Siapkan agent lokal sebagai fallback (C) |
| Browser dikunci ke Chromium | B | Konfirmasi tim; sediakan agent bila tak boleh dikunci |
| Attack surface API periferal | B | Filter device, izin eksplisit, HTTPS |
| Komponen agent jadi titik gagal | C | Auto-start + health check + fallback wedge |
| Reader berbeda dari Kiosk | A/B/C | `kantin.profil.reader-*` konfigurabel (ADR-0008), ubah tanpa kode |

---

## 8. Tindak Lanjut

- [x] Bandingkan opsi & tulis rekomendasi (dokumen ini).
- [x] Perbarui **ADR-0005** dengan keputusan bertingkat + hasil spike.
- [ ] **Tim RFID (Q17):** konfirmasi spesifikasi reader fisik + dukungan HID/serial.
- [ ] **Tim FE:** implementasi buffer keyboard-wedge (pola §6) untuk demo.
- [ ] Bila reader final ≠ Kiosk: pilih WebHID **atau** agent lokal → sesuaikan
      `kantin.profil.reader-*` & catat di ADR-0005.
- [ ] Uji lapangan: tap → UID sampai backend + beep ≤ 1 dtk (p95).
