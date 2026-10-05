package com.asqi.scholia_kantin_be.service.kartu;

import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.model.KartuTamu;
import com.asqi.scholia_kantin_be.repository.KartuTamuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service untuk CRUD {@link KartuTamu}.
 *
 * <p>Kartu Tamu = kartu RFID untuk non-siswa (guru/staf/tamu). Saldo terikat
 * ke nomor kartu (bukan orang). Diisi tunai di TU (PRD §9.4).
 *
 * <p><b>Anti-tabrakan UID:</b> {@code rfidUid} UNIQUE global — tidak boleh
 * sama dengan {@code rfid_uid} siswa di admin-be. Validasi sisi kantin-be:
 * cek {@link KartuTamuRepository#existsByRfidUidExcluding}. Validasi sisi
 * admin-be: akan ditambahkan di SiswaService (TODO Q-koordinasi).
 */
@Service
@RequiredArgsConstructor
public class KartuTamuService {

    private final KartuTamuRepository repo;
    private final IdGenerator idGenerator;

    /**
     * Buat kartu tamu baru.
     *
     * @param sekolahId   tenant
     * @param nomorKartu  nomor kartu human-readable (KT-001, dll)
     * @param rfidUid     UID RFID (nullable jika belum di-bind)
     * @param catatan     catatan bebas (nullable)
     * @param dibuatOleh  user ID pembuat
     * @return kartu yang baru dibuat
     * @throws ConflictException jika nomor kartu sudah ada atau UID bentrok
     */
    @Transactional
    public KartuTamu buatKartu(Long sekolahId, String nomorKartu, String rfidUid,
                               String catatan, Long dibuatOleh) {
        // Validasi: nomor kartu sudah ada?
        if (repo.findBySekolahIdAndNomorKartu(sekolahId, nomorKartu).isPresent()) {
            throw new ConflictException("Nomor kartu " + nomorKartu + " sudah digunakan");
        }

        // Validasi: UID bentrok dengan kartu lain?
        if (rfidUid != null && !rfidUid.isBlank()) {
            if (repo.existsByRfidUidExcluding(rfidUid, null)) {
                throw new ConflictException("RFID UID " + rfidUid + " sudah terdaftar pada kartu lain");
            }
        }

        KartuTamu kartu = KartuTamu.builder()
                .id(idGenerator.berikutnya())
                .sekolahId(sekolahId)
                .nomorKartu(nomorKartu)
                .rfidUid(rfidUid != null && !rfidUid.isBlank() ? rfidUid : null)
                .aktif(true)
                .catatan(catatan)
                .dibuatOleh(dibuatOleh)
                .dibuatPada(Instant.now())
                .build();

        return repo.save(kartu);
    }

    /**
     * Update kartu tamu (nomor, UID, catatan, status aktif).
     *
     * @param sekolahId    tenant
     * @param kartuId      ID kartu yang mau diupdate
     * @param nomorKartu   nomor baru (nullable = tidak diubah)
     * @param rfidUid      UID baru (nullable = tidak diubah, "" = hapus binding)
     * @param catatan      catatan baru (nullable = tidak diubah)
     * @param aktif        status aktif (nullable = tidak diubah)
     * @param diubahOleh   user ID pengubah
     * @return kartu yang sudah diupdate
     * @throws NotFoundEntity    jika kartu tidak ada atau beda sekolah
     * @throws ConflictException jika nomor/UID bentrok
     */
    @Transactional
    public KartuTamu updateKartu(Long sekolahId, Long kartuId, String nomorKartu,
                                 String rfidUid, String catatan, Boolean aktif,
                                 Long diubahOleh) {
        KartuTamu kartu = repo.findById(kartuId)
                .orElseThrow(() -> new NotFoundEntity("Kartu tamu tidak ditemukan"));

        // Guard tenant
        if (!kartu.getSekolahId().equals(sekolahId)) {
            throw new NotFoundEntity("Kartu tamu tidak ditemukan");
        }

        // Update nomor kartu (jika ada)
        if (nomorKartu != null && !nomorKartu.equals(kartu.getNomorKartu())) {
            // Cek bentrok dengan kartu lain
            if (repo.findBySekolahIdAndNomorKartu(sekolahId, nomorKartu).isPresent()) {
                throw new ConflictException("Nomor kartu " + nomorKartu + " sudah digunakan");
            }
            kartu.setNomorKartu(nomorKartu);
        }

        // Update RFID UID (jika ada)
        if (rfidUid != null) {
            if (rfidUid.isBlank()) {
                // Hapus binding (unbind)
                kartu.setRfidUid(null);
            } else if (!rfidUid.equals(kartu.getRfidUid())) {
                // Bind UID baru — cek bentrok
                if (repo.existsByRfidUidExcluding(rfidUid, kartuId)) {
                    throw new ConflictException("RFID UID " + rfidUid + " sudah terdaftar pada kartu lain");
                }
                kartu.setRfidUid(rfidUid);
            }
        }

        // Update catatan (jika ada)
        if (catatan != null) {
            kartu.setCatatan(catatan);
        }

        // Update status aktif (jika ada)
        if (aktif != null) {
            kartu.setAktif(aktif);
        }

        kartu.setDiubahOleh(diubahOleh);
        kartu.setDiubahPada(Instant.now());

        return repo.save(kartu);
    }

    /**
     * Nonaktifkan kartu (soft delete).
     *
     * @param sekolahId  tenant
     * @param kartuId    ID kartu
     * @param diubahOleh user ID pengubah
     * @return kartu yang sudah dinonaktifkan
     * @throws NotFoundEntity jika kartu tidak ada atau beda sekolah
     */
    @Transactional
    public KartuTamu nonaktifkanKartu(Long sekolahId, Long kartuId, Long diubahOleh) {
        return updateKartu(sekolahId, kartuId, null, null, null, false, diubahOleh);
    }

    /**
     * Daftar semua kartu tamu milik sekolah.
     *
     * @param sekolahId  tenant
     * @param hanyaAktif jika {@code true}, hanya kartu aktif
     * @return list kartu (bisa kosong)
     */
    @Transactional(readOnly = true)
    public List<KartuTamu> daftarKartu(Long sekolahId, boolean hanyaAktif) {
        return repo.findAllBySekolah(sekolahId, hanyaAktif);
    }

    /**
     * Ambil detail 1 kartu.
     *
     * @param sekolahId tenant
     * @param kartuId   ID kartu
     * @return kartu
     * @throws NotFoundEntity jika tidak ada atau beda sekolah
     */
    @Transactional(readOnly = true)
    public KartuTamu detailKartu(Long sekolahId, Long kartuId) {
        KartuTamu kartu = repo.findById(kartuId)
                .orElseThrow(() -> new NotFoundEntity("Kartu tamu tidak ditemukan"));

        if (!kartu.getSekolahId().equals(sekolahId)) {
            throw new NotFoundEntity("Kartu tamu tidak ditemukan");
        }

        return kartu;
    }

    /**
     * Cari kartu berdasarkan RFID UID (untuk lookup saat tap).
     *
     * @param rfidUid UID kartu
     * @return kartu jika ditemukan
     * @throws NotFoundEntity jika UID tidak terdaftar
     */
    @Transactional(readOnly = true)
    public KartuTamu cariByRfidUid(String rfidUid) {
        return repo.findByRfidUid(rfidUid)
                .orElseThrow(() -> new NotFoundEntity("Kartu dengan UID " + rfidUid + " tidak ditemukan"));
    }
}
