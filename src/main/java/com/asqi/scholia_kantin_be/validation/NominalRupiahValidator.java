package com.asqi.scholia_kantin_be.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementasi {@link NominalRupiah}.
 *
 * <p>Menerima {@code null} (gunanya {@code @NotNull} terpisah) agar pesan
 * kesalahan tidak bertumpuk.
 */
public class NominalRupiahValidator implements ConstraintValidator<NominalRupiah, Long> {

    private long max;

    @Override
    public void initialize(NominalRupiah annotation) {
        this.max = annotation.max();
    }

    @Override
    public boolean isValid(Long value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value > 0 && value <= max;
    }
}
