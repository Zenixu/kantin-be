# 📋 Plan — Proteksi Branch `main` & `develop`

> **Status: ⚠️ BELUM AKTIF (diverifikasi ulang 2026-10-12).**
> Klaim sebelumnya ("AKTIF sejak 2026-10-06") **tidak lagi benar**. Verifikasi GitHub API
> 2026-10-12: `branches/main.protected=false`, `branches/develop.protected=false`, dan satu-satunya
> ruleset (`main`, id 24626209) ber-`enforcement=disabled`. Re-aktivasi dilacak di
> [#171](https://github.com/Zenixu/kantin-be/issues/171).
>
> Dokumen ini menyimpan **konteks, cara verifikasi, dan riwayat** — berguna untuk
> mengaktifkan/memeriksa ulang proteksi.
>
> Rujukan aturan: [`WORKFLOW.md` §1](./WORKFLOW.md) — *"Tidak boleh push langsung
> ke `main` atau `develop`. Selalu via PR."* Proteksi branch adalah cara GitHub
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
| `git push --force` ke `develop` | commit anggota lain **hilang**, riwayat rusak | ditolak (rule *non-fast-forward*) |
| Branch `develop`/`main` terhapus | hilang, harus restore manual | ditolak (rule *deletion*) |
| Push langsung ke `develop` | semua orang ikut kena | wajib lewat PR → ada review |
| Kode rusak masuk `develop` | lolos bila CI dilewati/tidak wajib | ditangkap saat review PR + status CI |

> ⚠️ Risiko `--force` nyata di tim ini: `WORKFLOW.md` §6 memakai
> `--force-with-lease` di branch fitur — salah target sedikit saja bisa
> mengenai `develop`.

---

## 2. Kondisi saat ini (per 2026-10-12)

**Proteksi BELUM AKTIF.** Bukti verifikasi langsung (2026-10-12):

```bash
gh api repos/Zenixu/kantin-be/branches/main    --jq .protected   # -> false
gh api repos/Zenixu/kantin-be/branches/develop --jq .protected   # -> false
gh api repos/Zenixu/kantin-be/rulesets                            # -> 1 ruleset (main), enforcement=disabled
```

> **Catatan koreksi:** klaim §2 sebelumnya ("proteksi aktif, terbukti PR #5 ditolak")
> **tidak lagi berlaku** — kemungkinan rule klasik yang dulu dipasang sudah dilepas
> atau tidak pernah benar-benar mengikat `main`/`develop`. Endpoint klasik
> `branches/{b}/protection` mengembalikan **404** karena akun kita bukan admin —
> **jangan** dijadikan bukti; pakai `branches/{b}.protected` & `rulesets`.

**Cara paling andal memeriksa:** `branches/{b}.protected` = `true` **atau** ada
ruleset dengan `enforcement=active` & target ref yang cocok.

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
| 2026-10-12 | **Verifikasi ulang: proteksi BELUM AKTIF** — `branches/{main,develop}.protected=false`, ruleset `main` `enforcement=disabled`. Status dokumen dikoreksi. Dilacak di [#171](https://github.com/Zenixu/kantin-be/issues/171). |
