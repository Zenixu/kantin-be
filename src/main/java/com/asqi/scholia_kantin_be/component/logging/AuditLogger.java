package com.asqi.scholia_kantin_be.component.logging;

import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.helper.JamKantin;
import com.asqi.scholia_kantin_be.model.AuditLog;
import com.asqi.scholia_kantin_be.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pencatat jejak audit sensitif (PRD §11.7).
 *
 * <p>Menulis baris {@code audit_log} (tabel append-only) <b>dan</b> log
 * aplikasi terstruktur. Berjalan dengan propagasi {@link Propagation#MANDATORY}
 * — audit ikut transaksi aksi yang diaudit, sehingga jejak &amp; aksi
 * sungguh-sungguh kompak (audit gagal ⇒ aksi gagal, tidak ada aksi tanpa jejak).
 *
 * <p><b>Catatan pemanggil:</b> method ini <b>wajib</b> dipanggil di dalam
 * transaksi yang sudah berjalan (semua service domain sudah
 * {@code @Transactional}). Untuk aksi sistem di luar transaksi (mis. scheduler),
 * bungkus pemanggilan dalam transaksi.
 *
 * <p>Aksi yang wajib diaudit (PRD §11.7): void, koreksi, refund/pindah saldo,
 * ubah harga jual, barang masuk &amp; pembaliknya, penyesuaian stok (opname),
 * ubah limit/blokir. Top-up juga dicatat.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogger {

    private final AuditLogRepository auditRepo;
    private final IdGenerator idGenerator;
    private final JamKantin jam;

    /**
     * Catat satu aksi audit (tulis baris DB + log).
     *
     * @param aktorId   user pelaku (boleh {@code null} untuk aksi sistem)
     * @param sekolahId tenant (wajib — scoping PRD §11.4)
     * @param aksi      nama aksi (mis. {@code VOID_TRANSAKSI}, {@code OPNAME_STOK})
     * @param entitas   nama entitas (mis. {@code Transaksi}, {@code Stok})
     * @param entitasId id entitas (teks)
     * @param alasan    alasan (wajib untuk void/koreksi/opname)
     * @param nilaiLama nilai sebelum (boleh {@code null})
     * @param nilaiBaru nilai sesudah (boleh {@code null})
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void catat(Long aktorId, Long sekolahId, String aksi, String entitas,
                      String entitasId, String alasan, String nilaiLama, String nilaiBaru) {
        if (sekolahId == null) {
            throw new IllegalArgumentException("sekolahId wajib untuk audit (PRD §11.4)");
        }
        if (aksi == null || aksi.isBlank()) {
            throw new IllegalArgumentException("aksi audit wajib diisi");
        }
        if (entitas == null || entitas.isBlank()) {
            throw new IllegalArgumentException("entitas audit wajib diisi");
        }

        OffsetDateTime now = jam.sekarang();
        AuditLog baris = AuditLog.builder()
                .id(idGenerator.berikutnyaLong())
                .sekolahId(sekolahId)
                .aktorId(aktorId)
                .aksi(aksi)
                .entitas(entitas)
                .entitasId(entitasId)
                .nilaiLama(nilaiLama)
                .nilaiBaru(nilaiBaru)
                .alasan(potong(alasan, 500))
                .waktu(now)
                .createdAt(now)
                .build();
        auditRepo.save(baris);

        // Log terstruktur tetap dipertahankan (observability + fallback grep).
        Map<String, Object> entri = new LinkedHashMap<>();
        entri.put("audit", true);
        entri.put("aksi", aksi);
        entri.put("sekolahId", sekolahId);
        entri.put("aktorId", aktorId);
        entri.put("entitas", entitas);
        entri.put("entitasId", entitasId);
        entri.put("nilaiLama", nilaiLama);
        entri.put("nilaiBaru", nilaiBaru);
        entri.put("alasan", alasan);
        log.info("AUDIT {}", entri);
    }

    /** Potong agar aman terhadap batas kolom DB (audit tak boleh gagal karena panjang). */
    private static String potong(String s, int maks) {
        if (s == null || s.length() <= maks) {
            return s;
        }
        return s.substring(0, maks);
    }
}
