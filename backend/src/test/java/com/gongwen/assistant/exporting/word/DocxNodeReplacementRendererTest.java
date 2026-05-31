package com.gongwen.assistant.exporting.word;

import com.gongwen.assistant.support.DocxTestFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DocxNodeReplacementRendererTest {
    private final DocxNodeReplacementRenderer renderer = new DocxNodeReplacementRenderer();

    @Test
    void replacesMappedParagraphInPlaceAndPreservesFormatting() throws Exception {
        byte[] template = DocxTestFactory.speechReferenceDocument();
        Map<String, String> replacements = Map.of(
                "paragraph-0", "新的会议讲话标题",
                "paragraph-4", "新的第一段正文"
        );

        byte[] rendered = renderer.render(template, replacements, Set.of());

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            assertThat(document.getParagraphs().get(0).getText()).isEqualTo("新的会议讲话标题");
            assertThat(document.getParagraphs().get(4).getText()).isEqualTo("新的第一段正文");
            assertThat(document.getParagraphs().get(0).getAlignment().name()).isEqualTo("CENTER");
            assertThat(document.getHeaderList().getFirst().getParagraphs().getFirst().getText()).isEqualTo("内部测试资料");
        }
    }
}
