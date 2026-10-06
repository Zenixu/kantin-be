package com.asqi.scholia_kantin_be.service.laporan;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Penulis berkas Excel (.xlsx) untuk ekspor laporan (PRD §9.5: "Semua laporan
 * dapat diekspor ke Excel"). Memakai Apache POI.
 *
 * <p>Hanya menyediakan primitif: menulis satu sheet dari daftar baris (header +
 * data). Pemetaan laporan → baris dilakukan {@link LaporanExportService}.
 */
@Component
public class ExcelWriter {

    /**
     * Bangun workbook satu sheet.
     *
     * @param namaSheet nama sheet
     * @param judul     baris judul (mis. nama laporan + periode); boleh kosong
     * @param header    nama kolom
     * @param baris     data; tiap sel boleh {@code null}
     * @return byte .xlsx siap kirim
     */
    public byte[] tulis(String namaSheet, List<String> judul, List<String> header,
                        List<List<Object>> baris) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sanitasiNamaSheet(namaSheet));

            CellStyle gayaJudul = gayaTebal(wb);
            CellStyle gayaHeader = gayaTebal(wb);
            gayaHeader.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            gayaHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int r = 0;
            if (judul != null && !judul.isEmpty()) {
                for (String j : judul) {
                    Row row = sheet.createRow(r++);
                    Cell c = row.createCell(0);
                    c.setCellValue(j);
                    c.setCellStyle(gayaJudul);
                }
                r++; // satu baris kosong pemisah
            }

            if (header != null && !header.isEmpty()) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < header.size(); c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(header.get(c));
                    cell.setCellStyle(gayaHeader);
                }
            }

            if (baris != null) {
                for (List<Object> data : baris) {
                    Row row = sheet.createRow(r++);
                    for (int c = 0; c < data.size(); c++) {
                        isiSel(row.createCell(c), data.get(c));
                    }
                }
            }

            // Lebarkan kolom agar terbaca (maks 60 char).
            int kolom = header != null ? header.size() : 0;
            for (int c = 0; c < kolom; c++) {
                sheet.autoSizeColumn(c);
                int lebar = Math.min(sheet.getColumnWidth(c) + 512, 60 * 256);
                sheet.setColumnWidth(c, lebar);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Gagal membangun berkas Excel", e);
        }
    }

    private void isiSel(Cell cell, Object nilai) {
        if (nilai == null) {
            cell.setBlank();
        } else if (nilai instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (nilai instanceof Boolean b) {
            cell.setCellValue(b);
        } else {
            cell.setCellValue(nilai.toString());
        }
    }

    private CellStyle gayaTebal(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    /** Buang karakter yang dilarang di nama sheet Excel ([:\\/?*\[\]]). */
    private String sanitasiNamaSheet(String nama) {
        if (nama == null || nama.isBlank()) {
            return "Laporan";
        }
        String bersih = nama.replaceAll("[\\\\/:?*\\[\\]]", "-");
        return bersih.length() > 31 ? bersih.substring(0, 31) : bersih;
    }
}
