package com.gongwen.assistant.template.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocxPlaceholderParser {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");

    public ParsedTemplate parse(byte[] docxBytes) {
        Set<String> placeholders = new LinkedHashSet<>();

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            document.getParagraphs().forEach(paragraph -> collectFromText(paragraph.getText(), placeholders));
            for (XWPFTable table : document.getTables()) {
                table.getRows().forEach(row -> row.getTableCells()
                        .forEach(cell -> collectFromText(cell.getText(), placeholders)));
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取 Word 模板文件", exception);
        }

        return new ParsedTemplate(new ArrayList<>(placeholders));
    }

    private void collectFromText(String text, Set<String> placeholders) {
        if (text == null || text.isBlank()) {
            return;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            String placeholder = matcher.group(1).trim();
            if (!placeholder.isEmpty()) {
                placeholders.add(placeholder);
            }
        }
    }

    public List<String> parsePlaceholders(byte[] docxBytes) {
        return parse(docxBytes).placeholders();
    }
}
