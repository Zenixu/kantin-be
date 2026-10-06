# 🌿 Git Workflow — Tim Kantin SKOOLIA

**Strategy:** Feature Branch + Pull Request Review (mandatory)  
**Conflict Handler:** Ibnu (Aruthtale)  
**Branch Protection:** `main` branch protected, wajib PR review sebelum merge

---

## 📋 Aturan Tim

### 1. **JANGAN PERNAH** Push Langsung ke `main`
```bash
# ❌ SALAH
git checkout main
git commit -m "..."
git push origin main  # BAHAYA! Bisa conflict dengan tim lain

# ✅ BENAR
git checkout -b feature/nama-fitur
git commit -m "..."
git push origin feature/nama-fitur  # Aman, tidak ganggu yang lain
```

### 2. **WAJIB** Buat Branch Baru per Fitur
Format nama branch: `feature/nama-fitur` atau `fix/nama-bug`

**Contoh:**
- `feature/frontend-security` — Ibnu (secure storage + role guard)
- `feature/laporan-excel` — Tim 1 (laporan penjualan)
- `feature/integration-test` — Tim 2 (testing)
- `fix/bug-login` — bugfix urgent

### 3. **WAJIB** Pull Request + Review Sebelum Merge
- Buat PR di GitHub
- Tag Ibnu untuk review (`@Aruthtale`)
- **JANGAN merge sendiri** — tunggu approval Ibnu
- Ibnu yang handle conflict kalau ada

---

## 🔄 Workflow Lengkap

### A. Mulai Kerja Fitur Baru

```bash
# 1. Pastikan di main & update
cd ~/Projects/PKL/Kantin-Skoolia/kantin-be/  # atau kantin-fe
git checkout main
git pull origin main

# 2. Buat branch baru dari main
git checkout -b feature/nama-fitur

# 3. Kerja seperti biasa (edit, commit)
# ... coding ...
git add .
git commit -m "feat: implementasi fitur X"

# 4. Push ke remote (branch sendiri)
git push -u origin feature/nama-fitur
```

### B. Buat Pull Request

```bash
# Setelah push, buka GitHub:
# https://github.com/Zenixu/kantin-be/pulls (untuk backend)
# https://github.com/Derylfabiensyah/kantin-fe/pulls (untuk frontend)

# Klik "New Pull Request"
# - Base: main
# - Compare: feature/nama-fitur
# - Title: [FEAT] Nama fitur yang jelas
# - Description: Jelaskan apa yang diubah + screenshot kalau perlu
# - Reviewers: pilih @Aruthtale (Ibnu)
# - Create Pull Request
```

### C. Setelah Review Disetujui

```bash
# JANGAN merge sendiri — Ibnu yang merge via GitHub UI
# Atau kalau Ibnu approve, baru boleh merge via GitHub

# Setelah merged, hapus branch lokal:
git checkout main
git pull origin main
git branch -D feature/nama-fitur
```

---

## ⚠️ Handling Conflict (Khusus Ibnu)

Kalau ada conflict saat PR:

```bash
# 1. Checkout branch yang conflict
git checkout feature/nama-fitur
git pull origin feature/nama-fitur

# 2. Merge main terbaru
git fetch origin
git merge origin/main

# 3. Resolve conflict manual
# Edit file yang conflict (cari <<<<<<<, =======, >>>>>>>)
git add .
git commit -m "fix: resolve merge conflict with main"
git push origin feature/nama-fitur

# 4. PR otomatis update, bisa merge sekarang
```

---

## 📊 Contoh Skenario Tim

### Skenario 1: 3 Orang Kerja Parallel

**Ibnu (Anda):**
```bash
git checkout -b feature/frontend-security
# Kerja secure storage + role guard
git push origin feature/frontend-security
# Buat PR → self-review → merge
```

**Teman 1:**
```bash
git checkout -b feature/laporan-excel
# Kerja laporan penjualan
git push origin feature/laporan-excel
# Buat PR → tag @Aruthtale → tunggu review
```

**Teman 2:**
```bash
git checkout -b feature/integration-test
# Kerja testing
git push origin feature/integration-test
# Buat PR → tag @Aruthtale → tunggu review
```

**Result:** Tidak ada conflict karena kerja di branch terpisah! 🎉

---

### Skenario 2: Conflict Terjadi

**Situasi:**
- Teman 1 ubah `KatalogController.java` di `feature/laporan-excel`
- Anda juga ubah `KatalogController.java` di `feature/frontend-security`
- Teman 1 merge duluan ke `main`
- PR Anda sekarang conflict ❌

**Solusi:**
```bash
# Anda (Ibnu) yang handle:
git checkout feature/frontend-security
git fetch origin
git merge origin/main  # Conflict muncul di sini

# Edit KatalogController.java manual:
# - Lihat perubahan Teman 1 (<<<<<<< HEAD)
# - Lihat perubahan Anda (>>>>>>> feature/frontend-security)
# - Gabungkan keduanya (ambil yang perlu, hapus marker)

git add src/main/java/.../KatalogController.java
git commit -m "fix: resolve conflict with main (laporan-excel changes)"
git push origin feature/frontend-security

# PR hijau lagi, bisa merge ✅
```

---

## 🛡️ Branch Protection (GitHub Settings)

**Untuk repo owner (Zenixu/Aruthtale):**

1. Buka `Settings` → `Branches` → `Add rule`
2. Branch name pattern: `main`
3. Centang:
   - ✅ Require pull request reviews before merging (1 approval)
   - ✅ Require status checks to pass (kalau ada CI/CD)
   - ✅ Include administrators (agar Ibnu juga wajib PR)
4. Save

**Result:** `main` tidak bisa di-push langsung, **HARUS via PR**.

---

## 📝 Commit Message Convention

**Format:**
```
<type>: <subject>

<body (optional)>
```

**Types:**
- `feat:` — fitur baru
- `fix:` — bugfix
- `docs:` — update dokumentasi
- `refactor:` — refactor code (tidak ubah behavior)
- `test:` — tambah/update test
- `chore:` — maintenance (update dependency, dll)

**Contoh:**
```bash
git commit -m "feat: implementasi CRUD Kartu Tamu"
git commit -m "fix: role guard tidak jalan di KasirController"
git commit -m "docs: update API-KARTU-TAMU.md dengan use case baru"
```

---

## 🚨 Emergency: Rollback Commit yang Salah

Kalau tidak sengaja commit ke `main`:

```bash
# 1. Jangan panik!
# 2. Batalkan commit (belum di-push)
git reset HEAD~1  # Undo 1 commit terakhir, file tetap ada (unstaged)

# 3. Pindah ke branch baru
git checkout -b feature/nama-fitur
git add .
git commit -m "feat: ..."
git push origin feature/nama-fitur

# 4. Kembali ke main bersih
git checkout main
```

Kalau sudah ke-push ke `main`:
```bash
# Hubungi Ibnu ASAP! Jangan coba revert sendiri
# Ibnu yang handle via git revert atau force-push (BAHAYA)
```

---

## 📞 Kontak

**Conflict Handler:** Ibnu (Aruthtale)  
**GitHub:** @Aruthtale / @Zenixu

**Kalau stuck:**
1. Screenshot error/conflict
2. Tag Ibnu di PR comment atau chat
3. JANGAN force-push tanpa konfirmasi Ibnu

---

**Dibuat:** 2026-10-05  
**Tim:** Kantin Cashless SKOOLIA PKL
