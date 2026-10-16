package com.asqi.scholia_kantin_be.web;

import com.asqi.scholia_kantin_be.support.EnabledIfDockerAvailable;
import com.asqi.scholia_kantin_be.support.TestcontainersConfig;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SMOKE TEST kontrak endpoint GET utama — <b>lewat HTTP sungguhan</b> ke
 * PostgreSQL nyata (Testcontainers), bukan lewat mock service.
 *
 * <p><b>Latar (issue #199, turunan bug #198).</b> {@code GET /api/stok/riwayat}
 * mengembalikan <b>500</b> di produksi/lokal sementara {@code ./mvnw verify}
 * tetap hijau, karena controller diuji dengan {@code MockMvc + mock(Service.class)}
 * sehingga query JPQL/SQL <b>tidak pernah dieksekusi</b>. Kelas bug ini (tipe
 * parameter, dialect, constraint, locking) hanya muncul saat SQL benar-benar
 * dijalankan.
 *
 * <p>Uji ini menutup celah itu dengan cara termurah namun menyeluruh: memanggil
 * <b>semua</b> endpoint GET utama ke DB nyata dan memastikan <b>tidak ada 5xx</b>.
 * Endpoint yang datanya belum ada tetap sah membalas 200 (list kosong) atau 404
 * (entitas tak ditemukan) — yang dilarang adalah error server.
 *
 * <p>Autentikasi memakai <b>token RS256 sungguhan</b> yang ditandatangani keypair
 * yang dibuat saat uji &amp; didaftarkan sebagai {@code jwt.admin.public-key}. Jadi
 * jalur verifikasi produksi (ADR-0002) benar-benar dilewati — ini bukan bypass.
 * Peran {@code PENGELOLA_KANTIN} dipilih karena diizinkan di <b>semua</b> endpoint
 * GET yang diuji.
 *
 * <p>Menegakkan acceptance criteria #199: endpoint GET utama
 * {@code SaldoController}, {@code StokController}, {@code LaporanController},
 * {@code KartuTamuController}, {@code KatalogController}, {@code KasirController}
 * terbukti berjalan di atas DB nyata.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@EnabledIfDockerAvailable
class SmokeGetEndpointIT {

    /** Tenant uji — tidak perlu baris `sekolah`; query hanya menyaring `sekolahId`. */
    private static final long SEKOLAH_ID = 1L;
    private static final long USER_ID = 42L;

    private static final KeyPair KEYPAIR = buatKeyPair();
    private static final String TOKEN = tokenPengelola(KEYPAIR.getPrivate());

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void jwtDummy(DynamicPropertyRegistry registry) {
        registry.add("jwt.admin-public-key",
                () -> Base64.getEncoder().encodeToString(KEYPAIR.getPublic().getEncoded()));
        registry.add("jwt.issuer.admin", () -> "skoolia-admin");
    }

    // ---------------------------------------------------------------- helper

    private static KeyPair buatKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Gagal membuat keypair RSA uji", e);
        }
    }

    /** Token meniru admin-be: klaim lengkap, tanpa `iss` (lihat AUDIT Q1). */
    private static String tokenPengelola(PrivateKey privateKey) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("pengelola01")
                .claim("typ", "access")
                .claim("user_id", USER_ID)
                .claim("nama", "Pengelola Kantin Uji")
                .claim("role", "PENGELOLA_KANTIN")
                .claim("sekolah_id", SEKOLAH_ID)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + path))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient()
                .send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ------------------------------------------------------------- skenario

    /**
     * Endpoint GET yang <b>wajib</b> membalas 200 meski tabel masih kosong
     * (daftar/ringkasan kosong tetap 200 — bukan 404, bukan 500).
     */
    static Stream<String> endpointHarus200() {
        return Stream.of(
                // SaldoController
                "/api/saldo?subjekTipe=SISWA&subjekId=1",
                // #213 — daftar koreksi bendahara (semua filter)
                "/api/saldo/koreksi",
                "/api/saldo/koreksi?tanggal=2026-10-16",
                "/api/saldo/koreksi?subjekTipe=SISWA",
                "/api/saldo/koreksi?subjekTipe=KARTU_TAMU&subjekId=1",
                "/api/saldo/koreksi?tanggal=2026-10-16&subjekTipe=SISWA&subjekId=1",
                "/api/saldo/koreksi?halaman=0&ukuran=5",
                "/api/saldo/rekonsiliasi?subjekTipe=SISWA&subjekId=1",
                "/api/saldo/refund/kandidat",
                "/api/saldo/setoran-tu/rekap",

                // StokController — `/riwayat` = endpoint bug #198
                "/api/stok/riwayat",
                "/api/stok/riwayat?menuId=1&jenis=BARANG_MASUK&dari=2026-01-01T00:00:00Z&sampai=2026-12-31T23:59:59Z",
                "/api/stok/riwayat?jenis=PENJUALAN",
                "/api/stok/riwayat?halaman=0&ukuran=5",
                "/api/stok/menipis",

                // LaporanController (16 GET)
                "/api/laporan/penjualan",
                "/api/laporan/penjualan?dari=2026-01-01T00:00:00Z&sampai=2026-12-31T23:59:59Z",
                "/api/laporan/penjualan/item",
                "/api/laporan/penjualan/kategori",
                "/api/laporan/saldo-mengendap",
                "/api/laporan/rekonsiliasi",
                "/api/laporan/stok",
                "/api/laporan/stok?hanyaMenipis=true",
                "/api/laporan/kerugian-stok",
                "/api/laporan/pembatalan",
                "/api/laporan/kartu-tamu",
                "/api/laporan/penjualan/titik",
                "/api/laporan/penjualan/petugas",
                "/api/laporan/per-siswa?subjekId=1",
                "/api/laporan/setoran-tu",
                "/api/laporan/ekspor?jenis=PENJUALAN",

                // KartuTamuController
                "/api/kartu-tamu",
                "/api/kartu-tamu?hanyaAktif=true",
                "/api/kartu-tamu/nomor-berikutnya",

                // KatalogController
                "/api/katalog/kategori",
                "/api/katalog/menu",
                "/api/katalog/menu?hanyaAktif=true",

                // KasirController
                "/api/kasir/tap/menunggu",
                // #212 — daftar transaksi utk koreksi bendahara (semua filter)
                "/api/kasir/transaksi",
                "/api/kasir/transaksi?sesiId=1",
                "/api/kasir/transaksi?tanggal=2026-10-16",
                "/api/kasir/transaksi?status=SUKSES",
                "/api/kasir/transaksi?status=VOID",
                "/api/kasir/transaksi?sesiId=1&tanggal=2026-10-16&status=SUKSES",
                "/api/kasir/transaksi?halaman=0&ukuran=5",

                // SiswaController — cari siswa (#214); tanpa demo → daftar kosong 200
                "/api/siswa",
                "/api/siswa?q=budi",
                "/api/siswa?hanyaTidakAktif=true&batas=5",

                // AuthController — identitas dari token
                "/api/auth/me"
        );
    }

    /**
     * Endpoint GET ber-{@code @PathVariable} dengan id yang <b>tidak ada</b>.
     * Boleh 404 (memang tak ditemukan) tetapi <b>tidak boleh</b> 5xx.
     */
    static Stream<String> endpointTakBolehErrorServer() {
        return Stream.of(
                "/api/stok/999999",
                "/api/stok/999999/rekonsiliasi",
                "/api/laporan/kartu-stok/999999",
                "/api/kartu-tamu/999999",
                "/api/kartu-tamu/999999/riwayat",
                "/api/katalog/menu/999999",
                "/api/kasir/sesi/999999",
                "/api/kasir/sesi/999999/rekap"
        );
    }

    @ParameterizedTest(name = "200 → {0}")
    @MethodSource("endpointHarus200")
    @DisplayName("#199: endpoint GET utama balas 200 di atas DB nyata (bukan 500)")
    void endpointUtamaBalas200(String path) throws Exception {
        HttpResponse<String> res = get(path, TOKEN);

        assertThat(res.statusCode())
                .as("GET %s — badan: %s", path, potong(res.body()))
                .isEqualTo(200);
    }

    @ParameterizedTest(name = "bukan 5xx → {0}")
    @MethodSource("endpointTakBolehErrorServer")
    @DisplayName("#199: endpoint GET ber-id tak ada tidak meledak (bukan 5xx)")
    void endpointIdTakAdaTidakErrorServer(String path) throws Exception {
        HttpResponse<String> res = get(path, TOKEN);

        assertThat(res.statusCode())
                .as("GET %s — badan: %s", path, potong(res.body()))
                .isLessThan(500);
    }

    @Test
    @DisplayName("#199: tanpa token → 401 (jalur verifikasi tetap ketat)")
    void tanpaTokenDitolak() throws Exception {
        HttpResponse<String> res = get("/api/stok/riwayat", null);

        assertThat(res.statusCode()).isEqualTo(401);
    }

    private static String potong(String body) {
        if (body == null) {
            return "(kosong)";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "…";
    }
}
