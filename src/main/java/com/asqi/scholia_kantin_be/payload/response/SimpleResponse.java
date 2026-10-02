package com.asqi.scholia_kantin_be.payload.response;

import lombok.Data;

/**
 * Respons ringkas untuk error non-domain (binding, type mismatch, upload).
 * Parity {@code admin-be}.
 */
@Data
public class SimpleResponse {
    private Integer status = 0;
    private Object message;
}
