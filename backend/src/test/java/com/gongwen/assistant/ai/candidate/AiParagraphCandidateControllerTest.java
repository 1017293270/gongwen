package com.gongwen.assistant.ai.candidate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiParagraphCandidateController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiParagraphCandidateControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiParagraphCandidateService service;

    @Test
    void listsDraftParagraphCandidates() throws Exception {
        when(service.listCandidates(7L)).thenReturn(List.of(candidate("READY")));

        mockMvc.perform(get("/api/drafts/7/ai/paragraph-candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].status").value("READY"))
                .andExpect(jsonPath("$.data[0].candidateText").value("candidate text"));
    }

    @Test
    void createsBatchCandidates() throws Exception {
        when(service.createBatch(eq(7L), any(CreateParagraphCandidateBatchRequest.class)))
                .thenReturn(List.of(candidate("PENDING")));

        CreateParagraphCandidateBatchRequest request = new CreateParagraphCandidateBatchRequest(
                null,
                "outline",
                List.of(new ParagraphCandidateSectionRequest(
                        10L,
                        "BODY",
                        "Body",
                        1,
                        "Section one",
                        List.of("point"),
                        "candidate text"
                ))
        );

        mockMvc.perform(post("/api/drafts/7/ai/paragraph-candidates/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void retryRouteGeneratesCandidateThroughService() throws Exception {
        when(service.retry(7L, 42L)).thenReturn(candidate("READY"));

        mockMvc.perform(post("/api/drafts/7/ai/paragraph-candidates/42/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("READY"));

        verify(service).retry(7L, 42L);
    }

    @Test
    void mapsCandidateExceptionToApiResponse() throws Exception {
        when(service.acceptBatch(eq(7L), any(AcceptParagraphCandidateBatchRequest.class)))
                .thenThrow(new AiParagraphCandidateException(
                        "AI_CANDIDATE_TARGET_BLOCKED",
                        "Target node cannot be accepted because it is locked or deleted"
                ));

        mockMvc.perform(post("/api/drafts/7/ai/paragraph-candidates/accept-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcceptParagraphCandidateBatchRequest(List.of(1L)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AI_CANDIDATE_TARGET_BLOCKED"));
    }

    private AiParagraphCandidateDto candidate(String status) {
        return new AiParagraphCandidateDto(
                1L,
                7L,
                10L,
                "BODY",
                "Body",
                null,
                null,
                1,
                "Section one",
                List.of("point"),
                "outline",
                "candidate text",
                "digest",
                status,
                "",
                "",
                null,
                null,
                Instant.parse("2026-06-02T00:00:00Z"),
                Instant.parse("2026-06-02T00:00:00Z")
        );
    }

}
