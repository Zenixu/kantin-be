#!/usr/bin/env bash
# ============================================================================
# gen-jwt-dummy.sh — Hasilkan keypair RS256 DUMMY untuk dev/test kantin-be
# ----------------------------------------------------------------------------
# KENAPA ADA: sampai admin-be/mobile-be menyediakan public key RS256 produksi
# (OPEN-QUESTIONS Q1/Q2), kantin-be butuh keypair DUMMY agar pengembangan &
# pengujian bisa jalan tanpa menunggu tim lain.
#
# ⚠️  DUMMY — JANGAN DIPAKAI DI STAGING/PRODUCTION.
#     Private key ditulis ke berkas LOKAL yang di-gitignore. Tidak pernah
#     di-commit (daftar merah AGENTS.md §10).
#
# Pakai:
#   scripts/dev/gen-jwt-dummy.sh            # tulis ke application-local.properties
#   scripts/dev/gen-jwt-dummy.sh --print     # hanya cetak public key (base64)
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DIR="${KANTIN_JWT_DUMMY_DIR:-$ROOT/.dev-jwt}"
PRINT_ONLY=0
[[ "${1:-}" == "--print" ]] && PRINT_ONLY=1

mkdir -p "$DIR"
chmod 700 "$DIR"

# Base64 tanpa newline (format yang diharapkan aplikasi: X.509/PKCS8 DER base64).
b64() { openssl base64 -e -A; }

gen_satu() {
  local nama="$1"          # admin | mobile
  local priv="$DIR/$nama-priv.pem"
  local pub="$DIR/$nama-pub.pem"

  if [[ -f "$priv" ]]; then
    echo "  (ada) $priv — pakai ulang"
  else
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$priv" 2>/dev/null
    chmod 600 "$priv"
    echo "  (baru) $priv"
  fi
  openssl rsa -in "$priv" -pubout -out "$pub" 2>/dev/null

  # base64 DER: PKCS8 (private) & X.509 (public)
  openssl pkcs8 -topk8 -nocrypt -in "$priv" -outform DER 2>/dev/null | b64 > "$DIR/$nama-priv.b64"
  openssl rsa -in "$priv" -pubout -outform DER 2>/dev/null | b64 > "$DIR/$nama-pub.b64"
}

echo "==> Membuat keypair RS256 DUMMY di: $DIR"
gen_satu admin
gen_satu mobile

PUB_ADMIN="$(cat "$DIR/admin-pub.b64")"
PUB_MOBILE="$(cat "$DIR/mobile-pub.b64")"

if [[ "$PRINT_ONLY" == "1" ]]; then
  echo "JWT_ADMIN_PUBLIC_KEY=$PUB_ADMIN"
  echo "JWT_MOBILE_PUBLIC_KEY=$PUB_MOBILE"
  exit 0
fi

TARGET="$ROOT/application-local.properties"
if [[ ! -f "$TARGET" ]]; then
  echo "!!  $TARGET tidak ada. Jalankan dengan --print lalu tempel manual."
  exit 1
fi

# Backup sekali.
cp -n "$TARGET" "$TARGET.bak" 2>/dev/null || true

# Tulis/ganti baris key (portabel: python untuk edit aman).
python3 - "$TARGET" "$PUB_ADMIN" "$PUB_MOBILE" <<'PY'
import sys, re, pathlib
path, pub_admin, pub_mobile = sys.argv[1], sys.argv[2], sys.argv[3]
p = pathlib.Path(path)
lines = p.read_text().splitlines()
out, seen_a, seen_m = [], False, False
for ln in lines:
    if re.match(r'^\s*JWT_ADMIN_PUBLIC_KEY\s*=', ln):
        out.append(f"JWT_ADMIN_PUBLIC_KEY={pub_admin}"); seen_a = True
    elif re.match(r'^\s*JWT_MOBILE_PUBLIC_KEY\s*=', ln):
        out.append(f"JWT_MOBILE_PUBLIC_KEY={pub_mobile}"); seen_m = True
    else:
        out.append(ln)
if not seen_a:
    out.append(f"JWT_ADMIN_PUBLIC_KEY={pub_admin}")
if not seen_m:
    out.append(f"JWT_MOBILE_PUBLIC_KEY={pub_mobile}")
p.write_text("\n".join(out) + "\n")
print(f"  ✔ JWT_ADMIN_PUBLIC_KEY & JWT_MOBILE_PUBLIC_KEY diperbarui di {path}")
PY

cat <<EOF

==> Selesai. Keypair DUMMY:
    $DIR/admin-priv.pem   (private — JANGAN sebar)
    $DIR/mobile-priv.pem  (private — JANGAN sebar)

Berikutnya (mint token uji):
    scripts/dev/mint-jwt-dummy.sh staf  --sekolah 1 --user 42 --role PETUGAS_KANTIN
    scripts/dev/mint-jwt-dummy.sh ortu  --sekolah 1 --siswa 7 --user 100

⚠️  DUMMY — WAJIB ganti ke public key RS256 PRODUKSI sebelum staging/production
    (OPEN-QUESTIONS Q1/Q2). Hapus .dev-jwt/ setelahnya.
EOF
