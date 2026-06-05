package com.gongwen.assistant.template;

import com.gongwen.assistant.security.CurrentUserProvider;
import com.gongwen.assistant.documentstructure.DocumentStructureProfileRepository;
import com.gongwen.assistant.template.parser.DocxPlaceholderParser;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TemplateController.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(roles = {"TEMPLATE_ADMIN"})
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
    private TemplateStructureFormattingRepository structureFormattingRepository;

    @MockBean
    private DocumentStructureProfileRepository documentStructureProfileRepository;

    @MockBean
    private TemplateVersionRepository versionRepository;

    @MockBean
    private TemplateRepository templateRepository;

    @MockBean
    private CurrentUserProvider currentUserProvider;

    @Test
    void uploadsTemplateVersion() throws Exception {
        when(templateRepository.findById(eq(1L), isNull()))
                .thenReturn(Optional.of(new TemplateSummary(1L, "notice", "NOTICE", "ACTIVE")));
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
    void returnsStructureFormattingOverrides() throws Exception {
        when(structureFormattingRepository.findOverrides(9L))
                .thenReturn(Map.of("paragraph-0", new TemplateStructureFormattingProfile(
                        "SimSun",
                        52,
                        true,
                        "CENTER",
                        null,
                        100,
                        null,
                        180
                )));

        mockMvc.perform(get("/api/templates/versions/9/structure-formatting").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data['paragraph-0'].fontFamily").value("SimSun"))
                .andExpect(jsonPath("$.data['paragraph-0'].alignment").value("CENTER"));
    }

    @Test
    void updatesStructureFormattingOverride() throws Exception {
        mockMvc.perform(put("/api/templates/versions/9/structures/paragraph-0/formatting")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fontFamily": "SimSun",
                                  "fontSizeHalfPoints": 52,
                                  "bold": true,
                                  "alignment": "CENTER",
                                  "indentationFirstLine": null,
                                  "spacingBetween": 100,
                                  "spacingBefore": null,
                                  "spacingAfter": 180
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fontFamily").value("SimSun"));

        verify(structureFormattingRepository).saveOverride(eq(9L), eq("paragraph-0"), any(TemplateStructureFormattingProfile.class));
    }

    @Test
    void normalizesTemplateErrors() throws Exception {
        when(templateRepository.findById(eq(1L), isNull()))
                .thenReturn(Optional.of(new TemplateSummary(1L, "notice", "NOTICE", "ACTIVE")));
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
