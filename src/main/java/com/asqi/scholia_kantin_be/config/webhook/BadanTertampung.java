package com.asqi.scholia_kantin_be.config.webhook;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Pembungkus request yang menyimpan badan request <b>mentah</b> di memori.
 *
 * <p>HMAC dihitung atas byte persis yang dikirim pengirim, jadi badan harus
 * dibaca sekali di filter lalu tetap bisa dibaca lagi oleh controller
 * ({@code getInputStream()}/{@code getReader()} mengembalikan salinan baru).
 */
public class BadanTertampung extends HttpServletRequestWrapper {

    private final byte[] badan;

    public BadanTertampung(HttpServletRequest request, byte[] badan) {
        super(request);
        this.badan = badan == null ? new byte[0] : badan;
    }

    /** Badan request mentah yang sudah dibaca. */
    public byte[] badan() {
        return badan;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream in = new ByteArrayInputStream(badan);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return in.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // Tidak dipakai: pemrosesan sinkron (webmvc).
            }

            @Override
            public int read() {
                return in.read();
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return in.read(b, off, len);
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        Charset cs = getCharacterEncoding() == null
                ? StandardCharsets.UTF_8
                : Charset.forName(getCharacterEncoding());
        return new BufferedReader(new InputStreamReader(getInputStream(), cs));
    }
}
