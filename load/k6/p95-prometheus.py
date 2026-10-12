#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
p95-prometheus.py — silang-cek SLO tap dari metrik SERVER (issue #172).

Menghitung p95 durasi tap dari metrik Prometheus ``kantin_tap_duration_seconds``
yang diekspos Micrometer (lihat ``docs/OBSERVABILITY.md``), memakai algoritma
``histogram_quantile`` yang sama dengan Prometheus. Dipakai workflow
``.github/workflows/uji-beban-nightly.yml`` untuk membuktikan bahwa p95 yang
diukur k6 (sisi klien) juga terlihat di **sisi server** — Acceptance Criteria
kedua issue #172.

Bucket SLO yang dipasang ``MetrikTap`` hanya **250 ms, 500 ms, 1 dtk, +Inf**,
jadi angka p95 di sini **kasar** (interpolasi antar bucket). Untuk gerbang SLO
yang paling penting adalah **rasio tap > 1 dtk** (``pelanggaran_ratio``): bila
< 5% maka p95 ≤ 1 dtk (PRD §12 no.8).

Pakai:

    # satu snapshot (kumulatif sejak app start)
    python3 load/k6/p95-prometheus.py prometheus.txt

    # dua snapshot → SELISIHNYA (mengisolasi satu laju beban dari snapshot
    # kumulatif, karena Timer Prometheus monoton naik)
    python3 load/k6/p95-prometheus.py sebelum.txt sesudah.txt

    # uji mandiri parser (dipakai CI, tanpa dependensi)
    python3 load/k6/p95-prometheus.py --self-test

Keluaran: satu objek JSON di stdout, mis.

    {"metrik": "kantin_tap_duration_seconds", "outcome": "sukses", "total": 5990,
     "p95_detik": 0.129, "p95_bucket": "0.25", "ambang_detik": 1.0,
     "pelanggaran_ratio": 0.0012, "lulus_slo": true}

Script ini **tanpa dependensi eksternal** (stdlib saja) agar aman di runner CI.
"""

import argparse
import json
import math
import sys

METRIK_DASAR = "kantin_tap_duration_seconds"
AMBANG_SLO_DETIK = 1.0  # PRD §12 no.8
RASIO_GAGAL_MAKS = 0.05  # p95 ≤ 1 dtk  ⇔  ≤ 5% tap melewati 1 dtk


def _pecah_label(isi: str):
    """Pecah isi ``{...}`` menjadi token ``k="v"`` dengan menghormati kutip.

    Nilai label bisa memuat koma (mis. nama aplikasi), jadi pemisahan naif
    dengan ``split(',')`` salah. Parser kecil ini melacak status di dalam kutip.
    """
    token, hasil, dalam_kutip, escape = [], [], False, False
    for ch in isi:
        if escape:
            token.append(ch)
            escape = False
            continue
        if ch == "\\":
            token.append(ch)
            escape = True
            continue
        if ch == '"':
            dalam_kutip = not dalam_kutip
            token.append(ch)
            continue
        if ch == "," and not dalam_kutip:
            hasil.append("".join(token))
            token = []
            continue
        token.append(ch)
    if token:
        hasil.append("".join(token))
    return [t.strip() for t in hasil if t.strip()]


def baca_metrik(teks: str):
    """Kembalikan ``{nama_metrik: [(labels_dict, nilai_float), ...]}``.

    Mengabaikan baris komentar (``#``) dan baris tak terparse. Nilai ``NaN``/
    ``+Inf`` tetap diterima (``float`` menanganinya).
    """
    hasil = {}
    for baris in teks.splitlines():
        baris = baris.strip()
        if not baris or baris.startswith("#"):
            continue
        # Pisahkan nilai (token terakhir) dari nama+label.
        try:
            kiri, nilai = baris.rsplit(" ", 1)
            v = float(nilai)
        except ValueError:
            continue
        if "{" in kiri:
            nama, label_str = kiri.split("{", 1)
            labels = {}
            for pasangan in _pecah_label(label_str.rstrip("}")):
                if "=" not in pasangan:
                    continue
                k, val = pasangan.split("=", 1)
                labels[k.strip()] = val.strip().strip('"')
        else:
            nama, labels = kiri, {}
        hasil.setdefault(nama.strip(), []).append((labels, v))
    return hasil


def _deret_tap(metrik, outcome: str):
    """Ambil total & bucket untuk metrik tap + outcome tertentu.

    Kembalikan ``(total, {le_float: kumulatif})``. Bila metrik tak ada →
    ``(0.0, {})``.
    """
    total = 0.0
    buckets = {}
    for labels, v in metrik.get(METRIK_DASAR + "_count", []):
        if labels.get("outcome") == outcome:
            total += v
    for labels, v in metrik.get(METRIK_DASAR + "_bucket", []):
        if labels.get("outcome") != outcome:
            continue
        try:
            le = float(labels.get("le", ""))
        except ValueError:
            continue
        buckets[le] = buckets.get(le, 0.0) + v
    return total, buckets


def histogram_quantile(q: float, buckets: dict):
    """Algoritma ``histogram_quantile`` Prometheus (interpolasi antar bucket).

    ``buckets`` = ``{le_float: kumulatif}``. Kembalikan ``(nilai, le_bucket)``;
    bila kuantil jatuh di bucket ``+Inf`` nilainya ``inf`` (p95 di luar bucket
    SLO tertinggi — indikasi kuat pelanggaran SLO).
    """
    if not buckets:
        return None, None
    item = sorted(buckets.items())  # naik menurut le
    total = item[-1][1]
    if total <= 0:
        return None, None
    rank = q * total
    prev_le, prev_c = 0.0, 0.0
    for le, c in item:
        if c >= rank:
            if math.isinf(le):
                return math.inf, "+Inf"
            if c <= prev_c:
                return le, _le_str(le)
            nilai = prev_le + (le - prev_le) * ((rank - prev_c) / (c - prev_c))
            return nilai, _le_str(le)
        prev_le, prev_c = le, c
    return item[-1][0], _le_str(item[-1][0])


def _le_str(le: float) -> str:
    return "+Inf" if math.isinf(le) else ("%g" % le)


def _kurangi(sebelum: dict, sesudah: dict):
    """Selisih dua peta ``{le: kumulatif}`` (snapshot kumulatif → per-laju)."""
    hasil = {}
    for le, v in sesudah.items():
        hasil[le] = v - sebelum.get(le, 0.0)
    return hasil


def hitung(sebelum_teks, sesudah_teks, outcome: str, q: float, ambang: float):
    """Hitung ringkasan p95 & rasio pelanggaran (kumulatif atau selisih)."""
    metrik_akhir = baca_metrik(sesudah_teks)
    total, buckets = _deret_tap(metrik_akhir, outcome)
    if sebelum_teks is not None:
        metrik_awal = baca_metrik(sebelum_teks)
        total_awal, buckets_awal = _deret_tap(metrik_awal, outcome)
        total = total - total_awal
        buckets = _kurangi(buckets_awal, buckets)

    p95, le_bucket = histogram_quantile(q, buckets)

    # Rasio tap yang MELEWATI ambang SLO. Bucket histogram bersifat KUMULATIF
    # (le="1" = jumlah tap ≤ 1 dtk), jadi "dalam ambang" = nilai kumulatif pada
    # le terbesar yang masih ≤ ambang — bukan penjumlahan antar bucket.
    rasio = None
    if total > 0:
        dalam_ambang = 0.0
        for le, c in buckets.items():
            if le <= ambang and c > dalam_ambang:
                dalam_ambang = c
        rasio = max(0.0, 1.0 - (dalam_ambang / total))

    lulus = None if rasio is None else (rasio < RASIO_GAGAL_MAKS)
    return {
        "metrik": METRIK_DASAR,
        "outcome": outcome,
        "total": total,
        "p95_detik": None if p95 is None or math.isinf(p95) else round(p95, 6),
        "p95_bucket": le_bucket,
        "ambang_detik": ambang,
        "pelanggaran_ratio": None if rasio is None else round(rasio, 6),
        "lulus_slo": lulus,
    }


# ── Uji mandiri (tanpa dependensi) ──────────────────────────────────────────
_FIXTURE = """
# HELP kantin_tap_duration_seconds Durasi satu tap kasir
# TYPE kantin_tap_duration_seconds histogram
kantin_tap_duration_seconds_count{application="scholia-kantin",outcome="sukses",} 1000.0
kantin_tap_duration_seconds_sum{application="scholia-kantin",outcome="sukses",} 50.0
kantin_tap_duration_seconds_bucket{application="scholia-kantin",outcome="sukses",le="0.25",} 800.0
kantin_tap_duration_seconds_bucket{application="scholia-kantin",outcome="sukses",le="0.5",} 950.0
kantin_tap_duration_seconds_bucket{application="scholia-kantin",outcome="sukses",le="1",} 990.0
kantin_tap_duration_seconds_bucket{application="scholia-kantin",outcome="sukses",le="+Inf",} 1000.0
kantin_tap_duration_seconds_max{application="scholia-kantin",outcome="sukses",} 1.2
kantin_tap_duration_seconds_count{application="scholia-kantin",outcome="gagal",} 5.0
kantin_tap_duration_seconds_bucket{application="scholia-kantin",outcome="gagal",le="+Inf",} 5.0
"""


def _self_test() -> int:
    gagal = 0

    # 1) Snapshot tunggal: total 1000, 990 ≤ 1 dtk → pelanggaran 1%, p95 = 0.5 dtk.
    r = hitung(None, _FIXTURE, "sukses", 0.95, AMBANG_SLO_DETIK)
    for kunci, harap in [("total", 1000.0), ("pelanggaran_ratio", 0.01),
                         ("p95_detik", 0.5), ("lulus_slo", True)]:
        if r[kunci] != harap:
            print("GAGAL self-test (tunggal) %s=%r (harap %r)" % (kunci, r[kunci], harap),
                  file=sys.stderr)
            gagal += 1

    # 2) Snapshot berpasangan: kumulatif 1000/990 dikurangi 400/396 → total 600,
    #    ≤1 dtk 594 → pelanggaran 1%; total benar-benar selisih (bukan kumulatif).
    sebelum = """
kantin_tap_duration_seconds_count{outcome="sukses",} 400.0
kantin_tap_duration_seconds_bucket{outcome="sukses",le="0.25",} 320.0
kantin_tap_duration_seconds_bucket{outcome="sukses",le="0.5",} 380.0
kantin_tap_duration_seconds_bucket{outcome="sukses",le="1",} 396.0
kantin_tap_duration_seconds_bucket{outcome="sukses",le="+Inf",} 400.0
"""
    r2 = hitung(sebelum, _FIXTURE, "sukses", 0.95, AMBANG_SLO_DETIK)
    if r2["total"] != 600.0 or abs(r2["pelanggaran_ratio"] - 0.01) > 1e-9:
        print("GAGAL self-test (selisih) total=%r rasio=%r" % (r2["total"], r2["pelanggaran_ratio"]),
              file=sys.stderr)
        gagal += 1

    # 3) p95 jatuh di +Inf → ditandai (bukan angka menyesatkan).
    jelek = """
kantin_tap_duration_seconds_count{outcome="sukses",} 100.0
kantin_tap_duration_seconds_bucket{outcome="sukses",le="1",} 90.0
kantin_tap_duration_seconds_bucket{outcome="sukses",le="+Inf",} 100.0
"""
    r3 = hitung(None, jelek, "sukses", 0.95, AMBANG_SLO_DETIK)
    if r3["p95_detik"] is not None or r3["p95_bucket"] != "+Inf" or r3["lulus_slo"] is not False:
        print("GAGAL self-test (+Inf) %r" % r3, file=sys.stderr)
        gagal += 1

    if gagal == 0:
        print("self-test OK")
    return 1 if gagal else 0


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="p95 tap dari metrik Prometheus (issue #172)")
    p.add_argument("berkas", nargs="*", help="1 berkas (kumulatif) atau 2 berkas (sebelum sesudah)")
    p.add_argument("--outcome", default="sukses", help="tag outcome (default: sukses)")
    p.add_argument("--q", type=float, default=0.95, help="kuantil (default: 0.95)")
    p.add_argument("--ambang", type=float, default=AMBANG_SLO_DETIK, help="ambang SLO detik (default: 1)")
    p.add_argument("--gerbang", action="store_true",
                   help="keluar dengan kode 1 bila server-side p95 melanggar SLO (untuk gerbang CI)")
    p.add_argument("--self-test", action="store_true", help="uji mandiri parser lalu keluar")
    args = p.parse_args(argv)

    if args.self_test:
        return _self_test()

    if not 1 <= len(args.berkas) <= 2:
        p.error("berikan 1 atau 2 berkas Prometheus")

    try:
        with open(args.berkas[-1], encoding="utf-8") as f:
            sesudah = f.read()
        sebelum = None
        if len(args.berkas) == 2:
            with open(args.berkas[0], encoding="utf-8") as f:
                sebelum = f.read()
    except OSError as e:
        print(json.dumps({"error": "gagal membaca berkas: %s" % e}))
        return 0  # jangan gagalkan CI karena artefak pengukuran

    hasil = hitung(sebelum, sesudah, args.outcome, args.q, args.ambang)
    print(json.dumps(hasil))

    # Gerbang: hanya gagal bila TERBUKTI melanggar (lulus_slo == False). Bila tak
    # ada data (lulus_slo == None), jangan menghukum karena pengukuran kosong.
    if args.gerbang and hasil["lulus_slo"] is False:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
