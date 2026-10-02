package com.asqi.scholia_kantin_be.service.kasir;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.dto.TapRequest;
import com.asqi.scholia_kantin_be.dto.TapResponse;
import com.asqi.scholia_kantin_be.enums.JenisMutasiSaldo;
import com.asqi.scholia_kantin_be.enums.MetodeRequestKartu;
import com.asqi.scholia_kantin_be.enums.StatusTransaksi;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.SesiKasir;
import com.asqi.scholia_kantin_be.model.Transaksi;
import com.asqi.scholia_kantin_be.model.TransaksiItem;
import com.asqi.scholia_kantin_be.repository.TransaksiRepository;
import com.asqi.scholia_kantin_be.security.IdentitasKantin;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import com.asqi.scholia_kantin_be.service.integrasi.KartuLookupPort;
import com.asqi.scholia_kantin_be.service.integrasi.MenuLookupPort;
import com.asqi.scholia_kantin_be.service.stok.HasilMutasiStok;
import com.asqi.scholia_kantin_be.service.stok.LedgerStokService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Alur logika bisnis utama: <b>satu tap di kasir</b> (PRD §6.1–6.2).
 *
 * <p>Langkah (semua dalam <b>satu</b> transaksi DB — PRD §11.2):
 * <ol>
 *   <li><b>Idempotency.</b> Key dari klien diperiksa lebih dulu; key yang sama
 *       mengembalikan hasil transaksi pertama (tap ganda ≠ 2× potong).</li>
 *   <li><b>Lookup kartu</b> (port) — kartu dikenal &amp; tidak diblokir
 *       (diperiksa server tiap tap, tanpa cache — PRD §11.11).</li>
 *   <li><b>Validasi 6 tahap</b> via {@link TapValidator}.</li>
 *   <li><b>Eksekusi atomik:</b> buka/ambil sesi → potong stok (FOR UPDATE) →
 *       catat transaksi + item (snapshot HPP) → debit saldo (FOR UPDATE).</li>
 * </ol>
 *
 * <p><b>Kenapa {@link TransactionTemplate} &amp; bukan {@code @Transactional}?</b>
 * Bila dua request ber-idempotency-key sama masuk nyaris bersamaan, yang kalah
 * akan kena pelanggaran UNIQUE saat menyimpan transaksi. Dengan menangkapnya
 * <i>di luar</i> transaksi (transaksi sudah di-rollback bersih), request yang
 * kalah dapat mengembalikan <b>hasil pemenang</b> — bukan error 409. Ini menjaga
 * kontrak idempotency (PRD §11.3) tetap benar pada kondisi balapan.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TapService {

    private final TransaksiRepository transaksiRepo;
    private final TapValidator validator;
    private final LedgerSaldoService ledgerSaldo;
    private final LedgerStokService ledgerStok;
    private final SesiKasirService sesiKasir;
    private final KartuLookupPort kartuLookup;
    private final MenuLookupPort menuLookup;
    private final IdGenerator idGenerator;
    private final JamKantin jam;
    private final TransactionTemplate txTemplate;

    /**
     * Proses satu tap.
     *
     * @param sekolahId tenant dari JWT (sudah diverifikasi filter)
     * @param identitas principal petugas (audit)
     * @param request   data tap (uid, items, idempotencyKey, titikKasir)
     * @return hasil tap untuk ditampilkan di layar kasir
     */
    public TapResponse tap(Long sekolahId, IdentitasKantin identitas, TapRequest request) {
        String key = request.getIdempotencyKey();

        // Idempotency jalur cepat: key sudah pernah diproses → kembalikan hasil lama.
        if (transaksiRepo.existsByIdempotencyKey(key)) {
            log.debug("Idempotency replay tap key={}", key);
            return txTemplate.execute(status -> bangunReplay(sekolahId, key));
        }

        try {
            return txTemplate.execute(status -> eksekusi(sekolahId, identitas, request));
        } catch (DataIntegrityViolationException e) {
            // Balapan idempotency: transaksi kalah sudah di-rollback; pakai hasil pemenang.
            if (transaksiRepo.existsByIdempotencyKey(key)) {
                return txTemplate.execute(status -> bangunReplay(sekolahId, key));
            }
            throw e;
        }
    }

    /** Inti eksekusi — berjalan di dalam satu transaksi DB. */
    private TapResponse eksekusi(Long sekolahId, IdentitasKantin identitas, TapRequest request) {
        Long petugasId = idPetugas(identitas);

        // 2) Lookup kartu (port). Status blokir diperiksa server tiap tap.
        InfoKartu kartu = kartuLookup.cariBerdasarkanUid(sekolahId, request.getRfidUid());

        long saldo = 0L;
        long belanjaHariIni = 0L;
        if (kartu.isDikenal() && kartu.getSubjekTipe() != null && kartu.getSubjekId() != null) {
            saldo = ledgerSaldo.saldo(sekolahId, kartu.getSubjekTipe(), kartu.getSubjekId());
            if (kartu.siswa()) {
                belanjaHariIni = ledgerSaldo.belanjaHariIni(sekolahId, kartu.getSubjekTipe(), kartu.getSubjekId());
            }
        }

        // 3) Validasi 6 tahap.
        TapValidator.Hasil hasil = validator.validasi(
                sekolahId, request, kartu, saldo, belanjaHariIni,
                menuId -> menuLookup.cari(sekolahId, menuId));
        if (!hasil.valid()) {
            throw new ConflictException(hasil.getValidasi().getPesan());
        }

        // 4) Sesi kasir hari ini (buka bila belum ada).
        SesiKasir sesi = sesiKasir.sesiTerbukaAtauBuka(sekolahId, request.getTitikKasirId());

        OffsetDateTime now = jam.sekarang();
        Long transaksiId = idGenerator.berikutnyaLong();

        Transaksi trx = Transaksi.builder()
                .id(transaksiId)
                .idempotencyKey(request.getIdempotencyKey())
                .sekolahId(sekolahId)
                .sesiKasirId(sesi.getId())
                .titikKasirId(request.getTitikKasirId())
                .subjekTipe(kartu.getSubjekTipe())
                .subjekId(kartu.getSubjekId())
                .kartuUid(request.getRfidUid())
                .petugasId(petugasId)
                .total(hasil.getTotal())
                .totalHpp(0L)
                .status(StatusTransaksi.SUKSES)
                .waktu(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Potong stok tiap item (FOR UPDATE) + snapshot HPP & harga.
        long totalHpp = 0L;
        List<String> namaItem = new ArrayList<>();
        for (TapValidator.Baris baris : hasil.getBaris()) {
            HasilMutasiStok mutasiStok = ledgerStok.keluarPenjualan(
                    sekolahId, baris.getMenuId(), baris.getQty(), transaksiId);
            long hppSnapshot = mutasiStok.getHppSetelah();
            totalHpp += hppSnapshot * baris.getQty();

            TransaksiItem item = TransaksiItem.builder()
                    .id(idGenerator.berikutnyaLong())
                    .menuId(baris.getMenuId())
                    .namaMenu(baris.getMenu().getNama())
                    .kategoriId(baris.getMenu().getKategoriId())
                    .hargaJual(baris.getMenu().getHargaJual())
                    .qty(baris.getQty())
                    .hppSnapshot(hppSnapshot)
                    .subtotal(baris.getSubtotal())
                    .createdAt(now)
                    .build();
            trx.tambahItem(item);
            namaItem.add(baris.getMenu().getNama() + " x" + baris.getQty());
        }
        trx.setTotalHpp(totalHpp);

        // Simpan transaksi + item (cascade) SEBELUM debit, agar FK aman & baris ada.
        transaksiRepo.save(trx);

        // Debit saldo (FOR UPDATE) — atomik dengan langkah di atas.
        var hasilDebit = ledgerSaldo.debit(PerintahMutasiSaldo.builder()
                .sekolahId(sekolahId)
                .subjekTipe(kartu.getSubjekTipe())
                .subjekId(kartu.getSubjekId())
                .jenis(JenisMutasiSaldo.PENJUALAN)
                .nominal(hasil.getTotal())
                .idempotencyKey(request.getIdempotencyKey())
                .transaksiId(transaksiId)
                .referensiTipe("TRANSAKSI")
                .referensiId(String.valueOf(transaksiId))
                .aktorId(petugasId)
                .build());

        log.info("Tap sukses trx={} sekolah={} subjek={}/{} total={} saldoSisa={}",
                transaksiId, sekolahId, kartu.getSubjekTipe(), kartu.getSubjekId(),
                hasil.getTotal(), hasilDebit.getSaldoSetelah());

        return TapResponse.builder()
                .transaksiId(transaksiId)
                .subjekTipe(kartu.getSubjekTipe())
                .nama(kartu.getNama())
                .kelas(kartu.getKelas())
                .fotoUrl(kartu.getFotoUrl())
                .total(hasil.getTotal())
                .saldoSisa(hasilDebit.getSaldoSetelah())
                .namaItem(namaItem)
                .metode(MetodeRequestKartu.UID)
                .build();
    }

    /**
     * Bangun respons untuk permintaan idempoten (tidak membuat transaksi baru).
     * Dipanggil <b>di dalam</b> transaksi agar relasi {@code items} (lazy) bisa
     * dimuat untuk menampilkan rincian item.
     */
    private TapResponse bangunReplay(Long sekolahId, String idempotencyKey) {
        Transaksi trx = transaksiRepo.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new InvalidOperationException(
                        "Transaksi idempoten tidak ditemukan: " + idempotencyKey));

        long saldoSisa = 0L;
        if (trx.getSubjekTipe() != null && trx.getSubjekId() != null) {
            saldoSisa = ledgerSaldo.saldo(sekolahId, trx.getSubjekTipe(), trx.getSubjekId());
        }
        InfoKartu kartu = trx.getKartuUid() == null ? null
                : kartuLookup.cariBerdasarkanUid(sekolahId, trx.getKartuUid());

        List<String> namaItem = new ArrayList<>();
        for (TransaksiItem item : trx.getItems()) {
            namaItem.add(item.getNamaMenu() + " x" + item.getQty());
        }

        return TapResponse.builder()
                .transaksiId(trx.getId())
                .subjekTipe(trx.getSubjekTipe())
                .nama(kartu == null ? null : kartu.getNama())
                .kelas(kartu == null ? null : kartu.getKelas())
                .fotoUrl(kartu == null ? null : kartu.getFotoUrl())
                .total(trx.getTotal())
                .saldoSisa(saldoSisa)
                .namaItem(namaItem)
                .metode(MetodeRequestKartu.UID)
                .build();
    }

    /** Ambil id petugas (Long) dari principal; token SKOOLIA memakai user id numerik. */
    private Long idPetugas(IdentitasKantin identitas) {
        if (identitas == null || identitas.getUserId() == null) {
            throw new InvalidOperationException("Identitas petugas tidak tersedia");
        }
        try {
            return Long.valueOf(identitas.getUserId().trim());
        } catch (NumberFormatException e) {
            throw new InvalidOperationException(
                    "ID petugas tidak valid pada token: " + identitas.getUserId());
        }
    }
}
