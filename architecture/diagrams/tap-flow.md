# 🔄 diagrams/tap-flow.md — Alur Tap Kartu di Kasir

> Diagram teks alur utama (PRD §6.1). Sumber kebenaran perilaku = PRD teknis.

## 1. Alur Normal (happy path)

```
Petugas                    Layar Kasir (kantin-fe)         kantin-be                 SKOOLIA
   │                              │                            │                       │
   │─ pilih menu ────────────────►│                            │                       │
   │                              │ (item stok 0 = "Habis")    │                       │
   │  Pembeli tap kartu           │                            │                       │
   │─────────────────────────────►│─ POST /kasir/tap (uid,     │                       │
   │                              │   items, idempotencyKey) ─►│                       │
   │                              │                            │─ lookup kartu (uid) ─►│ (admin-be)
   │                              │                            │◄─ siswa/kartu ────────│
   │                              │                            │                       │
   │                              │              [VALIDASI BERURUTAN 1..6]              │
   │                              │              1 kartu dikenal?                       │
   │                              │              2 tidak diblokir? (server, no cache)   │
   │                              │              3 tidak ada item diblokir ortu?        │
   │                              │              4 stok cukup?                          │
   │                              │              5 ≤ limit harian?                      │
   │                              │              6 saldo ≥ total?                       │
   │                              │                            │                       │
   │                              │              [SATU TRANSAKSI DB]                    │
   │                              │              debit saldo (FOR UPDATE)               │
   │                              │              kurang stok                             │
   │                              │              catat transaksi + HPP snapshot         │
   │                              │◄─ hasil + foto/nama/kelas ─│                        │
   │◄─ beep sukses + overlay ─────│                            │─ notifikasi ortu ────►│ (mobile-be)
   │   (3 dtk, tombol Batalkan)   │                            │                       │
   │                              │─ kosongkan keranjang ─────►│                       │
```

## 2. Alur Gagal (salah satu validasi)

```
tap ─► validasi 1..6 ─► GAGAL di N ─► beep gagal (nada berbeda) + pesan merah besar
                                          │
                                          ├─ #3/#4/#6 → petugas kurangi/ganti item → minta tap ulang
                                          ├─ #1 → "Kartu tidak dikenal"
                                          ├─ #2 → "Kartu diblokir" (+ "hubungi orang tua" utk siswa)
                                          └─ #5 → "Melebihi limit harian (sisa Rp X)"
        Saldo TIDAK terpotong · Stok TIDAK berkurang · Transaksi TIDAK dibuat
```

## 3. Batalkan (wajah tidak cocok)

```
beep sukses ─► overlay foto 3 dtk ─► petugas lihat wajah TIDAK cocok ─► tekan Batalkan
                                              │
                                              ▼
                       void (alasan otomatis "Kartu dipakai bukan pemiliknya")
                       saldo kembali · stok kembali · limit kembali · notif ortu
                       kartu ditahan → diserahkan ke TU
```

## 4. Aturan Kunci (dari PRD §6.2)

- 1 tap = 1 potong (tap ganda ≠ 2×) → idempotency key.
- Saldo & stok **tak pernah minus**.
- Angka saldo penuh **tidak** ditampilkan (privasi) — hanya kekurangan.
- Tidak ada input nominal bebas — semua dari katalog.
- Target tap → beep **≤1 dtk (p95)**.
