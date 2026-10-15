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
 * Penjaga agar <b>angka statistik</b> di {@code README.md} dan
 * {@code architecture/API-ENDPOINTS.md} tidak basi (issue <b>#165</b>).
 *
 * <p><b>Masalah yang dicegah:</b> angka "87 endpoint", "22 migrasi Flyway",
 * "256 unit test" diperbarui manual dan sudah <b>tiga kali</b> tertinggal dari kode
 * (#162 → #195 → audit ulang #165: 89 endpoint / 23 migrasi / 260 unit test).
 * {@code ApiEndpointsDocTest} menjaga <b>daftar</b> endpoint, tetapi <b>tidak</b>
 * menjaga angka total di header dokumen maupun di README.
 *
 * <p><b>Yang dijaga uji ini</b> (semuanya bisa dihitung ulang dari repo):
 * <ul>
 *   <li>jumlah endpoint — tabel {@code API-ENDPOINTS.md} == header
 *       {@code Total: N endpoint} == README {@code **N endpoint**};</li>
 *   <li>jumlah migrasi — berkas {@code db/migration/*.sql} == README
 *       {@code **N migrasi Flyway**} == baris {@code V1 … Vn (Flyway)};</li>
 *   <li>jumlah berkas test — {@code *Test.java} &amp; {@code *IT.java} == README.</li>
 * </ul>
 *
 * <p><b>Yang TIDAK dijaga:</b> angka <i>jumlah kasus</i> unit test (mis.
 * "260 unit test") karena {@code @ParameterizedTest} melipatgandakan kasus saat
 * runtime sehingga tidak bisa dihitung dari anotasi; angkanya diambil dari
 * laporan surefire run terakhir. Karena itu: bila uji ini gagal karena jumlah
 * <b>berkas</b> test berubah, perbarui <b>juga</b> angka jumlah kasus di README.
 *
 * <p><b>Cara memperbaiki bila gagal:</b> jalankan {@code ./mvnw -o -q test}, baca
 * pesan assertion (berisi angka dokumen vs angka nyata), lalu selaraskan README.md
 * dan {@code architecture/API-ENDPOINTS.md} (termasuk tanggal "Sinkron terakhir").
 */
@DisplayName("Angka statistik README & API-ENDPOINTS.md sinkron dengan kode (#165)")
class StatistikDokumenTest {

    /** Lokasi dokumen relatif root proyek (Maven menjalankan test dari root). */
    private static final String[] KANDIDAT_README = {"README.md", "../README.md"};
    private static final String[] KANDIDAT_ENDPOINT = {
            "architecture/API-ENDPOINTS.md",
            "../architecture/API-ENDPOINTS.md"};

    private static final Path DIREKTORI_MIGRASI = Paths.get("src/main/resources/db/migration");
    private static final Path DIREKTORI_TEST = Paths.get("src/test");

    /** Baris tabel: | `GET` | `/api/...` | … (sama dengan ApiEndpointsDocTest). */
    private static final Pattern BARIS_ENDPOINT = Pattern.compile(
            "^\\|\\s*`(GET|POST|PUT|DELETE|PATCH)`\\s*\\|\\s*`([^`]+)`");

    // ────────────────────────────────────────────────────────────────
    // Uji
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Jumlah endpoint: tabel API-ENDPOINTS.md == header == README")
    void jumlahEndpointSinkron() {
        int dariTabel = jumlahBarisEndpoint();
        int dariHeader = angkaTunggal(baca(KANDIDAT_ENDPOINT),
                "\\*\\*Total:\\s*(\\d+)\\s*endpoint\\*\\*",
                "header API-ENDPOINTS.md ('**Total: N endpoint**')");
        int dariReadme = angkaTunggal(baca(KANDIDAT_README),
                "\\*\\*(\\d+)\\s*endpoint\\*\\*",
                "README.md ('**N endpoint**')");

        assertThat(dariTabel)
                .as("Tabel API-ENDPOINTS.md tidak wajar (parsing gagal?)")
                .isGreaterThan(50);
        assertThat(dariHeader)
                .as("Header API-ENDPOINTS.md basi — perbarui 'Total: N endpoint' "
                        + "(angka nyata: %d)", dariTabel)
                .isEqualTo(dariTabel);
        assertThat(dariReadme)
                .as("README.md basi — perbarui '**N endpoint**' (angka nyata: %d)", dariTabel)
                .isEqualTo(dariTabel);
    }

    @Test
    @DisplayName("Jumlah migrasi: berkas db/migration/*.sql == README (dua tempat)")
    void jumlahMigrasiSinkron() {
        int nyata = (int) hitungBerkas(DIREKTORI_MIGRASI, ".sql");
        String readme = baca(KANDIDAT_README);

        List<Integer> dariReadme = semuaAngka(readme, "\\*\\*(\\d+)\\s*migrasi Flyway\\*\\*");
        assertThat(dariReadme)
                .as("README.md harus menyebut '**N migrasi Flyway**' tepat satu kali")
                .hasSize(1);

        int terakhirDiPohon = angkaTunggal(readme, "V1 … V(\\d+)\\s*\\(Flyway\\)",
                "README.md ('V1 … Vn (Flyway)')");

        assertThat(dariReadme.get(0))
                .as("README.md basi — perbarui '**N migrasi Flyway**' (angka nyata: %d)", nyata)
                .isEqualTo(nyata);
        assertThat(terakhirDiPohon)
                .as("README.md basi — perbarui 'V1 … Vn (Flyway)' (migrasi terakhir: V%d)", nyata)
                .isEqualTo(nyata);
    }

    @Test
    @DisplayName("Jumlah berkas test (*Test.java & *IT.java) == README")
    void jumlahBerkasTestSinkron() {
        int nyataTest = (int) hitungBerkas(DIREKTORI_TEST, "Test.java");
        int nyataIt = (int) hitungBerkas(DIREKTORI_TEST, "IT.java");
        String readme = baca(KANDIDAT_README);

        int dariReadmeTest = angkaTunggal(readme, "\\((\\d+)\\s*file \\*Test\\)",
                "README.md ('(N file *Test)')");

        List<Integer> dariReadmeIt = semuaAngka(readme, "(\\d+)\\s*kelas IT");
        assertThat(dariReadmeIt)
                .as("README.md harus menyebut 'N kelas IT' (status + bagian test)")
                .isNotEmpty();

        assertThat(dariReadmeTest)
                .as("README.md basi — perbarui '(N file *Test)' (angka nyata: %d)", nyataTest)
                .isEqualTo(nyataTest);
        assertThat(dariReadmeIt)
                .as("README.md basi — perbarui 'N kelas IT' (angka nyata: %d)", nyataIt)
                .allSatisfy(n -> assertThat(n).isEqualTo(nyataIt));
    }

    // ────────────────────────────────────────────────────────────────
    // Pembantu
    // ────────────────────────────────────────────────────────────────

    private int jumlahBarisEndpoint() {
        int jumlah = 0;
        for (String baris : baca(KANDIDAT_ENDPOINT).split("\\R")) {
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
        try (Stream<Path> berkas = Files.walk(direktori)) {
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
