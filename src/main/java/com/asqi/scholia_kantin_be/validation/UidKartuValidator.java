package com.asqi.scholia_kantin_be.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Implementasi {@link UidKartuValid}.
 *
 * <p>Menerima {@code null} (pakai {@code @NotBlank} terpisah).
 */
public class UidKartuValidator implements ConstraintValidator<UidKartuValid, String> {

    /** 4–64 karakter heksadesimal, pemisah titik dua/strip opsional. */
    private static final Pattern POLA = Pattern.compile("(?i)^[0-9a-f]{2}([.:-]?[0-9a-f]{2}){1,31}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return false;
        }
        return POLA.matcher(v).matches();
    }
}
