package com.asqi.scholia_kantin_be.security.blacklist;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Uji sidik jari token: deterministik, 64 hex, tahan tabrakan praktis. */
@DisplayName("SidikJari — hash token untuk blacklist")
class SidikJariTest {

    @Test
    @DisplayName("SHA-256 heksadesimal 64 karakter")
    void format() {
        String h = SidikJari.dari("token-abc");
        assertThat(h).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("deterministik: token sama → hash sama")
    void deterministik() {
        assertThat(SidikJari.dari("sama")).isEqualTo(SidikJari.dari("sama"));
    }

    @Test
    @DisplayName("token berbeda → hash berbeda")
    void berbeda() {
        assertThat(SidikJari.dari("a")).isNotEqualTo(SidikJari.dari("b"));
    }

    @Test
    @DisplayName("tidak menyimpan token mentah di hash")
    void tidakBocor() {
        assertThat(SidikJari.dari("rahasia-sekali")).doesNotContain("rahasia");
    }
}
