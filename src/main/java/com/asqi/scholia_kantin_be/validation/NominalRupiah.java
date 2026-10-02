package com.asqi.scholia_kantin_be.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Nominal rupiah integer yang sah: {@code > 0} dan tidak melebihi batas wajar.
 *
 * <p>Menegakkan PRD §11.6 (uang = integer rupiah) di lapisan input, sebelum
 * menyentuh ledger. Validator DB tetap ada (CHECK) sebagai pertahanan terakhir.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NominalRupiahValidator.class)
@Documented
public @interface NominalRupiah {

    String message() default "Nominal harus bilangan bulat rupiah lebih dari 0";

    /** Batas atas (default 1 miliar rupiah) — cegah salah input ekstrem. */
    long max() default 1_000_000_000L;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
