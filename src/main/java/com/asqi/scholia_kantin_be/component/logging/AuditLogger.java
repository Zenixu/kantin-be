package com.asqi.scholia_kantin_be.component.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pencatat jejak audit terstruktur untuk aksi sensitif (PRD §11.7).
 *
 * <p><b>Status:</b> implementasi saat ini menulis ke log aplikasi dengan format
 * terstruktur (kunci=nilai) sebagai <b>placeholder</b>. Tabel {@code audit_log}
 * &amp; {@code service/audit} (MODULE-MAP) belum dibuat — itu bagian modul
 * audit lintas-modul (membutuhkan migrasi Flyway baru). Ketika tabel tersedia,
 * cukup ganti isi {@link #catat} agar juga menyimpan baris; kontrak pemanggil
 * (service domain) tidak berubah.
 *
 * <p>Aksi yang wajib diaudit (PRD §11.7): void, koreksi, refund/pindah saldo,
 * ubah harga jual, barang masuk &amp; pembaliknya, penyesuaian stok, ubah
 * limit/blokir.
 */
@Component
@Slf4j
public class AuditLogger {

    /**
     * Catat satu aksi audit.
     *
     * @param aktorId   user pelaku
     * @param sekolahId tenant
     * @param aksi      nama aksi (mis. {@code VOID_TRANSAKSI})
     * @param entitas   nama entitas (mis. {@code Transaksi})
     * @param entitasId id entitas
     * @param alasan    alasan (wajib untuk void/koreksi)
     * @param nilaiLama nilai sebelum (boleh null)
     * @param nilaiBaru nilai sesudah (boleh null)
     */
    public void catat(Long aktorId, Long sekolahId, String aksi, String entitas,
                      String entitasId, String alasan, String nilaiLama, String nilaiBaru) {
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
}
