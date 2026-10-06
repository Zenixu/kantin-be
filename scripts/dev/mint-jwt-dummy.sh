#!/usr/bin/env bash
# ============================================================================
# mint-jwt-dummy.sh — Buat token JWT DUMMY (RS256) untuk dev/test kantin-be
# ----------------------------------------------------------------------------
# Token ditandatangani dengan private key DUMMY dari gen-jwt-dummy.sh, sehingga
# LULUS verifikasi asli kantin-be (RS256 + public key). Ini BUKAN bypass auth —
# jalur verifikasi produksi tetap utuh (PRD §11.10, AGENTS.md §10).
#
# Pakai:
#   scripts/dev/mint-jwt-dummy.sh staf --sekolah 1 --user 42 --role PETUGAS_KANTIN
#   scripts/dev/mint-jwt-dummy.sh ortu --sekolah 1 --siswa 7 --user 100
#
# Opsi umum:
#   --ttl 3600        umur token (detik)
#   --iss <issuer>    set klaim 'iss' (default: TIDAK diset, meniru admin-be)
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DIR="${KANTIN_JWT_DUMMY_DIR:-$ROOT/.dev-jwt}"

JENIS="${1:-}"; shift || true
if [[ "$JENIS" != "staf" && "$JENIS" != "ortu" ]]; then
  echo "Pakai: $0 {staf|ortu} [--sekolah N] [--user N] [--role R] [--siswa N] [--ttl DETIK] [--iss ISSUER]" >&2
  exit 2
fi

SEKOLAH="1"; USER=""; ROLE=""; SISWA=""; TTL="3600"; ISS=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --sekolah) SEKOLAH="$2"; shift 2;;
    --user)    USER="$2";    shift 2;;
    --role)    ROLE="$2";    shift 2;;
    --siswa)   SISWA="$2";   shift 2;;
    --ttl)     TTL="$2";     shift 2;;
    --iss)     ISS="$2";     shift 2;;
    *) echo "Opsi tak dikenal: $1" >&2; exit 2;;
  esac
done

if [[ "$JENIS" == "staf" ]]; then
  KEY="$DIR/admin-priv.pem"; DEFAULT_ISS="skoolia-admin"
  USER="${USER:-42}"; ROLE="${ROLE:-PETUGAS_KANTIN}"
else
  KEY="$DIR/mobile-priv.pem"; DEFAULT_ISS="skoolia-mobile"
  USER="${USER:-100}"; SISWA="${SISWA:-7}"
fi

if [[ ! -f "$KEY" ]]; then
  echo "!!  Private key DUMMY tidak ada: $KEY" >&2
  echo "    Jalankan dulu: scripts/dev/gen-jwt-dummy.sh" >&2
  exit 1
fi

# Bangun JWT: header.payload + tanda tangan RS256 via openssl (tanpa pyjwt).
python3 - "$JENIS" "$SEKOLAH" "$USER" "$ROLE" "$SISWA" "$TTL" "$ISS" "$KEY" <<'PY'
import sys, json, time, base64, subprocess, tempfile, os

jenis, sekolah, user, role, siswa, ttl, iss, keypath = sys.argv[1:9]
ttl = int(ttl); sekolah = int(sekolah); user = int(user)

def b64url(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()

now = int(time.time())
header = {"alg": "RS256", "typ": "JWT"}
payload = {
    "sub": "petugas01" if jenis == "staf" else "ortu01",
    "typ": "access",
    "user_id": user,
    "iat": now,
    "exp": now + ttl,
    "jti": base64.urlsafe_b64encode(os.urandom(12)).rstrip(b"=").decode(),
}
if jenis == "staf":
    payload["nama"] = "Petugas Dummy"
    payload["role"] = role
    payload["sekolah_id"] = sekolah
else:
    payload["nama"] = "Orang Tua Dummy"
    payload["role"] = "ORANG_TUA"
    payload["sekolah_id"] = sekolah
    payload["siswa_id"] = int(siswa)
if iss:
    payload["iss"] = iss

signing_input = f"{b64url(json.dumps(header,separators=(',',':')).encode())}." \
                f"{b64url(json.dumps(payload,separators=(',',':')).encode())}"

with tempfile.NamedTemporaryFile("wb", delete=False) as f:
    f.write(signing_input.encode()); inp = f.name
try:
    sig = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", keypath, inp],
        check=True, capture_output=True).stdout
finally:
    os.unlink(inp)

print(f"{signing_input}.{b64url(sig)}")
PY
