package com.asqi.scholia_kantin_be.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji unit generator ID ledger — monoton &amp; unik dalam satu JVM.
 *
 * <p>Mitigasi tabrakan PK pada trafik tinggi (docs/spesifikasi-fase4-ledger.md §5.5).
 */
class IdGeneratorTest {

    private final IdGenerator generator = new IdGenerator();

    @Test
    @DisplayName("id selalu naik monoton")
    void monotonNaik() {
        long sebelum = generator.berikutnya();
        for (int i = 0; i < 1000; i++) {
            long sekarang = generator.berikutnya();
            assertThat(sekarang).isGreaterThan(sebelum);
            sebelum = sekarang;
        }
    }

    @Test
    @DisplayName("tidak ada tabrakan pada 50.000 pembangkitan cepat")
    void unikPadaTrafikTinggi() {
        Set<Long> terlihat = new HashSet<>();
        for (int i = 0; i < 50_000; i++) {
            assertThat(terlihat.add(generator.berikutnya())).isTrue();
        }
    }

    @Test
    @DisplayName("berikutnyaLong() mengembalikan nilai sama bentuknya")
    void berikutnyaLong() {
        Long id = generator.berikutnyaLong();
        assertThat(id).isPositive();
    }
}
