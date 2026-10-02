package com.asqi.scholia_kantin_be.component.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Logger error terfilter — hanya menampilkan stack trace dari paket aplikasi
 * kantin agar log tidak penuh dengan frame framework (parity admin-be).
 */
@Component
@Slf4j
public class ErrorLogger {

    /** Nama paket utama aplikasi kantin-be. */
    private static final String PACKAGE_PREFIX = "com.asqi.scholia_kantin_be";

    private ErrorLogger() {
    }

    public static void printFilteredStackTrace(Exception e) {
        StringBuilder stackTraceString = new StringBuilder();
        Pattern pattern = Pattern.compile(Pattern.quote(PACKAGE_PREFIX) + "\\..+");
        for (StackTraceElement element : e.getStackTrace()) {
            if (pattern.matcher(element.getClassName()).find()) {
                stackTraceString.append("\t").append(element).append("\n");
            }
        }

        String logString = """
                
                Error Message: "%s"
                Error stack trace --------------------------------------------------------------------------------------------------------------
                %s
                """.formatted(e.getClass().getSimpleName() + ": " + e.getMessage(), stackTraceString.toString());

        log.error(logString);
    }
}
