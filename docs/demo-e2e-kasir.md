# Demo E2E Kasir (lokal) — issue #163

Panduan menjalankan **alur kasir end-to-end** di lokal — *tap → potong saldo &
stok → tutup sesi → cek laporan* — **tanpa** menunggu data/keys tim SKOOLIA
(OPEN-QUESTIONS **Q7**). Semua data demo **milik kantin-be sendiri** (tabel lokal).

> ⚠️ **Hanya untuk lokal/demo.** Profil `demo` mengaktifkan jalur lookup Kartu
> Tamu & shim login dev. **JANGAN** dipakai di staging/production.

---

## 0. Ringkas alur

```
docker compose up -d            # PostgreSQL + Redis
SPRING_PROFILES_ACTIVE=local,demo  (butuh private key JWT dummy)
psql … -f load/seed/seed-demo.sql   # seed data demo
POST /api/v1/auth/login         # ambil token (shim dev)
POST /api/kasir/tap             # tap kartu → transaksi
POST /api/kasir/sesi/1/tutup    # tutup sesi
GET  /api/laporan/…             # cek laporan
```

Data yang di-seed: sekolah `1`, titik kasir `DEMO1`, 1 sesi **TERBUKA**, 3 menu,
**5 Kartu Tamu** (UID `DE000001`..`DE000005`, saldo Rp 100.000).

> **Integrasi eksternal disimulasikan (bukan API key).** Profil `demo` juga
> mengaktifkan adapter **demo** untuk 6 integrasi yang masih `blocked` (butuh
> kredensial tim SKOOLIA) — Buku Kas, Notifikasi, Lookup kartu, Status siswa,
> UID siswa, Aktivasi modul. Semua meniru sistem eksternal di lokal sehingga alur
> end-to-end (termasuk posting Buku Kas & notifikasi) benar-benar berjalan tanpa
> API key. Lihat §9.

---

## 1. Infrastruktur

```bash
docker compose up -d
docker compose ps            # pastikan postgres & redis "healthy"
```

## 2. Jalankan aplikasi (profil demo)

Profil `demo` butuh **private key JWT** agar shim login dev bisa menerbitkan
token (pasangan `jwt.admin-public-key`). Buat keypair dummy **sekali**:

```bash
# Private key (untuk shim login) + public key (untuk verifikasi kantin-be)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/priv.pem
export KANTIN_DEV_LOGIN_PRIVATE_KEY=$(openssl pkcs8 -topk8 -nocrypt -in /tmp/priv.pem -outform DER | base64 -w0)
export JWT_ADMIN_PUBLIC_KEY=$(openssl rsa -in /tmp/priv.pem -pubout -outform DER | base64 -w0)
```

Lalu:

```bash
SPRING_PROFILES_ACTIVE=local,demo \
KANTIN_DEV_LOGIN_ENABLED=true \
KANTIN_RATE_LIMIT_ENABLED=false \
  ./mvnw spring-boot:run
```

Aplikasi siap di `http://localhost:8082` (health: `/actuator/health`).

> `application-demo.properties` sudah menyetel `kantin.demo.enabled=true`,
> `kantin.dev-login.enabled=true`, dan mematikan rate limit — jadi env di atas
> hanya menegaskan.

## 3. Seed data demo (sekali)

```bash
psql "postgresql://kantin:kantin_dev@localhost:5432/kantin_db" -f load/seed/seed-demo.sql
```

**Idempoten** — aman dijalankan berulang (uji: 2× run tidak menggandakan baris).

## 4. Ambil token (shim login dev)

```bash
TOKEN=$(curl -s -X POST http://localhost:8082/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"kasir@skoolia.id","password":"bebas","sekolah_id":1}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
echo "$TOKEN" | head -c 20; echo "…"
```

Role dipetakan dari email: `kasir@…` → **PETUGAS** (lihat `DevLoginController`).
Bentuk respons: `{"code":200,"message":"…","data":{"token":"…","user":{…}}}`.

## 5. Tap kartu (transaksi)

```bash
curl -s -X POST http://localhost:8082/api/kasir/tap \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "rfidUid": "DE000001",
        "titikKasirId": 1,
        "idempotencyKey": "demo-tap-001",
        "items": [ {"menuId": 1, "qty": 1}, {"menuId": 3, "qty": 2} ]
      }' | python3 -m json.tool
```

Harapan: `code=200`, saldo terpotong (100.000 − 12.000 − 6.000 = **82.000**),
stok Nasi Goreng & Es Teh berkurang, `transaksiId` terisi.

> **Idempotency (PRD §11.3):** kirim ulang dengan `idempotencyKey` **sama** →
> hasil yang sama, **tidak** memotong saldo dua kali. Ganti key untuk tap baru.

## 6. Tutup sesi

```bash
curl -s -X POST http://localhost:8082/api/kasir/sesi/1/tutup \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

## 7. Cek laporan

```bash
# Ringkasan penjualan harian
curl -s "http://localhost:8082/api/laporan/penjualan?tanggal=$(date +%F)" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# Ekspor XLSX (Apache POI) — jenis laporan via query `jenis`
curl -s -o laporan.xlsx \
  "http://localhost:8082/api/laporan/ekspor?jenis=PENJUALAN&tanggal=$(date +%F)" \
  -H "Authorization: Bearer $TOKEN"
file laporan.xlsx     # → Microsoft Excel 2007+
```

Daftar lengkap endpoint laporan (penjualan/item/kategori, stok, setoran-tu,
per-siswa, dll) ada di [`architecture/API-ENDPOINTS.md`](../architecture/API-ENDPOINTS.md) §Laporan.

---

## 8. Reset data demo

Karena ledger & transaksi bersifat **append-only**, untuk mengulang dari nol:

```bash
docker compose down -v && docker compose up -d    # hapus volume → DB bersih
./mvnw spring-boot:run                            # migrasi Flyway jalan otomatis
psql "postgresql://kantin:kantin_dev@localhost:5432/kantin_db" -f load/seed/seed-demo.sql
```

## 9. Kenapa ini aman (tidak bocor ke produksi)

| Gerbang | Default | Efek |
|---|---|---|
| `kantin.demo.enabled` | `false` | Adapter **demo** (6 integrasi) & `KartuLookupDemo` hanya hidup bila `true` (profil `demo`) |
| `kantin.loadtest.enabled` | `false` | `KartuLookupLoadTest` hanya hidup bila `true` (profil `loadtest`) |
| `kantin.dev-login.enabled` | `false` | Shim login hanya hidup bila `true` + profil `local` |
| `kantin.integrasi.*.enabled` | `false` | Adapter REST **nyata** hanya hidup bila `true` (staging/produksi) |
| `kantin.integrasi.wajib-aktif` | `false` | Bila `true`, start DIGAGALKAN bila masih ada `*Fallback` (penjaga produksi #164) |
| `@Profile("local")` | — | Shim login dev **tidak** dimuat di staging/produksi |

**Adapter demo (semua digerbangi `kantin.demo.enabled`):**

| Port | Kelas demo | Meniru | Perilaku |
|---|---|---|---|
| `KartuLookupPort` | `KartuLookupDemo` | admin-be lookup siswa | Kenali **Kartu Tamu** (data lokal) |
| `BukuKasPort` | `BukuKasDemo` | admin-be Buku Kas | Entri **SUKSES** (referensi tiruan) |
| `NotifikasiPort` | `NotifikasiDemo` | mobile-be push | **TERKIRIM** (log ringkasan) |
| `StatusSiswaPort` | `StatusSiswaDemo` | admin-be status siswa | Siswa **aktif**; blokir = log |
| `UidSiswaPort` | `UidSiswaDemo` | admin-be anti-tabrakan UID | UID **tidak** dipakai siswa |
| `AktivasiModulPort` | `AktivasiModulDemo` | internal-be aktivasi/fee | Modul **aktif** (`diketahui=true`) |

- Semua adapter demo `@Primary` — **jangan** aktifkan bersamaan dengan adapter
  REST nyata (`kantin.integrasi.*.enabled=true`) → dua `@Primary` ambigu.
- `KartuLookupDemo` & `KartuLookupLoadTest` sama-sama `@Primary` — **jangan**
  aktifkan keduanya bersamaan (dua `@Primary` → ambigu). Pilih satu profil.
- Identitas **siswa** tetap menunggu Q7: hanya Kartu Tamu yang dikenali di sini.
- Status blokir tetap dibaca **tanpa cache** lewat `KontrolKartuService` (PRD §11.11).

---

## 10. Untuk tim FE (kantin-fe)

- Base URL: `http://localhost:8082`, login: `POST /api/v1/auth/login` (shim dev).
- Akun demo (email → role): `kasir@skoolia.id` (kasir), `pengelola@skoolia.id`
  (pengelola), `tu@skoolia.id` (TU), `bendahara@skoolia.id` (bendahara),
  `admin@skoolia.id` (admin). Password **tidak diverifikasi** (alat uji).
- Data seed: 3 menu, 5 Kartu Tamu siap tap (`DE000001`..`DE000005`).

> Terkait: [`docs/uji-beban-tap.md`](uji-beban-tap.md) (profil `loadtest`),
> [`architecture/OPEN-QUESTIONS.md`](../architecture/OPEN-QUESTIONS.md) (Q7).
