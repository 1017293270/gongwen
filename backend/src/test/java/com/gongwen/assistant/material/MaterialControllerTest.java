package com.gongwen.assistant.material;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MaterialController.class)
@AutoConfigureMockMvc(addFilters = false)
class MaterialControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MaterialService materialService;

    @Test
    void uploadsDraftMaterial() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "meeting.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "会议纪要".getBytes()
        );
        when(materialService.uploadMaterial(1L, file)).thenReturn(sampleReadyMaterial());

        mockMvc.perform(multipart("/api/drafts/1/materials").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalFileName").value("meeting.docx"))
                .andExpect(jsonPath("$.data.status").value("READY"));
    }

    @Test
    void listsDraftMaterials() throws Exception {
        when(materialService.listMaterials(1L)).thenReturn(List.of(sampleReadyMaterial()));

        mockMvc.perform(get("/api/drafts/1/materials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].originalFileName").value("meeting.docx"))
                .andExpect(jsonPath("$.data[0].extractedTextLength").value(4));
    }

    @Test
    void returnsBadRequestForInvalidUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "plain text".getBytes()
        );
        when(materialService.uploadMaterial(1L, file))
                .thenThrow(new MaterialUploadException("MATERIAL_TYPE_NOT_ALLOWED", "仅支持上传 Word 或 PDF 材料"));

        mockMvc.perform(multipart("/api/drafts/1/materials").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("MATERIAL_TYPE_NOT_ALLOWED"));
    }

    private MaterialDto sampleReadyMaterial() {
        return new MaterialDto(
                1L,
                1L,
                "meeting.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                12L,
                "docx",
                "READY",
                4,
                null
        );
    }
}
