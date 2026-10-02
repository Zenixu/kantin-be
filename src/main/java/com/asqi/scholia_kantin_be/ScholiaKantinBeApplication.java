package com.asqi.scholia_kantin_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point Modul Kantin Cashless SKOOLIA.
 *
 * <p>kantin-be tidak memiliki login sendiri; identitas datang dari JWT RS256
 * yang diterbitkan admin-be (staf) &amp; mobile-be (orang tua). Lihat ADR-0002.
 */
@SpringBootApplication
public class ScholiaKantinBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScholiaKantinBeApplication.class, args);
	}

}
