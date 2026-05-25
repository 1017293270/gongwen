package com.gongwen.assistant.material;

import com.gongwen.assistant.support.DocxTestFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialTextExtractorTest {
    private final MaterialTextExtractor extractor = new DocxPdfMaterialTextExtractor();

    @Test
    void extractsDocxText() {
        byte[] docx = DocxTestFactory.docxWithParagraphs("第一段材料", "第二段材料");

        String text = extractor.extract("docx", docx);

        assertThat(text).contains("第一段材料", "第二段材料");
    }

    @Test
    void extractsPdfText() throws IOException {
        byte[] pdf = pdfWithText("meeting material");

        String text = extractor.extract("pdf", pdf);

        assertThat(text).contains("meeting material");
    }

    private byte[] pdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
