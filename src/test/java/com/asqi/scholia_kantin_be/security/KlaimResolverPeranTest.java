package com.asqi.scholia_kantin_be.security;

import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.SumberToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AUDIT RBAC — pemetaan string peran SKOOLIA → {@link AktorKantin}.
 *
 * <p><b>Bug yang dikunci:</b> sebelumnya {@code KlaimResolver.petakanPeran}
 * memeriksa cabang generik {@code contains("KANTIN")} SEBELUM cabang spesifik
 * {@code contains("PETUGAS")}. Akibatnya peran {@code PETUGAS_KANTIN} (kasir)
 * dipetakan ke {@link AktorKantin#PENGELOLA_KANTIN} yang punya hak lebih tinggi
 * (katalog, stok, HPP) — <b>eskalasi hak istimewa</b>. Ditemukan saat membangun
 * harness dummy JWT (#14/#15).
 *
 * <p>Pemetaan peran adalah lapisan otorisasi inti (PRD §11.5) tetapi sebelumnya
 * <b>tak teruji</b>. Uji ini menjadi guard regresi.
 */
@DisplayName("AUDIT RBAC — KlaimResolver.petakanPeran")
class KlaimResolverPeranTest {

    private final KlaimResolver resolver = new KlaimResolver();

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            // ── REGRESI BUG: kasir/petugas TIDAK boleh jadi pengelola ──
            "PETUGAS_KANTIN,      PETUGAS_KANTIN",
            "PETUGAS,             PETUGAS_KANTIN",
            "KASIR,               PETUGAS_KANTIN",
            "petugas_kantin,      PETUGAS_KANTIN",
            "Petugas Kantin,      PETUGAS_KANTIN",
            // ── peran lain ──
            "PENGELOLA_KANTIN,    PENGELOLA_KANTIN",
            "TU,                  TU_SEKOLAH",
            "BENDAHARA,           TU_SEKOLAH",
            "TATA_USAHA,          TU_SEKOLAH",
            "ADMIN,               ADMIN_SEKOLAH",
            "KEPSEK,              ADMIN_SEKOLAH",
            "ORANG_TUA,           ORANG_TUA",
            "ORTU,                ORANG_TUA",
            // ── tak dikenal → fail-closed ──
            "GURU,                TIDAK_DIKENAL",
            "RESEPSIONIS,         TIDAK_DIKENAL",
            "SISWA,               TIDAK_DIKENAL",
    })
    @DisplayName("peran staf dipetakan sesuai haknya")
    void petakanPeranStaf(String role, AktorKantin diharapkan) {
        assertThat(resolver.petakanPeran(role, SumberToken.ADMIN)).isEqualTo(diharapkan);
    }

    @Test
    @DisplayName("REGRESI: PETUGAS_KANTIN ≠ PENGELOLA_KANTIN (cegah eskalasi hak kasir)")
    void petugasBukanPengelola() {
        assertThat(resolver.petakanPeran("PETUGAS_KANTIN", SumberToken.ADMIN))
                .isNotEqualTo(AktorKantin.PENGELOLA_KANTIN);
    }

    @Test
    @DisplayName("token MOBILE selalu ORANG_TUA apa pun klaim role-nya")
    void mobileSelaluOrangTua() {
        assertThat(resolver.petakanPeran("ADMIN", SumberToken.MOBILE))
                .isEqualTo(AktorKantin.ORANG_TUA);
        assertThat(resolver.petakanPeran(null, SumberToken.MOBILE))
                .isEqualTo(AktorKantin.ORANG_TUA);
    }

    @Test
    @DisplayName("role null/kosong → TIDAK_DIKENAL (fail-closed)")
    void roleKosongFailClosed() {
        assertThat(resolver.petakanPeran(null, SumberToken.ADMIN)).isEqualTo(AktorKantin.TIDAK_DIKENAL);
        assertThat(resolver.petakanPeran("  ", SumberToken.ADMIN)).isEqualTo(AktorKantin.TIDAK_DIKENAL);
    }
}
