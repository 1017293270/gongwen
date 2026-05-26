package com.gongwen.assistant.quality;

import com.gongwen.assistant.draft.DraftNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QualityCheckController.class)
class QualityCheckControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QualityCheckService qualityCheckService;

    @Test
    void runsQualityCheck() throws Exception {
        UUID id = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        when(qualityCheckService.runCheck(1L)).thenReturn(new QualityCheckResponse(
                id,
                1L,
                "ERROR",
                true,
                traceId,
                Instant.parse("2026-05-26T10:00:00Z"),
                List.of(new QualityCheckItem(
                        "ERROR",
                        "REQUIRED_FIELD",
                        "REQUIRED_RECIPIENT_MISSING",
                        "主送对象不能为空。",
                        "RECIPIENT",
                        2L,
                        "请先补齐该字段后再导出。"
                ))
        ));

        mockMvc.perform(post("/api/drafts/1/quality-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ERROR"))
                .andExpect(jsonPath("$.data.exportBlocked").value(true))
                .andExpect(jsonPath("$.data.items[0].code").value("REQUIRED_RECIPIENT_MISSING"));
    }

    @Test
    void returnsLatestQualityCheck() throws Exception {
        when(qualityCheckService.findLatest(1L)).thenReturn(Optional.of(new QualityCheckResponse(
                UUID.randomUUID(),
                1L,
                "PASS",
                false,
                UUID.randomUUID(),
                Instant.parse("2026-05-26T10:00:00Z"),
                List.of()
        )));

        mockMvc.perform(get("/api/drafts/1/quality-check/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PASS"))
                .andExpect(jsonPath("$.data.exportBlocked").value(false));
    }

    @Test
    void returnsNotFoundForMissingDraft() throws Exception {
        when(qualityCheckService.runCheck(99L)).thenThrow(new DraftNotFoundException(99L));

        mockMvc.perform(post("/api/drafts/99/quality-check"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DRAFT_NOT_FOUND"));
    }
}
