package com.gongwen.assistant.common.web;

import com.gongwen.assistant.ai.candidate.AiParagraphCandidateException;
import com.gongwen.assistant.material.MaterialUploadException;
import com.gongwen.assistant.quality.QualityCheckException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CommonApiExceptionHandlerTest.FailingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({CommonApiExceptionHandler.class, CommonApiExceptionHandlerTest.FailingController.class})
class CommonApiExceptionHandlerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsUserFacingMessageForDataIntegrityFailures() throws Exception {
        mockMvc.perform(get("/test/failing/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DATA_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Data conflict. Please sync latest data and retry."));
    }

    @Test
    void returnsUserFacingMessageForUnknownFailures() throws Exception {
        mockMvc.perform(get("/test/failing/unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("System error occurred while processing request. Please retry later."));
    }

    @Test
    void returnsUserFacingMessageForInvalidRequestBodies() throws Exception {
        mockMvc.perform(post("/test/failing/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request parameter error. Please retry."));
    }

    @Test
    void returnsBadRequestForMaterialUploadErrors() throws Exception {
        mockMvc.perform(get("/test/failing/material-upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("MATERIAL_TYPE_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("Only .docx and .pdf files are supported"));
    }

    @Test
    void returnsNotFoundForQualityCheckMissingData() throws Exception {
        mockMvc.perform(get("/test/failing/quality-check-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("QUALITY_CHECK_NOT_FOUND"));
    }

    @Test
    void returnsConflictForBlockedCandidate() throws Exception {
        mockMvc.perform(get("/test/failing/candidate-blocked"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AI_CANDIDATE_TARGET_BLOCKED"));
    }

    @RestController
    public static class FailingController {
        @GetMapping("/test/failing/data-integrity")
        void dataIntegrity() {
            throw new DataIntegrityViolationException("foreign key detail that should not leak");
        }

        @GetMapping("/test/failing/unknown")
        void unknown() {
            throw new RuntimeException("sensitive internal detail");
        }

        @PostMapping("/test/failing/validation")
        void validation(@Valid @RequestBody ValidationRequest request) {
        }

        @GetMapping("/test/failing/material-upload")
        void materialUpload() {
            throw new MaterialUploadException("MATERIAL_TYPE_NOT_ALLOWED", "Only .docx and .pdf files are supported");
        }

        @GetMapping("/test/failing/quality-check-missing")
        void qualityCheckMissing() {
            throw new QualityCheckException("QUALITY_CHECK_NOT_FOUND", "Latest quality check is not available");
        }

        @GetMapping("/test/failing/candidate-blocked")
        void candidateBlocked() {
            throw new AiParagraphCandidateException("AI_CANDIDATE_TARGET_BLOCKED", "Target node is blocked");
        }
    }

    record ValidationRequest(@NotBlank String name) {
    }
}
