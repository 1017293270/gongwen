package com.gongwen.assistant.exporting.word;

import com.gongwen.assistant.template.parser.DocxPlaceholderParser;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
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
}
