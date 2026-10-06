package com.asqi.scholia_kantin_be.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Uji allowlist IP/CIDR webhook (SECURITY.md §5). */
@DisplayName("IpAllowlist — IP & CIDR")
class IpAllowlistTest {

    @Test
    @DisplayName("Allowlist kosong ⇒ semua IP diizinkan")
    void kosongMengizinkanSemua() {
        IpAllowlist a = IpAllowlist.dari(List.of());
        assertThat(a.kosong()).isTrue();
        assertThat(a.mengizinkan("203.0.113.9")).isTrue();
        assertThat(a.mengizinkan(null)).isTrue();
    }

    @Test
    @DisplayName("IP tunggal cocok persis; IP lain ditolak")
    void ipTunggal() {
        IpAllowlist a = IpAllowlist.dari(List.of("203.0.113.7"));
        assertThat(a.mengizinkan("203.0.113.7")).isTrue();
        assertThat(a.mengizinkan("203.0.113.8")).isFalse();
        assertThat(a.mengizinkan(null)).isFalse();
    }

    @Test
    @DisplayName("CIDR IPv4: 10.0.0.0/8 mencakup rentang, menolak di luar")
    void cidrIpv4() {
        IpAllowlist a = IpAllowlist.dari(List.of("10.0.0.0/8"));
        assertThat(a.mengizinkan("10.0.0.1")).isTrue();
        assertThat(a.mengizinkan("10.255.255.255")).isTrue();
        assertThat(a.mengizinkan("11.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("CIDR dengan prefiks bukan kelipatan 8 (mis. /26) dihitung benar")
    void cidrPrefiksParsial() {
        IpAllowlist a = IpAllowlist.dari(List.of("192.168.1.0/26"));
        assertThat(a.mengizinkan("192.168.1.0")).isTrue();
        assertThat(a.mengizinkan("192.168.1.63")).isTrue();
        assertThat(a.mengizinkan("192.168.1.64")).isFalse();
    }

    @Test
    @DisplayName("Entri tak valid diabaikan; IPv6 literal didukung")
    void entriTakValidDiabaikan() {
        IpAllowlist a = IpAllowlist.dari(List.of("bukan-ip", "  ", "::1"));
        assertThat(a.kosong()).isFalse();
        assertThat(a.mengizinkan("::1")).isTrue();
        assertThat(a.mengizinkan("10.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("IPv4-mapped IPv6 (::ffff:10.0.0.5) cocok dengan aturan IPv4")
    void ipv4Mapped() {
        IpAllowlist a = IpAllowlist.dari(List.of("10.0.0.0/8"));
        assertThat(a.mengizinkan("::ffff:10.0.0.5")).isTrue();
    }
}
