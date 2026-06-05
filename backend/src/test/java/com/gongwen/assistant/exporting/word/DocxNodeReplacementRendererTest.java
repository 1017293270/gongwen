package com.gongwen.assistant.exporting.word;

import com.gongwen.assistant.support.DocxTestFactory;
import org.apache.poi.xwpf.usermodel.LineSpacingRule;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
    void resolvesStyleSourcesBeforeInsertionsShiftOriginalParagraphIndexes() throws Exception {
        byte[] template = DocxTestFactory.speechReferenceDocument();

        byte[] rendered = renderer.render(
                template,
                Map.of(),
                Set.of(),
                List.of(
                        new DocxNodeReplacementRenderer.NodeInsertion("paragraph-4", "AFTER", "一、第一标题", "paragraph-6"),
                        new DocxNodeReplacementRenderer.NodeInsertion("paragraph-4", "AFTER", "第一段正文", "paragraph-4"),
                        new DocxNodeReplacementRenderer.NodeInsertion("paragraph-4", "AFTER", "二、第二标题", "paragraph-6"),
                        new DocxNodeReplacementRenderer.NodeInsertion("paragraph-4", "AFTER", "第二段正文", "paragraph-4")
                )
        );

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            List<String> texts = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .toList();
            XWPFParagraph firstHeading = document.getParagraphs().get(texts.indexOf("一、第一标题"));
            XWPFParagraph secondHeading = document.getParagraphs().get(texts.indexOf("二、第二标题"));
            XWPFParagraph firstBody = document.getParagraphs().get(texts.indexOf("第一段正文"));

            assertThat(firstHeading.getRuns().getFirst().isBold()).isTrue();
            assertThat(secondHeading.getRuns().getFirst().isBold()).isTrue();
            assertThat(secondHeading.getRuns().getFirst().getFontSize()).isEqualTo(firstHeading.getRuns().getFirst().getFontSize());
            assertThat(secondHeading.getIndentationFirstLine()).isEqualTo(firstHeading.getIndentationFirstLine());
            assertThat(firstBody.getRuns().getFirst().isBold()).isFalse();
        }
    }

    @Test
    void doesNotCopyPaginationPropertiesWhenInsertingSyntheticParagraphs() throws Exception {
        byte[] template = docxWithPaginationStyleSource();

        byte[] rendered = renderer.render(
                template,
                Map.of(),
                Set.of(),
                List.of(new DocxNodeReplacementRenderer.NodeInsertion(
                        "paragraph-0",
                        "AFTER",
                        "插入正文",
                        "paragraph-1",
                        new com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile(
                                null,
                                null,
                                null,
                                null,
                                420,
                                null,
                                null,
                                null
                        )
                ))
        );

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            List<String> texts = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .toList();
            XWPFParagraph inserted = document.getParagraphs().get(texts.indexOf("插入正文"));
            CTPPr paragraphProperties = inserted.getCTP().getPPr();

            assertThat(paragraphProperties.isSetPageBreakBefore()).isFalse();
            assertThat(paragraphProperties.isSetKeepNext()).isFalse();
            assertThat(paragraphProperties.isSetKeepLines()).isFalse();
            assertThat(paragraphProperties.isSetSectPr()).isFalse();
            assertThat(inserted.getSpacingBetween()).isCloseTo(28.0d, within(0.01d));
            assertThat(inserted.getIndentationFirstLine()).isEqualTo(420);
            assertThat(inserted.getRuns().getFirst().isBold()).isTrue();
        }
    }

    @Test
    void appliesParsedFormattingWhenInsertingSyntheticParagraphs() throws Exception {
        byte[] template = DocxTestFactory.docxWithParagraphs("锚点", "无缩进样式来源");

        byte[] rendered = renderer.render(
                template,
                Map.of(),
                Set.of(),
                List.of(new DocxNodeReplacementRenderer.NodeInsertion(
                        "paragraph-0",
                        "AFTER",
                        "插入正文",
                        "paragraph-1",
                        new com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile(
                                "FangSong",
                                32,
                                false,
                                "LEFT",
                                840,
                                150,
                                0,
                                0
                        )
                ))
        );

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            List<String> texts = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .toList();
            XWPFParagraph inserted = document.getParagraphs().get(texts.indexOf("插入正文"));

            assertThat(inserted.getIndentationFirstLine()).isEqualTo(840);
            assertThat(inserted.getSpacingBetween()).isCloseTo(1.5d, within(0.01d));
            assertThat(inserted.getRuns().getFirst().getFontSize()).isEqualTo(16);
        }
    }

    @Test
    void removesCopiedNumberingAndHiddenIndentBeforeApplyingParsedFormatting() throws Exception {
        byte[] template = docxWithNumberedIndentedStyleSource();

        byte[] rendered = renderer.render(
                template,
                Map.of(),
                Set.of(),
                List.of(new DocxNodeReplacementRenderer.NodeInsertion(
                        "paragraph-0",
                        "AFTER",
                        "1. 任务一",
                        "paragraph-1",
                        new com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile(
                                "FangSong",
                                32,
                                false,
                                "LEFT",
                                280,
                                150,
                                0,
                                0
                        )
                ))
        );

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            List<String> texts = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .toList();
            XWPFParagraph inserted = document.getParagraphs().get(texts.indexOf("1. 任务一"));
            CTPPr paragraphProperties = inserted.getCTP().getPPr();

            assertThat(paragraphProperties.isSetNumPr()).isFalse();
            assertThat(inserted.getIndentationLeft()).isEqualTo(-1);
            assertThat(inserted.getIndentationHanging()).isEqualTo(-1);
            assertThat(inserted.getIndentationFirstLine()).isEqualTo(280);
        }
    }

    @Test
    void insertsSyntheticParagraphsBeforeRemovingDeletedAnchor() throws Exception {
        byte[] template = DocxTestFactory.speechReferenceDocument();
        List<String> deletedBodyTexts;
        try (XWPFDocument source = new XWPFDocument(new ByteArrayInputStream(template))) {
            deletedBodyTexts = source.getParagraphs().subList(4, source.getParagraphs().size()).stream()
                    .map(paragraph -> paragraph.getText())
                    .toList();
        }

        byte[] rendered = renderer.render(
                template,
                Map.of(),
                Set.of(),
                Set.of(
                        "paragraph-4",
                        "paragraph-5",
                        "paragraph-6",
                        "paragraph-7",
                        "paragraph-8",
                        "paragraph-9",
                        "paragraph-10",
                        "paragraph-11",
                        "paragraph-12",
                        "paragraph-13",
                        "paragraph-14",
                        "paragraph-15",
                        "paragraph-16",
                        "paragraph-17",
                        "paragraph-18",
                        "paragraph-19"
                ),
                List.of(
                        new DocxNodeReplacementRenderer.NodeInsertion(
                                "paragraph-4",
                                "AFTER",
                                "Applied outline heading",
                                "paragraph-6"
                        ),
                        new DocxNodeReplacementRenderer.NodeInsertion(
                                "paragraph-4",
                                "AFTER",
                                "Applied outline body",
                                "paragraph-4"
                        )
                )
        );

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            List<String> texts = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .toList();
            assertThat(texts).contains("Applied outline heading", "Applied outline body");
            assertThat(texts).doesNotContain(deletedBodyTexts.toArray(String[]::new));
            assertThat(texts.get(4)).isEqualTo("Applied outline heading");
            assertThat(texts.get(5)).isEqualTo("Applied outline body");
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

    private byte[] docxWithPaginationStyleSource() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("锚点");

            XWPFParagraph source = document.createParagraph();
            source.setSpacingBetween(28.0d, LineSpacingRule.EXACT);
            source.setIndentationFirstLine(420);
            CTPPr paragraphProperties = source.getCTP().isSetPPr()
                    ? source.getCTP().getPPr()
                    : source.getCTP().addNewPPr();
            paragraphProperties.addNewPageBreakBefore();
            paragraphProperties.addNewKeepNext();
            paragraphProperties.addNewKeepLines();
            paragraphProperties.addNewSectPr();
            XWPFRun run = source.createRun();
            run.setBold(true);
            run.setText("样式来源");

            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] docxWithNumberedIndentedStyleSource() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("锚点");

            XWPFParagraph source = document.createParagraph();
            source.setIndentationLeft(960);
            source.setIndentationHanging(420);
            CTPPr paragraphProperties = source.getCTP().isSetPPr()
                    ? source.getCTP().getPPr()
                    : source.getCTP().addNewPPr();
            paragraphProperties.addNewNumPr();
            XWPFRun run = source.createRun();
            run.setText("1. 样式来源");

            document.write(output);
            return output.toByteArray();
        }
    }
}
