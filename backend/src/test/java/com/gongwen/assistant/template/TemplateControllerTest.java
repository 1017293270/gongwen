package com.gongwen.assistant.template;

import com.gongwen.assistant.support.DocxTestFactory;
import com.gongwen.assistant.documentstructure.DocumentStructureProfileRepository;
import com.gongwen.assistant.security.CurrentUserProvider;
import com.gongwen.assistant.template.parser.DocxPlaceholderParser;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TemplateController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(DocxPlaceholderParser.class)
class TemplateControllerTest {
    @Autowired
    private MockMvc mockMvc;

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
    void parsesUploadedTemplatePlaceholders() throws Exception {
        byte[] template = DocxTestFactory.docxWithParagraphs("{{标题}}", "{{正文}}");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notice-template.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                template
        );

        mockMvc.perform(multipart("/api/templates/parse").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.placeholders[0]").value("标题"))
                .andExpect(jsonPath("$.data.placeholders[1]").value("正文"));
    }

    @Test
    void deletesTemplate() throws Exception {
        mockMvc.perform(delete("/api/templates/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(templateRepository).deleteById(7L, null);
    }
}
