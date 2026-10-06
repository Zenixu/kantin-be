package com.asqi.scholia_kantin_be.service.integrasi;

import com.asqi.scholia_kantin_be.component.exception.InvalidOperationException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.enums.MetodeBukuKas;
import com.asqi.scholia_kantin_be.enums.TipeBukuKas;
import com.asqi.scholia_kantin_be.helper.Constants;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.MutasiStok;
import com.asqi.scholia_kantin_be.model.PostingBukuKas;
import com.asqi.scholia_kantin_be.model.SesiKasir;
import com.asqi.scholia_kantin_be.repository.PostingBukuKasRepository;
import com.asqi.scholia_kantin_be.repository.SesiKasirRepository;
import com.asqi.scholia_kantin_be.security.SekolahGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Posting rekap sesi kasir ke Buku Kas admin-be — INTEGRATIONS.md §3.
 *
 * <p><b>Idempotency (INTEGRATIONS.md §3.4 poin 2).</b> Buku Kas admin-be
 * <b>tidak</b> idempoten ({@code catatTransaksi()} selalu {@code save}), jadi
 * kantin-be yang menjamin sekali-posting:
 * <ol>
 *   <li>flag {@code sesi_kasir.posting_buku_kas} — dicek sebelum memanggil port;</li>
 *   <li>{@code refId} <b>deterministik</b> {@code KANTIN-SESI-<id>} — agar
 *       retry/percobaan ganda mengarah ke entri yang sama;</li>
 *   <li>unique partial index {@code uq_sesi_kasir_referensi_buku_kas} di DB —
 *       jaring terakhir bila dua request balapan.</li>
 * </ol>
 *
 * <p><b>Mitigasi Q3.</b> {@code refModul} dikirim {@code null} sampai tim
 * admin-be menambah case kantin di {@code migrateBukuKas()} — entri masuk
 * {@code remainingBks} dan <b>tidak</b> dihapus saat migrasi
 * (INTEGRATIONS.md §3.4 poin 1). Nilai bisa diaktifkan tanpa ubah kode lewat
 * {@code kantin.bukukas.ref-modul} begitu Q3 terjawab.
 *
 * <p><b>Fail-open terhadap kasir:</b> kegagalan posting <b>tidak</b> membatalkan
 * penutupan sesi. Port yang melempar ditangkap &amp; diubah menjadi
 * {@link HasilPostingBukuKas#gagal}; sesi tetap tertutup dan bisa di-posting
 * ulang.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BukuKasPostingService {

    /** Prefix referensi entri Buku Kas untuk sesi kasir (deterministik). */
    public static final String PREFIX_REF_SESI = "KANTIN-SESI-";

    /** Prefix referensi entri Buku Kas untuk barang masuk (belanja stok). */
    public static final String PREFIX_REF_BARANG_MASUK = "KANTIN-BM-";

    /** Prefix referensi entri Buku Kas untuk koreksi barang masuk (pembalik). */
    public static final String PREFIX_REF_PEMBALIK = "KANTIN-BMP-";

    /** Prefix referensi entri Buku Kas untuk koreksi saldo (penyesuaian). */
    public static final String PREFIX_REF_KOREKSI_SALDO = "KANTIN-KOR-";

    private final BukuKasPort bukuKasPort;
    private final SesiKasirRepository sesiRepo;
    private final PostingBukuKasRepository postingRepo;
    private final SekolahGuard sekolahGuard;
    private final AuditLogger auditLogger;
    private final IdGenerator idGenerator;
    private final JamKantin jam;

    /** Modul referensi yang dikirim ke Buku Kas. Default kosong → {@code null} (mitigasi Q3). */
    @Value("${kantin.bukukas.ref-modul:}")
    private String refModul;

    /** Kategori pos Buku Kas untuk pendapatan kantin (INTEGRATIONS.md §3.3). */
    @Value("${kantin.bukukas.kategori-pendapatan:" + Constants.KATEGORI_PENDAPATAN_KANTIN + "}")
    private String kategoriPendapatan;

    /** Kategori pos Buku Kas untuk belanja stok / barang masuk (INTEGRATIONS.md §3.3). */
    @Value("${kantin.bukukas.kategori-belanja-stok:" + Constants.KATEGORI_BELANJA_STOK_KANTIN + "}")
    private String kategoriBelanjaStok;

    /** Kategori pos Buku Kas untuk koreksi/penyesuaian (INTEGRATIONS.md §3.3). */
    @Value("${kantin.bukukas.kategori-penyesuaian:" + Constants.KATEGORI_PENYESUAIAN_KANTIN + "}")
    private String kategoriPenyesuaian;

    /** Referensi entri Buku Kas untuk sebuah sesi — deterministik &amp; unik. */
    public static String refIdSesi(Long sesiId) {
        return PREFIX_REF_SESI + sesiId;
    }

    /**
     * Referensi entri Buku Kas untuk satu baris barang masuk — deterministik.
     *
     * <p>Nomor bukti boleh memuat beberapa item (kiriman multi-menu), jadi
     * {@code menuId} ikut ke dalam refId agar tiap baris punya entri sendiri
     * tanpa saling menimpa pada kunci idempotency.
     */
    public static String refIdBarangMasuk(String referensiId, Long menuId) {
        return PREFIX_REF_BARANG_MASUK + referensiId + "-" + menuId;
    }

    /** Referensi entri Buku Kas untuk pembalik barang masuk — deterministik. */
    public static String refIdPembalik(String referensiId) {
        return PREFIX_REF_PEMBALIK + referensiId;
    }

    /** Referensi entri Buku Kas untuk koreksi saldo — deterministik &amp; unik. */
    public static String refIdKoreksiSaldo(String referensiId) {
        return PREFIX_REF_KOREKSI_SALDO + referensiId;
    }

    /**
     * Posting total bersih sesi ke Buku Kas. Idempoten.
     *
     * <p>Propagasi {@link Propagation#REQUIRED}: ikut transaksi penutupan sesi
     * bila ada (sehingga flag {@code posting_buku_kas} + audit kompak dengan
     * baris {@code sesi_kasir}), atau membuka transaksi sendiri bila pemanggil
     * tidak punya (mis. jalur auto-tutup lintas-tenant yang menyimpan per-baris).
     * <b>Jangan</b> memakai {@code MANDATORY}: jalur auto-tutup tidak selalu
     * berada dalam satu transaksi, sehingga posting akan gagal hanya karena
     * ketiadaan transaksi.
     *
     * @param sesi    sesi yang sudah DITUTUP (punya {@code totalBersih})
     * @param aktorId pelaku (audit); {@code null} untuk auto-tutup sistem
     * @return hasil posting — {@code DILEWATI} bila belum dikonfigurasi / nominal 0
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public HasilPostingBukuKas postingSesi(SesiKasir sesi, Long aktorId) {
        if (sesi.isPostingBukuKas()) {
            log.debug("Sesi {} sudah diposting (ref={}) — dilewati (idempoten).",
                    sesi.getId(), sesi.getReferensiBukuKas());
            return HasilPostingBukuKas.sukses(sesi.getReferensiBukuKas(), "Sudah diposting sebelumnya");
        }

        long bersih = sesi.getTotalBersih() == null ? 0L : sesi.getTotalBersih();
        if (bersih <= 0) {
            // Tidak ada pendapatan bersih (mis. semua di-void / tidak ada transaksi).
            // Tidak ada yang diposting ke Buku Kas — jangan tandai terposting palsu.
            log.info("Sesi {} total bersih={} — tidak ada yang diposting ke Buku Kas.",
                    sesi.getId(), bersih);
            return HasilPostingBukuKas.dilewati("Total bersih 0 — tidak ada yang diposting");
        }

        String refId = refIdSesi(sesi.getId());
        PerintahBukuKas perintah = PerintahBukuKas.builder()
                .sekolahId(sesi.getSekolahId())
                .tanggal(sesi.getTanggal().atStartOfDay(jam.zona()).toOffsetDateTime())
                .tipe(TipeBukuKas.MASUK)
                .kategori(kategoriPendapatan)
                .jumlah(BigDecimal.valueOf(bersih))
                .metode(MetodeBukuKas.NON_TUNAI)
                .keterangan("Pendapatan kantin sesi " + sesi.getId())
                .refId(refId)
                .refModul(refModulAktif())
                .build();

        HasilPostingBukuKas hasil;
        try {
            hasil = bukuKasPort.catat(perintah);
        } catch (RuntimeException e) {
            // Kegagalan integrasi TIDAK boleh membatalkan penutupan sesi (fail-open kasir).
            log.error("Posting Buku Kas gagal (ref={}): {}", refId, e.getMessage(), e);
            return HasilPostingBukuKas.gagal("Gagal posting ke Buku Kas: " + e.getMessage());
        }

        if (hasil.sukses()) {
            sesi.setPostingBukuKas(true);
            sesi.setReferensiBukuKas(hasil.referensi() != null ? hasil.referensi() : refId);
            sesi.setUpdatedAt(jam.sekarang());
            sesiRepo.save(sesi);

            auditLogger.catat(aktorId, sesi.getSekolahId(), "POSTING_BUKU_KAS", "SesiKasir",
                    String.valueOf(sesi.getId()), null, null, refId);
            log.info("Sesi {} diposting ke Buku Kas (ref={}, jumlah={}, refModul={}).",
                    sesi.getId(), refId, bersih, perintah.getRefModul());
        } else if (hasil.dilewati()) {
            log.warn("Posting Buku Kas sesi {} DILEWATI: {}", sesi.getId(), hasil.pesan());
        } else {
            log.error("Posting Buku Kas sesi {} GAGAL: {}", sesi.getId(), hasil.pesan());
        }
        return hasil;
    }

    /**
     * Posting <b>belanja stok</b> ke Buku Kas saat barang masuk (PRD §5.1, §7.2).
     *
     * <p>Setiap barang masuk diposting sebagai <b>pengeluaran</b> pos
     * {@code "Belanja Stok Kantin"} — {@link TipeBukuKas#KELUAR} /
     * {@link MetodeBukuKas#TUNAI} (pembelian tunai ke pemasok, INTEGRATIONS.md
     * §3.3). Total = {@code qty × hargaBeliPerUnit} (rupiah integer).
     *
     * <p><b>Idempoten</b> lewat tabel penanda {@code posting_buku_kas} (V13):
     * refId deterministik {@code KANTIN-BM-<bukti>-<menuId>} dicek lebih dulu;
     * baris penanda hanya ditulis saat SUKSES sehingga retry jaringan tidak
     * menggandakan entri (Buku Kas admin-be tidak idempoten, §3.4).
     *
     * <p><b>Fail-open:</b> kegagalan/dilewati <b>tidak</b> membatalkan mutasi
     * stok yang sudah tercatat (pola sama seperti {@link #postingSesi}); tanpa
     * penanda, posting bisa diulang lewat retry.
     *
     * @return hasil posting ({@code SUKSES}/{@code DILEWATI}/{@code GAGAL})
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public HasilPostingBukuKas postingBarangMasuk(Long sekolahId, Long mutasiId, Long menuId,
                                                  int qty, long hargaBeliPerUnit,
                                                  String referensiId, Long aktorId) {
        String refId = refIdBarangMasuk(referensiId, menuId);
        if (postingRepo.existsBySekolahIdAndReferensiId(sekolahId, refId)) {
            log.debug("Barang masuk {} sudah diposting (ref={}) — dilewati (idempoten).",
                    mutasiId, refId);
            return HasilPostingBukuKas.sukses(refId, "Sudah diposting sebelumnya");
        }

        long total = (long) qty * hargaBeliPerUnit;
        if (total <= 0) {
            // Barang masuk gratis (harga beli 0) tidak menghasilkan pengeluaran.
            log.info("Barang masuk {} total belanja={} — tidak ada yang diposting.",
                    mutasiId, total);
            return HasilPostingBukuKas.dilewati("Total belanja 0 — tidak ada yang diposting");
        }

        PerintahBukuKas perintah = PerintahBukuKas.builder()
                .sekolahId(sekolahId)
                .tanggal(jam.sekarang())
                .tipe(TipeBukuKas.KELUAR)
                .kategori(kategoriBelanjaStok)
                .jumlah(BigDecimal.valueOf(total))
                .metode(MetodeBukuKas.TUNAI)
                .keterangan("Belanja stok kantin (bukti " + referensiId + ")")
                .refId(refId)
                .refModul(refModulAktif())
                .build();

        HasilPostingBukuKas hasil = kirim(perintah, refId);
        if (hasil.sukses()) {
            simpanPenanda(sekolahId, refId, "BARANG_MASUK", mutasiId, total,
                    hasil.referensi() != null ? hasil.referensi() : refId);
            auditLogger.catat(aktorId, sekolahId, "POSTING_BUKU_KAS", "MutasiStok",
                    String.valueOf(mutasiId), null, null, refId);
            log.info("Barang masuk {} diposting ke Buku Kas (ref={}, total={}).",
                    mutasiId, refId, total);
        }
        return hasil;
    }

    /**
     * Posting <b>entri koreksi</b> saat barang masuk dibalik (PRD §7.2).
     *
     * <p>Koreksi dicatat sebagai entri <b>pembalik</b> ({@link TipeBukuKas#MASUK},
     * pos {@code "Penyesuaian Kantin"}) — entri belanja lama <b>tidak</b> dihapus
     * (konsisten dengan aturan ledger append-only). Total = {@code qty × harga beli}.
     *
     * <p>Idempoten lewat refId deterministik {@code KANTIN-BMP-<bukti pembalik>}.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public HasilPostingBukuKas postingPembalikBarangMasuk(Long sekolahId, Long mutasiId, int qty,
                                                          long hargaBeliAsal, String referensiId,
                                                          Long aktorId) {
        String refId = refIdPembalik(referensiId);
        if (postingRepo.existsBySekolahIdAndReferensiId(sekolahId, refId)) {
            log.debug("Pembalik {} sudah diposting (ref={}) — dilewati (idempoten).",
                    mutasiId, refId);
            return HasilPostingBukuKas.sukses(refId, "Sudah diposting sebelumnya");
        }

        long total = (long) qty * hargaBeliAsal;
        if (total <= 0) {
            log.info("Pembalik {} total koreksi={} — tidak ada yang diposting.", mutasiId, total);
            return HasilPostingBukuKas.dilewati("Total koreksi 0 — tidak ada yang diposting");
        }

        PerintahBukuKas perintah = PerintahBukuKas.builder()
                .sekolahId(sekolahId)
                .tanggal(jam.sekarang())
                .tipe(TipeBukuKas.MASUK)
                .kategori(kategoriPenyesuaian)
                .jumlah(BigDecimal.valueOf(total))
                .metode(MetodeBukuKas.TUNAI)
                .keterangan("Koreksi belanja stok kantin (bukti pembalik " + referensiId + ")")
                .refId(refId)
                .refModul(refModulAktif())
                .build();

        HasilPostingBukuKas hasil = kirim(perintah, refId);
        if (hasil.sukses()) {
            simpanPenanda(sekolahId, refId, "BARANG_MASUK_PEMBALIK", mutasiId, total,
                    hasil.referensi() != null ? hasil.referensi() : refId);
            auditLogger.catat(aktorId, sekolahId, "POSTING_BUKU_KAS", "MutasiStok",
                    String.valueOf(mutasiId), null, null, refId);
            log.info("Pembalik barang masuk {} diposting ke Buku Kas (ref={}, total={}).",
                    mutasiId, refId, total);
        }
        return hasil;
    }

    /**
     * Posting <b>koreksi saldo</b> bendahara ke Buku Kas sebagai entri
     * penyesuaian pos {@code "Penyesuaian Kantin"} (PRD §5.1, §9.2).
     *
     * <p>Koreksi saldo (top-up salah input / transaksi sesi tertutup) dilakukan
     * sebagai mutasi pembalik beralasan; entri lama <b>tidak</b> diubah.
     *
     * <p><b>Arah (menjaga invariant PRD §5):</b> {@code Σ top-up − Σ refund =
     * Σ saldo + Σ penjualan kantin (bersih setelah void &amp; koreksi)}. Karena
     * itu koreksi <b>KREDIT</b> (menambah saldo siswa) menurunkan pendapatan
     * kantin → {@link TipeBukuKas#KELUAR}; koreksi <b>DEBIT</b> (mengurangi
     * saldo) → {@link TipeBukuKas#MASUK}. Metode mengikuti jalur saldo:
     * {@link MetodeBukuKas#NON_TUNAI} (dana titipan/saldo, bukan uang fisik).
     *
     * <p><b>Idempoten</b> lewat refId deterministik {@code KANTIN-KOR-<berita acara>}
     * (nomor berita acara koreksi sudah unik per tenant). Retry tidak
     * menggandakan entri (Buku Kas admin-be tidak idempoten, §3.4).
     *
     * <p><b>Fail-open:</b> kegagalan posting <b>tidak</b> membatalkan koreksi
     * saldo (pola sama seperti {@link #postingBarangMasuk}).
     *
     * @param kredit true bila koreksi menambah saldo (KREDIT), false bila
     *               mengurangi (DEBIT) — menentukan arah entri Buku Kas
     * @param ledgerId id baris {@code saldo_ledger} koreksi (telusur; boleh null)
     * @return hasil posting
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public HasilPostingBukuKas postingKoreksiSaldo(Long sekolahId, boolean kredit, long nominal,
                                                   String referensiId, String alasan,
                                                   Long ledgerId, Long aktorId) {
        String refId = refIdKoreksiSaldo(referensiId);
        if (postingRepo.existsBySekolahIdAndReferensiId(sekolahId, refId)) {
            log.debug("Koreksi saldo {} sudah diposting (ref={}) — dilewati (idempoten).",
                    referensiId, refId);
            return HasilPostingBukuKas.sukses(refId, "Sudah diposting sebelumnya");
        }

        if (nominal <= 0) {
            log.info("Koreksi saldo {} nominal={} — tidak ada yang diposting.", referensiId, nominal);
            return HasilPostingBukuKas.dilewati("Nominal koreksi 0 — tidak ada yang diposting");
        }

        // KREDIT (saldo bertambah) → KELUAR; DEBIT (saldo berkurang) → MASUK.
        // Menjaga invariant PRD §5: koreksi ikut menyesuaikan pendapatan kantin.
        TipeBukuKas tipe = kredit ? TipeBukuKas.KELUAR : TipeBukuKas.MASUK;
        PerintahBukuKas perintah = PerintahBukuKas.builder()
                .sekolahId(sekolahId)
                .tanggal(jam.sekarang())
                .tipe(tipe)
                .kategori(kategoriPenyesuaian)
                .jumlah(BigDecimal.valueOf(nominal))
                .metode(MetodeBukuKas.NON_TUNAI)
                .keterangan("Koreksi saldo kantin (berita acara " + referensiId + ")"
                        + (alasan == null || alasan.isBlank() ? "" : ": " + alasan))
                .refId(refId)
                .refModul(refModulAktif())
                .build();

        HasilPostingBukuKas hasil = kirim(perintah, refId);
        if (hasil.sukses()) {
            simpanPenanda(sekolahId, refId, "KOREKSI_SALDO", ledgerId, nominal,
                    hasil.referensi() != null ? hasil.referensi() : refId);
            auditLogger.catat(aktorId, sekolahId, "POSTING_BUKU_KAS", "Saldo",
                    referensiId, null, null, refId);
            log.info("Koreksi saldo {} diposting ke Buku Kas (ref={}, tipe={}, nominal={}).",
                    referensiId, refId, tipe, nominal);
        }
        return hasil;
    }

    /** Kirim ke port dengan fail-open: exception jaringan → {@code GAGAL}, tak melempar. */
    private HasilPostingBukuKas kirim(PerintahBukuKas perintah, String refId) {
        try {
            HasilPostingBukuKas hasil = bukuKasPort.catat(perintah);
            if (hasil.dilewati()) {
                log.warn("Posting Buku Kas ref={} DILEWATI: {}", refId, hasil.pesan());
            } else if (!hasil.sukses()) {
                log.error("Posting Buku Kas ref={} GAGAL: {}", refId, hasil.pesan());
            }
            return hasil;
        } catch (RuntimeException e) {
            log.error("Posting Buku Kas gagal (ref={}): {}", refId, e.getMessage(), e);
            return HasilPostingBukuKas.gagal("Gagal posting ke Buku Kas: " + e.getMessage());
        }
    }

    /** Tulis baris penanda posting sukses (idempotency jalur cepat). */
    private void simpanPenanda(Long sekolahId, String refId, String entitas, Long mutasiId,
                               long jumlah, String refBukuKas) {
        OffsetDateTime now = jam.sekarang();
        postingRepo.save(PostingBukuKas.builder()
                .id(idGenerator.berikutnyaLong())
                .sekolahId(sekolahId)
                .referensiId(refId)
                .entitas(entitas)
                .mutasiId(mutasiId)
                .refBukuKas(refBukuKas)
                .jumlah(jumlah)
                .status("SUKSES")
                .waktu(now)
                .createdAt(now)
                .build());
    }

    /**
     * Posting ulang sesi yang sudah DITUTUP namun belum terposting (retry manual
     * setelah Q3 terjawab, atau setelah gangguan admin-be).
     *
     * @throws NotFoundEntity       bila sesi bukan milik sekolah pemanggil
     * @throws InvalidOperationException bila sesi masih TERBUKA
     */
    @Transactional
    public HasilPostingBukuKas postingUlang(Long sekolahId, Long sesiId, Long aktorId) {
        SesiKasir sesi = sesiRepo.kunciUntukUpdate(sekolahId, sesiId)
                .orElseThrow(() -> new NotFoundEntity("Sesi kasir tidak ditemukan"));
        sekolahGuard.pastikanMilikSekolah(sesi.getSekolahId(), "Sesi kasir");
        if (sesi.terbuka()) {
            throw new InvalidOperationException("Sesi kasir masih terbuka — tutup dulu sebelum posting");
        }
        return postingSesi(sesi, aktorId);
    }

    /** {@code null} bila belum dikonfigurasi (mitigasi Q3), bukan string kosong. */
    private String refModulAktif() {
        return (refModul == null || refModul.isBlank()) ? null : refModul;
    }
}
