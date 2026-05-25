package com.gongwen.assistant.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentTypeController.class)
class DocumentTypeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentTypeRepository documentTypeRepository;

    @Test
    void listsActiveDocumentTypes() throws Exception {
        when(documentTypeRepository.findActive()).thenReturn(List.of(
                new DocumentTypeDto("NOTICE", "通知", "ACTIVE", 1),
                new DocumentTypeDto("REQUEST", "请示", "ACTIVE", 2),
                new DocumentTypeDto("REPORT", "报告", "ACTIVE", 3)
        ));

        mockMvc.perform(get("/api/document-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("NOTICE"))
                .andExpect(jsonPath("$.data[0].name").value("通知"))
                .andExpect(jsonPath("$.data[1].code").value("REQUEST"))
                .andExpect(jsonPath("$.data[2].code").value("REPORT"));
    }
}
