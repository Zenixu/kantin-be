package com.asqi.scholia_kantin_be.security;

import com.asqi.scholia_kantin_be.component.exception.ForbiddenException;
import com.asqi.scholia_kantin_be.component.exception.UnauthorizedException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Menegakkan {@link PerluPeran} pada method controller.
 *
 * <p>Pendekatan AOP dipilih agar anotasi bisa dipasang di level method dengan
 * pesan kesalahan yang jelas (konsisten dengan gaya {@code @PreAuthorize}).
 * Alternatif: {@code @PreAuthorize("hasRole('...')")} bawaan Spring — keduanya
 * sah. Anotasi kustom lebih ramah karena memakai enum yang sama dengan domain.
 */
@Aspect
@Component
public class PeranAspect {

    @Before("@annotation(perluPeran)")
    public void cek(PerluPeran perluPeran) {
        IdentitasKantin identitas = TenantContext.get();
        if (identitas == null) {
            throw new UnauthorizedException("Belum terautentikasi");
        }
        List<com.asqi.scholia_kantin_be.enums.AktorKantin> diizinkan =
                List.of(perluPeran.value());
        if (!diizinkan.contains(identitas.getPeran())) {
            throw new ForbiddenException(
                    "Peran " + identitas.getPeran() + " tidak berhak atas aksi ini");
        }
    }
}
