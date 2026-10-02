package com.asqi.scholia_kantin_be.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Amplop respons standar kantin-be (parity {@code admin-be}).
 *
 * <p><b>CATATAN JACKSON 3 (Spring Boot 4):</b> anotasi
 * {@code @JsonInclude} di kelas ini memakai paket {@code com.fasterxml.jackson.annotation}
 * yang masih valid (annotations tetap di paket lama). Yang BERUBAH adalah
 * {@code ObjectMapper}/{@code JsonMapper} → {@code tools.jackson}. Lihat
 * {@code JacksonConfig} &amp; {@code docs/spesifikasi-fase4-ledger.md} §5.
 *
 * @param <D> tipe data payload
 */
@Getter
@Setter
public class Response<D> {

    /** 0 = OK bawaan; akan diisi oleh {@link #setResponseCode(ResponseCode)}. */
    private Integer code = 0;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object validation;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long total;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer showing;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer totalPages;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer currentPage;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private D data;

    public Response() {
    }

    public Response(Integer code, String message, D data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public void setResponseCode(ResponseCode responseCode) {
        this.code = responseCode.getCode();
        this.message = responseCode.getMessage();
    }

    public void setData(D data) {
        if (data instanceof List<?> list) {
            this.total = (long) list.size();
        }
        this.data = data;
    }

    /** Halaman + daftar DTO hasil map (pola admin-be). */
    @SuppressWarnings("unchecked")
    public void setData(Page<?> pageData, Object dataDtoList) {
        this.data = (D) dataDtoList;
        this.total = pageData.getTotalElements();
        this.showing = pageData.getNumberOfElements();
        this.totalPages = pageData.getTotalPages();
        this.currentPage = pageData.getPageable().getPageNumber();
    }

    /** Halaman langsung dari {@code Page.getContent()}. */
    @SuppressWarnings("unchecked")
    public void setPageData(Page<?> pageData) {
        this.data = (D) pageData.getContent();
        this.total = pageData.getTotalElements();
        this.showing = pageData.getNumberOfElements();
        this.totalPages = pageData.getTotalPages();
        this.currentPage = pageData.getPageable().getPageNumber();
    }
}
