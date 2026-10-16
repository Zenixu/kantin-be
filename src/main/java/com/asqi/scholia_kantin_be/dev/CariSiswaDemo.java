package com.asqi.scholia_kantin_be.dev;

import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.service.integrasi.CariSiswaPort;
import com.asqi.scholia_kantin_be.service.integrasi.InfoSiswa;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Implementasi {@link CariSiswaPort} untuk <b>profil demo</b> (issue #214).
 *
 * <p>Aktif hanya bila {@code kantin.demo.enabled=true}. Menyediakan data siswa
 * tiruan agar alur TU (top-up tunai §9.2, kontrol siswa §9.6, refund siswa
 * keluar §9.3) bisa diuji <b>tanpa</b> menunggu data/kontrak admin-be (Q7).
 *
 * <p>Saldo diambil dari <b>ledger lokal</b> kantin-be ({@link LedgerSaldoService})
 * — jadi angka yang tampil konsisten dengan data nyata hasil tap/top-up demo.
 * {@code @Primary} agar menang atas {@link com.asqi.scholia_kantin_be.service.integrasi.CariSiswaFallback}.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "kantin.demo", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CariSiswaDemo implements CariSiswaPort {

    /** Data siswa tiruan: id, NIS, nama, kelas. */
    private static final List<SiswaDemo> DATA = List.of(
            new SiswaDemo(101L, "2024001", "Budi Santoso", "7A", false),
            new SiswaDemo(102L, "2024002", "Siti Aminah", "7A", false),
            new SiswaDemo(103L, "2024003", "Andi Wijaya", "8B", false),
            new SiswaDemo(104L, "2024004", "Dewi Lestari", "8B", false),
            new SiswaDemo(105L, "2024005", "Rizky Pratama", "9C", false),
            new SiswaDemo(106L, "2023006", "Maya Sari", "9C", true)); // nonaktif (lulus)

    private final LedgerSaldoService ledgerSaldo;

    @Override
    public List<InfoSiswa> cari(Long sekolahId, String kataKunci, boolean hanyaTidakAktif, int batas) {
        String q = kataKunci == null ? "" : kataKunci.trim().toLowerCase(Locale.ROOT);

        List<InfoSiswa> hasil = DATA.stream()
                .filter(s -> !hanyaTidakAktif || s.tidakAktif())
                .filter(s -> q.isEmpty()
                        || s.nama().toLowerCase(Locale.ROOT).contains(q)
                        || s.nis().contains(q))
                .limit(Math.max(1, batas))
                .map(s -> InfoSiswa.builder()
                        .siswaId(s.id())
                        .nis(s.nis())
                        .nama(s.nama())
                        .kelas(s.kelas())
                        .tidakAktif(s.tidakAktif())
                        .saldo(ledgerSaldo.saldo(sekolahId, SubjekTipe.SISWA, s.id()))
                        .build())
                .toList();

        log.debug("[DEMO] Cari siswa (Q7) sekolah={} q='{}' hanyaTidakAktif={} → {} hasil",
                sekolahId, kataKunci, hanyaTidakAktif, hasil.size());
        return hasil;
    }

    /** Baris data siswa tiruan. */
    private record SiswaDemo(Long id, String nis, String nama, String kelas, boolean tidakAktif) {
    }
}
