# Adapter REST Integrasi (issue #160)

Dokumen ini menjelaskan **adapter REST nyata** yang menggantikan `*Fallback`
untuk 6 port integrasi lintas-repo. Tujuannya: begitu endpoint nyata tersedia
(atau siap di-UAT), cukup **menyalakan flag** — tanpa mengubah logika bisnis.

## Prinsip

1. **Default OFF.** Semua adapter `@ConditionalOnProperty(... enabled, default
   false)`. Selama OFF, bean `*Fallback` tetap aktif → dev/demo/test **tidak
   berubah** dan tidak ada panggilan keluar tak terduga.
2. **@Primary.** Saat ON, adapter menang atas fallback pada injeksi yang sama.
3. **Configurable.** URL/header/timeout/retry semuanya properti, bukan
   hard-code. Kontrak default mengikuti keputusan tim BE
   (`architecture/OPEN-QUESTIONS.md` Q3–Q8).
4. **Fail-open / fail-safe eksplisit** — lihat tabel di bawah.
5. **Teruji kontrak.** `AdapterRestWireMockTest` (WireMock embedded, tanpa
   Docker) membuktikan pemetaan sukses / 5xx / timeout / payload salah untuk
   tiap adapter.

## Tabel adapter

| Port | Adapter | Endpoint default | Arah | Perilaku gagal |
|------|---------|------------------|------|----------------|
| BukuKasPort | `BukuKasRestClient` | `POST /api/buku-kas/catat` | → admin-be | GAGAL (sesi tak dibatalkan) |
| NotifikasiPort | `NotifikasiRestClient` | `POST /api/internal/notifikasi` | → mobile-be | fail-open (best-effort) |
| KartuLookupPort | `KartuLookupRestClient` | `GET /api/siswa/cek-kartu/{rfidUid}` | → admin-be | tidak dikenal |
| AktivasiModulPort | `AktivasiModulRestClient` | `GET /api/internal/modul-kantin/status` | → internal-be | fail-open (anggap aktif) |
| StatusSiswaPort | `StatusSiswaRestClient` | `GET /api/siswa/get-by-id/{id}` | → admin-be | `null` (tidak diketahui) |
| UidSiswaPort | `UidSiswaRestClient` | `GET /api/internal/siswa/cek-uid` | → admin-be | `null` (fail-open) |

Catatan `KartuLookupRestClient`: **kartu tamu lokal dicek lebih dulu** (tabel
`kartu_tamu`), baru jatuh ke admin-be. **Status blokir tidak di-cache** — selalu
dibaca segar (anti-tabrakan UID, lihat `docs/integrasi-anti-tabrakan-uid.md`).

Catatan `StatusSiswaRestClient.blokirKartu` bersifat **best-effort** — kegagalan
tidak melempar (blokir lokal tetap berlaku).

## Retry

`IntegrasiRestSupport` melakukan retry terbatas untuk kegagalan **jaringan /
timeout / 5xx**, dengan backoff linear (`max-attempts`, `backoff-ms`).
**4xx TIDAK di-retry** (salah permintaan — retry sia-sia). Default
`max-attempts=1` (tanpa retry).

## Keamanan

- **Bearer**: isi `bearer-token` → header `Authorization: Bearer …`.
- **HMAC internal**: isi `secret` → header `X-Internal-Timestamp` +
  `X-Internal-Signature` (`sha256=` HMAC dari `timestamp + "." + body`),
  memakai pola yang sama dengan `TandaTanganWebhook` (webhook, #29).

Rahasia **tidak** ditulis di properti; selalu lewat environment
(`KANTIN_INTEGRASI_<NAMA>_BEARER_TOKEN`, `…_SECRET`).

## Cara menyalakan

```properties
kantin.integrasi.bukukas.enabled=true
kantin.integrasi.bukukas.base-url=https://admin-be.contoh.id
# opsional:
kantin.integrasi.bukukas.bearer-token=…
kantin.integrasi.bukukas.max-attempts=3
```

Atau via environment (lihat `.env.example`).

## Penjaga kesiapan produksi (#164)

Untuk memastikan **tidak ada `*Fallback` yang dipakai di staging/produksi**,
`PemeriksaIntegrasiProduksi` berjalan saat aplikasi siap
(`ApplicationReadyEvent`). Bila `kantin.integrasi.wajib-aktif=true`, start
**DIGAGALKAN** (fail-closed) ketika:

- salah satu dari **6 adapter** belum aktif (`…enabled=false` → masih `*Fallback`);
- adapter aktif tetapi `base-url` kosong;
- public key **RS256** admin-be / mobile-be belum terpasang
  (`jwt.admin-public-key` / `jwt.mobile-public-key`).

```properties
# staging/produksi:
kantin.integrasi.wajib-aktif=true
```

Default `false` agar dev/demo/test (yang memang memakai `*Fallback` karena
kontrak tim SKOOLIA belum final) tetap berjalan. Pola sama dengan
`PemeriksaTopologiDatabase` (ADR-0010/Q11).

## Verifikasi

```bash
./mvnw test -Dtest=AdapterRestWireMockTest          # 22 uji kontrak
./mvnw test -Dtest=PemeriksaIntegrasiProduksiTest   # 6 uji penjaga produksi (#164)
```

WireMock berjalan embedded sehingga uji ini **tidak butuh Docker** dan ikut
dalam `./mvnw test` biasa.

## Uji integrasi jalur demo (E2E) — #164

`DemoE2EIT` (Testcontainers) membuktikan jalur demo berjalan **end-to-end**:
*tap Kartu Tamu → potong saldo/stok → tutup sesi → posting Buku Kas SUKSES →
idempoten*. IT ini mengaktifkan adapter demo nyata (`KartuLookupDemo` +
`BukuKasDemo`) lewat `kantin.demo.enabled=true`, sehingga yang diuji adalah jalur
kode produksi — hanya backend eksternalnya disimulasikan.

```bash
./mvnw verify -Dtest=DemoE2EIT    # butuh Docker (Testcontainers)
```
