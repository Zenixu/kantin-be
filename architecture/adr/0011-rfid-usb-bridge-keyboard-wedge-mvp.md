# ADR-0011 — RFID USB bridge: keyboard-wedge untuk MVP, WebHID/WebSerial menuju produksi

- **Status:** Diterima (2026-10-07)
- **Tanggal:** 2026-10-07
- **Pengusul:** Tim kantin-be
- **Terkait:** PRD §6.1, §11.8, §13 poin 5, §14; ADR-0005, ADR-0008; Q10 di OPEN-QUESTIONS; issue #26

---

## Konteks

Q10 (🔴) menanyakan bagaimana browser kasir (`kantin-fe`) membaca **reader RFID USB**.
Browser **tidak bisa** membuka perangkat USB/serial sembarang tanpa API khusus atau
perantara. Opsi: **WebHID**, **WebSerial**, atau **agent lokal** (Node/Electron).

Batasan:

- PRD §13 poin 5 hanya menyebut "cek dengan tim RFID"; **spesifikasi reader belum final**
  (Q17 → asumsi demo di ADR-0008: reader = Kiosk Presensi, mode **keyboard-wedge/HID**).
- Tap harus terasa ≤ 1 dtk (PRD §11.8).
- MVP = pilot 1 sekolah (PRD §14); mode offline kasir & kasir HP NFC ada di **fase berikutnya**.

## Keputusan

**Berjenjang sesuai fase, agar modul tap tidak tertahan spesifikasi perangkat:**

1. **MVP/demo — keyboard-wedge (HID).** Reader mengirim UID sebagai keystroke; FE menangkap
   pada field terkunci. Ini **selaras** dengan asumsi reader = Kiosk Presensi (ADR-0008) dan
   **tidak butuh** komponen tambahan. Sudah cukup membuktikan alur tap end-to-end.
2. **Target produksi — WebHID/WebSerial bila reader mendukung** (tanpa instalasi tambahan),
   **fallback agent lokal** (service kecil Node/Electron) untuk reader yang tidak mengekspos
   HID/serial standar. Pemilihan akhir menunggu konfirmasi Q17.
3. **Abstraksi di kode:** UID divalidasi backend dengan `@UidKartuValid` (bentuk & panjang),
   sehingga **transport** reader (keyboard-wedge / WebHID / agent) tidak mengubah kontrak
   backend — `TapRequest.rfidUid` tetap sama.

## Alasan

- Keyboard-wedge = jalur **tercepat untuk demo** dan konsisten dengan reader yang diasumsikan
  sama dengan Kiosk (ADR-0008). Menahan modul tap hanya karena spike perangkat akan
  memperlambat MVP tanpa manfaat.
- WebHID/WebSerial = paling ringkas bila didukung; agent lokal = **fallback universal** —
  urutan ini sudah dinyatakan di ADR-0005.
- Karena backend hanya melihat UID final, keputusan transport **tidak memblokir** pekerjaan
  backend dan **murah diubah**.

## Alternatif yang Dipertimbangkan

| Alternatif | Kenapa tidak dipilih |
|---|---|
| Langsung WebHID/WebSerial untuk MVP | Bergantung dukungan perangkat yang belum diketahui (Q17 belum final) |
| Agent lokal sejak MVP | Menambah komponen wajib di tiap mesin kasir (instalasi & pemeliharaan) untuk pilot kecil |
| Kasir HP Android + NFC | **Di luar lingkup MVP** (PRD §14 — fase berikutnya) |
| Tunggu spesifikasi reader sebelum memutuskan | Menahan modul tap; melanggar prinsip "kerjakan yang tidak terblokir" (OPEN-QUESTIONS §D) |

## Konsekuensi

**Positif:** modul tap bisa diuji sekarang; keputusan transport terpisah dari kontrak backend.

**Negatif / risiko:** keyboard-wedge **rapuh** (fokus field, tanpa ack, sulit jamin anti-duplikat
& 1 dtk) → **bukan** pilihan produksi; **wajib** ditinjau ulang setelah konfirmasi Q17 dan
sebelum rilis produksi.

## Tindak Lanjut

- [ ] Konfirmasi Q17 (spesifikasi reader + dukungan HID/serial) ke tim RFID
- [ ] Bila reader ≠ keyboard-wedge: aktifkan WebHID/WebSerial atau agent lokal; perbarui ADR-0005
- [ ] Uji: tap → UID sampai ke backend & beep ≤ 1 dtk; uji anti-duplikat
- [ ] Tinjau ulang sebelum rilis produksi
