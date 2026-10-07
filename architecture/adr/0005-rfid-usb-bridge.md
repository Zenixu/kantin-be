# ADR-0005 — RFID USB Bridge untuk Layar Kasir

 feat/spike-rfid-usb-bridge-adr
- **Status:** Diterima (bertahap) — demo = keyboard-wedge; produksi menunggu Q17
- **Tanggal:** 2026-10-02 (diperbarui 2026-10-08 — hasil spike #26)

- **Status:** Digantikan oleh [ADR-0011](./0011-rfid-usb-bridge-keyboard-wedge-mvp.md) (2026-10-07) — keputusan berjenjang: keyboard-wedge untuk MVP, WebHID/agent menuju produksi
- **Tanggal:** 2026-10-02
 main
- **Pengusul:** FE-1 / BE-1
- **Terkait:** PRD §6.1, §13 poin 5; Q10, Q17; ADR-0008; issue #26 & #22;
  `docs/spesifikasi-rfid-usb-bridge.md`

## Konteks

Layar kasir (`kantin-fe`) berjalan di browser (web). Reader RFID adalah perangkat
**USB**. **Browser tidak dapat membaca perangkat USB/serial sembarang secara
langsung** tanpa API khusus atau perantara. PRD §13 baru menyebut "cek dengan tim
RFID" — belum ada keputusan. Tap harus terasa ≤ 1 dtk (PRD §11.8).

Q10 meminta **spike** membandingkan **WebHID**, **WebSerial**, dan **agent lokal**
(dukungan browser, keamanan, kemudahan deploy, pengalaman kasir). Hasil spike
lengkap ada di [`docs/spesifikasi-rfid-usb-bridge.md`](../../docs/spesifikasi-rfid-usb-bridge.md);
ringkasannya menjadi dasar keputusan di bawah.

## Keputusan

**Strategi bertingkat (fallback berlapis):**

1. **Demo/MVP (sekarang):** **keyboard-wedge** — reader kasir = reader Kiosk
   Presensi mode HID (lihat ADR-0008). FE wajib memakai **buffer keystroke level
   dokumen + deteksi burst-terminator** agar tak kehilangan fokus. **Nol instalasi,
   universal.**
2. **Produksi (setelah Q17 terjawab):** pilih **satu**:
   - **WebHID / WebSerial** — bila reader final mengekspose HID/serial **dan**
     browser kasir boleh dikunci ke **Chromium desktop**. Tanpa instalasi, tapi
     Chromium-centric & memperluas attack surface.
   - **Agent lokal** (Node/Electron atau service native) yang membaca reader lalu
     mengekspos **WebSocket/HTTP `localhost`** — bila reader final **vendor-SDK
     only**, butuh reader apa pun, atau browser kasir harus bebas. Paling andal,
     tapi menambah komponen per mesin.
3. **Aturan pemilihan:** naik dari keyboard-wedge **hanya bila** reader final tak
   bisa mode wedge. Utamakan **WebHID** bila browser boleh dikunci Chromium;
   selain itu **agent lokal**.

**Persyaratan keamanan (semua opsi):** HTTPS/secure context, validasi UID di
backend, idempotency 1 tap = 1 transaksi; agent lokal **wajib** bind `127.0.0.1`
+ verifikasi `Origin` + token per mesin; **dilarang** menyimpan saldo/status kartu
di klien (AGENTS.md §10). Detail di spike §6.

## Alasan

- **Keyboard-wedge = baseline termurah & universal** untuk demo: reader Kiosk
  (asumsi Q17) sudah mode HID, tanpa API periferal baru, tanpa instalasi.
- **WebHID/WebSerial paling ringkas bila didukung**, tetapi **Chromium-centric**
  (Firefox & Safari tidak mendukung; WebKit menolak karena fingerprinting/keamanan)
  dan **memperluas attack surface** (riset menunjukkan periferal bisa direprogram).
- **Agent lokal = fallback universal & paling andal** (SDK vendor, reader apa pun,
  ack/beep), dengan harga: **instalasi + pemeliharaan per mesin**.
- Menyamakan reader dengan Kiosk Presensi mengurangi risiko & memudahkan suku
  cadang (Q17), selaras ADR-0008.
- Keputusan bertingkat ⇒ tim **tidak terblokir**: demo jalan sekarang, produksi
  menunggu fakta perangkat (Q17), bukan menunggu API eksotis.

## Alternatif yang Dipertimbangkan

| Alternatif | Kenapa belum dipilih |
|---|---|
| **Hanya WebHID/WebSerial untuk semua** | Mengunci ke Chromium (Firefox/Safari tak dukung) & memperluas attack surface; belum ada bukti reader final expose HID/serial |
| **Hanya agent lokal sejak awal** | Menambah instalasi/pemeliharaan per mesin untuk demo yang belum perlu |
| **Keyboard-wedge saja selamanya** | Rapuh (fokus, tanpa ack); tidak cukup bila reader final bukan HID |
| **Input keyboard-wedge tanpa pola buffer** | Scan tercampur input manual / fokus hilang → UID salah (lihat spike §6) |
| **Kasir pakai HP Android + NFC** | **Di luar lingkup MVP** (PRD §14, fase berikutnya) |

## Konsekuensi

**Positif:** kasir web tetap dipakai; demo bisa diuji **sekarang**; jalur produksi
jelas; tak ada komponen baru untuk demo.

**Negatif / risiko:** keyboard-wedge rapuh (fokus, tanpa ack) → **bukan** pilihan
produksi; bila kelak pakai WebHID (kunci browser) atau agent (komponen per mesin),
muncul biaya/beban baru yang harus dikelola. **Wajib ditinjau ulang setelah Q17.**

## Tindak Lanjut

- [x] **Spike (#26):** bandingkan WebHID/WebSerial/agent + rekomendasi
      (`docs/spesifikasi-rfid-usb-bridge.md`).
- [ ] Konfirmasi **Q17** ke tim RFID (spesifikasi reader + dukungan HID/serial).
- [ ] FE: implementasi **buffer keyboard-wedge** (pola spike §6) untuk demo.
- [ ] Bila reader berbeda: pilih WebHID **atau** agent lokal, sesuaikan
      `kantin.profil.reader-*`, dan catat keputusan final di ADR ini.
- [ ] Uji: tap → UID sampai ke backend & beep ≤ 1 dtk.
