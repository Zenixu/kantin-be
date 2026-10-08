package com.asqi.scholia_kantin_be.service.saldo;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.dto.HasilRefundResponse;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.model.KartuTamu;
import com.asqi.scholia_kantin_be.repository.KartuTamuRepository;
import com.asqi.scholia_kantin_be.service.kartu.KontrolKartuService;
import com.asqi.scholia_kantin_be.service.kasir.HasilMutasiSaldo;
import com.asqi.scholia_kantin_be.service.kasir.LedgerSaldoService;
import com.asqi.scholia_kantin_be.service.kasir.PerintahMutasiSaldo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Operasi saldo khusus <b>Kartu Tamu</b> (PRD §9.4, issue #120):
 * <ul>
 *   <li><b>Pengembalian kartu</b> — sisa saldo di-refund <b>tunai</b> oleh TU
 *       kepada pemegang, saldo menjadi 0, label pemegang dikosongkan sehingga
 *       kartu dapat dipakai ulang.</li>
 *   <li><b>Kartu hilang</b> — TU memblokir kartu lama (berlaku instan), sisa
 *       saldo dipindahkan ke Kartu Tamu baru.</li>
 * </ul>
 *
 * <p>Berbeda dengan {@link RefundSaldoService} (khusus siswa), operasi di sini
 * bekerja pada {@link SubjekTipe#KARTU_TAMU} dan memakai {@link KartuTamuRepository}
 * untuk validasi tenant &amp; pengosongan label pemegang.
 *
 * <p><b>Jaminan:</b> saldo hanya keluar lewat baris ledger {@code REFUND}/
 * {@code TRANSFER} (closed-loop, PRD §5); append-only &amp; idempoten (nomor
 * bukti); tenant-scoped (PRD §11.4); semua aksi diaudit (PRD §11.7).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundKartuTamuService {

    private static final String AKSI_REFUND = "REFUND_KARTU_TAMU";
    private static final String AKSI_PINDAH = "PINDAH_SALDO_KARTU_TAMU";

    private final LedgerSaldoService ledgerSaldo;
    private final KartuTamuRepository kartuRepo;
    private final KontrolKartuService kontrolKartu;
    private final AuditLogger auditLogger;

    /**
     * Refund <b>seluruh</b> sisa saldo Kartu Tamu saat <b>pengembalian kartu</b>
     * (PRD §9.4). Saldo menjadi 0, label pemegang dikosongkan.
     *
     * <p>Idempoten lewat {@code referensiId} (nomor bukti). Bila saldo sudah 0 →
     * ditolak.
     *
     * @param kartuId     ID kartu tamu
     * @param referensiId nomor bukti refund (unik, idempotency)
     * @param aktorId     TU/bendahara yang memproses (audit)
     */
    @Transactional
    public HasilRefundResponse refund(Long sekolahId, Long kartuId, String referensiId,
                                      String catatan, Long aktorId) {
        if (kartuId == null) {
            throw new InvalidOperationException("ID kartu tamu wajib diisi");
        }
        if (referensiId == null || referensiId.isBlank()) {
            throw new InvalidOperationException("Nomor bukti refund wajib diisi (idempotency)");
        }
        KartuTamu kartu = kartuRepo.findById(kartuId)
                .orElseThrow(() -> new NotFoundEntity("Kartu tamu tidak ditemukan"));
        if (!kartu.getSekolahId().equals(sekolahId)) {
            throw new NotFoundEntity("Kartu tamu tidak ditemukan");
        }

        String keterangan = "Refund sisa saldo pengembalian kartu " + kartu.getNomorKartu()
                + (catatan == null || catatan.isBlank() ? "" : " — " + catatan);

        HasilMutasiSaldo hasil = ledgerSaldo.debitSisaPenuh(PerintahMutasiSaldo.builder()
                .sekolahId(sekolahId)
                .subjekTipe(SubjekTipe.KARTU_TAMU)
                .subjekId(kartuId)
                .jenis(JenisMutasiSaldo.REFUND)
                .nominal(0L)
                .idempotencyKey("REFUND-KT-" + referensiId)
                .referensiTipe("REFUND")
                .referensiId(referensiId)
                .keterangan(keterangan)
                .aktorId(aktorId)
                .build());

        long nominal = hasil.getMutasi().getNominal() == null ? 0L : hasil.getMutasi().getNominal();
        boolean labelDikosongkan = false;
        if (!hasil.isIdempotentReplay()) {
            auditLogger.catat(aktorId, sekolahId, AKSI_REFUND, "KartuTamu",
                    SubjekTipe.KARTU_TAMU + ":" + kartuId, catatan,
                    String.valueOf(nominal), "0");
            // PRD §9.4: kartu dikembalikan → label pemegang dikosongkan agar bisa
            // dipakai ulang. Kartu TIDAK diblokir (masih dipakai untuk tamu lain).
            if (kartu.getLabelPemegang() != null) {
                kartu.setLabelPemegang(null);
                kartu.setDiubahOleh(aktorId);
                kartu.setDiubahPada(Instant.now());
                kartuRepo.save(kartu);
                labelDikosongkan = true;
            }
            log.info("Refund saldo kartu tamu sekolah={} kartu={} nominal={} ref={}",
                    sekolahId, kartuId, nominal, referensiId);
        }

        return HasilRefundResponse.builder()
                .subjekId(kartuId)
                .nominal(nominal)
                .referensiId(referensiId)
                .saldoSetelah(hasil.getSaldoSetelah())
                .labelDikosongkan(labelDikosongkan)
                .idempoten(hasil.isIdempotentReplay())
                .build();
    }

    /**
     * Pindahkan <b>seluruh</b> sisa saldo Kartu Tamu <b>hilang</b> ke Kartu Tamu
     * baru (PRD §9.4). Kartu lama diblokir (berlaku instan), saldo sumber → 0.
     *
     * <p>Idempoten lewat {@code referensiId}; kedua kaki ledger atomik.
     *
     * @param aktorId TU/bendahara yang memproses (audit)
     */
    @Transactional
    public HasilRefundResponse pindahKartuHilang(Long sekolahId, Long sumberId, Long tujuanId,
                                                 String referensiId, String catatan, Long aktorId) {
        if (sumberId == null || tujuanId == null) {
            throw new InvalidOperationException("ID kartu sumber & tujuan wajib diisi");
        }
        if (sumberId.equals(tujuanId)) {
            throw new InvalidOperationException("Kartu sumber dan tujuan tidak boleh sama");
        }
        if (referensiId == null || referensiId.isBlank()) {
            throw new InvalidOperationException("Nomor berita acara pemindahan wajib diisi (idempotency)");
        }
        KartuTamu sumber = kartuRepo.findById(sumberId)
                .orElseThrow(() -> new NotFoundEntity("Kartu tamu sumber tidak ditemukan"));
        if (!sumber.getSekolahId().equals(sekolahId)) {
            throw new NotFoundEntity("Kartu tamu sumber tidak ditemukan");
        }
        KartuTamu tujuan = kartuRepo.findById(tujuanId)
                .orElseThrow(() -> new NotFoundEntity("Kartu tamu tujuan tidak ditemukan"));
        if (!tujuan.getSekolahId().equals(sekolahId)) {
            throw new NotFoundEntity("Kartu tamu tujuan tidak ditemukan");
        }
        if (Boolean.FALSE.equals(tujuan.getAktif())) {
            throw new InvalidOperationException(
                    "Kartu tujuan nonaktif — saldo hanya dapat dipindah ke Kartu Tamu aktif");
        }

        String keterangan = "Pindah saldo kartu hilang " + sumber.getNomorKartu()
                + " → " + tujuan.getNomorKartu()
                + (catatan == null || catatan.isBlank() ? "" : " — " + catatan);

        HasilMutasiSaldo hasil = ledgerSaldo.pindahSaldo(sekolahId, SubjekTipe.KARTU_TAMU,
                sumberId, tujuanId, referensiId, keterangan, aktorId);

        long nominal = hasil.getMutasi().getNominal() == null ? 0L : hasil.getMutasi().getNominal();
        boolean diblokir = false;
        if (!hasil.isIdempotentReplay()) {
            auditLogger.catat(aktorId, sekolahId, AKSI_PINDAH, "KartuTamu",
                    SubjekTipe.KARTU_TAMU + ":" + sumberId, catatan,
                    String.valueOf(nominal), "0");
            // PRD §9.4: kartu hilang → diblokir (berlaku instan).
            kontrolKartu.ubahBlokir(sekolahId, SubjekTipe.KARTU_TAMU, sumberId,
                    true, "Kartu hilang — saldo dipindah ke kartu " + tujuan.getNomorKartu(), aktorId);
            diblokir = true;
            log.info("Pindah saldo kartu tamu sekolah={} dari={} ke={} nominal={} ref={}",
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
