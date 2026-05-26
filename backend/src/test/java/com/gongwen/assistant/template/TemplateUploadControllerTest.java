package com.gongwen.assistant.template;

import com.gongwen.assistant.template.parser.DocxPlaceholderParser;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TemplateController.class)
class TemplateUploadControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocxPlaceholderParser placeholderParser;

    @MockBean
    private TemplateUploadService uploadService;

    @MockBean
    private TemplateProfileRepository profileRepository;

    @MockBean
    private TemplateVersionRepository versionRepository;

    @MockBean
    private TemplateRepository templateRepository;

    @Test
    void uploadsTemplateVersion() throws Exception {
        when(uploadService.upload(eq(1L), eq("notice.docx"), any(), any()))
                .thenReturn(new TemplateUploadResponse(
                        9L,
                        1,
                        "READY",
                        2,
                        4,
                        1,
                        List.of("PLACEHOLDER_SPLIT_ACROSS_RUNS")
                ));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notice.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "content".getBytes()
        );

        mockMvc.perform(multipart("/api/templates/1/versions").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.templateVersionId").value(9))
                .andExpect(jsonPath("$.data.parseStatus").value("READY"))
                .andExpect(jsonPath("$.data.validationCodes[0]").value("PLACEHOLDER_SPLIT_ACROSS_RUNS"));
    }

    @Test
    void returnsProfileByVersionId() throws Exception {
        when(profileRepository.findByTemplateVersionId(9L))
                .thenReturn(Optional.of(new TemplateProfile(
                        1,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )));

        mockMvc.perform(get("/api/templates/versions/9/profile").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.schemaVersion").value(1));
    }

    @Test
    void normalizesTemplateErrors() throws Exception {
        when(uploadService.upload(eq(1L), eq("bad.pdf"), any(), any()))
                .thenThrow(new TemplateException("TEMPLATE_TYPE_NOT_ALLOWED", "Only .docx Word templates are supported"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bad.pdf",
                "application/pdf",
                "content".getBytes()
        );

        mockMvc.perform(multipart("/api/templates/1/versions").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("TEMPLATE_TYPE_NOT_ALLOWED"));
    }
}
