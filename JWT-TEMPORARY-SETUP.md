# ⚠️ JWT TEMPORARY SETUP — READ BEFORE DEPLOY

**Status:** WORKAROUND AKTIF (2026-10-05)  
**Expired:** WAJIB GANTI SEBELUM STAGING/PRODUCTION

---

## 🎯 Kenapa Ada Setup Ini?

Admin-be **belum punya** production RS256 keypair. Untuk unblock integrasi frontend, temporary keypair di-generate:

- **Private key:** `admin-be/.env.jwt-temporary` (UNTRACKED, tidak di-commit)
- **Public key:** `kantin-be/src/main/resources/application-local.properties` (UNTRACKED)

## ✅ Yang Sudah Dikerjakan (2026-10-05)

1. **admin-be `JwtUtils.java`** sudah di-patch untuk include klaim:
   - `sekolah_id` (Long) — KRITIS untuk tenant scoping kantin-be
   - `role` (String) — nama role SKOOLIA (ADMIN, GURU, BENDAHARA, dst)
   - `user_id` (Long) — ID user
   - `nama` (String) — nama lengkap user
   - `sub` (String) — username (sudah ada)
   - `typ` (String) — access/refresh (sudah ada)

2. **kantin-be `application-local.properties`** sudah diisi temporary public key

3. **Dokumentasi** updated:
   - `OPEN-QUESTIONS.md` Q1 status → 🟡 TEMPORARY
   - File ini sebagai reminder

## 🚨 BAHAYA Kalau Lupa Ganti

- **Private key ter-expose** di file `.env.jwt-temporary` yang bisa dibaca developer mana pun
- **Siapa pun bisa forge token** staf kalau dapat private key ini
- **Security audit GAGAL** karena key management tidak proper

## ✅ Cara Ganti ke Production Key (Sebelum Deploy)

### 1. Generate Production RSA Keypair

```bash
# Di server secure (BUKAN di laptop developer)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt_prod_priv.pem
openssl rsa -in jwt_prod_priv.pem -pubout -out jwt_prod_pub.pem

# Convert ke base64 DER (format yang dipakai admin-be/kantin-be)
openssl pkcs8 -topk8 -nocrypt -in jwt_prod_priv.pem -outform DER | base64 -w0 > jwt_prod_priv_b64.txt
openssl rsa -in jwt_prod_priv.pem -pubout -outform DER | base64 -w0 > jwt_prod_pub_b64.txt
```

### 2. Simpan Private Key di Vault/Secrets Manager

**JANGAN** commit private key ke git. Simpan di:
- HashiCorp Vault
- AWS Secrets Manager
- Azure Key Vault
- Google Secret Manager
- Kubernetes Secret (minimal base64, tapi enkripsi lebih baik)

### 3. Update admin-be

```bash
# Di Kubernetes Secret atau .env production (NEVER commit)
export JWT_PRIVATE_KEY="<isi jwt_prod_priv_b64.txt>"
export JWT_PUBLIC_KEY="<isi jwt_prod_pub_b64.txt>"
```

### 4. Update kantin-be

```bash
# Di Kubernetes ConfigMap atau .env production
export JWT_ADMIN_PUBLIC_KEY="<isi jwt_prod_pub_b64.txt>"
```

### 5. Hapus File Temporary

```bash
rm admin-be/.env.jwt-temporary
rm /tmp/jwt_temp_* /tmp/jwt_keys.env
```

### 6. Update Dokumentasi

```bash
# Di kantin-be/architecture/OPEN-QUESTIONS.md
# Ubah Q1 status dari "🟡 TEMPORARY" → "🟢 Production key deployed"
```

---

## 🔍 Cara Verifikasi Token (Debugging)

```bash
# Decode JWT (tanpa verify signature — JANGAN pakai di production logic)
echo "YOUR_JWT_TOKEN" | cut -d'.' -f2 | base64 -d 2>/dev/null | jq .

# Expected claims:
# {
#   "sub": "username",
#   "typ": "access",
#   "user_id": 123,
#   "nama": "John Doe",
#   "role": "ADMIN",
#   "sekolah_id": 10,
#   "jti": "uuid",
#   "iat": 1234567890,
#   "exp": 1234567890
# }
```

---

## 📋 Checklist Pre-Deploy

- [ ] Production RSA keypair generated di server secure
- [ ] Private key disimpan di Vault/Secrets Manager (NEVER git)
- [ ] Public key di-distribute ke kantin-be via ConfigMap/env var
- [ ] File `.env.jwt-temporary` dihapus
- [ ] OPEN-QUESTIONS.md Q1 diupdate → 🟢
- [ ] File `JWT-TEMPORARY-SETUP.md` ini dihapus (sudah tidak relevan)
- [ ] Security review passed

---

**Kontak:** Tim DevSecOps / Lead Backend  
**Dibuat:** 2026-10-05  
**Expired:** ASAP (sebelum deploy staging)
