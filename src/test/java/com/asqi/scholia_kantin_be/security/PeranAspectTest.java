package com.asqi.scholia_kantin_be.security;

import com.asqi.scholia_kantin_be.component.exception.ForbiddenException;
import com.asqi.scholia_kantin_be.component.exception.UnauthorizedException;
import com.asqi.scholia_kantin_be.enums.AktorKantin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AUDIT KEAMANAN — menegakkan bahwa {@link PeranAspect} benar-benar menolak
 * peran yang tidak berhak.
 *
 * <p>RBAC adalah lapisan otorisasi inti (PRD §11.5). Sebelumnya tidak ada uji
 * untuk aspect ini; bila AOP tak terpasang, {@code @PerluPeran} akan diam-diam
 * tidak berlaku. Uji ini menjadi guard regresi.
 */
@DisplayName("AUDIT PeranAspect — RBAC ditegakkan")
class PeranAspectTest {

    private final PeranAspect aspect = new PeranAspect();

    /** Anotasi contoh untuk diuji. */
    @PerluPeran({AktorKantin.ADMIN_SEKOLAH, AktorKantin.TU_SEKOLAH})
    void aksiKhususAdminTu() {
    }

    @AfterEach
    void bersihkan() {
        TenantContext.clear();
    }

    private PerluPeran perluPeran() throws NoSuchMethodException {
        return PeranAspectTest.class.getDeclaredMethod("aksiKhususAdminTu").getAnnotation(PerluPeran.class);
    }

    @Test
    @DisplayName("tanpa identitas → 401 (Unauthorized)")
    void tanpaIdentitasDitolak() throws Exception {
        TenantContext.clear();
        assertThatThrownBy(() -> aspect.cek(perluPeran()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("peran tak berhak (PETUGAS) → 403 (Forbidden)")
    void peranTakBerhakDitolak() throws Exception {
        TenantContext.set(IdentitasKantin.builder()
                .userId("7").sekolahId(1L).peran(AktorKantin.PETUGAS_KANTIN).build());
        assertThatThrownBy(() -> aspect.cek(perluPeran()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("peran berhak (TU) → lolos")
    void peranBerhakLolos() throws Exception {
        TenantContext.set(IdentitasKantin.builder()
                .userId("7").sekolahId(1L).peran(AktorKantin.TU_SEKOLAH).build());
        assertThatCode(() -> aspect.cek(perluPeran())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("peran TIDAK_DIKENAL → 403 (fail-closed)")
    void peranTidakDikenalDitolak() throws Exception {
        TenantContext.set(IdentitasKantin.builder()
                .userId("7").sekolahId(1L).peran(AktorKantin.TIDAK_DIKENAL).build());
        assertThatThrownBy(() -> aspect.cek(perluPeran()))
                .isInstanceOf(ForbiddenException.class);
    }
}
