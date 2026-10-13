# 📋 Plan — Proteksi Branch `main` & `develop`

> **Status: ✅ AKTIF (diaktifkan & diverifikasi 2026-10-13).**
> Proteksi ditegakkan lewat **ruleset** GitHub: `main` (id `24626209`) & `develop`
> (id `24968833`), keduanya `enforcement=active` dan target ref terisi. Verifikasi
> 2026-10-13: `branches/main.protected=true`, `branches/develop.protected=true`,
> push langsung ditolak `GH013: Changes must be made through a pull request`.
> Dilacak & ditutup di [#171](https://github.com/Zenixu/kantin-be/issues/171).
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

## 2. Kondisi saat ini (per 2026-10-13)

**Proteksi AKTIF.** Bukti verifikasi langsung (2026-10-13):

```bash
gh api repos/Zenixu/kantin-be/branches/main    --jq .protected   # -> true
gh api repos/Zenixu/kantin-be/branches/develop --jq .protected   # -> true
gh api repos/Zenixu/kantin-be/rulesets --jq '.[] | "\(.name) \(.enforcement)"'
#   main active
#   develop active
```

Uji push langsung (ditolak oleh ruleset):

```
remote: error: GH013: Repository rule violations found for refs/heads/main.
remote: - Changes must be made through a pull request.
remote: - Required status check "Build & Test (JDK 25)" is expected.
 ! [remote rejected] ... (push declined due to repository rule violations)
```

**Setelan yang ditegakkan** (identik untuk `main` & `develop`):

| Aturan | Nilai |
|---|---|
| Wajib PR sebelum merge | ya |
| Jumlah approval | **2** |
| Dismiss review basi saat push baru | ya |
| Wajib resolusi komentar review | ya |
| Status check wajib | `Build & Test (JDK 25)` |
| Branch harus up-to-date (`strict`) | ya |
| Force-push | ditolak (`non_fast_forward`) |
| Hapus branch | ditolak (`deletion`) |
| Bypass | **tidak ada** (admin pun ikut aturan) |

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

### Mengubah jumlah approval (mis. 2 → 1)

**UI:** Settings → Rules → Rulesets → pilih `main` / `develop` → *Require a pull
request before merging* → *Require approvals* → ubah angka → **Save**.

**`gh` CLI (owner)** — kirim ulang seluruh definisi ruleset (PUT menggantikan isi):

```bash
# contoh: turunkan jadi 1 approval untuk ruleset develop (id 24968833)
gh api -X PUT repos/Zenixu/kantin-be/rulesets/24968833 --input ruleset-develop.json
# (ubah "required_approving_review_count" di file JSON sebelum mengirim)
```

### Menonaktifkan sementara (mis. darurat)

```bash
gh api -X PUT repos/Zenixu/kantin-be/rulesets/24626209 \
  -f "enforcement=disabled"        # atau "evaluate" untuk uji tanpa memblokir
```

> **Jumlah approval — keputusan #171:** dipakai **2** (sesuai `WORKFLOW.md` §1 &
> permintaan issue). **Konsekuensi:** penulis PR tak boleh approve PR-nya sendiri,
> jadi setiap PR butuh **2 reviewer berbeda**. Tim saat ini punya **5 akun
> non-owner** dengan akses `push` (`Derylfabiensyah`, `M-Arkan-Raihan-Nugraha`,
> `Panca-Satia`, `Mayoranz`, `wibisanabama`) → memadai. Bila kelak terasa
> menghambat, turunkan ke **1** dengan perintah di atas (dan selaraskan
> `WORKFLOW.md` §1).

### Riwayat mekanisme

Ruleset lama `develop` (id `24553483`, dibuat 2026-10-06) ber-`disabled` & target
kosong; ruleset `main` (id `24626209`, dibuat 2026-10-07) juga `disabled` & target
kosong. Keduanya kini digantikan/ diperbaiki: `main` (24626209) diaktifkan &
target diisi, dan `develop` dibuat baru (id `24968833`).

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
| 2026-10-13 | Owner (via Zenixu) **mengaktifkan proteksi**: ruleset `main` (24626209) di-`active` + target ref diisi; ruleset `develop` baru dibuat (id `24968833`, `active`). Aturan: PR + **2 approval** + status check `Build & Test (JDK 25)` + branch up-to-date + blok force-push/hapus. |
| 2026-10-13 | **Verifikasi:** `branches/{main,develop}.protected=true`; uji push langsung → `GH013: Changes must be made through a pull request` (ditolak). Status dokumen → **AKTIF**. #171 selesai. |
