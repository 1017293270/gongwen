package com.gongwen.assistant.template;

import com.gongwen.assistant.support.DocxTestFactory;
import com.gongwen.assistant.template.parser.DocxPlaceholderParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TemplateController.class)
@Import(DocxPlaceholderParser.class)
class TemplateControllerTest {
    @Autowired
    private MockMvc mockMvc;

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
}
