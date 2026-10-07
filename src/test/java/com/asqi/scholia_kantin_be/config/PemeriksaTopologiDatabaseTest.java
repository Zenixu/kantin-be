package com.asqi.scholia_kantin_be.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji penjaga topologi DB (ADR-0010, Q11) — memastikan kantin-be menolak
 * menunjuk DB admin-be (pelanggaran isolasi) &amp; memberi peringatan bila host
 * sama dengan admin-be. Tanpa Spring context (cepat).
 */
class PemeriksaTopologiDatabaseTest {

    private MockEnvironment env(String url) {
        MockEnvironment e = new MockEnvironment();
        e.setProperty("spring.datasource.url", url);
        return e;
    }

    private TopologiProperties props(boolean enforce, String hostAdmin) {
        TopologiProperties p = new TopologiProperties();
        p.setEnforce(enforce);
        p.setHostDbAdminBe(hostAdmin);
        return p;
    }

    @Test
    @DisplayName("URL JDBC: ekstraksi nama database & host benar (termasuk query & kredensial)")
    void ekstraksiUrl() {
        String url = "jdbc:postgresql://db.internal:5432/kantin_db?stringtype=unspecified";
        assertThat(PemeriksaTopologiDatabase.namaDatabase(url)).isEqualTo("kantin_db");
        assertThat(PemeriksaTopologiDatabase.host(url)).isEqualTo("db.internal");

        assertThat(PemeriksaTopologiDatabase.namaDatabase(
                "jdbc:postgresql://user:pass@localhost:5432/admin_db")).isEqualTo("admin_db");
        assertThat(PemeriksaTopologiDatabase.host(
                "jdbc:postgresql://user:pass@localhost:5432/admin_db")).isEqualTo("localhost");
    }

    @Test
    @DisplayName("enforce=false: menunjuk DB admin-be → hanya peringatan (tidak menggagalkan start)")
    void dilarangTanpaEnforce() {
        var pemeriksa = new PemeriksaTopologiDatabase(
                props(false, ""), env("jdbc:postgresql://localhost:5432/admin_db"));

        assertThatCode(pemeriksa::periksa).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("enforce=true: menunjuk DB admin-be → start DIGAGALKAN")
    void dilarangDenganEnforce() {
        var pemeriksa = new PemeriksaTopologiDatabase(
                props(true, ""), env("jdbc:postgresql://localhost:5432/admin_db"));

        assertThatThrownBy(pemeriksa::periksa)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR-0010")
                .hasMessageContaining("admin_db");
    }

    @Test
    @DisplayName("DB kantin sah (kantin_db) → tidak ada pelanggaran walau enforce=true")
    void dbSah() {
        var pemeriksa = new PemeriksaTopologiDatabase(
                props(true, ""), env("jdbc:postgresql://localhost:5432/kantin_db"));

        assertThatCode(pemeriksa::periksa).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("host sama dengan admin-be → peringatan, bukan pelanggaran")
    void hostSamaPeringatan() {
        var pemeriksa = new PemeriksaTopologiDatabase(
                props(true, "localhost"), env("jdbc:postgresql://localhost:5432/kantin_db"));

        assertThatCode(pemeriksa::periksa).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("nama DB di daftar dilarang bersifat case-insensitive")
    void caseInsensitive() {
        var pemeriksa = new PemeriksaTopologiDatabase(
                props(true, ""), env("jdbc:postgresql://localhost:5432/ADMIN_DB"));

        assertThatThrownBy(pemeriksa::periksa).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("default db-dilarang memuat admin_db")
    void defaultDaftarDilarang() {
        assertThat(new TopologiProperties().getDbDilarang()).contains("admin_db");
        assertThat(new TopologiProperties().getDbDilarang())
                .isEqualTo(List.of("admin_db", "adminbe", "skoolia_admin",
                        "mobile_db", "internal_db", "callback_db"));
    }
}
