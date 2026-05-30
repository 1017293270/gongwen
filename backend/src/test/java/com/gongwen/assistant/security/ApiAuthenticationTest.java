package com.gongwen.assistant.security;

import com.gongwen.assistant.draft.DraftController;
import com.gongwen.assistant.draft.DraftService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DraftController.class)
@Import(SecurityConfiguration.class)
class ApiAuthenticationTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DraftService draftService;

    @Test
    void rejectsUnauthenticatedBusinessApiRequests() throws Exception {
        mockMvc.perform(get("/api/drafts").param("documentTypeCode", "NOTICE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    }
}
