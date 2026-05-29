package com.gongwen.assistant.exporting.word;

import com.gongwen.assistant.support.DocxTestFactory;
import com.gongwen.assistant.template.profile.TemplateProfileParser;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class DocxTemplateRendererTest {
    private static final String TITLE_KEY = "\u6807\u9898";
    private static final String RECIPIENT_KEY = "\u4e3b\u9001";
    private static final String BODY_KEY = "\u6b63\u6587";
    private static final String SIGNATURE_KEY = "\u843d\u6b3e";
    private static final String DATE_KEY = "\u65e5\u671f";

    private final DocxTemplateRenderer renderer = new DocxTemplateRenderer();

    @Test
    void replacesPlaceholdersAndPreservesLineBreaks() {
        byte[] template = DocxTestFactory.docxWithParagraphs(
                "{{" + TITLE_KEY + "}}",
                "{{" + RECIPIENT_KEY + "}}\uff1a",
                "{{" + BODY_KEY + "}}",
                "{{" + SIGNATURE_KEY + "}}",
                "{{" + DATE_KEY + "}}"
        );

        byte[] exported = renderer.render(template, Map.of(
                TITLE_KEY, "\u5173\u4e8e\u5f00\u5c55\u5e74\u5ea6\u6863\u6848\u6574\u7406\u5de5\u4f5c\u7684\u901a\u77e5",
                RECIPIENT_KEY, "\u5404\u90e8\u95e8\u3001\u5404\u76f4\u5c5e\u5355\u4f4d",
                BODY_KEY, "\u7b2c\u4e00\u6bb5\u5185\u5bb9\n\u7b2c\u4e8c\u6bb5\u5185\u5bb9",
                SIGNATURE_KEY, "\u529e\u516c\u5ba4",
                DATE_KEY, "2026-05-05"
        ));

        String text = DocxTestFactory.readText(exported);
        assertThat(text).contains("\u5173\u4e8e\u5f00\u5c55\u5e74\u5ea6\u6863\u6848\u6574\u7406\u5de5\u4f5c\u7684\u901a\u77e5");
        assertThat(text).contains("\u5404\u90e8\u95e8\u3001\u5404\u76f4\u5c5e\u5355\u4f4d\uff1a");
        assertThat(text).contains("\u7b2c\u4e00\u6bb5\u5185\u5bb9");
        assertThat(text).contains("\u7b2c\u4e8c\u6bb5\u5185\u5bb9");
        assertThat(text).contains("\u529e\u516c\u5ba4");
        assertThat(text).doesNotContain("{{" + TITLE_KEY + "}}");
    }

    @Test
    void failsWhenAPlaceholderValueIsMissing() {
        byte[] template = DocxTestFactory.docxWithParagraphs("{{" + TITLE_KEY + "}}", "{{" + BODY_KEY + "}}");

        assertThatThrownBy(() -> renderer.render(template, Map.of(TITLE_KEY, "\u6d4b\u8bd5\u6807\u9898")))
                .isInstanceOf(MissingTemplateValueException.class)
                .hasMessageContaining(BODY_KEY);
    }

    @Test
    void replacesSplitRunPlaceholdersInsteadOfLeavingFragmentsInOutput() throws Exception {
        byte[] template = docxWithSplitRunPlaceholder(TITLE_KEY);

        byte[] exported = renderer.render(template, Map.of(TITLE_KEY, "\u5206\u88c2\u5360\u4f4d\u7b26\u6807\u9898"));

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(exported))) {
            assertThat(document.getParagraphs()).hasSize(1);
            assertThat(document.getParagraphs().get(0).getText()).isEqualTo("\u5206\u88c2\u5360\u4f4d\u7b26\u6807\u9898");
            assertThat(document.getParagraphs().get(0).getRuns()).hasSize(1);
            assertThat(document.getParagraphs().get(0).getRuns().get(0).text()).isEqualTo("\u5206\u88c2\u5360\u4f4d\u7b26\u6807\u9898");
        }
    }

    @Test
    void snapshotExportAppliesTitleBodyAndSignatureFormatting() throws Exception {
        ExportFormattingContext formatting = new ExportFormattingContext(
                formatting("FangSong", 44, true, "CENTER", 0, 0, 0, 240),
                null,
                formatting("KaiTi", 32, false, "LEFT", 560, 150, 0, 0),
                formatting("FangSong", 32, false, "RIGHT", 0, 0, 0, 0),
                formatting("FangSong", 32, false, "RIGHT", 0, 0, 0, 0)
        );

        byte[] exported = renderer.renderDraftSnapshot(Map.of(
                TITLE_KEY, "\u5173\u4e8e\u53ec\u5f00\u4f1a\u8bae\u7684\u901a\u77e5",
                RECIPIENT_KEY, "\u5404\u5355\u4f4d",
                BODY_KEY, "\u4e00\u3001\u4f1a\u8bae\u65f6\u95f4\n2026-05-29 09:30",
                SIGNATURE_KEY, "\u7efc\u5408\u7ba1\u7406\u90e8",
                DATE_KEY, "2026-05-29"
        ), formatting);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(exported))) {
            assertThat(document.getParagraphs()).hasSize(6);
            assertThat(document.getParagraphs().get(0).getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
            assertThat(document.getParagraphs().get(0).getSpacingAfter()).isEqualTo(240);
            assertThat(document.getParagraphs().get(0).getRuns().get(0).isBold()).isTrue();
            assertThat(document.getParagraphs().get(0).getRuns().get(0).getFontSize()).isEqualTo(22);

            assertThat(document.getParagraphs().get(1).getAlignment()).isEqualTo(ParagraphAlignment.LEFT);
            assertThat(document.getParagraphs().get(1).getRuns().get(0).getFontFamily()).isEqualTo("FangSong");
            assertThat(document.getParagraphs().get(1).getRuns().get(0).getFontSize()).isEqualTo(16);

            assertThat(document.getParagraphs().get(2).getAlignment()).isEqualTo(ParagraphAlignment.LEFT);
            assertThat(document.getParagraphs().get(2).getFirstLineIndent()).isEqualTo(560);
            assertThat(document.getParagraphs().get(2).getSpacingBetween()).isCloseTo(1.5d, within(0.01d));
            assertThat(document.getParagraphs().get(2).getRuns().get(0).getFontFamily()).isEqualTo("KaiTi");
            assertThat(document.getParagraphs().get(2).getRuns().get(0).getFontSize()).isEqualTo(16);

            assertThat(document.getParagraphs().get(3).getAlignment()).isEqualTo(ParagraphAlignment.LEFT);
            assertThat(document.getParagraphs().get(3).getFirstLineIndent()).isEqualTo(560);
            assertThat(document.getParagraphs().get(3).getSpacingBetween()).isCloseTo(1.5d, within(0.01d));

            assertThat(document.getParagraphs().get(4).getAlignment()).isEqualTo(ParagraphAlignment.RIGHT);
            assertThat(document.getParagraphs().get(4).getRuns().get(0).getFontSize()).isEqualTo(16);
            assertThat(document.getParagraphs().get(5).getAlignment()).isEqualTo(ParagraphAlignment.RIGHT);
        }
    }

    @Test
    void snapshotExportPreservesIntentionalBlankBodyLines() throws Exception {
        byte[] exported = renderer.renderDraftSnapshot(Map.of(
                TITLE_KEY, "\u5e26\u7a7a\u884c\u7684\u6b63\u6587",
                BODY_KEY, "\u7b2c\u4e00\u6bb5\n\n\u7b2c\u4e09\u6bb5"
        ), ExportFormattingContext.EMPTY);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(exported))) {
            assertThat(document.getParagraphs()).hasSize(4);
            assertThat(document.getParagraphs().get(1).getText()).isEqualTo("\u7b2c\u4e00\u6bb5");
            assertThat(document.getParagraphs().get(1).getAlignment()).isEqualTo(ParagraphAlignment.LEFT);
            assertThat(document.getParagraphs().get(2).getAlignment()).isEqualTo(ParagraphAlignment.LEFT);
            assertThat(document.getParagraphs().get(2).getText()).isEmpty();
            assertThat(document.getParagraphs().get(2).getRuns()).hasSize(1);
            assertThat(document.getParagraphs().get(3).getText()).isEqualTo("\u7b2c\u4e09\u6bb5");
        }
    }

    @Test
    void placeholderExportAppliesFormattingToMatchedParagraphs() throws Exception {
        byte[] template = DocxTestFactory.docxWithParagraphs(
                "{{" + TITLE_KEY + "}}",
                "{{" + RECIPIENT_KEY + "}}",
                "{{" + BODY_KEY + "}}",
                "{{" + SIGNATURE_KEY + "}}",
                "{{" + DATE_KEY + "}}"
        );
        ExportFormattingContext formatting = new ExportFormattingContext(
                formatting("SimHei", 48, true, "CENTER", 0, 0, 120, 240),
                formatting("FangSong", 32, false, "LEFT", 0, 0, 0, 0),
                formatting("KaiTi", 32, false, "LEFT", 560, 150, 0, 0),
                formatting("FangSong", 32, false, "RIGHT", 0, 0, 0, 0),
                formatting("FangSong", 32, false, "RIGHT", 0, 0, 0, 0)
        );

        byte[] exported = renderer.render(template, Map.of(
                TITLE_KEY, "\u6d4b\u8bd5\u6807\u9898",
                RECIPIENT_KEY, "\u5404\u5355\u4f4d",
                BODY_KEY, "\u6b63\u6587\u7b2c\u4e00\u6bb5",
                SIGNATURE_KEY, "\u7efc\u5408\u7ba1\u7406\u90e8",
                DATE_KEY, "2026-05-29"
        ), formatting);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(exported))) {
            assertThat(document.getParagraphs().get(0).getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
            assertThat(document.getParagraphs().get(0).getSpacingAfter()).isEqualTo(240);
            assertThat(document.getParagraphs().get(0).getRuns().get(0).getFontFamily()).isEqualTo("SimHei");
            assertThat(document.getParagraphs().get(0).getRuns().get(0).getFontSize()).isEqualTo(24);

            assertThat(document.getParagraphs().get(1).getAlignment()).isEqualTo(ParagraphAlignment.LEFT);
            assertThat(document.getParagraphs().get(1).getRuns().get(0).getFontFamily()).isEqualTo("FangSong");

            assertThat(document.getParagraphs().get(2).getAlignment()).isEqualTo(ParagraphAlignment.LEFT);
            assertThat(document.getParagraphs().get(2).getFirstLineIndent()).isEqualTo(560);
            assertThat(document.getParagraphs().get(2).getSpacingBetween()).isCloseTo(1.5d, within(0.01d));
            assertThat(document.getParagraphs().get(2).getRuns().get(0).getFontFamily()).isEqualTo("KaiTi");
            assertThat(document.getParagraphs().get(2).getRuns().get(0).getFontSize()).isEqualTo(16);

            assertThat(document.getParagraphs().get(3).getAlignment()).isEqualTo(ParagraphAlignment.RIGHT);
            assertThat(document.getParagraphs().get(4).getAlignment()).isEqualTo(ParagraphAlignment.RIGHT);
        }
    }

    @Test
    void placeholderExportFallsBackToDefaultFormattingWhenSlotContextIsEmpty() throws Exception {
        byte[] template = DocxTestFactory.docxWithParagraphs("{{" + TITLE_KEY + "}}");

        byte[] exported = renderer.render(template, Map.of(TITLE_KEY, "\u9ed8\u8ba4\u6837\u5f0f\u6807\u9898"), new ExportFormattingContext(
                null,
                null,
                null,
                null,
                null
        ));

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(exported))) {
            assertThat(document.getParagraphs()).hasSize(1);
            assertThat(document.getParagraphs().get(0).getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
            assertThat(document.getParagraphs().get(0).getRuns().get(0).getFontFamily()).isEqualTo("FangSong");
            assertThat(document.getParagraphs().get(0).getRuns().get(0).getFontSize()).isEqualTo(22);
            assertThat(document.getParagraphs().get(0).getRuns().get(0).isBold()).isTrue();
        }
    }

    @Test
    void placeholderExportReproducesParserDerivedBodySpacingContract() throws Exception {
        byte[] template = DocxTestFactory.docxWithOfficialStyleFormatting();
        TemplateStructureFormattingProfile bodyFormatting = new TemplateProfileParser()
                .parse(template)
                .structures()
                .stream()
                .filter(structure -> "BODY".equals(structure.structureType()))
                .findFirst()
                .orElseThrow()
                .formatting();

        byte[] exported = renderer.render(template, Map.of(
                TITLE_KEY, "测试标题",
                BODY_KEY, "正文第一段"
        ), new ExportFormattingContext(
                null,
                null,
                bodyFormatting,
                null,
                null
        ));

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(exported))) {
            assertThat(bodyFormatting.spacingBetween()).isEqualTo(150);
            assertThat(document.getParagraphs().get(1).getSpacingBetween()).isCloseTo(1.5d, within(0.01d));
        }
    }

    private static TemplateStructureFormattingProfile formatting(
            String fontFamily,
            Integer fontSizeHalfPoints,
            Boolean bold,
            String alignment,
            Integer indentationFirstLine,
            Integer spacingBetween,
            Integer spacingBefore,
            Integer spacingAfter
    ) {
        return new TemplateStructureFormattingProfile(
                fontFamily,
                fontSizeHalfPoints,
                bold,
                alignment,
                indentationFirstLine,
                spacingBetween,
                spacingBefore,
                spacingAfter
        );
    }

    private static byte[] docxWithSplitRunPlaceholder(String key) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("{{" + key.substring(0, 1));
            paragraph.createRun().setText(key.substring(1) + "}}");
            document.write(output);
            return output.toByteArray();
        }
    }
}
