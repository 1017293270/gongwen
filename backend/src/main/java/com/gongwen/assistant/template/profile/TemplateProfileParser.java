package com.gongwen.assistant.template.profile;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
                    parseStructures(document),
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

    private List<TemplateStructureProfile> parseStructures(XWPFDocument document) {
        List<TemplateStructureProfile> structures = new ArrayList<>();
        for (int index = 0; index < document.getParagraphs().size(); index++) {
            XWPFParagraph paragraph = document.getParagraphs().get(index);
            addStructure(structures, paragraph, "PARAGRAPH", "paragraph-" + index);
        }
        for (int tableIndex = 0; tableIndex < document.getTables().size(); tableIndex++) {
            XWPFTable table = document.getTables().get(tableIndex);
            int cellIndex = 0;
            for (var row : table.getRows()) {
                for (var cell : row.getTableCells()) {
                    for (int paragraphIndex = 0; paragraphIndex < cell.getParagraphs().size(); paragraphIndex++) {
                        addStructure(
                                structures,
                                cell.getParagraphs().get(paragraphIndex),
                                "TABLE",
                                "table-" + tableIndex + "-cell-" + cellIndex + "-paragraph-" + paragraphIndex
                        );
                    }
                    cellIndex++;
                }
            }
        }
        for (int headerIndex = 0; headerIndex < document.getHeaderList().size(); headerIndex++) {
            XWPFHeader header = document.getHeaderList().get(headerIndex);
            for (int paragraphIndex = 0; paragraphIndex < header.getParagraphs().size(); paragraphIndex++) {
                addStructure(
                        structures,
                        header.getParagraphs().get(paragraphIndex),
                        "HEADER",
                        "header-" + headerIndex + "-paragraph-" + paragraphIndex
                );
            }
        }
        for (int footerIndex = 0; footerIndex < document.getFooterList().size(); footerIndex++) {
            var footer = document.getFooterList().get(footerIndex);
            for (int paragraphIndex = 0; paragraphIndex < footer.getParagraphs().size(); paragraphIndex++) {
                addStructure(
                        structures,
                        footer.getParagraphs().get(paragraphIndex),
                        "FOOTER",
                        "footer-" + footerIndex + "-paragraph-" + paragraphIndex
                );
            }
        }
        return structures;
    }

    private void addStructure(
            List<TemplateStructureProfile> structures,
            XWPFParagraph paragraph,
            String locationType,
            String paragraphKey
    ) {
        String text = normalizeText(paragraph.getText());
        if (text.isBlank()) {
            return;
        }
        String type = inferStructureType(paragraph, text, locationType);
        structures.add(new TemplateStructureProfile(
                paragraphKey,
                type,
                structureLabel(type),
                previewText(text),
                locationType,
                paragraph.getStyle(),
                styleName(paragraph),
                inferStructureSource(text, paragraph),
                formattingFromParagraph(paragraph)
        ));
    }

    private TemplateStructureFormattingProfile formattingFromParagraph(XWPFParagraph paragraph) {
        XWPFRun run = firstRun(paragraph);
        return new TemplateStructureFormattingProfile(
                run == null ? null : run.getFontFamily(),
                fontSizeHalfPoints(run),
                run == null ? null : run.isBold(),
                paragraph.getAlignment() == null ? null : paragraph.getAlignment().name(),
                positiveOrNull(paragraph.getIndentationFirstLine()),
                spacingBetween(paragraph),
                positiveOrNull(paragraph.getSpacingBefore()),
                positiveOrNull(paragraph.getSpacingAfter())
        );
    }

    private String inferStructureType(XWPFParagraph paragraph, String text, String locationType) {
        if ("HEADER".equals(locationType)) {
            return "HEADER";
        }
        if ("FOOTER".equals(locationType)) {
            return "FOOTER";
        }
        String raw = (text + " " + safeText(paragraph.getStyle()) + " " + safeText(styleName(paragraph))).toLowerCase();
        if (containsAny(raw, "标题", "title", "{{标题", "{{title")) {
            return "TITLE";
        }
        if (containsAny(raw, "主送", "recipient", "{{主送")) {
            return "RECIPIENT";
        }
        if (containsAny(raw, "正文", "body", "{{正文")) {
            return "BODY";
        }
        if (containsAny(raw, "附件", "attachment", "{{附件")) {
            return "ATTACHMENT";
        }
        if (containsAny(raw, "落款", "signature", "{{落款")) {
            return "SIGNATURE";
        }
        if (containsAny(raw, "日期", "date", "{{日期")) {
            return "DATE";
        }
        if (containsAny(raw, "文号", "meta", "〔", "号")) {
            return "META";
        }
        if (containsAny(raw, "机关", "单位", "unit")) {
            return "UNIT";
        }
        if ("TABLE".equals(locationType)) {
            return "TABLE";
        }
        return "UNKNOWN";
    }

    private String structureLabel(String type) {
        return switch (type) {
            case "UNIT" -> "发文机关";
            case "META" -> "文号/元信息";
            case "TITLE" -> "公文标题";
            case "RECIPIENT" -> "主送机关";
            case "BODY" -> "正文段落";
            case "ATTACHMENT" -> "附件";
            case "SIGNATURE" -> "落款";
            case "DATE" -> "日期";
            case "TABLE" -> "表格内容";
            case "HEADER" -> "页眉";
            case "FOOTER" -> "页脚";
            default -> "未归类段落";
        };
    }

    private String inferStructureSource(String text, XWPFParagraph paragraph) {
        if (PLACEHOLDER_PATTERN.matcher(text).find()) {
            return "PLACEHOLDER";
        }
        if (paragraph.getStyle() != null && !paragraph.getStyle().isBlank()) {
            return "STYLE";
        }
        return "TEXT";
    }

    private boolean containsAny(String raw, String... values) {
        for (String value : values) {
            if (raw.contains(value.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String previewText(String text) {
        if (text.length() <= 120) {
            return text;
        }
        return text.substring(0, 120) + "...";
    }

    private List<TemplateStyleProfile> parseStyles(XWPFDocument document) {
        List<TemplateStyleProfile> profiles = new ArrayList<>();
        Set<String> styleIds = new LinkedHashSet<>();
        Map<String, XWPFParagraph> representativeParagraphs = new LinkedHashMap<>();
        document.getParagraphs().forEach(paragraph -> collectStyleParagraph(paragraph, styleIds, representativeParagraphs));
        document.getTables().forEach(table -> table.getRows().forEach(row -> row.getTableCells()
                .forEach(cell -> cell.getParagraphs().forEach(paragraph -> collectStyleParagraph(paragraph, styleIds, representativeParagraphs)))));

        for (String styleId : styleIds) {
            XWPFStyle style = document.getStyles() == null ? null : document.getStyles().getStyle(styleId);
            XWPFParagraph paragraph = representativeParagraphs.get(styleId);
            XWPFRun run = firstRun(paragraph);
            profiles.add(new TemplateStyleProfile(
                    styleId,
                    style == null ? styleId : style.getName(),
                    style == null ? "PARAGRAPH" : String.valueOf(style.getType()),
                    style == null ? null : style.getBasisStyleID(),
                    run == null ? null : run.getFontFamily(),
                    fontSizeHalfPoints(run),
                    run == null ? null : run.isBold(),
                    paragraph == null || paragraph.getAlignment() == null ? null : paragraph.getAlignment().name(),
                    positiveOrNull(paragraph == null ? -1 : paragraph.getIndentationFirstLine()),
                    spacingBetween(paragraph),
                    positiveOrNull(paragraph == null ? -1 : paragraph.getSpacingBefore()),
                    positiveOrNull(paragraph == null ? -1 : paragraph.getSpacingAfter())
            ));
        }
        return profiles;
    }

    private void collectStyleParagraph(
            XWPFParagraph paragraph,
            Set<String> styleIds,
            Map<String, XWPFParagraph> representativeParagraphs
    ) {
        String styleId = paragraph.getStyle();
        if (styleId != null && !styleId.isBlank()) {
            styleIds.add(styleId);
            representativeParagraphs.putIfAbsent(styleId, paragraph);
        }
    }

    private XWPFRun firstRun(XWPFParagraph paragraph) {
        if (paragraph == null) {
            return null;
        }
        return paragraph.getRuns().stream()
                .filter(run -> run.text() != null && !run.text().isBlank())
                .findFirst()
                .orElse(null);
    }

    private Integer fontSizeHalfPoints(XWPFRun run) {
        if (run == null || run.getFontSize() <= 0) {
            return null;
        }
        return run.getFontSize() * 2;
    }

    private Integer spacingBetween(XWPFParagraph paragraph) {
        if (paragraph == null || paragraph.getSpacingBetween() <= 0) {
            return null;
        }
        return (int) Math.round(paragraph.getSpacingBetween() * 100);
    }

    private Integer positiveOrNull(int value) {
        return value <= 0 ? null : value;
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

    private String normalizeText(String text) {
        return safeText(text).replaceAll("\\s+", " ").trim();
    }
}
