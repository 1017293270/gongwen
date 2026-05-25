package com.gongwen.assistant.material;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class DocxPdfMaterialTextExtractor implements MaterialTextExtractor {
    @Override
    public String extract(String fileExtension, byte[] content) {
        return switch (fileExtension.toLowerCase(Locale.ROOT)) {
            case "docx" -> extractDocx(content);
            case "pdf" -> extractPdf(content);
            default -> throw new MaterialExtractionException("不支持的材料类型");
        };
    }

    private String extractDocx(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            return document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText().trim())
                    .filter(text -> !text.isBlank())
                    .collect(Collectors.joining("\n"));
        } catch (IOException exception) {
            throw new MaterialExtractionException("Word 材料文本提取失败", exception);
        }
    }

    private String extractPdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document).trim();
        } catch (IOException exception) {
            throw new MaterialExtractionException("PDF 材料文本提取失败", exception);
        }
    }
}
