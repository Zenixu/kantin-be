package com.asqi.scholia_kantin_be.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * UID kartu RFID: hex (dengan/tanpa titik dua), panjang wajar.
 *
 * <p>Menyelaraskan dengan {@code siswa.rfid_uid VARCHAR(64)} admin-be. Menerima
 * bentuk umum dari pembaca: {@code 04A1B2C3}, {@code 04:A1:B2:C3},
 * {@code 04-A1-B2-C3}.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UidKartuValidator.class)
@Documented
public @interface UidKartuValid {

    String message() default "Format UID kartu tidak valid";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
