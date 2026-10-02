package com.asqi.scholia_kantin_be.component.exception;

/**
 * 400 — operasi tidak valid pada state saat ini (bukan konflik permanen).
 *
 * <p>Contoh kantin: void transaksi yang sudah di-void, aksi pada sesi yang
 * belum dibuka.
 */
public class InvalidOperationException extends RuntimeException {
    public InvalidOperationException(String message) {
        super(message);
    }
}
