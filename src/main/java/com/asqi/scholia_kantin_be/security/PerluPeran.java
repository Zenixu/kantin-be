package com.asqi.scholia_kantin_be.security;

import com.asqi.scholia_kantin_be.enums.AktorKantin;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Batasi endpoint ke peran kantin tertentu (RBAC di backend — PRD §11.5).
 *
 * <p>Contoh pemakaian:
 * <pre>{@code
 * @PerluPeran(AktorKantin.PETUGAS_KANTIN)
 * @PostMapping("tap")
 * public ... tap(...) { ... }
 *
 * @PerluPeran({AktorKantin.ADMIN_SEKOLAH, AktorKantin.TU_SEKOLAH})
 * @PostMapping("topup-tunai")
 * public ... topUp(...) { ... }
 * }</pre>
 *
 * <p>Ditegakkan oleh {@link PeranAspect}. Bila gagal → 403
 * ({@code ForbiddenException}). <b>Bukan</b> pengganti tenant scoping — data
 * sekolah lain tetap 404.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PerluPeran {
    AktorKantin[] value();
}
