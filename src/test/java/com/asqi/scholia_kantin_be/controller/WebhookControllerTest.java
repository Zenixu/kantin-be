package com.asqi.scholia_kantin_be.controller;

import com.asqi.scholia_kantin_be.config.webhook.WebhookProperties;
import com.asqi.scholia_kantin_be.dto.WebhookHasil;
import com.asqi.scholia_kantin_be.enums.StatusWebhook;
import com.asqi.scholia_kantin_be.service.webhook.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uji wiring {@link WebhookController}: header event id → service, dan penanda
 * replay pada respons (idempotency).
 */
@DisplayName("WebhookController — wiring terima event")
class WebhookControllerTest {

    private WebhookService service;
    private WebhookProperties props;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(WebhookService.class);
        props = new WebhookProperties();
        mockMvc = MockMvcBuilders.standaloneSetup(new WebhookController(service, props)).build();
    }

    @Test
    @DisplayName("Event baru: eventId dari header X-Webhook-Id diteruskan ke service")
    void eventBaruDiteruskan() throws Exception {
        String badan = """
                {"eventType":"TOPUP_ONLINE_SUKSES","sekolahId":7,"data":{"nominal":50000}}""";

        when(service.terima(eq("SKOOLIA"), eq("evt-1"), eq("TOPUP_ONLINE_SUKSES"),
                eq(7L), any(), any())).thenReturn(WebhookHasil.builder()
                .sumber("SKOOLIA").eventId("evt-1").eventType("TOPUP_ONLINE_SUKSES")
                .status(StatusWebhook.DIPROSES).replay(false).build());

        mockMvc.perform(post("/api/webhook/skoolia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Id", "evt-1")
                        .content(badan.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value("evt-1"))
                .andExpect(jsonPath("$.data.replay").value(false));

        verify(service).terima(eq("SKOOLIA"), eq("evt-1"), eq("TOPUP_ONLINE_SUKSES"),
                eq(7L), any(Map.class), any());
    }

    @Test
    @DisplayName("Replay: respons menandai replay=true (tidak diproses ulang)")
    void replayDitandai() throws Exception {
        String badan = """
                {"eventType":"TOPUP_ONLINE_SUKSES"}""";

        when(service.terima(eq("SKOOLIA"), eq("evt-2"), any(), any(), any(), any()))
                .thenReturn(WebhookHasil.builder()
                        .sumber("SKOOLIA").eventId("evt-2").eventType("TOPUP_ONLINE_SUKSES")
                        .status(StatusWebhook.DIPROSES).replay(true).build());

        mockMvc.perform(post("/api/webhook/skoolia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Id", "evt-2")
                        .content(badan.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replay").value(true));
    }

    @Test
    @DisplayName("eventId boleh dari field body bila header X-Webhook-Id kosong")
    void eventIdDariBody() throws Exception {
        String badan = """
                {"eventId":"evt-body","eventType":"X"}""";

        when(service.terima(eq("SKOOLIA"), eq("evt-body"), eq("X"), any(), any(), any()))
                .thenReturn(WebhookHasil.builder()
                        .sumber("SKOOLIA").eventId("evt-body").eventType("X")
                        .status(StatusWebhook.DIABAIKAN).replay(false).build());

        mockMvc.perform(post("/api/webhook/skoolia")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badan.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value("evt-body"));

        verify(service).terima(eq("SKOOLIA"), eq("evt-body"), eq("X"), any(), any(), any());
    }
}
