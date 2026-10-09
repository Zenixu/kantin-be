// ============================================================================
// tap-slo.js — Uji beban endpoint tap kasir (issue #145)
// ----------------------------------------------------------------------------
// Tujuan: memverifikasi SLO PRD §12 no.8 — "Waktu respons tap (p95) <= 1 detik
// pada jam istirahat, termasuk lookup kartu ke data SKOOLIA".
//
// Skenario: N kasir tap BERSAMAAN (constant-arrival-rate, mis. 50–100 tap/detik),
// memakai PostgreSQL & Redis NYATA, menembus jalur SUKSES (bukan jalur error),
// sehingga latensi yang diukur adalah latensi jalur produksi.
//
// Pakai (lihat docs/uji-beban-tap.md untuk langkah lengkap):
//   k6 run load/k6/tap-slo.js
//   TAP_RATE=100 DURATION=3m k6 run load/k6/tap-slo.js
//
// Ambang REGRESI (thresholds) di bawah = gerbang CI: build GAGAL bila p95 > 1 dtk
// atau rasio gagal > 1%. Ubah lewat env untuk eksperimen.
//
// ⚠️  HANYA untuk lingkungan uji. Menjalankan ini di produksi = membanjiri kasir.
// ============================================================================

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import exec from 'k6/execution';

// ── Konfigurasi (override via env) ──────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';
const SEKOLAH = Number(__ENV.SEKOLAH || 1);
const TITIK_KASIR = Number(__ENV.TITIK_KASIR || 1);
const MENU_ID = Number(__ENV.MENU_ID || 1);
const QTY = Number(__ENV.QTY || 1);

// Beban: constant-arrival-rate → laju tap/detik tetap (mensimulasikan puncak).
const TAP_RATE = Number(__ENV.TAP_RATE || 50);   // tap/detik (50–100 = jam istirahat)
const DURATION = __ENV.DURATION || '2m';
const PRE_VUS = Number(__ENV.PRE_VUS || Math.max(20, TAP_RATE));
const MAX_VUS = Number(__ENV.MAX_VUS || TAP_RATE * 4);

// Ambang SLO & regresi.
const P95_MS = Number(__ENV.P95_MS || 1000);      // SLO PRD §12 no.8
const MAX_FAIL_RATE = Number(__ENV.MAX_FAIL_RATE || 0.01); // <= 1% gagal

// Jumlah kartu uji yang di-seed (load/seed/seed-loadtest.sql → 20).
const KARTU_COUNT = Number(__ENV.KARTU_COUNT || 20);

// Token opsional; bila kosong, skrip login via shim dev (butuh dev-login aktif).
const TOKEN_ENV = __ENV.TOKEN || '';

// ── Metrik kustom ───────────────────────────────────────────────────────────
const tapMs = new Trend('tap_ms', true);              // durasi tap (ms)
const tapOk = new Rate('tap_ok');                     // rasio tap sukses
const tapDitahanRate = new Rate('tap_rate_limited');  // rasio 429 (rate limit)

export const options = {
  scenarios: {
    tap_bersamaan: {
      executor: 'constant-arrival-rate',
      rate: TAP_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    // GERBANG SLO: p95 tap <= 1 dtk (PRD §12 no.8).
    'tap_ms': [`p(95)<${P95_MS}`],
    // Regresi: hampir semua tap harus sukses.
    'tap_ok': [`rate>${(1 - MAX_FAIL_RATE).toFixed(4)}`],
    // Jangan biarkan error HTTP mendominasi (mis. 500).
    'http_req_failed': [`rate<${MAX_FAIL_RATE}`],
  },
  // Ringkas: tanpa ini output terlalu ramai saat laju tinggi.
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// ── setup(): siapkan token sekali untuk semua VU ────────────────────────────
export function setup() {
  // Pastikan service hidup.
  const health = http.get(`${BASE_URL}/actuator/health`);
  if (health.status !== 200) {
    exec.test.abort(`kantin-be tidak sehat di ${BASE_URL} (status ${health.status}). Jalankan app dulu.`);
  }

  let token = TOKEN_ENV;
  if (!token) {
    const login = http.post(
      `${BASE_URL}/api/v1/auth/login`,
      JSON.stringify({ email: 'kasir@skoolia.id', password: 'loadtest', sekolah_id: SEKOLAH }),
      { headers: { 'Content-Type': 'application/json' } },
    );
    if (login.status !== 200) {
      exec.test.abort(
        `Login dev gagal (${login.status}). Set TOKEN=<jwt> atau aktifkan ` +
        `kantin.dev-login.enabled=true (lihat docs/uji-beban-tap.md).`,
      );
    }
    token = login.json('data.token');
    if (!token) {
      exec.test.abort('Login dev tidak mengembalikan data.token.');
    }
  }

  console.log(
    `Mulai uji beban: ${TAP_RATE} tap/dtk selama ${DURATION} → ${BASE_URL} ` +
    `(sekolah=${SEKOLAH}, kartu=${KARTU_COUNT}, SLO p95<${P95_MS}ms)`,
  );
  return { token };
}

// ── default(): satu tap ─────────────────────────────────────────────────────
export default function (data) {
  // Sebar tap ke banyak kartu (round-robin) agar tidak satu hot-row saldo.
  const idx = ((__VU + __ITER) % KARTU_COUNT) + 1;
  const uid = '04' + String(idx).padStart(6, '0'); // = seed SQL: 04000001..04000020

  const body = JSON.stringify({
    rfidUid: uid,
    titikKasirId: TITIK_KASIR,
    items: [{ menuId: MENU_ID, qty: QTY }],
    // Idempotency unik per tap (max 64 char) — jangan pernah diulang.
    idempotencyKey: `k6-${__VU}-${__ITER}-${Date.now()}`.slice(0, 64),
  });

  const res = http.post(`${BASE_URL}/api/kasir/tap`, body, {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${data.token}`,
    },
    tags: { nama: 'tap' },
  });

  tapMs.add(res.timings.duration);

  const rateLimited = res.status === 429;
  tapDitahanRate.add(rateLimited);

  // Sukses = HTTP 200 + amplop body.code == 200 (parity CommonResponse).
  let codeOk = false;
  try {
    codeOk = res.json('code') === 200;
  } catch (e) {
    codeOk = false;
  }
  const ok = res.status === 200 && codeOk;
  tapOk.add(ok);

  check(res, {
    'status 200': (r) => r.status === 200,
    'body.code 200': () => codeOk,
  });

  if (rateLimited && __ITER === 0) {
    console.warn(
      '⚠️  Menerima 429 (rate limit). Untuk uji beban, set ' +
      'KANTIN_RATE_LIMIT_ENABLED=false pada server uji (lihat docs/uji-beban-tap.md).',
    );
  }
}
