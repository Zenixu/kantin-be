package com.asqi.scholia_kantin_be.service.kartu;

import com.asqi.scholia_kantin_be.component.exception.ConflictException;
import com.asqi.scholia_kantin_be.helper.IdGenerator;
import com.asqi.scholia_kantin_be.model.KartuTamu;
import com.asqi.scholia_kantin_be.repository.KartuTamuRepository;
import com.asqi.scholia_kantin_be.service.integrasi.UidSiswaPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Uji anti-tabrakan UID dua arah (issue #29) dengan repository &amp; port tiruan.
 *
 * <p>Menegakkan: (a) UID yang sudah dipakai siswa di admin-be → ditolak;
 * (b) UID bebas → diterima; (c) integrasi absen (null) → <b>fail-open</b>
 * (tidak menolak keliru); (d) cek {@code dipakaiKartuTamu} untuk endpoint internal.
 */
class KartuTamuAntiTabrakanTest {

    private final KartuTamuRepository repo = mock(KartuTamuRepository.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);

    private KartuTamuService service(UidSiswaPort port) {
        return new KartuTamuService(repo, idGenerator, port);
    }

    @Test
    @DisplayName("#29: UID sudah dipakai siswa admin-be → ConflictException")
    void uidDipakaiSiswaDitolak() {
        when(repo.findBySekolahIdAndNomorKartu(anyLong(), anyString())).thenReturn(Optional.empty());
        when(repo.existsByRfidUidExcluding(anyString(), any())).thenReturn(false);
        UidSiswaPort port = (sekolahId, uid) -> true;

        assertThatThrownBy(() -> service(port).buatKartu(1L, "KT-001", "ABCDEF1234", null, 9L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("kartu siswa");
    }

    @Test
    @DisplayName("#29: UID bebas (bukan siswa, bukan Kartu Tamu lain) → tersimpan")
    void uidBebasDiterima() {
        when(repo.findBySekolahIdAndNomorKartu(anyLong(), anyString())).thenReturn(Optional.empty());
        when(repo.existsByRfidUidExcluding(anyString(), any())).thenReturn(false);
        when(idGenerator.berikutnya()).thenReturn(123L);
        when(repo.save(any(KartuTamu.class))).thenAnswer(i -> i.getArgument(0));
        UidSiswaPort port = (sekolahId, uid) -> false;

        KartuTamu hasil = service(port).buatKartu(1L, "KT-001", "ABCDEF1234", null, 9L);

        assertThat(hasil.getRfidUid()).isEqualTo("ABCDEF1234");
    }

    @Test
    @DisplayName("#29: integrasi absen (null) → fail-open, registrasi tetap jalan")
    void integrasiAbsenFailOpen() {
        when(repo.findBySekolahIdAndNomorKartu(anyLong(), anyString())).thenReturn(Optional.empty());
        when(repo.existsByRfidUidExcluding(anyString(), any())).thenReturn(false);
        when(idGenerator.berikutnya()).thenReturn(124L);
        when(repo.save(any(KartuTamu.class))).thenAnswer(i -> i.getArgument(0));
        UidSiswaPort port = (sekolahId, uid) -> null;

        KartuTamu hasil = service(port).buatKartu(1L, "KT-002", "ABCDEF9999", null, 9L);

        assertThat(hasil.getRfidUid()).isEqualTo("ABCDEF9999");
    }

    @Test
    @DisplayName("#29: UID sudah dipakai Kartu Tamu lain → ditolak (tanpa panggil port siswa)")
    void uidKartuTamuLainDitolak() {
        when(repo.findBySekolahIdAndNomorKartu(anyLong(), anyString())).thenReturn(Optional.empty());
        when(repo.existsByRfidUidExcluding(anyString(), any())).thenReturn(true);
        UidSiswaPort port = mock(UidSiswaPort.class);

        assertThatThrownBy(() -> service(port).buatKartu(1L, "KT-003", "ABCDEF0000", null, 9L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("kartu lain");
        verify(port, never()).dipakaiSiswa(anyLong(), anyString());
    }

    @Test
    @DisplayName("#29: dipakaiKartuTamu (endpoint internal) meneruskan hasil repo")
    void dipakaiKartuTamu() {
        when(repo.existsByRfidUid("ABCDEF1234")).thenReturn(true);
        when(repo.existsByRfidUid("ZZZZ")).thenReturn(false);

        assertThat(service((s, u) -> null).dipakaiKartuTamu("ABCDEF1234")).isTrue();
        assertThat(service((s, u) -> null).dipakaiKartuTamu("ZZZZ")).isFalse();
        assertThat(service((s, u) -> null).dipakaiKartuTamu("  ")).isFalse();
    }
}
