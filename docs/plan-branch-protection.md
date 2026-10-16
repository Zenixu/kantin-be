# 📋 Plan — Proteksi Branch `main`

> **Status: ✅ AKTIF untuk `main` (diaktifkan & diverifikasi 2026-10-13).**
> Proteksi ditegakkan lewat **ruleset** GitHub: `main` (id `24626209`),
> `enforcement=active` dan target ref terisi. Verifikasi 2026-10-13:
> `branches/main.protected=true`, push langsung ditolak
> `GH013: Changes must be made through a pull request`.
> Dilacak & ditutup di [#171](https://github.com/ngahizie/kantin-be/issues/171).
>
> **2026-10-14 — model main-only.** Branch `develop` **dihapus**; hanya `main` yang
> dilindungi. Ruleset `develop` (id `24968833`) ikut dihapus.
>
> Dokumen ini menyimpan **konteks, cara verifikasi, dan riwayat** — berguna untuk
> mengaktifkan/memeriksa ulang proteksi.
>
> Rujukan aturan: [`WORKFLOW.md` §1](./WORKFLOW.md) — *"Tidak boleh push langsung
> ke `main`. Selalu via PR."* Proteksi branch adalah cara GitHub
> **menegakkan** aturan itu, bukan sekadar kesepakatan lisan.

---

## 1. Kenapa ini penting (khusus repo ini)

Repo ini **punya CI** (`.github/workflows/ci.yml`, `./mvnw verify` per-PR — issue #141),
tetapi proteksi branch tetap penting: CI hanya **menjalankan** test, bukan
**mewajibkan** PR/approval. Tanpa proteksi, `git push` langsung bisa melewati
review sepenuhnya.

Risiko nyata yang dicegah:

| Kejadian | Tanpa proteksi | Dengan proteksi |
|---|---|---|
| `git push --force` ke `main` | commit anggota lain **hilang**, riwayat rusak | ditolak (rule *non-fast-forward*) |
| Branch `main` terhapus | hilang, harus restore manual | ditolak (rule *deletion*) |
| Push langsung ke `main` | semua orang ikut kena | wajib lewat PR → ada review |
| Kode rusak masuk `main` | lolos bila CI dilewati/tidak wajib | ditangkap saat review PR + status CI |

> ⚠️ Risiko `--force` nyata di tim ini: `WORKFLOW.md` §6 memakai
> `--force-with-lease` di branch fitur — salah target sedikit saja bisa
> mengenai `main`.

---

## 2. Kondisi saat ini (per 2026-10-14)

**Proteksi AKTIF untuk `main`.** Bukti verifikasi langsung:

```bash
gh api repos/ngahizie/kantin-be/branches/main --jq .protected   # -> true
gh api repos/ngahizie/kantin-be/rulesets --jq '.[] | "\(.name) \(.enforcement)"'
#   main active
```

Uji push langsung (ditolak oleh ruleset):

```
remote: error: GH013: Repository rule violations found for refs/heads/main.
remote: - Changes must be made through a pull request.
remote: - Required status check "Build & Test (JDK 25)" is expected.
 ! [remote rejected] ... (push declined due to repository rule violations)
```

**Setelan yang ditegakkan** (untuk `main`):

| Aturan | Nilai |
|---|---|
| Wajib PR sebelum merge | ya |
| Jumlah approval | **1** |
| Dismiss review basi saat push baru | ya |
| Wajib resolusi komentar review | ya |
| Status check wajib | `Build & Test (JDK 25)` |
| Branch harus up-to-date (`strict`) | ya |
| Force-push | ditolak (`non_fast_forward`) |
| Hapus branch | ditolak (`deletion`) |
| Bypass | **tidak ada** (`bypass_actors: null`) — **semua** akun, termasuk owner, wajib PR + 1 approval |

> ⚠️ **Koreksi pasca-migrasi organisasi (2026-10-15).** Saat repo dipindah ke
> organisasi `ngahizie`, ruleset **kehilangan `bypass_actors`** yang dulu
> didaftarkan (2026-10-13). Kini **tidak ada bypass**: PR dari siapa pun —
> termasuk owner — berstatus `REVIEW_REQUIRED` sampai **1 approval** dari akun
> lain. Karena penulis tak bisa meng-approve PR-nya sendiri, **setiap PR butuh
> approver lain** (atau owner mengembalikan bypass lewat setelan ruleset).
> Riwayat bypass lama tetap dicatat di §5.

> **Catatan koreksi (2026-10-12):** klaim §2 sebelumnya ("proteksi aktif, terbukti PR #5
> ditolak") **tidak berlaku** — ruleset `main` saat itu `enforcement=disabled` **dan**
> target ref kosong, sehingga tidak mengikat apa pun. Endpoint klasik
> `branches/{b}/protection` mengembalikan **404** karena akun kita bukan admin —
> **jangan** dijadikan bukti; pakai `branches/{b}.protected` & `rulesets`.

**Cara paling andal memeriksa:** `branches/{b}.protected` = `true` **atau** ada
ruleset dengan `enforcement=active` & target ref yang cocok.

---

## 3. Bila perlu mengubah setelan (owner saja)

Proteksi sekarang memakai **rulesets** (bukan rule klasik). Endpoint klasik
`branches/{b}/protection` **404** untuk akun non-admin, jadi ubah lewat rulesets.

### Mengubah jumlah approval (mis. 1 → 2)

**UI:** Settings → Rules → Rulesets → pilih `main` → *Require a pull
request before merging* → *Require approvals* → ubah angka → **Save**.

**`gh` CLI (owner)** — kirim ulang seluruh definisi ruleset (PUT menggantikan isi):

```bash
# contoh: ubah jumlah approval untuk ruleset main (id 24626209)
gh api -X PUT repos/ngahizie/kantin-be/rulesets/24626209 --input ruleset-main.json
# (ubah "required_approving_review_count" di file JSON sebelum mengirim)
```

### Menonaktifkan sementara (mis. darurat)

```bash
gh api -X PUT repos/ngahizie/kantin-be/rulesets/24626209 \
  -f "enforcement=disabled"        # atau "evaluate" untuk uji tanpa memblokir
```

> **Jumlah approval — keputusan #171:** dipakai **1** (keputusan owner,
> 2026-10-13). Penulis PR tak boleh approve PR-nya sendiri, jadi setiap PR butuh
> **1 reviewer lain**. Ini gerbang review yang cukup untuk tim kecil tanpa
> memperlambat merge. Bila ingin diperketat, naikkan ke **2** dengan perintah di
> atas (dan selaraskan `WORKFLOW.md` §1).

### Riwayat mekanisme

Ruleset `main` (id `24626209`, dibuat 2026-10-07) awalnya `disabled` & target
kosong; kini diaktifkan & target diisi. Ruleset `develop` (id `24968833`) yang
dibuat 2026-10-13 **dihapus** bersama branch `develop` (2026-10-14, model main-only).

---

## 4. Verifikasi

```bash
# Paling andal: lihat PR -> BLOCKED + REVIEW_REQUIRED = proteksi aktif
gh pr view <nomor> --repo ngahizie/kantin-be --json mergeStateStatus,reviewDecision

# Flag cepat (true = ada proteksi)
gh api repos/ngahizie/kantin-be/branches/main --jq .protected
```

Uji langsung: push ke `main` dari branch lokal → **ditolak** GitHub.
(Jangan diuji dengan `--force` ke `main` sungguhan.)

---

## 5. Riwayat

| Tanggal | Kejadian |
|---|---|
| 2026-10-06 | Anggota tim mencoba memasang proteksi via API → `404` (role bukan admin). Diteruskan ke owner. |
| 2026-10-06 | Owner membuat ruleset `develop` (id 24553483) tetapi `enforcement=disabled` & target kosong → belum berfungsi. |
| 2026-10-06 | Dokumen ini dibuat agar owner dapat menyelesaikan tanpa konteks tambahan. |
| 2026-10-06 | Owner **mengaktifkan proteksi** (rule klasik). Terbukti: merge PR #5 ke `develop` ditolak *"base branch policy prohibits the merge"*; `--admin` pun ditolak (*"At least 2 approving reviews"*). Owner lalu menembusnya via admin override. |
| 2026-10-06 | Dokumen diperbarui: status → **AKTIF**, + catatan 2 approval & cara verifikasi yang andal. |
| 2026-10-12 | **Verifikasi ulang: proteksi BELUM AKTIF** — `branches/{main}.protected=false`, ruleset `main` `enforcement=disabled`. Status dokumen dikoreksi. Dilacak di [#171](https://github.com/ngahizie/kantin-be/issues/171). |
| 2026-10-13 | Owner (via Zenixu) **mengaktifkan proteksi**: ruleset `main` (24626209) di-`active` + target ref diisi; ruleset `develop` baru dibuat (id `24968833`, `active`). Aturan: PR + approval + status check `Build & Test (JDK 25)` + branch up-to-date + blok force-push/hapus. |
| 2026-10-13 | **Keputusan owner: jumlah approval = 1** (bukan 2) — cukup sebagai gerbang review untuk tim kecil tanpa memperlambat merge. Kedua ruleset diperbarui (`required_approving_review_count=1`). |
| 2026-10-13 | **Verifikasi:** `branches/{main,develop}.protected=true`; uji push langsung → `GH013: Changes must be made through a pull request` (ditolak). Status dokumen → **AKTIF**. #171 selesai. |
| 2026-10-13 | Ditambahkan **bypass owner** (`Zenixu`, mode `pull_request`) — owner boleh merge PR sendiri (penulis tak bisa self-approve), tapi **push langsung tetap diblokir**. Aturan tetap penuh untuk anggota lain. |
| 2026-10-14 | **Model main-only:** branch `develop` **dihapus** & ruleset `develop` (id `24968833`) dihapus. Hanya `main` yang dilindungi. `WORKFLOW.md`, `AGENTS.md`, `ci.yml`, `README.md` diselaraskan. |
| 2026-10-15 | **Repo dipindah ke organisasi `ngahizie`** (`Zenixu/kantin-be` → `ngahizie/kantin-be`). Ruleset `main` (24626209) **terbawa & tetap `active`**, tetapi **`bypass_actors` hilang** (`null`) — kini **tanpa bypass**: semua PR butuh 1 approval. Referensi repo di dokumentasi diselaraskan ke `ngahizie`. |
