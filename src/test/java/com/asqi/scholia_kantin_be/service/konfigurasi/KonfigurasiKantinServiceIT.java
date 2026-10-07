package com.asqi.scholia_kantin_be.service.konfigurasi;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.dto.InsidenOfflineResponse;
import com.asqi.scholia_kantin_be.dto.KebijakanKantinResponse;
import com.asqi.scholia_kantin_be.dto.PosBukuKasResponse;
import com.asqi.scholia_kantin_be.enums.KebijakanSaldoMengendap;
import com.asqi.scholia_kantin_be.helper.Constants;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi solusi DEMO untuk pertanyaan terblokir (#21, #23, #25) dengan
 * PostgreSQL nyata (Testcontainers).
 *
 * <p>Menegakkan: seeding pos Buku Kas idempoten (#21/Q8), insiden offline
 * append-only (#23/Q14), dan kebijakan saldo mengendap default aman
 * (#25/Q16) — semuanya tenant-scoped (PRD §11.4).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class KonfigurasiKantinServiceIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;
    private static final long ADMIN = 900L;
    private static final long PETUGAS = 555L;

    @Autowired
    private PosBukuKasService posService;

    @Autowired
    private InsidenOfflineService insidenService;

    @Autowired
    private KebijakanKantinService kebijakanService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        jdbc.execute("TRUNCATE TABLE pos_buku_kas, insiden_offline, kebijakan_kantin, audit_log CASCADE");
    }

    // ────────────────────────────────────────────────────────────────
    // #21 / Q8 — POS BUKU KAS
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("#21: aktivasi membuat pos standar kantin (Pendapatan/Belanja Stok/Penyesuaian)")
    void aktivasiMembuatPosStandar() {
        List<PosBukuKasResponse> pos = posService.pastikanPosStandar(SEKOLAH, ADMIN);

        assertThat(pos).hasSize(3);
        assertThat(pos).extracting(PosBukuKasResponse::getNama)
                .containsExactlyInAnyOrder(
                        Constants.KATEGORI_PENDAPATAN_KANTIN,
                        Constants.KATEGORI_BELANJA_STOK_KANTIN,
                        Constants.KATEGORI_PENYESUAIAN_KANTIN);

        PosBukuKasResponse pendapatan = pos.stream()
                .filter(p -> p.getNama().equals(Constants.KATEGORI_PENDAPATAN_KANTIN))
                .findFirst().orElseThrow();
        assertThat(pendapatan.getTipe()).isEqualTo("MASUK");
        assertThat(pendapatan.isAktif()).isTrue();

        PosBukuKasResponse belanja = pos.stream()
                .filter(p -> p.getNama().equals(Constants.KATEGORI_BELANJA_STOK_KANTIN))
                .findFirst().orElseThrow();
        assertThat(belanja.getTipe()).isEqualTo("KELUAR");
    }

    @Test
    @DisplayName("#21: seeding idempoten — aktivasi dua kali tidak menggandakan pos")
    void seedingIdempoten() {
        posService.pastikanPosStandar(SEKOLAH, ADMIN);
        posService.pastikanPosStandar(SEKOLAH, ADMIN);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM pos_buku_kas WHERE sekolah_id = ?", Integer.class, SEKOLAH))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("#21: audit seeding tercatat")
    void seedingTercatatAudit() {
        posService.pastikanPosStandar(SEKOLAH, ADMIN);

        Integer audit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE aksi = 'SEED_POS_BUKU_KAS'", Integer.class);
        assertThat(audit).isEqualTo(1);
    }

    @Test
    @DisplayName("#21: tenant scoping — pos sekolah lain tidak bocor")
    void posTenantScoped() {
        posService.pastikanPosStandar(SEKOLAH, ADMIN);

        assertThat(posService.daftar(SEKOLAH_LAIN)).isEmpty();
    }

    // ────────────────────────────────────────────────────────────────
    // #23 / Q14 — INSIDEN OFFLINE
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("#23: lapor insiden offline → tersimpan dengan waktu mulai & pelapor")
    void laporInsidenOffline() {
        InsidenOfflineResponse insiden = insidenService.lapor(
                SEKOLAH, 7L, "Internet mati sejak 09:15, kasir mode tunai manual", PETUGAS);

        assertThat(insiden.getId()).isNotNull();
        assertThat(insiden.getMulai()).isNotNull();
        assertThat(insiden.getSelesai()).isNull(); // masih berlangsung
        assertThat(insiden.getTitikKasirId()).isEqualTo(7L);
        assertThat(insiden.getDilaporkanOleh()).isEqualTo(PETUGAS);

        Integer audit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE aksi = 'LAPOR_INSIDEN_OFFLINE'", Integer.class);
        assertThat(audit).isEqualTo(1);
    }

    @Test
    @DisplayName("#23: keterangan kosong → ditolak")
    void insidenTanpaKeteranganDitolak() {
        assertThatThrownBy(() -> insidenService.lapor(SEKOLAH, null, "  ", PETUGAS))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Keterangan");
    }

    @Test
    @DisplayName("#23: insiden append-only — UPDATE/DELETE ditolak trigger")
    void insidenAppendOnly() {
        InsidenOfflineResponse insiden = insidenService.lapor(
                SEKOLAH, null, "Server down", PETUGAS);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE insiden_offline SET keterangan = 'x' WHERE id = ?", insiden.getId()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM insiden_offline WHERE id = ?", insiden.getId()))
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("#23: tenant scoping — daftar insiden sekolah lain kosong")
    void insidenTenantScoped() {
        insidenService.lapor(SEKOLAH, null, "Internet mati", PETUGAS);

        assertThat(insidenService.daftar(SEKOLAH_LAIN)).isEmpty();
    }

    // ────────────────────────────────────────────────────────────────
    // #25 / Q16 — KEBIJAKAN SALDO MENGENDAP
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("#25: kebijakan belum diisi → default REFUND (aman, tanpa simpan)")
    void kebijakanDefaultRefund() {
        KebijakanKantinResponse k = kebijakanService.ambil(SEKOLAH);

        assertThat(k.getKebijakanSaldoMengendap())
                .isEqualTo(KebijakanSaldoMengendap.REFUND.name());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM kebijakan_kantin", Integer.class)).isZero();
    }

    @Test
    @DisplayName("#25: ubah kebijakan → tersimpan + audit mencatat nilai lama→baru")
    void ubahKebijakanTersimpanDanAudit() {
        kebijakanService.ubah(SEKOLAH, KebijakanSaldoMengendap.PINDAH_SAUDARA, 30,
                "Pindahkan ke saudara aktif", ADMIN);

        KebijakanKantinResponse k = kebijakanService.ambil(SEKOLAH);
        assertThat(k.getKebijakanSaldoMengendap()).isEqualTo("PINDAH_SAUDARA");
        assertThat(k.getAmbangHari()).isEqualTo(30);

        Integer audit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE aksi = 'UBAH_KEBIJAKAN_KANTIN'", Integer.class);
        assertThat(audit).isEqualTo(1);
    }

    @Test
    @DisplayName("#25: kebijakan tenant-scoped — sekolah lain tetap default")
    void kebijakanTenantScoped() {
        kebijakanService.ubah(SEKOLAH, KebijakanSaldoMengendap.TETAP_MENGENDAP, 120, null, ADMIN);

        assertThat(kebijakanService.ambil(SEKOLAH_LAIN).getKebijakanSaldoMengendap())
                .isEqualTo(KebijakanSaldoMengendap.REFUND.name());
    }
}
