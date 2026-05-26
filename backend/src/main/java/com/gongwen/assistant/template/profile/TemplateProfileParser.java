package com.gongwen.assistant.template.profile;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateProfileParser {
    private static final int SCHEMA_VERSION = 1;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");

    public TemplateProfile parse(byte[] docxBytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            List<TemplateValidationItem> validationItems = new ArrayList<>();
            List<TemplatePlaceholderProfile> placeholders = new ArrayList<>();
            collectParagraphPlaceholders(document.getParagraphs(), placeholders, validationItems, "PARAGRAPH", "paragraph");
            collectTablePlaceholders(document, placeholders, validationItems);
            return new TemplateProfile(
                    SCHEMA_VERSION,
                    parseStyles(document),
                    deduplicatePlaceholders(placeholders),
                    parseSections(document),
                    parseTables(document),
                    parseMedia(document),
                    validationItems
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read Word template file", exception);
        }
    }

    private List<TemplateStyleProfile> parseStyles(XWPFDocument document) {
        List<TemplateStyleProfile> profiles = new ArrayList<>();
        Set<String> styleIds = new LinkedHashSet<>();
        document.getParagraphs().forEach(paragraph -> collectStyleId(paragraph, styleIds));
        document.getTables().forEach(table -> table.getRows().forEach(row -> row.getTableCells()
                .forEach(cell -> cell.getParagraphs().forEach(paragraph -> collectStyleId(paragraph, styleIds)))));

        for (String styleId : styleIds) {
            XWPFStyle style = document.getStyles() == null ? null : document.getStyles().getStyle(styleId);
            profiles.add(new TemplateStyleProfile(
                    styleId,
                    style == null ? styleId : style.getName(),
                    style == null ? "PARAGRAPH" : String.valueOf(style.getType()),
                    style == null ? null : style.getBasisStyleID(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }
        return profiles;
    }

    private void collectStyleId(XWPFParagraph paragraph, Set<String> styleIds) {
        String styleId = paragraph.getStyle();
        if (styleId != null && !styleId.isBlank()) {
            styleIds.add(styleId);
        }
    }

    private void collectParagraphPlaceholders(
            List<XWPFParagraph> paragraphs,
            List<TemplatePlaceholderProfile> placeholders,
            List<TemplateValidationItem> validationItems,
            String locationType,
            String paragraphPrefix
    ) {
        for (int index = 0; index < paragraphs.size(); index++) {
            XWPFParagraph paragraph = paragraphs.get(index);
            collectPlaceholdersFromParagraph(
                    paragraph,
                    locationType,
                    paragraphPrefix + "-" + index,
                    placeholders,
                    validationItems
            );
        }
    }

    private void collectTablePlaceholders(
            XWPFDocument document,
            List<TemplatePlaceholderProfile> placeholders,
            List<TemplateValidationItem> validationItems
    ) {
        for (int tableIndex = 0; tableIndex < document.getTables().size(); tableIndex++) {
            XWPFTable table = document.getTables().get(tableIndex);
            int cellIndex = 0;
            for (var row : table.getRows()) {
                for (var cell : row.getTableCells()) {
                    for (int paragraphIndex = 0; paragraphIndex < cell.getParagraphs().size(); paragraphIndex++) {
                        collectPlaceholdersFromParagraph(
                                cell.getParagraphs().get(paragraphIndex),
                                "TABLE",
                                "table-" + tableIndex + "-cell-" + cellIndex + "-paragraph-" + paragraphIndex,
                                placeholders,
                                validationItems
                        );
                    }
                    cellIndex++;
                }
            }
        }
    }

    private void collectPlaceholdersFromParagraph(
            XWPFParagraph paragraph,
            String locationType,
            String paragraphKey,
            List<TemplatePlaceholderProfile> placeholders,
            List<TemplateValidationItem> validationItems
    ) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(safeText(paragraph.getText()));
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            if (key.isEmpty()) {
                continue;
            }
            boolean splitAcrossRuns = isSplitAcrossRuns(paragraph, matcher.start(), matcher.end());
            placeholders.add(new TemplatePlaceholderProfile(
                    key,
                    locationType,
                    paragraphKey,
                    paragraph.getStyle(),
                    styleName(paragraph),
                    splitAcrossRuns
            ));
            if (splitAcrossRuns) {
                validationItems.add(splitPlaceholderValidationItem(key, paragraphKey, locationType));
            }
        }
    }

    private TemplateValidationItem splitPlaceholderValidationItem(String key, String paragraphKey, String locationType) {
        return new TemplateValidationItem(
                "WARNING",
                "PLACEHOLDER_SPLIT_ACROSS_RUNS",
                "Placeholder is split across multiple Word runs and needs structured replacement during export",
                "PLACEHOLDER",
                key,
                Map.of("paragraphKey", paragraphKey, "locationType", locationType)
        );
    }

    private boolean isSplitAcrossRuns(XWPFParagraph paragraph, int start, int end) {
        int cursor = 0;
        int touchedRuns = 0;
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.text();
            if (text == null || text.isEmpty()) {
                continue;
            }
            int runStart = cursor;
            int runEnd = cursor + text.length();
            if (runEnd > start && runStart < end) {
                touchedRuns++;
            }
            cursor = runEnd;
        }
        return touchedRuns > 1;
    }

    private List<TemplatePlaceholderProfile> deduplicatePlaceholders(List<TemplatePlaceholderProfile> placeholders) {
        List<TemplatePlaceholderProfile> deduplicated = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (TemplatePlaceholderProfile placeholder : placeholders) {
            String key = placeholder.key() + "|" + placeholder.locationType() + "|" + placeholder.paragraphKey();
            if (seen.add(key)) {
                deduplicated.add(placeholder);
            }
        }
        return deduplicated;
    }

    private List<TemplateSectionProfile> parseSections(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().getSectPr();
        boolean hasHeader = !document.getHeaderList().isEmpty();
        boolean hasFooter = !document.getFooterList().isEmpty();
        if (section == null) {
            return List.of(new TemplateSectionProfile(0, hasHeader, hasFooter, null, null, null, null, null, null));
        }
        return List.of(new TemplateSectionProfile(
                0,
                hasHeader,
                hasFooter,
                section.isSetPgSz() ? intValue(section.getPgSz().getW()) : null,
                section.isSetPgSz() ? intValue(section.getPgSz().getH()) : null,
                section.isSetPgMar() ? intValue(section.getPgMar().getTop()) : null,
                section.isSetPgMar() ? intValue(section.getPgMar().getRight()) : null,
                section.isSetPgMar() ? intValue(section.getPgMar().getBottom()) : null,
                section.isSetPgMar() ? intValue(section.getPgMar().getLeft()) : null
        ));
    }

    private Integer intValue(Object value) {
        if (value instanceof BigInteger number) {
            return number.intValue();
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private List<TemplateTableProfile> parseTables(XWPFDocument document) {
        List<TemplateTableProfile> profiles = new ArrayList<>();
        for (int index = 0; index < document.getTables().size(); index++) {
            XWPFTable table = document.getTables().get(index);
            int rowCount = table.getNumberOfRows();
            int columnCount = rowCount == 0 ? 0 : table.getRow(0).getTableCells().size();
            int placeholderCount = countPlaceholders(table.getText());
            profiles.add(new TemplateTableProfile(index, rowCount, columnCount, placeholderCount));
        }
        return profiles;
    }

    private List<TemplateMediaProfile> parseMedia(XWPFDocument document) {
        return document.getAllPictures().stream()
                .map(picture -> new TemplateMediaProfile(picture.suggestFileExtension(), null, picture.getFileName()))
                .toList();
    }

    private int countPlaceholders(String text) {
        int count = 0;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(safeText(text));
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String styleName(XWPFParagraph paragraph) {
        return paragraph.getStyleID() == null ? null : paragraph.getStyle();
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }
}
