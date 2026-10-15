package com.asqi.scholia_kantin_be.docs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Penjaga agar <b>angka di header {@code architecture/API-ENDPOINTS.md}</b> tidak basi
 * (issue <b>#165</b>).
 *
 * <p><b>Masalah yang dicegah:</b> {@code ApiEndpointsDocTest} menjaga <b>daftar</b>
 * endpoint (tabel ↔ anotasi kode), tetapi <b>tidak</b> menjaga <b>angka</b> yang
 * ditulis di header dokumen — dan angka itu memang pernah basi (issue #162 menemukan
 * header masih menulis 86 padahal kode 87).
 *
 * <p><b>Yang dijaga:</b>
 * <ul>
 *   <li>{@code Total: N endpoint} == jumlah baris tabel endpoint;</li>
 *   <li>{@code di N controller} == jumlah kelas controller nyata
 *       ({@code controller/} + shim dev).</li>
 * </ul>
 *
 * <p>Angka statistik <b>tidak lagi</b> ditulis di {@code README.md} (sengaja dihapus
 * agar tidak cepat basi — PR #208), sehingga tidak ada lagi yang perlu dijaga di sana.
 *
 * <p><b>Cara memperbaiki bila gagal:</b> perbarui angka di header
 * {@code architecture/API-ENDPOINTS.md} (beserta tanggal "Sinkron terakhir").
 */
@DisplayName("Angka header API-ENDPOINTS.md sinkron dengan tabel & kode (#165)")
class StatistikDokumenTest {

    /** Lokasi dokumen relatif root proyek (Maven menjalankan test dari root). */
    private static final String[] KANDIDAT_ENDPOINT = {
            "architecture/API-ENDPOINTS.md",
            "../architecture/API-ENDPOINTS.md"};

    /** Direktori berisi kelas controller (termasuk shim dev). */
    private static final List<Path> DIREKTORI_CONTROLLER = List.of(
            Paths.get("src/main/java/com/asqi/scholia_kantin_be/controller"),
            Paths.get("src/main/java/com/asqi/scholia_kantin_be/dev"));

    /** Baris tabel: | `GET` | `/api/...` | … (sama dengan ApiEndpointsDocTest). */
    private static final Pattern BARIS_ENDPOINT = Pattern.compile(
            "^\\|\\s*`(GET|POST|PUT|DELETE|PATCH)`\\s*\\|\\s*`([^`]+)`");

    // ────────────────────────────────────────────────────────────────
    // Uji
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Header 'Total: N endpoint' == jumlah baris tabel")
    void totalEndpointSinkron() {
        String dokumen = baca(KANDIDAT_ENDPOINT);
        int dariTabel = jumlahBarisEndpoint(dokumen);
        int dariHeader = angkaTunggal(dokumen,
                "\\*\\*Total:\\s*(\\d+)\\s*endpoint\\*\\*",
                "header API-ENDPOINTS.md ('**Total: N endpoint**')");

        assertThat(dariTabel)
                .as("Tabel API-ENDPOINTS.md tidak wajar (parsing gagal?)")
                .isGreaterThan(50);
        assertThat(dariHeader)
                .as("Header API-ENDPOINTS.md basi — perbarui 'Total: N endpoint' "
                        + "(angka nyata: %d) beserta tanggal 'Sinkron terakhir'", dariTabel)
                .isEqualTo(dariTabel);
    }

    @Test
    @DisplayName("Header 'di N controller' == jumlah kelas controller nyata")
    void jumlahControllerSinkron() {
        int nyata = (int) DIREKTORI_CONTROLLER.stream()
                .mapToLong(d -> hitungBerkas(d, "Controller.java"))
                .sum();
        int dariHeader = angkaTunggal(baca(KANDIDAT_ENDPOINT),
                "di \\*\\*(\\d+)\\s*controller\\*\\*",
                "header API-ENDPOINTS.md ('di **N controller**')");

        assertThat(nyata)
                .as("Tidak ada kelas controller yang ditemukan — periksa DIREKTORI_CONTROLLER")
                .isGreaterThan(5);
        assertThat(dariHeader)
                .as("Header API-ENDPOINTS.md basi — perbarui 'di **N controller**' "
                        + "(angka nyata: %d)", nyata)
                .isEqualTo(nyata);
    }

    // ────────────────────────────────────────────────────────────────
    // Pembantu
    // ────────────────────────────────────────────────────────────────

    private int jumlahBarisEndpoint(String dokumen) {
        int jumlah = 0;
        for (String baris : dokumen.split("\\R")) {
            if (BARIS_ENDPOINT.matcher(baris.trim()).find()) {
                jumlah++;
            }
        }
        return jumlah;
    }

    private long hitungBerkas(Path direktori, String akhiran) {
        assertThat(Files.isDirectory(direktori))
                .as("Direktori tidak ditemukan dari cwd %s: %s",
                        Paths.get("").toAbsolutePath(), direktori)
                .isTrue();
        try (Stream<Path> berkas = Files.list(direktori)) {
            return berkas
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(akhiran))
                    .count();
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal memindai " + direktori, e);
        }
    }

    private String baca(String[] kandidat) {
        for (String nama : kandidat) {
            Path p = Paths.get(nama);
            if (Files.exists(p)) {
                try {
                    return Files.readString(p, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException("Gagal membaca " + p, e);
                }
            }
        }
        throw new IllegalStateException("Dokumen tidak ditemukan dari cwd "
                + Paths.get("").toAbsolutePath() + ": " + String.join(", ", kandidat));
    }

    private int angkaTunggal(String isi, String regex, String label) {
        List<Integer> angka = semuaAngka(isi, regex);
        assertThat(angka)
                .as("Tidak menemukan angka %s — pola dokumen berubah?", label)
                .hasSize(1);
        return angka.get(0);
    }

    private List<Integer> semuaAngka(String isi, String regex) {
        Matcher m = Pattern.compile(regex).matcher(isi);
        List<Integer> hasil = new ArrayList<>();
        while (m.find()) {
            hasil.add(Integer.parseInt(m.group(1)));
        }
        return hasil;
    }
}
