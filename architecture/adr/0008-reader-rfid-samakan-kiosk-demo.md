# ADR-0008 — Reader RFID USB kasir = reader Kiosk Presensi (asumsi demo)

- **Status:** Diusulkan (asumsi DEMO — menunggu konfirmasi tim RFID)
- **Tanggal:** 2026-10-07
- **Pengusul:** Tim kantin-be
- **Terkait:** PRD §6.1, §13 poin 5; ADR-0005; Q10, Q17 di OPEN-QUESTIONS; issue #22

---

## Konteks

Q17 bertanya apakah **spesifikasi reader RFID USB kasir = reader Kiosk Presensi**.
Keputusan ini menentukan mode koneksi (WebHID/WebSerial/agent lokal) di ADR-0005
dan bentuk UID yang dikirim ke backend (`TapRequest.rfidUid`, divalidasi
`@UidKartuValid`).

Tim RFID belum mengonfirmasi. Kantin-be **belum rilis penuh**, dan modul tap
tidak boleh tertahan hanya karena spesifikasi perangkat belum final.

## Keputusan

**Untuk demo, kantin-be mengasumsikan reader kasir = reader Kiosk Presensi**,
dengan parameter berikut (dapat diubah lewat konfigurasi, tanpa ubah kode):

| Parameter | Nilai demo | Kunci konfigurasi |
|---|---|---|
| Reader sama dengan Kiosk Presensi | `true` | `kantin.profil.reader-sama-dengan-kiosk` |
| Mode koneksi | `KEYBOARD_WEDGE` (HID) | `kantin.profil.reader-mode` |
| Panjang UID | 10 (heksadesimal) | `kantin.profil.reader-panjang-uid` |
| Waktu baca UID | ≤ 150 ms | `kantin.profil.reader-baca-ms` |

Asumsi & parameternya disajikan lewat endpoint `GET /api/konfigurasi/profil`
agar tim RFID/FE melihat postur yang aktif sekarang.

## Alasan

- Menyamakan reader dengan Kiosk Presensi **mengurangi risiko** & memudahkan
  suku cadang (sudah disebut sebagai preferensi di ADR-0005).
- Mode keyboard-wedge = paling sederhana untuk demo (reader mengirim UID sebagai
  keystroke); cukup untuk membuktikan alur tap end-to-end.
- Nilai konfigurabel ⇒ begitu tim RFID menjawab, cukup ubah environment —
  **tidak perlu ubah kode**.

## Alternatif yang Dipertimbangkan

| Alternatif | Kenapa belum dipilih |
|---|---|
| Tunggu spesifikasi final sebelum coding | Menahan modul tap padahal kantin-be belum rilis penuh (demo) |
| Pilih WebHID/WebSerial langsung | Bergantung dukungan perangkat yang belum diketahui |
| Hard-code asumsi di kode | Sulit diubah; melanggar prinsip konfigurasi (12-factor) |

## Konsekuensi

**Positif:** modul tap bisa diuji sekarang; asumsi terdokumentasi & terlihat
via API.

**Negatif / risiko:** keyboard-wedge rapuh (fokus field, tanpa ack) — **bukan**
pilihan produksi; wajib ditinjau ulang setelah konfirmasi Q17.

## Tindak Lanjut

- [ ] Konfirmasi Q17 ke tim RFID (spesifikasi reader + dukungan HID/serial)
- [ ] Bila reader berbeda: ubah `kantin.profil.reader-*` & mode koneksi di ADR-0005
- [ ] Uji: tap → UID sampai ke backend & beep ≤ 1 dtk
