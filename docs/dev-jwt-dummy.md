# 🔑 Harness JWT DUMMY (dev/test)

> ⚠️ **DUMMY — JANGAN dipakai di staging/production.** Ganti ke public key RS256
> **produksi** sebelum rilis (OPEN-QUESTIONS Q1/Q2).

## Cara tercepat: SHIM LOGIN DEV (disarankan untuk FE ↔ BE)

Bila yang dibutuhkan hanya **FE bisa login ke kantin-be**, pakai shim — tak
perlu mint token manual.

```properties
# src/main/resources/application-local.properties (untracked)
kantin.dev-login.enabled=true
kantin.dev-login.issuer=skoolia-admin
# Private key RSA base64 PKCS#8 DER — pasangan jwt.admin-public-key
kantin.dev-login.private-key=<BASE64_PRIVATE_KEY>
```

Lalu jalankan app (profil `local`) dan login dari FE:

```bash
curl -X POST http://localhost:8082/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@skoolia.id","password":"password123","sekolah_id":10}'
# -> { code:200, data:{ token:"eyJ...", user:{ currentRole:"admin", ... } } }
```

Role dipetakan dari email: `kasir@…`->kasir, `pengelola@…`->pengelola,
`tu@…`->tu, `bendahara@…`->bendahara, lainnya->admin.

**Gerbang keamanan:** kelas shim `@Profile("local")` **dan**
`kantin.dev-login.enabled=true`. Default `false` -> endpoint tetap **401**
(parity ADR-0002: kantin-be tanpa login sendiri).

## Harness JWT dummy (jalur lama — token manual)



kantin-be **tidak punya login sendiri** (ADR-0002) — identitas hanya dari token
JWT yang diterbitkan **admin-be** (staf) & **mobile-be** (orang tua), diverifikasi
RS256. Sampai tim lain menyediakan **public key RS256 produksi** (issue **#14**
staf, **#15** ortu), pengembangan & pengujian tak bisa menunggu.

Harness ini menyediakan **keypair dummy + pembuat token dummy** agar pengembangan
bisa jalan **tanpa menunggu** admin-be/mobile-be.

> ✅ Token dummy ditandatangani keypair dummy sehingga **lolos verifikasi RS256
> asli** — ini **bukan** bypass autentikasi. Jalur verifikasi produksi tetap utuh
> (PRD §11.10; daftar merah AGENTS.md §10). Private key **tidak pernah** di-commit.

## Pakai

```bash
# 1) Buat keypair dummy (tulis public key ke application-local.properties)
scripts/dev/gen-jwt-dummy.sh

#    …atau hanya cetak public key (tempel manual):
scripts/dev/gen-jwt-dummy.sh --print

# 2) Buat token dummy (RS256, ditandatangani private key dummy)
TOKEN=$(scripts/dev/mint-jwt-dummy.sh staf --sekolah 1 --user 42 --role PETUGAS_KANTIN)
TOKEN=$(scripts/dev/mint-jwt-dummy.sh ortu --sekolah 1 --siswa 7 --user 100)

# 3) Pakai ke API
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/auth/me
```

### Opsi `mint-jwt-dummy.sh`

| Opsi | Arti | Default |
|---|---|---|
| `--sekolah N` | klaim `sekolah_id` (tenant) | `1` |
| `--user N` | klaim `user_id` | staf `42`, ortu `100` |
| `--role R` | klaim `role` (staf) | `PETUGAS_KANTIN` |
| `--siswa N` | klaim `siswa_id` (ortu) | `7` |
| `--ttl DETIK` | umur token | `3600` |
| `--iss ISSUER` | set klaim `iss` | **tidak diset** (meniru admin-be) |

## Bentuk klaim (meniru penerbit asli)

**Staf (admin-be):** `sub`(username) · `typ`(access) · `user_id`(Long) ·
`nama`(String) · `role`(String) · `sekolah_id`(Long) · `jti`/`iat`/`exp`.
**Tanpa `iss`** — sesuai temuan Q1 (lihat `KompatibilitasTokenStafAdminTest`).

**Ortu (mobile-be):** sama, ditambah `siswa_id`(Long), `role=ORANG_TUA`.

## Berkas

| Berkas | Isi |
|---|---|
| `.dev-jwt/*-priv.pem` | private key dummy (**di-gitignore**, chmod 600) |
| `.dev-jwt/*-pub.b64` | public key dummy (base64 X.509 DER) |
| `scripts/dev/gen-jwt-dummy.sh` | generator keypair |
| `scripts/dev/mint-jwt-dummy.sh` | pembuat token |

## Sebelum staging/production (WAJIB)

- [ ] Dapatkan **public key RS256 produksi** admin-be (#14) & mobile-be (#15).
- [ ] Set `JWT_ADMIN_PUBLIC_KEY` / `JWT_MOBILE_PUBLIC_KEY` dari key produksi.
- [ ] **Hapus** `.dev-jwt/` & keypair dummy dari mesin dev.
- [ ] Minta admin-be/mobile-be **menambahkan klaim `iss`** → `jwt.verify-issuer=true`
      kembali ketat (saat ini `iss` absen diterima + peringatan).

> Uji regresi: `DummyTokenDevTest`, `KompatibilitasTokenStafAdminTest`,
> `KlaimResolverPeranTest`.
