package com.asqi.scholia_kantin_be.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Konfigurasi topologi database kantin (ADR-0010, OPEN-QUESTIONS <b>Q11</b>).
 *
 * <p>Menegakkan jaminan ADR-0001/ADR-0010: <b>DB kantin terpisah dari admin-be</b>.
 * Nilai di bawah mengatur pemeriksaan saat start (lihat
 * {@code PemeriksaTopologiDatabase}):
 * <ul>
 *   <li>{@link #enforce} — bila {@code true}, pelanggaran <b>menggagalkan start</b>
 *       (disarankan di staging/produksi); bila {@code false}, cukup <b>peringatan</b>
 *       (default, agar dev/CI satu host tetap lancar).</li>
 *   <li>{@link #hostDbAdminBe} — host server DB admin-be, bila diketahui. Sama
 *       dengan host DB kantin ⇒ peringatan (produksi sebaiknya terpisah).</li>
 * </ul>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kantin.topologi")
public class TopologiProperties {

    /**
     * Bila {@code true}, kantin-be <b>menolak start</b> ketika menunjuk DB
     * admin-be (pelanggaran isolasi). Default {@code false} = peringatan saja
     * (aman untuk dev/CI). Aktifkan di staging/produksi.
     */
    private boolean enforce = false;

    /**
     * Host server DB admin-be (opsional). Bila diisi &amp; <b>sama</b> dengan host
     * DB kantin, dicatat sebagai peringatan: produksi sebaiknya server terpisah
     * (ADR-0010 §Keputusan-2). Kosong = pemeriksaan dilewati.
     */
    private String hostDbAdminBe = "";

    /**
     * Nama database yang <b>dilarang</b> dipakai kantin-be (milik modul lain).
     * Bila datasource kantin menunjuk salah satunya ⇒ pelanggaran (bukan hanya
     * peringatan), karena berarti kantin-be menulis ke DB modul lain.
     */
    private List<String> dbDilarang = List.of(
            "admin_db", "adminbe", "skoolia_admin", "mobile_db", "internal_db", "callback_db");
}
