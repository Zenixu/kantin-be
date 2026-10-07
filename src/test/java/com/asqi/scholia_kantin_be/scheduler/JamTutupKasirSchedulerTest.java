package com.asqi.scholia_kantin_be.scheduler;

import com.asqi.scholia_kantin_be.service.kasir.SesiKasirService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Uji unit penjadwal auto-tutup sesuai jam tutup sekolah (PRD §6.4, §9.1, #42).
 */
class JamTutupKasirSchedulerTest {

    @Test
    @DisplayName("jadwal memanggil auto-tutup sesuai jam tutup sekolah")
    void memanggilService() {
        SesiKasirService service = mock(SesiKasirService.class);
        when(service.tutupSesiLewatJamTutup()).thenReturn(3);

        new JamTutupKasirScheduler(service).autoTutupSesuaiJamSekolah();

        verify(service, times(1)).tutupSesiLewatJamTutup();
    }

    @Test
    @DisplayName("tidak ada sesi yang perlu ditutup → tetap selesai tanpa error")
    void tanpaSesi() {
        SesiKasirService service = mock(SesiKasirService.class);
        when(service.tutupSesiLewatJamTutup()).thenReturn(0);

        new JamTutupKasirScheduler(service).autoTutupSesuaiJamSekolah();

        verify(service).tutupSesiLewatJamTutup();
    }
}
