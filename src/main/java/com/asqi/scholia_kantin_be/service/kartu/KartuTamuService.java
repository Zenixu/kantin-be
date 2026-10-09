package com.asqi.scholia_kantin_be.service.kartu;

import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.component.exception.NotFoundEntity;
import com.asqi.scholia_kantin_be.component.logging.AuditLogger;
import com.asqi.scholia_kantin_be.model.KartuTamu;
import com.asqi.scholia_kantin_be.repository.KartuTamuRepository;
import com.asqi.scholia_kantin_be.service.integrasi.UidSiswaPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service untuk CRUD {@link KartuTamu}.
 *
 * <p>Kartu Tamu = kartu RFID untuk non-siswa (guru/staf/tamu). Saldo terikat
 * ke nomor kartu (bukan orang). Diisi tunai di TU (PRD §9.4).
 *
 * <p><b>Anti-tabrakan UID (issue #29):</b> {@code rfidUid} UNIQUE global —
 * tidak boleh sama dengan {@code rfid_uid} siswa di admin-be. Validasi
 * <b>dua arah</b>:
 * <ul>
 *   <li><b>kantin-be (lokal):</b> tolak UID yang sudah dipakai Kartu Tamu lain
 *       ({@link KartuTamuRepository#existsByRfidUidExcluding});</li>
 *   <li><b>kantin-be → admin-be:</b> tolak UID yang sudah dipakai siswa lewat
 *       {@link UidSiswaPort} (fail-open sampai Q7 terjawab);</li>
 *   <li><b>admin-be → kantin-be:</b> admin-be menanyakan UID Kartu Tamu lewat
 *       endpoint internal {@code GET /api/internal/kartu-tamu/cek-uid} agar
 *       {@code SiswaService} menolak UID yang sudah dipakai Kartu Tamu.</li>
 * </ul>
 *
 * <p><b>Jejak audit (PRD §11.7, issue #148):</b> perubahan kartu tamu berdampak
 * pada uang (saldo) dan identitas pemegang, jadi wajib diaudit. {@link #updateKartu}
 * menulis aksi {@code UBAH_KARTU} (termasuk rebind/lepas {@code rfidUid} — UID
 * lama → baru dicatat) dan {@link #nonaktifkanKartu} menulis
 * {@code NONAKTIFKAN_KARTU}, lewat {@link AuditLogger}.
 */
@Service
@RequiredArgsConstructor
public class KartuTamuService {

    private final KartuTamuRepository repo;
    private final IdGenerator idGenerator;
    private final UidSiswaPort uidSiswaPort;
    private final AuditLogger auditLogger;

    /** Prefix nomor kartu tamu (PRD §9.4: mis. KT-012). */
    public static final String PREFIX_NOMOR = "KT-";

    /** Nama entitas pada jejak audit (PRD §11.7). */
    private static final String ENTITAS = "KartuTamu";

    /**
     * Generate nomor kartu berikutnya untuk sekolah (PRD §9.4, issue #121):
     * {@code KT-} + urutan numerik (minimal 3 digit, mis. {@code KT-001},
     * {@code KT-012}, {@code KT-1000}). Nomor unik per sekolah.
     */
    @Transactional(readOnly = true)
    public String generateNomorKartu(Long sekolahId) {
        int berikutnya = repo.nomorUrutTerakhir(sekolahId) + 1;
        return PREFIX_NOMOR + String.format("%03d", berikutnya);
    }

    /**
     * Buat kartu tamu baru (PRD §9.4).
     *
     * <p><b>Nomor kartu digenerate otomatis</b> bila {@code nomorKartu} kosong
     * (KT- + urutan per sekolah); isi manual untuk override.
     *
     * @param sekolahId     tenant
     * @param nomorKartu    nomor kartu human-readable; {@code null}/blank = generate
     * @param rfidUid       UID RFID (nullable jika belum di-bind)
     * @param catatan       catatan bebas (nullable)
     * @param labelPemegang label pemegang opsional (nama guru/staf, "Tamu")
     * @param dibuatOleh    user ID pembuat
     * @return kartu yang baru dibuat
     * @throws ConflictException jika nomor kartu sudah ada atau UID bentrok
     */
    @Transactional
    public KartuTamu buatKartu(Long sekolahId, String nomorKartu, String rfidUid,
                               String catatan, String labelPemegang, Long dibuatOleh) {
        String nomor = (nomorKartu == null || nomorKartu.isBlank())
                ? generateNomorKartu(sekolahId)
                : nomorKartu.trim();

        // Validasi: nomor kartu sudah ada?
        if (repo.findBySekolahIdAndNomorKartu(sekolahId, nomor).isPresent()) {
            throw new ConflictException("Nomor kartu " + nomor + " sudah digunakan");
        }

        // Validasi: UID bentrok dengan kartu lain?
        if (rfidUid != null && !rfidUid.isBlank()) {
            if (repo.existsByRfidUidExcluding(rfidUid, null)) {
                throw new ConflictException("RFID UID " + rfidUid + " sudah terdaftar pada kartu lain");
            }
            tolakBilaDipakaiSiswa(sekolahId, rfidUid);
        }

        KartuTamu kartu = KartuTamu.builder()
                .id(idGenerator.berikutnya())
                .sekolahId(sekolahId)
                .nomorKartu(nomor)
                .rfidUid(rfidUid != null && !rfidUid.isBlank() ? rfidUid : null)
                .aktif(true)
                .catatan(catatan)
                .labelPemegang(bersih(labelPemegang))
                .dibuatOleh(dibuatOleh)
                .dibuatPada(Instant.now())
                .build();

        return repo.save(kartu);
    }

    /**
     * Overload kompatibilitas: buat kartu tanpa label pemegang.
     *
     * @see #buatKartu(Long, String, String, String, String, Long)
     */
    @Transactional
    public KartuTamu buatKartu(Long sekolahId, String nomorKartu, String rfidUid,
                               String catatan, Long dibuatOleh) {
        return buatKartu(sekolahId, nomorKartu, rfidUid, catatan, null, dibuatOleh);
    }

    /**
     * Update kartu tamu (nomor, UID, catatan, label pemegang, status aktif).
     *
     * <p>Menulis jejak audit {@code UBAH_KARTU} (PRD §11.7, issue #148) berisi
     * field yang berubah; khusus perubahan {@code rfidUid} dicatat UID lama → baru
     * (rebind/lepas binding) agar dapat ditelusuri saat sengketa.
     *
     * @param sekolahId     tenant
     * @param kartuId       ID kartu yang mau diupdate
     * @param nomorKartu    nomor baru (nullable = tidak diubah)
     * @param rfidUid       UID baru (nullable = tidak diubah, "" = hapus binding)
     * @param catatan       catatan baru (nullable = tidak diubah)
     * @param labelPemegang label pemegang baru (nullable = tidak diubah,
     *                      "" = <b>kosongkan</b>, mis. saat pengembalian kartu)
     * @param aktif         status aktif (nullable = tidak diubah)
     * @param diubahOleh    user ID pengubah
     * @return kartu yang sudah diupdate
     * @throws NotFoundEntity    jika kartu tidak ada atau beda sekolah
     * @throws ConflictException jika nomor/UID bentrok
     */
    @Transactional
    public KartuTamu updateKartu(Long sekolahId, Long kartuId, String nomorKartu,
                                 String rfidUid, String catatan, String labelPemegang,
                                 Boolean aktif, Long diubahOleh) {
        KartuTamu kartu = ambilTerproteksi(sekolahId, kartuId);

        // Snapshot nilai lama (untuk jejak audit) sebelum mutasi.
        String nomorLama = kartu.getNomorKartu();
        String uidLama = kartu.getRfidUid();
        String catatanLama = kartu.getCatatan();
        String labelLama = kartu.getLabelPemegang();
        boolean aktifLama = Boolean.TRUE.equals(kartu.getAktif());

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
                // Bind UID baru — cek bentrok (Kartu Tamu lain + siswa admin-be)
                if (repo.existsByRfidUidExcluding(rfidUid, kartuId)) {
                    throw new ConflictException("RFID UID " + rfidUid + " sudah terdaftar pada kartu lain");
                }
                tolakBilaDipakaiSiswa(sekolahId, rfidUid);
                kartu.setRfidUid(rfidUid);
            }
        }

        // Update catatan (jika ada)
        if (catatan != null) {
            kartu.setCatatan(catatan);
        }

        // Update label pemegang (jika ada). String kosong/blank = kosongkan
        // (PRD §9.4: label dikosongkan saat kartu dikembalikan).
        if (labelPemegang != null) {
            kartu.setLabelPemegang(bersih(labelPemegang));
        }

        // Update status aktif (jika ada)
        if (aktif != null) {
            kartu.setAktif(aktif);
        }

        kartu.setDiubahOleh(diubahOleh);
        kartu.setDiubahPada(Instant.now());

        KartuTamu tersimpan = repo.save(kartu);

        catatUbahKartu(sekolahId, kartuId, diubahOleh,
                nomorLama, tersimpan.getNomorKartu(),
                uidLama, tersimpan.getRfidUid(),
                catatanLama, tersimpan.getCatatan(),
                labelLama, tersimpan.getLabelPemegang(),
                aktifLama, Boolean.TRUE.equals(tersimpan.getAktif()));

        return tersimpan;
    }

    /**
     * Overload kompatibilitas: update kartu tanpa menyentuh label pemegang.
     *
     * @see #updateKartu(Long, Long, String, String, String, String, Boolean, Long)
     */
    @Transactional
    public KartuTamu updateKartu(Long sekolahId, Long kartuId, String nomorKartu,
                                 String rfidUid, String catatan, Boolean aktif,
                                 Long diubahOleh) {
        return updateKartu(sekolahId, kartuId, nomorKartu, rfidUid, catatan, null, aktif, diubahOleh);
    }

    /**
     * Nonaktifkan kartu (soft delete).
     *
     * <p>Menulis jejak audit {@code NONAKTIFKAN_KARTU} (PRD §11.7, issue #148) —
     * kartu nonaktif tak bisa dipakai tap, jadi perubahan status ini sensitif.
     *
     * @param sekolahId  tenant
     * @param kartuId    ID kartu
     * @param diubahOleh user ID pengubah
     * @return kartu yang sudah dinonaktifkan
     * @throws NotFoundEntity jika kartu tidak ada atau beda sekolah
     */
    @Transactional
    public KartuTamu nonaktifkanKartu(Long sekolahId, Long kartuId, Long diubahOleh) {
        KartuTamu kartu = ambilTerproteksi(sekolahId, kartuId);
        boolean aktifLama = Boolean.TRUE.equals(kartu.getAktif());

        kartu.setAktif(false);
        kartu.setDiubahOleh(diubahOleh);
        kartu.setDiubahPada(Instant.now());

        KartuTamu tersimpan = repo.save(kartu);

        auditLogger.catat(diubahOleh, sekolahId, "NONAKTIFKAN_KARTU", ENTITAS,
                String.valueOf(kartuId), null,
                "aktif=" + aktifLama, "aktif=false");

        return tersimpan;
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
     * Cek apakah sebuah RFID UID sudah dipakai Kartu Tamu mana pun (issue #29).
     *
     * <p>Dipakai endpoint internal (mesin-ke-mesin, HMAC) agar admin-be dapat
     * menolak {@code rfid_uid} siswa yang sudah dipakai Kartu Tamu. UID bersifat
     * UNIQUE global, jadi tidak dibatasi tenant.
     *
     * @param rfidUid UID yang dicek
     * @return {@code true} bila UID dipakai Kartu Tamu
     */
    @Transactional(readOnly = true)
    public boolean dipakaiKartuTamu(String rfidUid) {
        return rfidUid != null && !rfidUid.isBlank() && repo.existsByRfidUid(rfidUid);
    }

    // ────────────────────────── internal ──────────────────────────

    /** Ambil kartu + guard tenant (404 bila tak ada / beda sekolah, PRD §11.4). */
    private KartuTamu ambilTerproteksi(Long sekolahId, Long kartuId) {
        KartuTamu kartu = repo.findById(kartuId)
                .orElseThrow(() -> new NotFoundEntity("Kartu tamu tidak ditemukan"));
        if (!kartu.getSekolahId().equals(sekolahId)) {
            throw new NotFoundEntity("Kartu tamu tidak ditemukan");
        }
        return kartu;
    }

    /**
     * Tulis jejak audit {@code UBAH_KARTU} — hanya field yang benar-benar
     * berubah yang dicatat. UID RFID (lama → baru) jadi perhatian utama karena
     * mengubah identitas fisik pemegang kartu (issue #148).
     */
    private void catatUbahKartu(Long sekolahId, Long kartuId, Long aktorId,
                                String nomorLama, String nomorBaru,
                                String uidLama, String uidBaru,
                                String catatanLama, String catatanBaru,
                                String labelLama, String labelBaru,
                                boolean aktifLama, boolean aktifBaru) {
        List<String> lama = new ArrayList<>();
        List<String> baru = new ArrayList<>();
        List<String> bidang = new ArrayList<>();

        if (!Objects.equals(nomorLama, nomorBaru)) {
            bidang.add("nomorKartu");
            lama.add("nomorKartu=" + tampil(nomorLama));
            baru.add("nomorKartu=" + tampil(nomorBaru));
        }
        if (!Objects.equals(uidLama, uidBaru)) {
            bidang.add("rfidUid");
            lama.add("rfidUid=" + tampil(uidLama));
            baru.add("rfidUid=" + tampil(uidBaru));
        }
        if (!Objects.equals(catatanLama, catatanBaru)) {
            bidang.add("catatan");
            lama.add("catatan=" + tampil(catatanLama));
            baru.add("catatan=" + tampil(catatanBaru));
        }
        if (!Objects.equals(labelLama, labelBaru)) {
            bidang.add("labelPemegang");
            lama.add("labelPemegang=" + tampil(labelLama));
            baru.add("labelPemegang=" + tampil(labelBaru));
        }
        if (aktifLama != aktifBaru) {
            bidang.add("aktif");
            lama.add("aktif=" + aktifLama);
            baru.add("aktif=" + aktifBaru);
        }

        if (bidang.isEmpty()) {
            // Tidak ada field berubah — tak ada jejak berarti untuk ditulis.
            return;
        }

        auditLogger.catat(aktorId, sekolahId, "UBAH_KARTU", ENTITAS,
                String.valueOf(kartuId),
                "Ubah field: " + String.join(", ", bidang),
                String.join("; ", lama), String.join("; ", baru));
    }

    /** Tampilkan nilai audit: {@code null}/blank → {@code (kosong)}. */
    private static String tampil(String nilai) {
        return (nilai == null || nilai.isBlank()) ? "(kosong)" : nilai;
    }

    /**
     * Tolak bila UID sudah dipakai siswa di admin-be (anti-tabrakan, issue #29).
     *
     * <p><b>Fail-open:</b> {@link UidSiswaPort} mengembalikan {@code null} bila
     * integrasi belum siap (Q7) — registrasi tidak diblokir keliru. Hanya
     * {@code Boolean.TRUE} yang menolak.
     */
    private void tolakBilaDipakaiSiswa(Long sekolahId, String rfidUid) {
        if (Boolean.TRUE.equals(uidSiswaPort.dipakaiSiswa(sekolahId, rfidUid))) {
            throw new ConflictException(
                    "RFID UID " + rfidUid + " sudah terdaftar sebagai kartu siswa");
        }
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

    /** Normalisasi label: trim; blank → {@code null} (dikosongkan). */
    private static String bersih(String teks) {
        if (teks == null) {
            return null;
        }
        String t = teks.trim();
        return t.isEmpty() ? null : t;
    }
}
