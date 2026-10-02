package com.asqi.scholia_kantin_be.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji konversi aktor id terpusat (temuan B24).
 *
 * <p>Sebelum dipusatkan, setiap pemanggil mengonversi {@code userId} (String)
 * ke {@code Long} sendiri-sendiri — rawan duplikasi &amp; inkonsistensi pesan
 * error. Uji ini mengunci perilaku tunggalnya.
 */
@DisplayName("IdentitasKantin.aktorIdWajib()")
class IdentitasKantinTest {

    @Test
    void konversiUserIdNumerik() {
        IdentitasKantin id = IdentitasKantin.builder().userId("42").build();
        assertThat(id.aktorIdWajib()).isEqualTo(42L);
    }

    @Test
    void abaikanSpasiSekitar() {
        IdentitasKantin id = IdentitasKantin.builder().userId("  7  ").build();
        assertThat(id.aktorIdWajib()).isEqualTo(7L);
    }

    @Test
    void tolakUserIdKosong() {
        IdentitasKantin id = IdentitasKantin.builder().userId("  ").build();
        assertThatThrownBy(id::aktorIdWajib)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("userId kosong");
    }

    @Test
    void tolakUserIdNull() {
        IdentitasKantin id = IdentitasKantin.builder().userId(null).build();
        assertThatThrownBy(id::aktorIdWajib)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tolakUserIdNonNumerik() {
        IdentitasKantin id = IdentitasKantin.builder().userId("abc-123").build();
        assertThatThrownBy(id::aktorIdWajib)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tidak valid");
    }
}
