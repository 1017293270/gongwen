package com.gongwen.assistant.exporting.word;

import com.gongwen.assistant.support.DocxTestFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
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

    @Test
    void insertsSyntheticParagraphsAfterOriginalAnchor() throws Exception {
        byte[] template = DocxTestFactory.speechReferenceDocument();

        byte[] rendered = renderer.render(
                template,
                Map.of("paragraph-4", "existing body"),
                Set.of(),
                List.of(
                        new DocxNodeReplacementRenderer.NodeInsertion("paragraph-4", "AFTER", "New level two heading", "paragraph-3"),
                        new DocxNodeReplacementRenderer.NodeInsertion("paragraph-4", "AFTER", "New body paragraph", "paragraph-4")
                )
        );

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            List<org.apache.poi.xwpf.usermodel.XWPFParagraph> paragraphs = document.getParagraphs();
            assertThat(paragraphs.stream().map(paragraph -> paragraph.getText()).toList())
                    .containsSequence("existing body", "New level two heading", "New body paragraph");
            int headingIndex = paragraphs.stream()
                    .map(org.apache.poi.xwpf.usermodel.XWPFParagraph::getText)
                    .toList()
                    .indexOf("New level two heading");
            assertThat(headingIndex).isGreaterThan(0);
            assertThat(paragraphs.get(headingIndex).getAlignment()).isEqualTo(paragraphs.get(3).getAlignment());
            assertThat(paragraphs.get(headingIndex).getRuns().getFirst().isBold()).isEqualTo(paragraphs.get(3).getRuns().getFirst().isBold());
        }
    }

    @Test
    void clearsIgnoredBodyParagraphsButRemovesDeletedBodyParagraphs() throws Exception {
        byte[] template = DocxTestFactory.speechReferenceDocument();

        byte[] rendered = renderer.render(
                template,
                Map.of(),
                Set.of("paragraph-16"),
                Set.of("paragraph-17"),
                List.of()
        );

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered));
             XWPFDocument source = new XWPFDocument(new ByteArrayInputStream(template))) {
            List<String> texts = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .toList();
            assertThat(document.getParagraphs()).hasSize(source.getParagraphs().size() - 1);
            assertThat(texts).doesNotContain("缁撴潫璇?");
            assertThat(texts).contains("");
        }
    }
}
