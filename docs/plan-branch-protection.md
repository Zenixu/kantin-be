# 📋 Plan — Aktifkan Proteksi Branch `main` & `develop`

> **Status: ⏳ MENUNGGU OWNER.** Dokumen ini untuk **owner repo** (`Zenixu`)
> melanjutkan pekerjaan yang belum selesai. Cukup ikuti langkah di §3.
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

Owner sudah **mulai** membuat proteksi lewat jalur **rulesets** (mekanisme baru
GitHub), tetapi **belum aktif** — masih ada 3 masalah:

| Pemeriksaan | Hasil | Artinya |
|---|---|---|
| Ruleset `develop` ada? | ✅ ada (`id 24553483`) | sudah dibuat |
| `enforcement` | ❌ `disabled` | aturan **tidak ditegakkan** |
| `conditions.ref_name.include` | ❌ `[]` (kosong) | **tidak menyasar branch apa pun** |
| Rules efektif di `develop` | `[]` | tidak ada aturan berlaku |
| Ruleset untuk `main` | ❌ belum ada | `main` sama sekali terbuka |

> Catatan: API `branches/{branch}.protected` melaporkan `true`, tetapi itu
> **menyesatkan** — rules yang benar-benar berlaku masih kosong. Cek yang benar:
> `GET /repos/{owner}/{repo}/rules/branches/{branch}`.

Cara memeriksa ulang (butuh login `gh`):

```bash
gh api repos/Zenixu/kantin-be/rules/branches/develop   # harus TIDAK kosong []
gh api repos/Zenixu/kantin-be/rules/branches/main      # harus TIDAK kosong []
gh api repos/Zenixu/kantin-be/rulesets \
  --jq '.[] | {id, name, enforcement, include: .conditions.ref_name.include}'
```

---

## 3. Langkah penyelesaian (pilih salah satu)

### Opsi A — lewat UI GitHub (paling mudah)

1. Buka **Settings → Rules → Rulesets**:
   `https://github.com/Zenixu/kantin-be/rules/24553483`
2. Pada ruleset **"develop"**:
   - **Enforcement status**: *Disabled* → **Active**
   - **Target branches → Add target → Include by pattern** → isi `develop`
   - **Save**
3. Klik **New ruleset → New branch ruleset** untuk **`main`**:
   - Enforcement: **Active**
   - Target: pola `main`
   - Centang **Require a pull request before merging**
   - **Create**

### Opsi B — lewat `gh` CLI (butuh role admin/owner)

```bash
# 1) Aktifkan + pasang target pada ruleset "develop" yang sudah ada
gh api -X PUT repos/Zenixu/kantin-be/rulesets/24553483 \
  -f "name=develop" -f "target=branch" -f "enforcement=active" \
  -f "conditions[ref_name][include][]=refs/heads/develop" \
  -f "rules[][type]=deletion" \
  -f "rules[][type]=non_fast_forward" \
  -f "rules[][type]=pull_request" \
  -F "rules[][parameters][required_approving_review_count]=1" \
  -F "rules[][parameters][dismiss_stale_reviews_on_push]=true" \
  -F "rules[][parameters][required_review_thread_resolution]=true" \
  -F "rules[][parameters][allowed_merge_methods][]=merge" \
  -F "rules[][parameters][allowed_merge_methods][]=squash" \
  -F "rules[][parameters][allowed_merge_methods][]=rebase"

# 2) Buat ruleset untuk "main"
gh api -X POST repos/Zenixu/kantin-be/rulesets \
  -f "name=main" -f "target=branch" -f "enforcement=active" \
  -f "conditions[ref_name][include][]=refs/heads/main" \
  -f "rules[][type]=deletion" \
  -f "rules[][type]=non_fast_forward" \
  -f "rules[][type]=pull_request" \
  -F "rules[][parameters][required_approving_review_count]=1"
```

> **Jumlah approval: gunakan `1`, bukan `2`.** Tim kecil (2–3 orang) → penulis PR
> tidak boleh meng-approve PR-nya sendiri, jadi `2` approval rawan *deadlock*
> (butuh 2 orang lain hadir bersamaan). `1` sudah cukup sebagai gerbang review.
> (Ruleset awal owner disetel `2` — pertimbangkan turunkan ke `1`.)

---

## 4. Verifikasi setelah selesai

```bash
gh api repos/Zenixu/kantin-be/rules/branches/develop   # harus ada rules (pull_request, deletion, non_fast_forward)
gh api repos/Zenixu/kantin-be/rules/branches/main      # idem
```

Uji cepat: coba `git push` langsung ke `develop` dari branch lokal → harus
**ditolak** GitHub. (Jangan diuji dengan `--force` ke `develop` sungguhan.)

---

## 5. Riwayat

| Tanggal | Kejadian |
|---|---|
| 2026-10-06 | Anggota tim mencoba memasang proteksi via API → `404` (role bukan admin). Diteruskan ke owner. |
| 2026-10-06 | Owner membuat ruleset `develop` (id 24553483) tetapi `enforcement=disabled` & target kosong → **belum aktif**. |
| 2026-10-06 | Dokumen ini dibuat agar owner dapat menyelesaikan tanpa perlu konteks tambahan. |
