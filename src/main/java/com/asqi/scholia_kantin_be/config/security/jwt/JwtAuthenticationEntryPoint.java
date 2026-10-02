package com.asqi.scholia_kantin_be.config.security.jwt;

import com.asqi.scholia_kantin_be.payload.response.CommonResponse;
import com.asqi.scholia_kantin_be.payload.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Titik masuk autentikasi: dipanggil bila request ke endpoint terlindungi
 * <b>tanpa</b> token yang valid.
 *
 * <p>Mengembalikan JSON standar kantin-be (bukan halaman login HTML, karena
 * kantin-be murni API) — parity {@code JwtAuthenticationEntryPoint} admin-be,
 * tetapi memakai Jackson 3.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Response<?> body = CommonResponse.unauthenticated("Autentikasi diperlukan").getBody();
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
