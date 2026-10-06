# 📋 Plan — Proteksi Branch `main` & `develop`

> **Status: ✅ AKTIF (sejak 2026-10-06).** Owner (`Zenixu`) sudah mengaktifkan
> proteksi. Dokumen ini menyimpan **konteks, cara verifikasi, dan riwayat** —
> berguna bila proteksi perlu diubah/diperiksa ulang.
>
> Rujukan aturan: [`WORKFLOW.md` §1](./WORKFLOW.md) — *"Tidak boleh push langsung
> ke `main` atau `develop`. Selalu via PR."* Proteksi branch adalah cara GitHub
> **menegakkan** aturan itu, bukan sekadar kesepakatan lisan.

---

## 1. Kenapa ini penting (khusus repo ini)

Repo ini **tidak punya CI** (`.github/workflows/` kosong) — tidak ada robot yang
memverifikasi PR. Jadi **PR + review adalah satu-satunya gerbang mutu**. Tanpa
proteksi, tidak ada gerbang sama sekali.

Risiko nyata yang dicegah:

| Kejadian | Tanpa proteksi | Dengan proteksi |
|---|---|---|
| `git push --force` ke `develop` | commit anggota lain **hilang**, riwayat rusak | ditolak (rule *non-fast-forward*) |
| Branch `develop`/`main` terhapus | hilang, harus restore manual | ditolak (rule *deletion*) |
| Push langsung ke `develop` | semua orang ikut kena | wajib lewat PR → ada review |
| Kode rusak masuk `develop` | tidak ada CI yang menangkap | tertangkap saat review PR |

> ⚠️ Risiko `--force` nyata di tim ini: `WORKFLOW.md` §6 memakai
> `--force-with-lease` di branch fitur — salah target sedikit saja bisa
> mengenai `develop`.

---

## 2. Kondisi saat ini (per 2026-10-06)

**Proteksi SUDAH AKTIF.** Bukti yang bisa diverifikasi ulang:

- `gh pr merge` ke `develop` **ditolak**: *"the base branch policy prohibits the merge"*.
- `gh pr merge --admin` juga **ditolak**: *"At least 2 approving reviews are required by reviewers with write access."*
- PR tanpa review → `mergeStateStatus: BLOCKED`, `reviewDecision: REVIEW_REQUIRED`.

> ⚠️ **Jumlah approval = 2.** Cukup berat untuk tim kecil: penulis PR **tidak boleh**
> meng-approve PR-nya sendiri, jadi butuh **2 orang lain** hadir. Bila terasa
> menghambat, pertimbangkan turunkan ke **1** (lihat §3).

**Catatan penting soal cara verifikasi (sempat menyesatkan):**

| Cara cek | Hasil | Akurasi |
|---|---|---|
| `branches/{b}.protected` | `true` | ✅ menandakan ada proteksi |
| `rules/branches/{b}` (rulesets) | `[]` | ⚠️ **kosong walau proteksi aktif** |
| `rulesets` | 1 ruleset `develop`, `enforcement=disabled` | ⚠️ **menyesatkan** |
| `gh pr merge` (praktik nyata) | ditolak | ✅ **paling andal** |

Artinya: proteksi di repo ini tampaknya memakai **branch protection rule klasik**
(bukan rulesets — ruleset `develop` yang lama masih `disabled` dan boleh
diabaikan/dihapus). Endpoint klasik `branches/{b}/protection` mengembalikan
**404** karena akun kita **bukan admin** — itu **bukan** tanda proteksi tidak ada.

**Cara paling andal memeriksa:** buat PR ke `develop`, lalu lihat
`gh pr view <n> --json mergeStateStatus,reviewDecision`. `BLOCKED` +
`REVIEW_REQUIRED` = proteksi bekerja.

```bash
gh pr view <nomor> --repo Zenixu/kantin-be --json mergeStateStatus,reviewDecision
# BLOCKED + REVIEW_REQUIRED -> proteksi aktif & menunggu review
```

---

## 3. Bila perlu mengubah setelan (owner saja)

### Mengubah jumlah approval (mis. 2 → 1)

**UI:** Settings → Branches → (edit rule `develop`/`main`) → *Require a pull request
before merging* → *Require approvals* → ubah angkanya → **Save**.

**`gh` CLI (owner):**

```bash
gh api -X PATCH repos/Zenixu/kantin-be/branches/develop/protection/required_pull_request_reviews \
  -F "required_approving_review_count=1"
```

### Bila ingin memakai rulesets (mekanisme baru) sebagai ganti rule klasik

Ruleset lama (`id 24553483`) saat ini `disabled` dan bisa **diabaikan atau dihapus**.
Bila owner mau pindah ke rulesets, pastikan **enforcement=active** dan
**target ref diisi** — dua hal inilah yang membuat ruleset lama tidak berfungsi:

```bash
gh api -X PUT repos/Zenixu/kantin-be/rulesets/24553483 \
  -f "name=develop" -f "target=branch" -f "enforcement=active" \
  -f "conditions[ref_name][include][]=refs/heads/develop" \
  -f "rules[][type]=deletion" \
  -f "rules[][type]=non_fast_forward" \
  -f "rules[][type]=pull_request" \
  -F "rules[][parameters][required_approving_review_count]=1"
```

> **Jumlah approval:** `1` sudah cukup sebagai gerbang review untuk tim kecil.
> `2` rawan *deadlock* (penulis tak boleh approve PR sendiri).

---

## 4. Verifikasi

```bash
# Paling andal: lihat PR -> BLOCKED + REVIEW_REQUIRED = proteksi aktif
gh pr view <nomor> --repo Zenixu/kantin-be --json mergeStateStatus,reviewDecision

# Flag cepat (true = ada proteksi)
gh api repos/Zenixu/kantin-be/branches/develop --jq .protected
gh api repos/Zenixu/kantin-be/branches/main    --jq .protected
```

Uji langsung: push ke `develop` dari branch lokal → **ditolak** GitHub.
(Jangan diuji dengan `--force` ke `develop` sungguhan.)

---

## 5. Riwayat

| Tanggal | Kejadian |
|---|---|
| 2026-10-06 | Anggota tim mencoba memasang proteksi via API → `404` (role bukan admin). Diteruskan ke owner. |
| 2026-10-06 | Owner membuat ruleset `develop` (id 24553483) tetapi `enforcement=disabled` & target kosong → belum berfungsi. |
| 2026-10-06 | Dokumen ini dibuat agar owner dapat menyelesaikan tanpa konteks tambahan. |
| 2026-10-06 | Owner **mengaktifkan proteksi** (rule klasik). Terbukti: merge PR #5 ke `develop` ditolak *"base branch policy prohibits the merge"*; `--admin` pun ditolak (*"At least 2 approving reviews"*). Owner lalu menembusnya via admin override. |
| 2026-10-06 | Dokumen diperbarui: status → **AKTIF**, + catatan 2 approval & cara verifikasi yang andal. |
