package com.asqi.scholia_kantin_be.service.konfigurasi;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.dto.PengaturanKantinRequest;
import com.asqi.scholia_kantin_be.dto.TitikKasirResponse;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi pengaturan kantin &amp; CRUD titik kasir (PRD §9.1, issue #42)
 * dengan PostgreSQL nyata (Testcontainers).
 *
 * <p>Menegakkan: default aman sebelum diatur, simpan/ubah + audit, validasi
 * min/maks top-up, CRUD titik kasir (kode unik, soft delete, tenant scoping).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class PengaturanKantinServiceIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;
    private static final long ADMIN = 900L;

    @Autowired
    private PengaturanKantinService pengaturanService;

    @Autowired
    private TitikKasirService titikKasirService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE sekolah_kantin_config, titik_kasir, sesi_kasir, audit_log CASCADE");
    }

    // ────────────────────────────────────────────────────────────────
    // PENGATURAN KANTIN (PRD §9.1)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("#42: pengaturan belum diisi → default aman (jam 23:59, konfirmasi off, foto 3s)")
    void defaultAman() {
        var p = pengaturanService.ambil(SEKOLAH);

        assertThat(p.getJamTutupOtomatis()).isEqualTo(LocalTime.of(23, 59));
        assertThat(p.isKonfirmasiManual()).isFalse();
        assertThat(p.getDurasiFotoDetik()).isEqualTo(3);
        assertThat(p.isDisimpan()).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sekolah_kantin_config", Integer.class)).isZero();
    }

    @Test
    @DisplayName("#42: ubah pengaturan → tersimpan + audit mencatat")
    void ubahTersimpanDanAudit() {
        PengaturanKantinRequest req = new PengaturanKantinRequest();
        req.setNamaKantin("Kantin Sehat");
        req.setJamTutupOtomatis(LocalTime.of(15, 30));
        req.setKonfirmasiManual(true);
        req.setDurasiFotoDetik(5);
        req.setMinTopup(10_000L);
        req.setMaksTopup(500_000L);
        req.setBatasSaldoSiswa(1_000_000L);
        req.setBatasSaldoKartuTamu(300_000L);

        var p = pengaturanService.ubah(SEKOLAH, req, ADMIN);

        assertThat(p.getNamaKantin()).isEqualTo("Kantin Sehat");
        assertThat(p.getJamTutupOtomatis()).isEqualTo(LocalTime.of(15, 30));
        assertThat(p.isKonfirmasiManual()).isTrue();
        assertThat(p.getDurasiFotoDetik()).isEqualTo(5);
        assertThat(p.getMaksTopup()).isEqualTo(500_000L);
        assertThat(p.getBatasSaldoSiswa()).isEqualTo(1_000_000L);
        assertThat(p.isDisimpan()).isTrue();

        Integer audit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE aksi = 'UBAH_PENGATURAN_KANTIN'", Integer.class);
        assertThat(audit).isEqualTo(1);
    }

    @Test
    @DisplayName("#42: min top-up > maks top-up → ditolak")
    void minTopupLebihBesarDitolak() {
        PengaturanKantinRequest req = new PengaturanKantinRequest();
        req.setMinTopup(100_000L);
        req.setMaksTopup(50_000L);

        assertThatThrownBy(() -> pengaturanService.ubah(SEKOLAH, req, ADMIN))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Minimum top-up");
    }

    @Test
    @DisplayName("#42: jam tutup otomatis efektif per sekolah (default bila belum diatur)")
    void jamTutupEfektif() {
        assertThat(pengaturanService.jamTutupOtomatis(SEKOLAH)).isEqualTo(LocalTime.of(23, 59));

        PengaturanKantinRequest req = new PengaturanKantinRequest();
        req.setJamTutupOtomatis(LocalTime.of(16, 0));
        pengaturanService.ubah(SEKOLAH, req, ADMIN);

        assertThat(pengaturanService.jamTutupOtomatis(SEKOLAH)).isEqualTo(LocalTime.of(16, 0));
        // Sekolah lain tetap default.
        assertThat(pengaturanService.jamTutupOtomatis(SEKOLAH_LAIN)).isEqualTo(LocalTime.of(23, 59));
    }

    @Test
    @DisplayName("#42: pengaturan tenant-scoped — sekolah lain tetap default")
    void pengaturanTenantScoped() {
        PengaturanKantinRequest req = new PengaturanKantinRequest();
        req.setNamaKantin("Kantin A");
        pengaturanService.ubah(SEKOLAH, req, ADMIN);

        assertThat(pengaturanService.ambil(SEKOLAH_LAIN).isDisimpan()).isFalse();
        assertThat(pengaturanService.ambil(SEKOLAH_LAIN).getNamaKantin()).isNull();
    }

    // ────────────────────────────────────────────────────────────────
    // TITIK KASIR (PRD §9.1, §6.6)
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("#42: buat titik kasir → aktif + audit")
    void buatTitikKasir() {
        TitikKasirResponse t = titikKasirService.buat(SEKOLAH, "Kasir Utama", "KSR-1", ADMIN);

        assertThat(t.getId()).isNotNull();
        assertThat(t.getNama()).isEqualTo("Kasir Utama");
        assertThat(t.getKode()).isEqualTo("KSR-1");
        assertThat(t.isAktif()).isTrue();

        Integer audit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE aksi = 'BUAT_TITIK_KASIR'", Integer.class);
        assertThat(audit).isEqualTo(1);
    }

    @Test
    @DisplayName("#42: kode titik kasir duplikat → ditolak")
    void kodeDuplikatDitolak() {
        titikKasirService.buat(SEKOLAH, "Kasir 1", "KSR-1", ADMIN);

        assertThatThrownBy(() -> titikKasirService.buat(SEKOLAH, "Kasir 2", "KSR-1", ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("sudah digunakan");
    }

    @Test
    @DisplayName("#42: daftar titik kasir (hanyaAktif)")
    void daftarTitikKasir() {
        TitikKasirResponse t1 = titikKasirService.buat(SEKOLAH, "Kasir 1", null, ADMIN);
        titikKasirService.buat(SEKOLAH, "Kasir 2", null, ADMIN);
        titikKasirService.nonaktifkan(SEKOLAH, t1.getId(), ADMIN);

        assertThat(titikKasirService.daftar(SEKOLAH, false)).hasSize(2);
        List<TitikKasirResponse> aktif = titikKasirService.daftar(SEKOLAH, true);
        assertThat(aktif).hasSize(1);
        assertThat(aktif.get(0).getNama()).isEqualTo("Kasir 2");
    }

    @Test
    @DisplayName("#42: titik kasir sekolah lain → 404")
    void titikKasirTenantScoped() {
        TitikKasirResponse t = titikKasirService.buat(SEKOLAH, "Kasir 1", null, ADMIN);

        assertThatThrownBy(() -> titikKasirService.detail(SEKOLAH_LAIN, t.getId()))
                .isInstanceOf(NotFoundEntity.class);
        assertThat(titikKasirService.daftar(SEKOLAH_LAIN, false)).isEmpty();
    }

    @Test
    @DisplayName("#42: titik kasir dengan sesi terbuka tidak bisa dinonaktifkan")
    void titikDenganSesiTerbukaTidakBisaNonaktif() {
        TitikKasirResponse t = titikKasirService.buat(SEKOLAH, "Kasir 1", null, ADMIN);
        jdbc.update("INSERT INTO sesi_kasir (id, sekolah_id, titik_kasir_id, tanggal, status, "
                + "total_bruto, total_void, total_bersih, dibuka_at, auto_tutup, posting_buku_kas, "
                + "created_at, updated_at) "
                + "VALUES (9001, ?, ?, CURRENT_DATE, 'TERBUKA', 0, 0, 0, now(), false, false, now(), now())",
                SEKOLAH, t.getId());

        assertThatThrownBy(() -> titikKasirService.nonaktifkan(SEKOLAH, t.getId(), ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("sesi terbuka");
    }
}
