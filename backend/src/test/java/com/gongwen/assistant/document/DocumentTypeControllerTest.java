package com.gongwen.assistant.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentTypeController.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentTypeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentTypeService documentTypeService;

    @Test
    void listsActiveDocumentTypes() throws Exception {
        when(documentTypeService.findActive()).thenReturn(List.of(
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

    @Test
    void createsDocumentType() throws Exception {
        when(documentTypeService.create(new CreateDocumentTypeRequest("MEETING", "会议纪要", 4)))
                .thenReturn(new DocumentTypeDto("MEETING", "会议纪要", "ACTIVE", 4));

        mockMvc.perform(post("/api/document-types")
                        .contentType("application/json")
                        .content("""
                                {"code":"MEETING","name":"会议纪要","sortOrder":4}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("MEETING"))
                .andExpect(jsonPath("$.data.name").value("会议纪要"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void updatesDocumentType() throws Exception {
        when(documentTypeService.update("NOTICE", new UpdateDocumentTypeRequest("通知公文", 8)))
                .thenReturn(new DocumentTypeDto("NOTICE", "通知公文", "ACTIVE", 8));

        mockMvc.perform(put("/api/document-types/NOTICE")
                        .contentType("application/json")
                        .content("""
                                {"name":"通知公文","sortOrder":8}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("NOTICE"))
                .andExpect(jsonPath("$.data.name").value("通知公文"))
                .andExpect(jsonPath("$.data.sortOrder").value(8));
    }

    @Test
    void deletesDocumentType() throws Exception {
        mockMvc.perform(delete("/api/document-types/MEETING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(documentTypeService).delete("MEETING");
    }

    @Test
    void returnsBadRequestForDocumentTypeErrors() throws Exception {
        when(documentTypeService.create(new CreateDocumentTypeRequest("", "", 0)))
                .thenThrow(new DocumentTypeException("DOCUMENT_TYPE_CODE_REQUIRED", "Document type code is required"));

        mockMvc.perform(post("/api/document-types")
                        .contentType("application/json")
                        .content("""
                                {"code":"","name":"","sortOrder":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DOCUMENT_TYPE_CODE_REQUIRED"));
    }
}
