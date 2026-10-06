package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.dto.HasilRefundResponse;
import com.asqi.scholia_kantin_be.dto.KandidatRefundItem;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.SaldoCache;
import com.asqi.scholia_kantin_be.repository.SaldoCacheRepository;
import com.asqi.scholia_kantin_be.service.integrasi.StatusSiswaPort;
import com.asqi.scholia_kantin_be.service.kasir.HasilMutasiSaldo;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Refund sisa saldo siswa keluar &amp; pemindahan ke saudara kandung
 * (PRD §9.3, issue #38).
 *
 * <p><b>Alur:</b> bendahara memilih siswa nonaktif (lulus/pindah/keluar) yang
 * masih bersisa saldo, lalu:
 * <ul>
 *   <li><b>Refund</b> — seluruh sisa saldo dikembalikan ke ortu (tunai/transfer,
 *       dengan bukti = {@code referensiId}); saldo menjadi 0.</li>
 *   <li><b>Pindah ke saudara</b> — seluruh sisa saldo dipindahkan ke siswa lain
 *       yang masih aktif di sekolah yang sama; saldo sumber menjadi 0.</li>
 * </ul>
 * Setelah saldo 0, kartu siswa diminta diblokir (via {@link StatusSiswaPort};
 * best-effort, tidak membatalkan perpindahan uang).
 *
 * <p><b>Jaminan:</b> saldo <b>tidak</b> ditarik tunai dari sistem selain lewat
 * baris ledger {@code REFUND}/{@code TRANSFER} (closed-loop, PRD §5); operasi
 * append-only &amp; idempoten (nomor bukti); tenant-scoped (PRD §11.4); semua
 * aksi diaudit (PRD §11.7).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundSaldoService {

    private static final String AKSI_REFUND = "REFUND_SALDO";
    private static final String AKSI_PINDAH = "PINDAH_SALDO";

    private final LedgerSaldoService ledgerSaldo;
    private final SaldoCacheRepository cacheRepo;
    private final StatusSiswaPort statusSiswa;
    private final AuditLogger auditLogger;

    /**
     * Daftar kandidat refund/pindah: subjek SISWA yang masih bersisa saldo,
     * diperkaya status keaktifan (PRD §9.3). Baca-saja; tenant-scoped.
     *
     * @param hanyaTidakAktif bila {@code true}, hanya siswa yang <b>diketahui</b>
     *                        nonaktif (integrasi Q7 siap). Bila {@code false},
     *                        semua siswa bersisa saldo (default).
     */
    @Transactional(readOnly = true)
    public List<KandidatRefundItem> daftarKandidat(Long sekolahId, boolean hanyaTidakAktif) {
        List<SaldoCache> baris = cacheRepo.findBySekolahIdAndSubjekTipe(sekolahId, SubjekTipe.SISWA);
        return baris.stream()
                .filter(c -> c.getSaldo() != null && c.getSaldo() > 0)
                .map(c -> KandidatRefundItem.builder()
                        .subjekId(c.getSubjekId())
                        .saldo(c.getSaldo())
                        .tidakAktif(statusSiswa.tidakAktif(sekolahId, c.getSubjekId()))
                        .build())
                .filter(k -> !hanyaTidakAktif || Boolean.TRUE.equals(k.getTidakAktif()))
                .toList();
    }

    /**
     * Refund <b>seluruh</b> sisa saldo siswa ke ortu (PRD §9.3).
     *
     * <p>Idempoten lewat {@code referensiId} (nomor bukti). Bila saldo sudah 0
     * → ditolak (tidak ada yang dikembalikan).
     *
     * @param referensiId nomor bukti refund (unik, idempotency)
     * @param aktorId     bendahara yang memproses (audit)
     */
    @Transactional
    public HasilRefundResponse refund(Long sekolahId, Long siswaId, String referensiId,
                                      String catatan, Long aktorId) {
        if (siswaId == null) {
            throw new InvalidOperationException("ID siswa wajib diisi");
        }
        if (referensiId == null || referensiId.isBlank()) {
            throw new InvalidOperationException("Nomor bukti refund wajib diisi (idempotency)");
        }

        String keterangan = "Refund saldo siswa keluar"
                + (catatan == null || catatan.isBlank() ? "" : " — " + catatan);

        HasilMutasiSaldo hasil = ledgerSaldo.debitSisaPenuh(PerintahMutasiSaldo.builder()
                .sekolahId(sekolahId)
                .subjekTipe(SubjekTipe.SISWA)
                .subjekId(siswaId)
                .jenis(JenisMutasiSaldo.REFUND)
                .nominal(0L)
                .idempotencyKey("REFUND-" + referensiId)
                .referensiTipe("REFUND")
                .referensiId(referensiId)
                .keterangan(keterangan)
                .aktorId(aktorId)
                .build());

        long nominal = hasil.getMutasi().getNominal() == null ? 0L : hasil.getMutasi().getNominal();
        boolean diblokir = false;
        if (!hasil.isIdempotentReplay()) {
            auditLogger.catat(aktorId, sekolahId, AKSI_REFUND, "Saldo",
                    SubjekTipe.SISWA + ":" + siswaId, catatan,
                    String.valueOf(nominal), "0");
            if (hasil.getSaldoSetelah() == 0) {
                statusSiswa.blokirKartu(sekolahId, siswaId, "Siswa keluar — saldo di-refund");
                diblokir = true;
            }
            log.info("Refund saldo sekolah={} siswa={} nominal={} ref={}",
                    sekolahId, siswaId, nominal, referensiId);
        }

        return HasilRefundResponse.builder()
                .subjekId(siswaId)
                .nominal(nominal)
                .referensiId(referensiId)
                .saldoSetelah(hasil.getSaldoSetelah())
                .kartuDiblokir(diblokir)
                .idempoten(hasil.isIdempotentReplay())
                .build();
    }

    /**
     * Pindahkan <b>seluruh</b> sisa saldo siswa sumber ke saudara kandung yang
     * masih aktif di sekolah yang sama (PRD §9.3).
     *
     * <p>Idempoten lewat {@code referensiId}; kedua kaki ledger atomik.
     *
     * @param aktorId bendahara yang memproses (audit)
     */
    @Transactional
    public HasilRefundResponse pindahKeSaudara(Long sekolahId, Long sumberId, Long tujuanId,
                                               String referensiId, String catatan, Long aktorId) {
        if (sumberId == null || tujuanId == null) {
            throw new InvalidOperationException("ID siswa sumber & tujuan wajib diisi");
        }
        if (sumberId.equals(tujuanId)) {
            throw new InvalidOperationException("Siswa sumber dan tujuan tidak boleh sama");
        }
        if (referensiId == null || referensiId.isBlank()) {
            throw new InvalidOperationException("Nomor berita acara pemindahan wajib diisi (idempotency)");
        }
        // Tujuan wajib siswa yang masih aktif (saudara kandung). Bila status tak
        // diketahui (integrasi Q7 belum siap) → dilewatkan (fail-safe).
        if (Boolean.TRUE.equals(statusSiswa.tidakAktif(sekolahId, tujuanId))) {
            throw new InvalidOperationException(
                    "Siswa tujuan tidak aktif — saldo hanya dapat dipindah ke saudara yang masih aktif");
        }

        String keterangan = "Pindah saldo ke saudara " + tujuanId
                + (catatan == null || catatan.isBlank() ? "" : " — " + catatan);

        HasilMutasiSaldo hasil = ledgerSaldo.pindahSaldo(sekolahId, SubjekTipe.SISWA,
                sumberId, tujuanId, referensiId, keterangan, aktorId);

        long nominal = hasil.getMutasi().getNominal() == null ? 0L : hasil.getMutasi().getNominal();
        boolean diblokir = false;
        if (!hasil.isIdempotentReplay()) {
            auditLogger.catat(aktorId, sekolahId, AKSI_PINDAH, "Saldo",
                    SubjekTipe.SISWA + ":" + sumberId, catatan,
                    String.valueOf(nominal), "0");
            if (hasil.getSaldoSetelah() == 0) {
                statusSiswa.blokirKartu(sekolahId, sumberId, "Siswa keluar — saldo dipindah ke saudara");
                diblokir = true;
            }
            log.info("Pindah saldo sekolah={} dari={} ke={} nominal={} ref={}",
                    sekolahId, sumberId, tujuanId, nominal, referensiId);
        }

        return HasilRefundResponse.builder()
                .subjekId(sumberId)
                .tujuanSubjekId(tujuanId)
                .nominal(nominal)
                .referensiId(referensiId)
                .saldoSetelah(hasil.getSaldoSetelah())
                .kartuDiblokir(diblokir)
                .idempoten(hasil.isIdempotentReplay())
                .build();
    }
}
