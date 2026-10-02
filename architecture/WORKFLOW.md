# 🔀 WORKFLOW.md — Alur Kerja Git & Kolaborasi Tim

> Proyek berkelompok. Aturan ini menjaga `main` tetap sehat dan mencegah konflik.

---

## 1. Model Branching

```
main            ← production-ready. PROTECTED. Hanya merge dari PR (develop).
 └── develop    ← integrasi harian tim. PROTECTED. Hanya merge dari PR.
      ├── feat/<modul>-<deskripsi>
      ├── fix/<modul>-<deskripsi>
      ├── hotfix/<deskripsi>       (dari main, untuk bug production mendesak)
      ├── chore/<deskripsi>
      └── docs/<deskripsi>
```

**Penamaan branch:**
| Prefix | Untuk | Contoh |
|---|---|---|
| `feat/` | Fitur baru | `feat/kasir-tap-validasi` |
| `fix/` | Perbaikan bug | `fix/ledger-race-debit` |
| `hotfix/` | Bug production | `hotfix/saldo-minus` |
| `chore/` | Non-fitur | `chore/setup-docker-compose` |
| `docs/` | Dokumentasi | `docs/adr-0002-jwt-rs256` |

> **Tidak boleh push langsung ke `main` atau `develop`.** Selalu via PR.

---

## 2. Alur Kerja Harian (per anggota)

```bash
# 1. Sinkron develop
git checkout develop && git pull origin develop

# 2. Buat branch fitur
git checkout -b feat/kasir-tap-validasi

# 3. Kerjakan, commit kecil & bermakna
git add -A
git commit -m "feat(kasir): tambah validasi urutan 6 tahap"

# 4. Push
git push -u origin feat/kasir-tap-validasi

# 5. Buka PR ke develop (via GitHub)
```

---

## 3. Conventional Commits

Format: `type(scope): deskripsi` (imperatif, huruf kecil, tanpa titik akhir).

| type | Untuk |
|---|---|
| `feat` | Fitur baru |
| `fix` | Perbaikan bug |
| `docs` | Dokumentasi |
| `refactor` | Ubah kode tanpa ubah perilaku |
| `test` | Tambah/perbaiki test |
| `chore` | Build, config, dependency |
| `perf` | Peningkatan performa |

**Contoh:**
```
feat(kasir): validasi kartu blokir instan tanpa cache
fix(ledger): cegah saldo minus saat tap bersamaan
docs(adr): tambah ADR-0003 strategi locking ledger
chore(deps): tambah jjwt 0.12.6
```
Sertakan nomor § PRD bila relevan:
```
feat(saldo): top-up tunai TU (PRD §9.2)
```

---

## 4. Pull Request

**Template PR (buat `.github/PULL_REQUEST_TEMPLATE.md`):**
```markdown
## Ringkasan
<jelaskan perubahan singkat>

## Terkait
- PRD §<nomor> <judul>
- Issue #<nomor>
- ADR: <jika ada>

## Jenis perubahan
- [ ] Fitur  - [ ] Fix  - [ ] Refactor  - [ ] Docs

## Cara uji
<langkah reproduksi / perintah test>

## Checklist DoD
- [ ] Test ditambahkan
- [ ] Tenant scoping & RBAC diuji
- [ ] Audit log (bila perlu)
- [ ] Migrasi Flyway (bila perlu)
- [ ] Dokumentasi diperbarui
```

**Aturan review:**
- Minimal **1 reviewer** (bukan penulis).
- CI harus **hijau**.
- Perubahan menyentuh **ledger/saldo/stok/auth** → butuh reviewer dari peran **BE-1** atau **BE-2**.
- PR besar (>500 baris) sebaiknya dipecah.

---

## 5. Konvensi Review

Sebagai reviewer, periksa:
1. Sesuai PRD & `AGENTS.md §3` (aturan emas)?
2. Ada test?
3. Ada hard delete / update ledger? (tolak)
4. Tenant scoping di semua query?
5. Uang integer? Bukan float?
6. Logika bisnis tidak di controller?
7. Secret tidak ter-commit?

Reviewer gunakan label: `nit:` (opsional), `suggestion:`, `blocking:`.

---

## 6. Konflik Merge

```bash
git checkout feat/nama-branch
git fetch origin
git rebase origin/develop
# selesaikan konflik
git add <file>
git rebase --continue
git push --force-with-lease
```
**Wajib** `--force-with-lease` (bukan `--force`) untuk branch milik Anda saja.

---

## 7. Rilis

- `develop` → `main` = PR rilis, diberi **tag** (`v0.1.0`).
- Versi: `MAJOR.MINOR.PATCH`. Fase MVP pilot = `v0.x`.
- Deploy via Jenkins (pola admin-be).

---

## 8. Aturan untuk Agen AI (Codebuddy/Claude/dll.)

- Agen **selalu** baca `AGENTS.md` + dokumen terkait sebelum menulis kode.
- Agen **tidak** commit/push tanpa diminta manusia.
- Agen **tidak** mengubah `main`/`develop` langsung.
- Agen mengikuti konvensi §3 commit & DoD `AGENTS.md §9`.
- Perubahan agen ditinjau manusia seperti kontributor biasa.
