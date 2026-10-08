package com.asqi.scholia_kantin_be.security;

import com.asqi.scholia_kantin_be.component.exception.ForbiddenException;
import com.asqi.scholia_kantin_be.controller.AuthController;
import com.asqi.scholia_kantin_be.controller.KonfigurasiKantinController;
import com.asqi.scholia_kantin_be.controller.KontrolKartuController;
import com.asqi.scholia_kantin_be.controller.LaporanController;
import com.asqi.scholia_kantin_be.controller.PengaturanKantinController;
import com.asqi.scholia_kantin_be.dto.BlokirKartuRequest;
import com.asqi.scholia_kantin_be.dto.LimitHarianRequest;
import com.asqi.scholia_kantin_be.dto.PengaturanKantinRequest;
import com.asqi.scholia_kantin_be.dto.TitikKasirRequest;
import com.asqi.scholia_kantin_be.dto.UbahKebijakanRequest;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import com.asqi.scholia_kantin_be.enums.JenisLaporan;
import com.asqi.scholia_kantin_be.enums.SumberToken;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AUDIT RBAC (issue #123) — kepala sekolah <b>tidak</b> boleh mewarisi hak
 * CRUD {@link AktorKantin#ADMIN_SEKOLAH}.
 *
 * <p><b>Bug yang dikunci:</b> {@code KlaimResolver} dulu melebur
 * {@code KEPSEK}/{@code KEPALA_SEKOLAH} ke {@code ADMIN_SEKOLAH}, sehingga
 * kepsek mendapat hak tulis (pengaturan &amp; titik kasir §9.6, blokir kartu)
 * padahal PRD §9.5/§9.6 hanya memberinya hak <b>baca</b> sebagian laporan —
 * <b>eskalasi hak</b> (melanggar PRD §11.5). Uji ini mengunci dua sisi:
 * (1) pemetaan peran → {@link AktorKantin#KEPSEK}; (2) anotasi RBAC pada
 * endpoint nyata tidak pernah menyertakan kepsek di jalur tulis, tetapi
 * menyertakannya di laporan yang berhak (read).
 */
@DisplayName("AUDIT RBAC #123 — kepsek read-only, bukan ADMIN_SEKOLAH")
class RbacKepsekGuardTest {

    @AfterEach
    void bersihkan() {
        TenantContext.clear();
    }

    // ────────────────────────────────────────────────────────────────
    // 1) Pemetaan peran
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("KEPSEK/KEPALA_SEKOLAH → KEPSEK (bukan ADMIN_SEKOLAH)")
    void kepsekDipetakanKeKepsek() {
        KlaimResolver resolver = new KlaimResolver();
        assertThat(resolver.petakanPeran("KEPSEK", com.asqi.scholia_kantin_be.enums.SumberToken.ADMIN))
                .isEqualTo(AktorKantin.KEPSEK);
        assertThat(resolver.petakanPeran("KEPALA_SEKOLAH", com.asqi.scholia_kantin_be.enums.SumberToken.ADMIN))
                .isEqualTo(AktorKantin.KEPSEK);
        assertThat(resolver.petakanPeran("Kepala Sekolah", com.asqi.scholia_kantin_be.enums.SumberToken.ADMIN))
                .isEqualTo(AktorKantin.KEPSEK);
        // kontrol: admin biasa tetap ADMIN_SEKOLAH
        assertThat(resolver.petakanPeran("ADMIN", com.asqi.scholia_kantin_be.enums.SumberToken.ADMIN))
                .isEqualTo(AktorKantin.ADMIN_SEKOLAH);
    }

    // ────────────────────────────────────────────────────────────────
    // 2) PeranAspect menegakkan penolakan kepsek pada aksi khusus admin
    // ────────────────────────────────────────────────────────────────

    @PerluPeran({AktorKantin.ADMIN_SEKOLAH})
    void aksiKhususAdmin() {
    }

    @Test
    @DisplayName("kepsek → 403 pada aksi @PerluPeran(ADMIN_SEKOLAH)")
    void kepsekDitolakPadaAksiAdmin() throws Exception {
        PeranAspect aspect = new PeranAspect();
        Method m = RbacKepsekGuardTest.class.getDeclaredMethod("aksiKhususAdmin");
        PerluPeran anotasi = m.getAnnotation(PerluPeran.class);

        TenantContext.set(IdentitasKantin.builder()
                .userId("9").sekolahId(1L).peran(AktorKantin.KEPSEK).build());

        assertThatThrownBy(() -> aspect.cek(anotasi))
                .isInstanceOf(ForbiddenException.class);
    }

    // ────────────────────────────────────────────────────────────────
    // 3) Anotasi RBAC endpoint nyata (guard regresi level kode)
    // ────────────────────────────────────────────────────────────────

    /** Semua peran yang tercantum di {@code @PerluPeran} sebuah method. */
    private static List<AktorKantin> peranPada(Class<?> kelas, String method, Class<?>... params) {
        try {
            Method m = kelas.getDeclaredMethod(method, params);
            PerluPeran p = m.getAnnotation(PerluPeran.class);
            assertThat(p).as("@PerluPeran harus ada di %s#%s", kelas.getSimpleName(), method).isNotNull();
            return Arrays.asList(p.value());
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Method tidak ditemukan: " + kelas.getSimpleName() + "#" + method, e);
        }
    }

    @Test
    @DisplayName("endpoint TULIS pengaturan & titik kasir (§9.6) tertutup untuk kepsek")
    void pengaturanTitikKasirTertutupUntukKepsek() {
        // PUT /api/pengaturan-kantin (ubah pengaturan) — ADMIN_SEKOLAH saja
        assertThat(peranPada(PengaturanKantinController.class, "ubah",
                PengaturanKantinRequest.class, IdentitasKantin.class))
                .doesNotContain(AktorKantin.KEPSEK);
        // POST/PUT/DELETE titik-kasir — ADMIN_SEKOLAH saja
        assertThat(peranPada(PengaturanKantinController.class, "buatTitik",
                TitikKasirRequest.class, IdentitasKantin.class))
                .doesNotContain(AktorKantin.KEPSEK);
        assertThat(peranPada(PengaturanKantinController.class, "ubahTitik",
                Long.class, TitikKasirRequest.class, IdentitasKantin.class))
                .doesNotContain(AktorKantin.KEPSEK);
        assertThat(peranPada(PengaturanKantinController.class, "nonaktifkanTitik",
                Long.class, IdentitasKantin.class))
                .doesNotContain(AktorKantin.KEPSEK);
    }

    @Test
    @DisplayName("endpoint TULIS kebijakan kantin (§9.6) tertutup untuk kepsek")
    void kebijakanKantinTertutupUntukKepsek() {
        assertThat(peranPada(KonfigurasiKantinController.class, "ubahKebijakan",
                UbahKebijakanRequest.class, IdentitasKantin.class))
                .doesNotContain(AktorKantin.KEPSEK);
    }

    @Test
    @DisplayName("endpoint TULIS kontrol kartu (blokir/limit) tertutup untuk kepsek")
    void kontrolKartuTertutupUntukKepsek() {
        assertThat(peranPada(KontrolKartuController.class, "ubahBlokir",
                BlokirKartuRequest.class, IdentitasKantin.class))
                .doesNotContain(AktorKantin.KEPSEK);
        assertThat(peranPada(KontrolKartuController.class, "setLimit",
                LimitHarianRequest.class, IdentitasKantin.class))
                .doesNotContain(AktorKantin.KEPSEK);
    }

    @Test
    @DisplayName("cabut token staf (aksi sensitif) tertutup untuk kepsek")
    void cabutTokenTertutupUntukKepsek() {
        assertThat(peranPada(AuthController.class, "cabut",
                HttpServletRequest.class, IdentitasKantin.class))
                .doesNotContain(AktorKantin.KEPSEK);
    }

    @Test
    @DisplayName("laporan yang berhak (read, §9.5) TERBUKA untuk kepsek")
    void laporanBerhakTerbukaUntukKepsek() {
        // penjualan/laba kotor, saldo mengendap, rekonsiliasi, kerugian stok
        assertThat(peranPada(LaporanController.class, "penjualan",
                LocalDate.class, OffsetDateTime.class, OffsetDateTime.class))
                .contains(AktorKantin.KEPSEK);
        assertThat(peranPada(LaporanController.class, "saldoMengendap"))
                .contains(AktorKantin.KEPSEK);
        assertThat(peranPada(LaporanController.class, "rekonsiliasi",
                LocalDate.class, OffsetDateTime.class, OffsetDateTime.class))
                .contains(AktorKantin.KEPSEK);
        assertThat(peranPada(LaporanController.class, "kerugianStok",
                LocalDate.class, OffsetDateTime.class, OffsetDateTime.class))
                .contains(AktorKantin.KEPSEK);
    }

    @Test
    @DisplayName("laporan STOK (§9.5: hanya Pengelola & Bendahara) tertutup untuk kepsek")
    void laporanStokTertutupUntukKepsek() {
        assertThat(peranPada(LaporanController.class, "stok", boolean.class))
                .doesNotContain(AktorKantin.KEPSEK);
    }

    // ────────────────────────────────────────────────────────────────
    // 4) Ekspor: kepsek hanya jenis yang boleh dibacanya
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ekspor laporan: kepsek dilarang mengekspor STOK/BARANG_MASUK")
    void eksporKepsekDibatasiPerJenis() {
        LaporanController controller = new LaporanController(null, null);
        TenantContext.set(IdentitasKantin.builder()
                .userId("9").sekolahId(1L).peran(AktorKantin.KEPSEK).build());

        assertThatThrownBy(() -> controller.ekspor(JenisLaporan.STOK, null, null, null, null))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> controller.ekspor(JenisLaporan.BARANG_MASUK, null, null, null, null))
                .isInstanceOf(ForbiddenException.class);
    }
}
