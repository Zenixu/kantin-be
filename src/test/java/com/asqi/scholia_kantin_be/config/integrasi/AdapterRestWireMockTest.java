package com.asqi.scholia_kantin_be.config.integrasi;

import com.asqi.scholia_kantin_be.enums.JenisNotifikasi;
import com.asqi.scholia_kantin_be.enums.MetodeBukuKas;
import com.asqi.scholia_kantin_be.enums.SubjekTipe;
import com.asqi.scholia_kantin_be.enums.TipeBukuKas;
import com.asqi.scholia_kantin_be.service.integrasi.AktivasiModulInfo;
import com.asqi.scholia_kantin_be.service.integrasi.HasilKirimNotifikasi;
import com.asqi.scholia_kantin_be.service.integrasi.HasilPostingBukuKas;
import com.asqi.scholia_kantin_be.service.integrasi.InfoKartu;
import com.asqi.scholia_kantin_be.service.integrasi.PerintahBukuKas;
import com.asqi.scholia_kantin_be.service.integrasi.PerintahNotifikasi;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uji <b>kontrak</b> adapter REST integrasi memakai <b>WireMock</b> (issue #160).
 *
 * <p>Membuktikan tiap adapter memetakan respons dengan benar untuk:
 * <b>sukses</b>, <b>5xx</b> (fail-open/fail-safe), <b>timeout</b>, dan
 * <b>payload salah</b> — tanpa menyentuh layanan nyata. WireMock berjalan
 * embedded (tanpa Docker) sehingga ikut di {@code mvn test}.
 *
 * <p>Karena adapter hanya hidup bila {@code kantin.integrasi.*.enabled=true},
 * test ini membuat adapter secara manual dengan properti yang diarahkan ke port
 * WireMock — tidak menyentuh konteks Spring.
 */
@DisplayName("Adapter REST integrasi — uji kontrak WireMock (#160)")
class AdapterRestWireMockTest {

    private WireMockServer wm;

    @BeforeEach
    void mulai() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
    }

    @AfterEach
    void henti() {
        if (wm != null) {
            wm.stop();
        }
    }

    private IntegrasiRestProperties props() {
        return new IntegrasiRestProperties();
    }

    // ------------------------------------------------------------------
    // BukuKas (#160, Q3)
    // ------------------------------------------------------------------

    private BukuKasRestClient bukuKas(int maxAttempts) {
        IntegrasiRestProperties p = props();
        var e = p.getBukukas();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setMaxAttempts(maxAttempts);
        e.setBackoffMs(10);
        return new BukuKasRestClient(p);
    }

    private PerintahBukuKas perintahBukuKas() {
        return PerintahBukuKas.builder()
                .sekolahId(1L)
                .tanggal(OffsetDateTime.parse("2026-10-12T10:00:00+07:00"))
                .tipe(TipeBukuKas.MASUK)
                .kategori("Pendapatan Kantin")
                .jumlah(BigDecimal.valueOf(50_000L))
                .metode(MetodeBukuKas.NON_TUNAI)
                .keterangan("Tutup sesi #1")
                .refId("KANTIN-SESI-1")
                .refModul("KANTIN")
                .build();
    }

    @Test
    @DisplayName("BukuKas: sukses 200 → SUKSES + kirim refId deterministik")
    void bukukasSukses() {
        wm.stubFor(post(urlEqualTo("/api/buku-kas/catat"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":200,\"message\":\"OK\"}")));

        HasilPostingBukuKas hasil = bukuKas(1).catat(perintahBukuKas());

        assertThat(hasil.status()).isEqualTo(HasilPostingBukuKas.Status.SUKSES);
        wm.verify(postRequestedFor(urlEqualTo("/api/buku-kas/catat"))
                .withRequestBody(matchingJsonPath("$.refId", equalTo("KANTIN-SESI-1")))
                .withRequestBody(matchingJsonPath("$.jumlah", equalTo("50000")))
                .withRequestBody(matchingJsonPath("$.tipe", equalTo("MASUK"))));
    }

    @Test
    @DisplayName("BukuKas: 5xx → GAGAL (fail-open, sesi tidak dibatalkan)")
    void bukukasGagal5xx() {
        wm.stubFor(post(urlEqualTo("/api/buku-kas/catat"))
                .willReturn(aResponse().withStatus(500).withBody("boom")));

        HasilPostingBukuKas hasil = bukuKas(1).catat(perintahBukuKas());

        assertThat(hasil.status()).isEqualTo(HasilPostingBukuKas.Status.GAGAL);
        assertThat(hasil.sukses()).isFalse();
    }

    @Test
    @DisplayName("BukuKas: 5xx lalu sukses → retry berhasil (max-attempts=2)")
    void bukukasRetry() {
        wm.stubFor(post(urlEqualTo("/api/buku-kas/catat"))
                .inScenario("retry")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("ok"));
        wm.stubFor(post(urlEqualTo("/api/buku-kas/catat"))
                .inScenario("retry")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse().withStatus(200)));

        HasilPostingBukuKas hasil = bukuKas(2).catat(perintahBukuKas());

        assertThat(hasil.status()).isEqualTo(HasilPostingBukuKas.Status.SUKSES);
        wm.verify(2, postRequestedFor(urlEqualTo("/api/buku-kas/catat")));
    }

    @Test
    @DisplayName("BukuKas: 4xx TIDAK di-retry (salah permintaan)")
    void bukukasTidakRetry4xx() {
        wm.stubFor(post(urlEqualTo("/api/buku-kas/catat"))
                .willReturn(aResponse().withStatus(400).withBody("bad")));

        HasilPostingBukuKas hasil = bukuKas(3).catat(perintahBukuKas());

        assertThat(hasil.status()).isEqualTo(HasilPostingBukuKas.Status.GAGAL);
        wm.verify(1, postRequestedFor(urlEqualTo("/api/buku-kas/catat")));
    }

    // ------------------------------------------------------------------
    // Notifikasi (#160, Q5) — fail-open
    // ------------------------------------------------------------------

    private NotifikasiRestClient notifikasi() {
        IntegrasiRestProperties p = props();
        var e = p.getNotifikasi();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setBackoffMs(10);
        return new NotifikasiRestClient(p);
    }

    private PerintahNotifikasi perintahNotif() {
        return PerintahNotifikasi.builder()
                .sekolahId(1L)
                .jenis(JenisNotifikasi.BELANJA)
                .subjekTipe(SubjekTipe.SISWA)
                .subjekId(7L)
                .nominal(8_000L)
                .ringkasan("Budi belanja Rp8.000")
                .build();
    }

    @Test
    @DisplayName("Notifikasi: sukses 200 → TERKIRIM + body sesuai kontrak Q5")
    void notifikasiSukses() {
        wm.stubFor(post(urlEqualTo("/api/internal/notifikasi"))
                .willReturn(aResponse().withStatus(200).withBody("{\"code\":200}")));

        HasilKirimNotifikasi hasil = notifikasi().kirim(perintahNotif());

        assertThat(hasil.status()).isEqualTo(HasilKirimNotifikasi.Status.TERKIRIM);
        wm.verify(postRequestedFor(urlEqualTo("/api/internal/notifikasi"))
                .withRequestBody(matchingJsonPath("$.siswaId", equalTo("7")))
                .withRequestBody(matchingJsonPath("$.jenis", equalTo("BELANJA"))));
    }

    @Test
    @DisplayName("Notifikasi: 5xx → GAGAL tapi tidak melempar (best-effort)")
    void notifikasiGagal5xx() {
        wm.stubFor(post(urlEqualTo("/api/internal/notifikasi"))
                .willReturn(aResponse().withStatus(500)));

        HasilKirimNotifikasi hasil = notifikasi().kirim(perintahNotif());

        assertThat(hasil.status()).isEqualTo(HasilKirimNotifikasi.Status.GAGAL);
    }

    @Test
    @DisplayName("Notifikasi: timeout → GAGAL (best-effort, tidak melempar)")
    void notifikasiTimeout() {
        wm.stubFor(post(urlEqualTo("/api/internal/notifikasi"))
                .willReturn(aResponse().withFixedDelay(1500).withStatus(200)));

        IntegrasiRestProperties p = props();
        var e = p.getNotifikasi();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setTimeoutMs(300); // lebih pendek dari delay
        NotifikasiRestClient klien = new NotifikasiRestClient(p);

        HasilKirimNotifikasi hasil = klien.kirim(perintahNotif());

        assertThat(hasil.status()).isEqualTo(HasilKirimNotifikasi.Status.GAGAL);
    }

    // ------------------------------------------------------------------
    // KartuLookup (#160, Q7) — tanpa cache status blokir
    // ------------------------------------------------------------------

    private KartuLookupRestClient kartuLookup() {
        IntegrasiRestProperties p = props();
        var e = p.getKartuLookup();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setBackoffMs(10);
        // Repo kartu tamu di-stub: tak ada kartu tamu → selalu jatuh ke jalur siswa.
        return new KartuLookupRestClient(p,
                org.mockito.Mockito.mock(com.asqi.scholia_kantin_be.repository.KartuTamuRepository.class));
    }

    @Test
    @DisplayName("KartuLookup: 200 siswa → dikenal, SISWA, data terisi")
    void kartuLookupSiswa() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/cek-kartu/UID-SISWA"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":200,"data":{"id":7,"namaLengkap":"Budi",
                                "fotoAnakUrl":"http://x/f.jpg","kelas":{"id":3,"namaKelas":"5A"}}}
                                """)));

        InfoKartu info = kartuLookup().cariBerdasarkanUid(1L, "UID-SISWA");

        assertThat(info.isDikenal()).isTrue();
        assertThat(info.getSubjekTipe()).isEqualTo(SubjekTipe.SISWA);
        assertThat(info.getSubjekId()).isEqualTo(7L);
        assertThat(info.getNama()).isEqualTo("Budi");
        assertThat(info.getKelas()).isEqualTo("5A");
    }

    @Test
    @DisplayName("KartuLookup: 404 → tidak dikenal (kartu tak terdaftar, bukan error)")
    void kartuLookup404() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/cek-kartu/UID-HILANG"))
                .willReturn(aResponse().withStatus(404)));

        InfoKartu info = kartuLookup().cariBerdasarkanUid(1L, "UID-HILANG");

        assertThat(info.isDikenal()).isFalse();
    }

    @Test
    @DisplayName("KartuLookup: 5xx → tidak dikenal (fail-closed, bukan crash)")
    void kartuLookup5xx() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/cek-kartu/UID-X"))
                .willReturn(aResponse().withStatus(500)));

        InfoKartu info = kartuLookup().cariBerdasarkanUid(1L, "UID-X");

        assertThat(info.isDikenal()).isFalse();
    }

    @Test
    @DisplayName("KartuLookup: payload salah (tanpa id) → tidak dikenal")
    void kartuLookupPayloadSalah() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/cek-kartu/UID-Y"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":200,\"data\":{\"namaLengkap\":\"Tanpa Id\"}}")));

        InfoKartu info = kartuLookup().cariBerdasarkanUid(1L, "UID-Y");

        assertThat(info.isDikenal()).isFalse();
    }

    // ------------------------------------------------------------------
    // AktivasiModul (#160, Q6) — fail-open
    // ------------------------------------------------------------------

    private AktivasiModulRestClient aktivasi() {
        IntegrasiRestProperties p = props();
        var e = p.getAktivasi();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setBackoffMs(10);
        return new AktivasiModulRestClient(p);
    }

    @Test
    @DisplayName("AktivasiModul: 200 aktif=false → diketahui & nonaktif")
    void aktivasiNonaktif() {
        wm.stubFor(get(urlPathEqualTo("/api/internal/modul-kantin/status"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"aktif\":false,\"fee\":{\"perTopupNominal\":500}}")));

        AktivasiModulInfo info = aktivasi().status(1L);

        assertThat(info.isDiketahui()).isTrue();
        assertThat(info.isAktif()).isFalse();
        assertThat(info.getFeeTopup()).isEqualTo(500L);
        wm.verify(getRequestedFor(urlPathEqualTo("/api/internal/modul-kantin/status"))
                .withQueryParam("sekolahId", equalTo("1")));
    }

    @Test
    @DisplayName("AktivasiModul: 5xx → tidak diketahui, dianggap AKTIF (fail-open)")
    void aktivasiFailOpen() {
        wm.stubFor(get(urlPathEqualTo("/api/internal/modul-kantin/status"))
                .willReturn(aResponse().withStatus(500)));

        AktivasiModulInfo info = aktivasi().status(1L);

        assertThat(info.isDiketahui()).isFalse();
        assertThat(info.isAktif()).isTrue(); // fail-open
    }

    // ------------------------------------------------------------------
    // StatusSiswa (#160, Q7)
    // ------------------------------------------------------------------

    private StatusSiswaRestClient statusSiswa() {
        IntegrasiRestProperties p = props();
        var e = p.getStatusSiswa();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setBackoffMs(10);
        return new StatusSiswaRestClient(p);
    }

    @Test
    @DisplayName("StatusSiswa: isActive=false → tidakAktif=true")
    void statusTidakAktif() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/get-by-id/9"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":200,\"data\":{\"id\":9,\"isActive\":false}}")));

        assertThat(statusSiswa().tidakAktif(1L, 9L)).isTrue();
    }

    @Test
    @DisplayName("StatusSiswa: isLulus=true → tidakAktif=true")
    void statusLulus() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/get-by-id/9"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":200,\"data\":{\"id\":9,\"isActive\":true,\"isLulus\":true}}")));

        assertThat(statusSiswa().tidakAktif(1L, 9L)).isTrue();
    }

    @Test
    @DisplayName("StatusSiswa: aktif → tidakAktif=false")
    void statusAktif() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/get-by-id/9"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":200,\"data\":{\"id\":9,\"isActive\":true}}")));

        assertThat(statusSiswa().tidakAktif(1L, 9L)).isFalse();
    }

    @Test
    @DisplayName("StatusSiswa: gagal → null (tidak diketahui, fail-safe)")
    void statusGagal() {
        wm.stubFor(get(urlPathEqualTo("/api/siswa/get-by-id/9"))
                .willReturn(aResponse().withStatus(500)));

        assertThat(statusSiswa().tidakAktif(1L, 9L)).isNull();
    }

    @Test
    @DisplayName("StatusSiswa: blokirKartu best-effort — 5xx tidak melempar")
    void blokirBestEffort() {
        wm.stubFor(post(urlEqualTo("/api/siswa/kartu/blokir"))
                .willReturn(aResponse().withStatus(500)));

        // Tidak boleh melempar:
        statusSiswa().blokirKartu(1L, 9L, "siswa keluar");
        wm.verify(postRequestedFor(urlEqualTo("/api/siswa/kartu/blokir"))
                .withRequestBody(matchingJsonPath("$.siswaId", equalTo("9"))));
    }

    // ------------------------------------------------------------------
    // UidSiswa (#160, Q7)
    // ------------------------------------------------------------------

    private UidSiswaRestClient uidSiswa() {
        IntegrasiRestProperties p = props();
        var e = p.getUidSiswa();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setBackoffMs(10);
        return new UidSiswaRestClient(p);
    }

    @Test
    @DisplayName("UidSiswa: dipakai=true → true + query sekolahId")
    void uidDipakai() {
        wm.stubFor(get(urlPathEqualTo("/api/internal/siswa/cek-uid"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"dipakai\":true}")));

        assertThat(uidSiswa().dipakaiSiswa(1L, "UID-A")).isTrue();
        wm.verify(getRequestedFor(urlPathEqualTo("/api/internal/siswa/cek-uid"))
                .withQueryParam("rfidUid", equalTo("UID-A"))
                .withQueryParam("sekolahId", equalTo("1")));
    }

    @Test
    @DisplayName("UidSiswa: gagal → null (fail-open, tidak menolak registrasi)")
    void uidGagal() {
        wm.stubFor(get(urlPathEqualTo("/api/internal/siswa/cek-uid"))
                .willReturn(aResponse().withStatus(503)));

        assertThat(uidSiswa().dipakaiSiswa(1L, "UID-A")).isNull();
    }

    // ------------------------------------------------------------------
    // Keamanan: header HMAC bila secret diisi (#29)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Header HMAC-SHA256 terkirim bila secret diisi (pola #29)")
    void headerHmac() {
        wm.stubFor(get(urlPathEqualTo("/api/internal/siswa/cek-uid"))
                .willReturn(aResponse().withStatus(200).withBody("{\"dipakai\":false}")));

        IntegrasiRestProperties p = props();
        var e = p.getUidSiswa();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setSecret("rahasia-uji");
        UidSiswaRestClient klien = new UidSiswaRestClient(p);

        assertThat(klien.dipakaiSiswa(1L, "UID-A")).isFalse();
        wm.verify(getRequestedFor(urlPathEqualTo("/api/internal/siswa/cek-uid"))
                .withHeader("X-Internal-Timestamp", com.github.tomakehurst.wiremock.client.WireMock.matching(".+"))
                .withHeader("X-Internal-Signature", com.github.tomakehurst.wiremock.client.WireMock.matching("sha256=[0-9a-f]{64}")));
    }

    @Test
    @DisplayName("Bearer token terkirim bila diisi")
    void headerBearer() {
        wm.stubFor(get(urlPathEqualTo("/api/internal/siswa/cek-uid"))
                .willReturn(aResponse().withStatus(200).withBody("{\"dipakai\":false}")));

        IntegrasiRestProperties p = props();
        var e = p.getUidSiswa();
        e.setEnabled(true);
        e.setBaseUrl(wm.baseUrl());
        e.setBearerToken("token-abc");
        UidSiswaRestClient klien = new UidSiswaRestClient(p);

        klien.dipakaiSiswa(1L, "UID-A");
        wm.verify(getRequestedFor(urlPathEqualTo("/api/internal/siswa/cek-uid"))
                .withHeader("Authorization", equalTo("Bearer token-abc")));
    }
}
