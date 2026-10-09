package com.asqi.scholia_kantin_be.docs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Penjaga agar {@code architecture/API-ENDPOINTS.md} tidak basi (issue <b>#147</b>).
 *
 * <p><b>Masalah yang dicegah:</b> dokumen endpoint diperbarui manual dan mudah
 * tertinggal dari kode (issue #147 menemukan 58 vs 88 endpoint). Uji ini
 * membandingkan <b>himpunan endpoint nyata</b> (dibaca via refleksi dari seluruh
 * controller ber-{@code @RestController}, termasuk shim dev) dengan
 * <b>himpunan endpoint yang didokumentasikan</b> di tabel markdown.
 *
 * <p><b>Kontrak:</b> setiap baris tabel berbentuk
 * {@code | `METHOD` | `PATH` | … } dihitung sebagai satu endpoint. Uji
 * <b>gagal</b> bila ada endpoint di kode yang belum didokumentasikan, atau ada
 * endpoint didokumentasikan yang tidak lagi ada di kode. Dengan begitu, PR yang
 * menambah/mengubah endpoint wajib memperbarui dokumen.
 *
 * <p><b>Cara memperbaiki bila gagal:</b> jalankan uji, baca daftar
 * "belum didokumentasikan"/"sudah tidak ada", lalu sinkronkan
 * {@code architecture/API-ENDPOINTS.md}. Perbarui juga tanggal "Sinkron terakhir".
 */
@DisplayName("API-ENDPOINTS.md sinkron dengan anotasi mapping di kode (#147)")
class ApiEndpointsDocTest {

    /** Paket yang dipindai untuk controller (endpoint nyata). */
    private static final List<String> PAKET_CONTROLLER = List.of(
            "com/asqi/scholia_kantin_be/controller",
            "com/asqi/scholia_kantin_be/dev");

    /** Lokasi dokumen relatif root proyek (Maven menjalankan test dari root). */
    private static final String[] KANDIDAT_DOKUMEN = {
            "architecture/API-ENDPOINTS.md",
            "../architecture/API-ENDPOINTS.md"};

    /** Baris tabel: | `GET` | `/api/...` | … */
    private static final Pattern BARIS_ENDPOINT = Pattern.compile(
            "^\\|\\s*`(GET|POST|PUT|DELETE|PATCH)`\\s*\\|\\s*`([^`]+)`");

    // ────────────────────────────────────────────────────────────────
    // Uji utama
    // ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Himpunan endpoint kode == himpunan endpoint di API-ENDPOINTS.md")
    void dokumenSinkronDenganKode() {
        Set<String> dariKode = endpointDariKode();
        Set<String> dariDokumen = endpointDariDokumen();

        Set<String> belumDidokumentasikan = new TreeSet<>(dariKode);
        belumDidokumentasikan.removeAll(dariDokumen);

        Set<String> sudahTidakAdaDiKode = new TreeSet<>(dariDokumen);
        sudahTidakAdaDiKode.removeAll(dariKode);

        assertThat(belumDidokumentasikan)
                .as("Endpoint ADA di kode tetapi BELUM didokumentasikan di API-ENDPOINTS.md "
                        + "— tambahkan barisnya lalu perbarui tanggal 'Sinkron terakhir'.")
                .isEmpty();
        assertThat(sudahTidakAdaDiKode)
                .as("Endpoint TERTULIS di API-ENDPOINTS.md tetapi TIDAK ADA di kode "
                        + "— hapus/perbaiki barisnya.")
                .isEmpty();
    }

    @Test
    @DisplayName("Jumlah endpoint yang didokumentasikan wajar (guard terhadap tabel kosong/rusak)")
    void dokumenTidakKosong() {
        assertThat(endpointDariDokumen())
                .as("Dokumen endpoint tidak boleh kosong — parsing tabel gagal?")
                .isNotEmpty()
                .hasSizeGreaterThan(50);
    }

    // ────────────────────────────────────────────────────────────────
    // Endpoint nyata (refleksi)
    // ────────────────────────────────────────────────────────────────

    private Set<String> endpointDariKode() {
        Set<String> hasil = new LinkedHashSet<>();
        for (Class<?> kelas : kelasController()) {
            String base = basePath(kelas);
            for (var metode : kelas.getDeclaredMethods()) {
                RequestMapping rm =
                        AnnotatedElementUtils.findMergedAnnotation(metode, RequestMapping.class);
                if (rm == null || rm.method().length == 0) {
                    continue;
                }
                String sub = pathPertama(rm.path(), rm.value());
                String penuh = gabung(base, sub);
                for (RequestMethod m : rm.method()) {
                    hasil.add(m.name() + " " + penuh);
                }
            }
        }
        return hasil;
    }

    private Set<Class<?>> kelasController() {
        var resolver = new PathMatchingResourcePatternResolver();
        Set<Class<?>> kelas = new LinkedHashSet<>();
        for (String paket : PAKET_CONTROLLER) {
            Resource[] resources;
            try {
                resources = resolver.getResources(
                        "classpath*:" + paket + "/*.class");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            for (Resource r : resources) {
                String nama = namaKelas(r, paket);
                if (nama == null) {
                    continue;
                }
                try {
                    Class<?> c = Class.forName(nama, false,
                            Thread.currentThread().getContextClassLoader());
                    if (c.isAnnotationPresent(RestController.class)
                            || c.isAnnotationPresent(Controller.class)) {
                        kelas.add(c);
                    }
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Kelas controller tak ditemukan: " + nama, e);
                }
            }
        }
        assertThat(kelas)
                .as("Tidak ada kelas controller yang terpindai — periksa PAKET_CONTROLLER.")
                .isNotEmpty();
        return kelas;
    }

    private String namaKelas(Resource r, String paket) {
        String path;
        try {
            path = r.getURL().getPath();
        } catch (IOException e) {
            return null;
        }
        int idx = path.indexOf(paket + "/");
        if (idx < 0) {
            return null;
        }
        String relatif = path.substring(idx + paket.length() + 1);
        if (!relatif.endsWith(".class") || relatif.contains("$")) {
            return null;
        }
        return paket.replace('/', '.') + "." + relatif.substring(0, relatif.length() - 6);
    }

    private String basePath(Class<?> kelas) {
        RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(kelas, RequestMapping.class);
        if (rm == null) {
            return "";
        }
        return pathPertama(rm.path(), rm.value());
    }

    private String pathPertama(String[] path, String[] value) {
        for (String p : path) {
            if (p != null && !p.isBlank()) {
                return p;
            }
        }
        for (String v : value) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    /** Gabungkan base + sub ala Spring (tanpa duplikasi slash). */
    private String gabung(String base, String sub) {
        String b = base == null ? "" : base.trim();
        String s = sub == null ? "" : sub.trim();
        if (b.isEmpty()) {
            b = "";
        } else if (!b.startsWith("/")) {
            b = "/" + b;
        }
        if (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        if (s.isEmpty()) {
            return b;
        }
        if (!s.startsWith("/")) {
            s = "/" + s;
        }
        return b + s;
    }

    // ────────────────────────────────────────────────────────────────
    // Endpoint terdokumentasi (markdown)
    // ────────────────────────────────────────────────────────────────

    private Set<String> endpointDariDokumen() {
        Path dokumen = cariDokumen();
        List<String> baris;
        try {
            baris = Files.readAllLines(dokumen, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Gagal membaca " + dokumen, e);
        }
        Set<String> hasil = new LinkedHashSet<>();
        for (String b : baris) {
            Matcher m = BARIS_ENDPOINT.matcher(b.trim());
            if (m.find()) {
                hasil.add(m.group(1) + " " + m.group(2).trim());
            }
        }
        return hasil;
    }

    private Path cariDokumen() {
        for (String kandidat : KANDIDAT_DOKUMEN) {
            Path p = Paths.get(kandidat);
            if (Files.exists(p)) {
                return p;
            }
        }
        throw new IllegalStateException(
                "architecture/API-ENDPOINTS.md tidak ditemukan dari cwd " + Paths.get("").toAbsolutePath());
    }
}
