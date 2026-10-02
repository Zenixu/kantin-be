package com.asqi.scholia_kantin_be.helper;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generator ID untuk entitas bernilai tinggi (ledger, transaksi).
 *
 * <p><b>Kenapa tidak memakai {@code Constants.idGenerator()}?</b>
 * Pola lama (epoch-millis + 3 digit acak) berisiko <b>tabrakan PK</b> pada
 * trafik tinggi dan <b>tidak monoton</b> (urutan id ≠ urutan waktu). Temuan ini
 * tercatat di {@code docs/spesifikasi-fase4-ledger.md §5.5}.
 *
 * <p><b>Skema yang dipakai:</b>
 * <pre>
 *   id = epochMillis × 1000 + (offsetNode + counter) mod 1000
 * </pre>
 * - <b>Monoton naik</b> dalam satu JVM (dijaga {@link AtomicLong}) → urutan id
 *   mengikuti waktu, aman untuk {@code ORDER BY id}.
 * - <b>Unik</b> dalam satu JVM (counter naik saat milidetik sama).
 * - <b>Anti-tabrakan antar-JVM</b>: tiap JVM punya {@code offsetNode} acak
 *   0–999, sehingga dua node pada milidetik sama hampir pasti berbeda.
 *
 * <p>Rentang aman hingga tahun ~292 juta. Ini mitigasi pragmatis tanpa
 * mengubah skema; keputusan final (sequence PostgreSQL vs ULID) tetap menunggu
 * tim (OPEN-QUESTIONS) dan mudah ditukar karena semua pemanggil lewat bean ini.
 */
@Component
public class IdGenerator {

    private static final long SKALA = 1000L;

    /** Offset acak per-JVM agar dua instance tidak bertabrakan pada ms yang sama. */
    private final long offsetNode = new SecureRandom().nextInt((int) SKALA);

    private final AtomicLong terakhir = new AtomicLong(0L);

    /**
     * ID baru yang monoton &amp; unik untuk entitas ledger/transaksi.
     *
     * @return id positif bertipe {@code long}
     */
    public long berikutnya() {
        long sekarang = System.currentTimeMillis();
        long dasar = sekarang * SKALA + offsetNode;
        return terakhir.updateAndGet(prev -> Math.max(dasar, prev + 1));
    }

    /** ID baru sebagai {@link Long} (untuk kolom BIGINT entitas). */
    public Long berikutnyaLong() {
        return berikutnya();
    }
}
