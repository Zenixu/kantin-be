package com.asqi.scholia_kantin_be.service.kartu;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.KartuTamu;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uji integrasi Kartu Tamu (PRD §9.4, migrasi V7) dengan <b>PostgreSQL nyata</b>
 * (Testcontainers).
 *
 * <p>Menutup TODO pada {@code docs/API-KARTU-TAMU.md} ("Integration test") dan
 * menegakkan jaminan berikut pada skema sungguhan:
 * <ul>
 *   <li>nomor kartu UNIQUE <b>per sekolah</b> (tenant-scoped), boleh sama di
 *       sekolah lain;</li>
 *   <li>{@code rfid_uid} UNIQUE <b>global</b> — anti-tabrakan antar kartu;</li>
 *   <li>bind / unbind UID (unbind = string kosong) &amp; anti bentrok saat update
 *       (UID sendiri diabaikan lewat {@code excludeId});</li>
 *   <li>soft delete (nonaktif) — kartu tetap ada, hanya tersaring bila
 *       {@code hanyaAktif=true};</li>
 *   <li>isolasi tenant: kartu sekolah lain = 404 (PRD §11.4);</li>
 *   <li>lookup tap via {@code cariByRfidUid};</li>
 *   <li>saldo terikat ke <b>nomor kartu</b>: top-up lewat ledger dengan
 *       {@code subjekTipe=KARTU_TAMU, subjekId=kartu.id} tercatat di
 *       {@code saldo_cache} (integrasi Kartu Tamu ↔ Ledger Saldo).</li>
 * </ul>
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
@DisplayName("KartuTamuService — CRUD + integrasi ledger saldo")
class KartuTamuServiceIT {

    private static final long SEKOLAH = 1L;
    private static final long SEKOLAH_LAIN = 2L;
    private static final long AKTOR = 42L;

    @Autowired
    private KartuTamuService kartu;

    @Autowired
    private LedgerSaldoService ledger;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void bersihkan() {
        // TRUNCATE aman: trigger append-only hanya menolak DELETE/UPDATE baris,
        // bukan TRUNCATE (lihat LedgerSaldoServiceIT).
        jdbc.execute("TRUNCATE TABLE kartu_tamu, saldo_ledger, saldo_cache, audit_log CASCADE");
    }

    // ────────────────────────────── CREATE ──────────────────────────────

    @Test
    @DisplayName("buat kartu: default aktif, UID null bila tak di-bind, tersimpan di DB")
    void buatKartuDasar() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, "KT-001", null, "Kartu guru matematika", AKTOR);

        assertThat(k.getId()).isNotNull();
        assertThat(k.getSekolahId()).isEqualTo(SEKOLAH);
        assertThat(k.getNomorKartu()).isEqualTo("KT-001");
        assertThat(k.getRfidUid()).isNull();
        assertThat(k.getAktif()).isTrue();
        assertThat(k.getCatatan()).isEqualTo("Kartu guru matematika");
        assertThat(k.getDibuatOleh()).isEqualTo(AKTOR);
        assertThat(k.getDibuatPada()).isNotNull();

        Integer baris = jdbc.queryForObject(
                "SELECT count(*) FROM kartu_tamu WHERE id = ?", Integer.class, k.getId());
        assertThat(baris).isEqualTo(1);
    }

    @Test
    @DisplayName("buat kartu dengan UID: UID tersimpan apa adanya")
    void buatKartuDenganUid() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, "KT-002", "04:A1:B2:C3", null, AKTOR);
        assertThat(k.getRfidUid()).isEqualTo("04:A1:B2:C3");
    }

    @Test
    @DisplayName("UID kosong/blank diperlakukan sebagai belum di-bind (null)")
    void uidBlankJadiNull() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, "KT-003", "   ", null, AKTOR);
        assertThat(k.getRfidUid()).isNull();
    }

    @Test
    @DisplayName("nomor kartu duplikat di sekolah sama ditolak (409)")
    void nomorDuplikatDitolak() {
        kartu.buatKartu(SEKOLAH, "KT-001", null, null, AKTOR);

        assertThatThrownBy(() -> kartu.buatKartu(SEKOLAH, "KT-001", null, null, AKTOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("sudah digunakan");
    }

    @Test
    @DisplayName("nomor kartu sama boleh dipakai di sekolah lain (UNIQUE per sekolah)")
    void nomorSamaSekolahLainBoleh() {
        kartu.buatKartu(SEKOLAH, "KT-001", null, null, AKTOR);
        KartuTamu lain = kartu.buatKartu(SEKOLAH_LAIN, "KT-001", null, null, AKTOR);

        assertThat(lain.getId()).isNotNull();
        assertThat(lain.getSekolahId()).isEqualTo(SEKOLAH_LAIN);
    }

    @Test
    @DisplayName("UID RFID duplikat global ditolak (anti-tabrakan, 409)")
    void uidDuplikatDitolak() {
        kartu.buatKartu(SEKOLAH, "KT-001", "04:A1:B2:C3", null, AKTOR);

        assertThatThrownBy(() -> kartu.buatKartu(SEKOLAH, "KT-002", "04:A1:B2:C3", null, AKTOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("sudah terdaftar");
    }

    @Test
    @DisplayName("UID duplikat juga ditolak lintas sekolah (UNIQUE global)")
    void uidDuplikatLintasSekolahDitolak() {
        kartu.buatKartu(SEKOLAH, "KT-001", "04:AA", null, AKTOR);

        assertThatThrownBy(() -> kartu.buatKartu(SEKOLAH_LAIN, "KT-001", "04:AA", null, AKTOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("sudah terdaftar");
    }

    // ────────────────────────────── UPDATE ──────────────────────────────

    @Test
    @DisplayName("bind UID ke kartu yang belum punya UID")
    void bindUid() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, "KT-001", null, null, AKTOR);

        KartuTamu diubah = kartu.updateKartu(SEKOLAH, k.getId(), null,
                "04:D1:E2:F3", null, null, AKTOR);

        assertThat(diubah.getRfidUid()).isEqualTo("04:D1:E2:F3");
        assertThat(diubah.getDiubahOleh()).isEqualTo(AKTOR);
        assertThat(diubah.getDiubahPada()).isNotNull();
    }

    @Test
    @DisplayName("unbind UID: string kosong menghapus binding")
    void unbindUid() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, "KT-001", "04:A1:B2:C3", null, AKTOR);

        KartuTamu diubah = kartu.updateKartu(SEKOLAH, k.getId(), null,
                "", null, null, AKTOR);

        assertThat(diubah.getRfidUid()).isNull();
    }

    @Test
    @DisplayName("set UID ke nilai yang sama tidak dianggap bentrok (excludeId)")
    void uidSendiriTidakBentrok() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, "KT-001", "04:A1:B2:C3", null, AKTOR);

        KartuTamu diubah = kartu.updateKartu(SEKOLAH, k.getId(), null,
                "04:A1:B2:C3", null, null, AKTOR);

        assertThat(diubah.getRfidUid()).isEqualTo("04:A1:B2:C3");
    }

    @Test
    @DisplayName("update nomor kartu ke nilai yang sudah dipakai kartu lain ditolak")
    void updateNomorBentrokDitolak() {
        kartu.buatKartu(SEKOLAH, "KT-001", null, null, AKTOR);
        KartuTamu k2 = kartu.buatKartu(SEKOLAH, "KT-002", null, null, AKTOR);

        assertThatThrownBy(() -> kartu.updateKartu(SEKOLAH, k2.getId(), "KT-001",
                null, null, null, AKTOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("sudah digunakan");
    }

    @Test
    @DisplayName("field null saat update = tidak diubah")
    void updateFieldNullTidakMengubah() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, "KT-001", "04:A1", "catatan awal", AKTOR);

        KartuTamu diubah = kartu.updateKartu(SEKOLAH, k.getId(), null,
                null, null, null, AKTOR);

        assertThat(diubah.getNomorKartu()).isEqualTo("KT-001");
        assertThat(diubah.getRfidUid()).isEqualTo("04:A1");
        assertThat(diubah.getCatatan()).isEqualTo("catatan awal");
        assertThat(diubah.getAktif()).isTrue();
    }

    // ────────────────────────── SOFT DELETE ──────────────────────────

    @Test
    @DisplayName("nonaktifkan = soft delete: aktif=false, baris tetap ada")
    void nonaktifkanSoftDelete() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, "KT-001", "04:A1", null, AKTOR);

        KartuTamu nonaktif = kartu.nonaktifkanKartu(SEKOLAH, k.getId(), AKTOR);

        assertThat(nonaktif.getAktif()).isFalse();

        Integer baris = jdbc.queryForObject(
                "SELECT count(*) FROM kartu_tamu WHERE id = ?", Integer.class, k.getId());
        assertThat(baris).isEqualTo(1);
        Boolean aktifDb = jdbc.queryForObject(
                "SELECT aktif FROM kartu_tamu WHERE id = ?", Boolean.class, k.getId());
        assertThat(aktifDb).isFalse();
    }

    @Test
    @DisplayName("daftarKartu: hanyaAktif=false mengembalikan semua, =true menyaring nonaktif")
    void daftarDanFilterHanyaAktif() {
        kartu.buatKartu(SEKOLAH, "KT-001", null, null, AKTOR);
        KartuTamu k2 = kartu.buatKartu(SEKOLAH, "KT-002", null, null, AKTOR);
        kartu.nonaktifkanKartu(SEKOLAH, k2.getId(), AKTOR);
        // Kartu sekolah lain tak boleh ikut terlihat (tenant-scoped).
        kartu.buatKartu(SEKOLAH_LAIN, "KT-999", null, null, AKTOR);

        List<KartuTamu> semua = kartu.daftarKartu(SEKOLAH, false);
        List<KartuTamu> aktif = kartu.daftarKartu(SEKOLAH, true);

        assertThat(semua).extracting(KartuTamu::getNomorKartu)
                .containsExactly("KT-001", "KT-002");
        assertThat(aktif).extracting(KartuTamu::getNomorKartu)
                .containsExactly("KT-001");
    }

    // ──────────────────────── TENANT ISOLATION ────────────────────────

    @Test
    @DisplayName("detail kartu sekolah lain = 404 (NotFoundEntity, PRD §11.4)")
    void detailIsolasiTenant() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, "KT-001", null, null, AKTOR);

        assertThatThrownBy(() -> kartu.detailKartu(SEKOLAH_LAIN, k.getId()))
                .isInstanceOf(NotFoundEntity.class);
    }

    @Test
    @DisplayName("update kartu sekolah lain = 404 (tidak bisa menyentuh tenant lain)")
    void updateIsolasiTenant() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, "KT-001", null, null, AKTOR);

        assertThatThrownBy(() -> kartu.updateKartu(SEKOLAH_LAIN, k.getId(), "KT-HACK",
                null, null, null, AKTOR))
                .isInstanceOf(NotFoundEntity.class);

        // Pastikan data sekolah pemilik tidak berubah.
        assertThat(kartu.detailKartu(SEKOLAH, k.getId()).getNomorKartu()).isEqualTo("KT-001");
    }

    @Test
    @DisplayName("detail kartu tak dikenal = 404")
    void detailTakDikenal404() {
        assertThatThrownBy(() -> kartu.detailKartu(SEKOLAH, 999_999L))
                .isInstanceOf(NotFoundEntity.class);
    }

    // ────────────────────────── LOOKUP TAP ──────────────────────────

    @Test
    @DisplayName("cariByRfidUid: ketemu saat UID terdaftar, 404 saat tidak")
    void cariByRfidUid() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, "KT-001", "04:DE:AD", null, AKTOR);

        assertThat(kartu.cariByRfidUid("04:DE:AD").getId()).isEqualTo(k.getId());

        assertThatThrownBy(() -> kartu.cariByRfidUid("04:TIDAK-ADA"))
                .isInstanceOf(NotFoundEntity.class);
    }

    // ──────────────────── INTEGRASI LEDGER SALDO ────────────────────

    @Test
    @DisplayName("top-up kartu tamu: saldo terikat subjekTipe=KARTU_TAMU, subjekId=kartu.id")
    void topupKartuTamuMasukLedger() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, "KT-001", null, "Kartu tamu umum", AKTOR);

        tx.executeWithoutResult(s -> ledger.kredit(PerintahMutasiSaldo.builder()
                .sekolahId(SEKOLAH)
                .subjekTipe(SubjekTipe.KARTU_TAMU)
                .subjekId(k.getId())
                .jenis(JenisMutasiSaldo.TOPUP_TUNAI)
                .nominal(50_000L)
                .idempotencyKey("topup-" + k.getId())
                .aktorId(AKTOR)
                .build()));

        assertThat(ledger.saldo(SEKOLAH, SubjekTipe.KARTU_TAMU, k.getId())).isEqualTo(50_000L);
        assertThat(ledger.hitungUlangDariLedger(SEKOLAH, SubjekTipe.KARTU_TAMU, k.getId()))
                .isEqualTo(50_000L);

        Integer baris = jdbc.queryForObject(
                "SELECT count(*) FROM saldo_ledger WHERE subjek_tipe = 'KARTU_TAMU' AND subjek_id = ?",
                Integer.class, k.getId());
        assertThat(baris).isEqualTo(1);
    }

    @Test
    @DisplayName("saldo kartu tamu terpisah dari saldo siswa (subjek berbeda)")
    void saldoKartuTamuTerpisahDariSiswa() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, "KT-001", null, null, AKTOR);
        long siswaId = 1000L;

        tx.executeWithoutResult(s -> {
            ledger.kredit(PerintahMutasiSaldo.builder()
                    .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.KARTU_TAMU).subjekId(k.getId())
                    .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(30_000L)
                    .idempotencyKey("kt-" + k.getId()).aktorId(AKTOR).build());
            ledger.kredit(PerintahMutasiSaldo.builder()
                    .sekolahId(SEKOLAH).subjekTipe(SubjekTipe.SISWA).subjekId(siswaId)
                    .jenis(JenisMutasiSaldo.TOPUP_TUNAI).nominal(10_000L)
                    .idempotencyKey("sw-" + siswaId).aktorId(AKTOR).build());
        });

        assertThat(ledger.saldo(SEKOLAH, SubjekTipe.KARTU_TAMU, k.getId())).isEqualTo(30_000L);
        assertThat(ledger.saldo(SEKOLAH, SubjekTipe.SISWA, siswaId)).isEqualTo(10_000L);
    }

    // ──────────────── #121: nomor kartu auto + label pemegang ────────────────

    @Test
    @DisplayName("#121: nomor kartu digenerate otomatis (KT-001, KT-002, ...) per sekolah")
    void nomorKartuDigenerateOtomatis() {
        KartuTamu k1 = kartu.buatKartu(SEKOLAH, null, null, null, AKTOR);
        KartuTamu k2 = kartu.buatKartu(SEKOLAH, "  ", null, null, AKTOR);
        KartuTamu k3 = kartu.buatKartu(SEKOLAH, null, null, null, AKTOR);

        assertThat(k1.getNomorKartu()).isEqualTo("KT-001");
        assertThat(k2.getNomorKartu()).isEqualTo("KT-002");
        assertThat(k3.getNomorKartu()).isEqualTo("KT-003");
    }

    @Test
    @DisplayName("#121: generate nomor menghormati urutan numerik (bukan leksikal) lintas 999")
    void nomorKartuUrutanNumerik() {
        // Seed kartu manual hingga lewat 999, plus nomor non-standar yang harus diabaikan.
        kartu.buatKartu(SEKOLAH, "KT-999", null, null, AKTOR);
        kartu.buatKartu(SEKOLAH, "KT-1000", null, null, AKTOR);
        kartu.buatKartu(SEKOLAH, "CUSTOM-X", null, null, AKTOR);

        assertThat(kartu.generateNomorKartu(SEKOLAH)).isEqualTo("KT-1001");
    }

    @Test
    @DisplayName("#121: nomor otomatis dihitung per sekolah (tenant-scoped)")
    void nomorKartuPerSekolah() {
        kartu.buatKartu(SEKOLAH, "KT-005", null, null, AKTOR);
        kartu.buatKartu(SEKOLAH_LAIN, "KT-001", null, null, AKTOR);

        // Sekolah lain sudah punya KT-001 → berikutnya KT-002 (bukan ikut sekolah 1).
        assertThat(kartu.generateNomorKartu(SEKOLAH_LAIN)).isEqualTo("KT-002");
        assertThat(kartu.generateNomorKartu(SEKOLAH)).isEqualTo("KT-006");
    }

    @Test
    @DisplayName("#121: label pemegang tersimpan saat buat; blank → null")
    void labelPemegangDisimpan() {
        KartuTamu guru = kartu.buatKartu(SEKOLAH, null, null, null, "Bu Sari (Guru)", AKTOR);
        KartuTamu tamu = kartu.buatKartu(SEKOLAH, null, null, null, "   ", AKTOR);

        assertThat(guru.getLabelPemegang()).isEqualTo("Bu Sari (Guru)");
        assertThat(tamu.getLabelPemegang()).isNull();
    }

    @Test
    @DisplayName("#121: label pemegang bisa diganti & dikosongkan saat pengembalian")
    void labelPemegangDikosongkanSaatPengembalian() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, null, null, null, "Tamu", AKTOR);
        assertThat(k.getLabelPemegang()).isEqualTo("Tamu");

        // Ganti label ke guru lain.
        KartuTamu diubah = kartu.updateKartu(
                SEKOLAH, k.getId(), null, null, null, "Pak Budi (Staf)", null, AKTOR);
        assertThat(diubah.getLabelPemegang()).isEqualTo("Pak Budi (Staf)");

        // Kosongkan label (kartu dikembalikan) — "" = clear.
        KartuTamu kosong = kartu.updateKartu(
                SEKOLAH, k.getId(), null, null, null, "", null, AKTOR);
        assertThat(kosong.getLabelPemegang()).isNull();
    }

    @Test
    @DisplayName("#121: update tanpa menyentuh label (null) → label lama tetap")
    void labelTidakBerubahBilaNull() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, null, null, null, "Tamu", AKTOR);

        KartuTamu diubah = kartu.updateKartu(
                SEKOLAH, k.getId(), null, null, "catatan baru", null, null, AKTOR);

        assertThat(diubah.getLabelPemegang()).isEqualTo("Tamu");
        assertThat(diubah.getCatatan()).isEqualTo("catatan baru");
    }

    // ──────────────── #148: jejak audit nonaktifkan & ubah kartu ────────────────

    @Test
    @DisplayName("#148: nonaktifkanKartu menulis audit NONAKTIFKAN_KARTU (aktor, entitas, id)")
    void nonaktifkanKartuMenulisAudit() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, "KT-001", "04:A1", null, AKTOR);

        kartu.nonaktifkanKartu(SEKOLAH, k.getId(), AKTOR);

        Integer baris = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE aksi = 'NONAKTIFKAN_KARTU' "
                        + "AND entitas = 'KartuTamu' AND entitas_id = ? AND aktor_id = ? AND sekolah_id = ?",
                Integer.class, String.valueOf(k.getId()), AKTOR, SEKOLAH);
        assertThat(baris).isEqualTo(1);

        String nilaiBaru = jdbc.queryForObject(
                "SELECT nilai_baru FROM audit_log WHERE aksi = 'NONAKTIFKAN_KARTU' AND entitas_id = ?",
                String.class, String.valueOf(k.getId()));
        assertThat(nilaiBaru).contains("aktif=false");
    }

    @Test
    @DisplayName("#148: rebind RFID menulis audit UBAH_KARTU dengan UID lama → baru")
    void rebindUidMenulisAudit() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, "KT-001", "04:LAMA", null, AKTOR);

        kartu.updateKartu(SEKOLAH, k.getId(), null, "04:BARU", null, null, AKTOR);

        Integer baris = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE aksi = 'UBAH_KARTU' AND entitas = 'KartuTamu' "
                        + "AND entitas_id = ? AND aktor_id = ?",
                Integer.class, String.valueOf(k.getId()), AKTOR);
        assertThat(baris).isEqualTo(1);

        String nilaiLama = jdbc.queryForObject(
                "SELECT nilai_lama FROM audit_log WHERE aksi = 'UBAH_KARTU' AND entitas_id = ?",
                String.class, String.valueOf(k.getId()));
        String nilaiBaru = jdbc.queryForObject(
                "SELECT nilai_baru FROM audit_log WHERE aksi = 'UBAH_KARTU' AND entitas_id = ?",
                String.class, String.valueOf(k.getId()));
        assertThat(nilaiLama).contains("rfidUid=04:LAMA");
        assertThat(nilaiBaru).contains("rfidUid=04:BARU");
    }

    @Test
    @DisplayName("#148: update tanpa perubahan berarti tidak menulis audit (hindari noise)")
    void updateTanpaPerubahanTidakMenulisAudit() {
        KartuTamu k = kartu.buatKartu(SEKOLAH, "KT-001", "04:A1", "catatan", AKTOR);

        // Semua null = tidak ada field diubah.
        kartu.updateKartu(SEKOLAH, k.getId(), null, null, null, null, AKTOR);

        Integer baris = jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE aksi = 'UBAH_KARTU'", Integer.class);
        assertThat(baris).isZero();
    }
}
