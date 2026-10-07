# ADR-0005 — RFID USB Bridge untuk Layar Kasir

- **Status:** Digantikan oleh [ADR-0011](./0011-rfid-usb-bridge-keyboard-wedge-mvp.md) (2026-10-07) — keputusan berjenjang: keyboard-wedge untuk MVP, WebHID/agent menuju produksi
- **Tanggal:** 2026-10-02
- **Pengusul:** FE-1 / BE-1
- **Terkait:** PRD §6.1, §13 poin 5, Q10, Q17

## Konteks

Layar kasir (`kantin-fe`) berjalan di browser (web). Reader RFID adalah perangkat **USB**. **Browser tidak dapat membaca perangkat USB/serial sembarang secara langsung** tanpa API khusus atau perantara. PRD §13 baru menyebut "cek dengan tim RFID" — belum ada keputusan. Tap harus terasa ≤1 dtk.

## Keputusan

_(Perlu spike sebelum diterima.)_ Arah usulan: pakai **bridge lokal** yang membaca reader USB dan meneruskan UID ke kantin-fe/kantin-be, dengan urutan preferensi:
1. **WebHID / WebSerial API** (bila reader & browser mendukung) — tanpa instalasi tambahan.
2. **Agent lokal** (Node/Electron kecil atau service native) yang membaca reader dan mengirim ke kantin-fe via WebSocket/HTTP — paling andal untuk reader yang tidak expose HID/serial standar.

Keputusan final menunggu hasil spike terhadap **reader fisik yang dipakai** (samakan dengan reader Kiosk Presensi bila memungkinkan — Q17).

## Alasan

- Web browser sengaja tidak memberi akses USB bebas → butuh salah satu mekanisme di atas.
- WebHID/WebSerial = paling ringkas bila didukung; agent lokal = fallback universal.
- Menyamakan reader dengan Kiosk Presensi mengurangi risiko & memudahkan suku cadang.

## Alternatif yang Dipertimbangkan

| Alternatif | Kenapa belum dipilih |
|---|---|
| Input keyboard-wedge (reader mengirim UID sebagai keystrokes) | Sederhana, tapi rapuh (fokus field, tak ada ack) & sulit jamin 1dtk/anti-duplikat |
| Kasir pakai HP Android + NFC | **Di luar lingkup MVP** (PRD §14, fase berikutnya) |

## Konsekuensi

**Positif:** kasir web tetap dipakai; pengalaman tap cepat.

**Negatif / risiko:** menambah komponen (bridge/agent) di mesin kasir → perlu instalasi & pemeliharaan; perlu uji kompatibilitas reader/browser.

## Tindak Lanjut

- [ ] Spike: identifikasi reader fisik + dukungan HID/serial (Q10, Q17)
- [ ] Bila agent: tentukan distribusi & auto-start
- [ ] Uji: tap → UID sampai ke backend & beep ≤1 dtk
