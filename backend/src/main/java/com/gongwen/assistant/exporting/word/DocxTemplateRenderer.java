package com.gongwen.assistant.exporting.word;

import com.gongwen.assistant.template.parser.DocxPlaceholderParser;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DocxTemplateRenderer {
    private final DocxPlaceholderParser placeholderParser;

    public DocxTemplateRenderer() {
        this(new DocxPlaceholderParser());
    }

    public DocxTemplateRenderer(DocxPlaceholderParser placeholderParser) {
        this.placeholderParser = placeholderParser;
    }

    public boolean hasPlaceholders(byte[] templateBytes) {
        return !placeholderParser.parsePlaceholders(templateBytes).isEmpty();
    }

    public byte[] render(byte[] templateBytes, Map<String, String> values) {
        validateValues(templateBytes, values);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(templateBytes));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getParagraphs().forEach(paragraph -> replaceInParagraph(paragraph, values));
            for (XWPFTable table : document.getTables()) {
                table.getRows().forEach(row -> row.getTableCells()
                        .forEach(cell -> cell.getParagraphs()
                                .forEach(paragraph -> replaceInParagraph(paragraph, values))));
            }
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法渲染 Word 模板", exception);
        }
    }

    public byte[] renderDraftSnapshot(Map<String, String> values) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addTitle(document, value(values, "标题", "TITLE"));
            addParagraph(document, value(values, "主送", "RECIPIENT"));
            addBody(document, value(values, "正文", "BODY_PARAGRAPH"));
            addOptionalParagraph(document, value(values, "附件", "ATTACHMENT"));
            addRightParagraph(document, value(values, "落款", "SIGNATURE"));
            addRightParagraph(document, value(values, "日期", "DATE"));
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法生成当前草稿 Word", exception);
        }
    }

    private void validateValues(byte[] templateBytes, Map<String, String> values) {
        for (String placeholder : placeholderParser.parsePlaceholders(templateBytes)) {
            if (!values.containsKey(placeholder) || values.get(placeholder) == null) {
                throw new MissingTemplateValueException(placeholder);
            }
        }
    }

    private void replaceInParagraph(XWPFParagraph paragraph, Map<String, String> values) {
        List<XWPFRun> runs = paragraph.getRuns();
        for (XWPFRun run : runs) {
            String text = run.getText(0);
            if (text == null || !text.contains("{{")) {
                continue;
            }
            replaceRunText(run, text, values);
        }
    }

    private void replaceRunText(XWPFRun run, String text, Map<String, String> values) {
        String replaced = text;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            replaced = replaced.replace("{{" + entry.getKey() + "}}", entry.getValue());
            replaced = replaced.replace("{{ " + entry.getKey() + " }}", entry.getValue());
        }

        String[] lines = replaced.split("\\R", -1);
        run.setText(lines[0], 0);
        for (int index = 1; index < lines.length; index++) {
            run.addBreak();
            run.setText(lines[index]);
        }
    }

    private void addTitle(XWPFDocument document, String title) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontFamily("FangSong");
        run.setFontSize(22);
        run.setText(title);
    }

    private void addBody(XWPFDocument document, String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        for (String line : body.split("\\R", -1)) {
            addParagraph(document, line);
        }
    }

    private void addOptionalParagraph(XWPFDocument document, String text) {
        if (text == null || text.isBlank() || "无".equals(text.trim())) {
            return;
        }
        addParagraph(document, text);
    }

    private void addParagraph(XWPFDocument document, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("FangSong");
        run.setFontSize(16);
        run.setText(text);
    }

    private void addRightParagraph(XWPFDocument document, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("FangSong");
        run.setFontSize(16);
        run.setText(text);
    }

    private String value(Map<String, String> values, String primaryKey, String fallbackKey) {
        String primary = values.get(primaryKey);
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        String fallback = values.get(fallbackKey);
        return fallback == null ? "" : fallback;
    }
}
