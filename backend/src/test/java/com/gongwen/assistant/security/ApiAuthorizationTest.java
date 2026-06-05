package com.gongwen.assistant.security;

import com.gongwen.assistant.ai.candidate.AiParagraphCandidateController;
import com.gongwen.assistant.ai.candidate.AiParagraphCandidateService;
import com.gongwen.assistant.ai.candidate.ParagraphCandidateJobService;
import com.gongwen.assistant.draft.DraftController;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.material.MaterialController;
import com.gongwen.assistant.material.MaterialService;
import com.gongwen.assistant.quality.QualityCheckController;
import com.gongwen.assistant.quality.QualityCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        DraftController.class,
        MaterialController.class,
        QualityCheckController.class,
        AiParagraphCandidateController.class
})
@Import(SecurityConfiguration.class)
class ApiAuthorizationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DraftService draftService;

    @MockBean
    private MaterialService materialService;

    @MockBean
    private QualityCheckService qualityCheckService;

    @MockBean
    private AiParagraphCandidateService aiParagraphCandidateService;

    @MockBean
    private ParagraphCandidateJobService jobService;

    @Test
    @WithMockUser(roles = "GUEST")
    void deniesWorkbenchScopedApiRoutesForNonWorkbenchRoles() throws Exception {
        mockMvc.perform(get("/api/drafts"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PERMISSION_DENIED"));

        mockMvc.perform(get("/api/drafts/1/materials"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PERMISSION_DENIED"));

        mockMvc.perform(get("/api/drafts/1/quality-check/latest"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PERMISSION_DENIED"));

        mockMvc.perform(get("/api/drafts/1/ai/paragraph-candidates"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PERMISSION_DENIED"));
    }
}
