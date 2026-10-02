package com.asqi.scholia_kantin_be.scheduler;

import com.asqi.scholia_kantin_be.service.kasir.SesiKasirService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Uji unit penjadwal auto-tutup sesi (PRD §6.4).
 *
 * <p>Tidak memakai context Spring {@code @Scheduled} (itu diuji terpisah oleh
 * IT); di sini cukup memastikan tugas memanggil service lintas-tenant.
 */
class SesiKasirSchedulerTest {

    @Test
    @DisplayName("jadwal memanggil auto-tutup lintas-tenant")
    void memanggilService() {
        SesiKasirService service = mock(SesiKasirService.class);
        when(service.tutupOtomatisLintasTenant()).thenReturn(2);

        new SesiKasirScheduler(service).autoTutupSesi();

        verify(service, times(1)).tutupOtomatisLintasTenant();
    }

    @Test
    @DisplayName("tidak ada sesi tertinggal → tetap selesai tanpa error")
    void tanpaSesiTertinggal() {
        SesiKasirService service = mock(SesiKasirService.class);
        when(service.tutupOtomatisLintasTenant()).thenReturn(0);

        new SesiKasirScheduler(service).autoTutupSesi();

        verify(service).tutupOtomatisLintasTenant();
    }
}