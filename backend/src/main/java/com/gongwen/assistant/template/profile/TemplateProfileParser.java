package com.gongwen.assistant.template.profile;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.LineSpacingRule;
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
    private static final Pattern CHINESE_DATE_LINE_PATTERN = Pattern.compile("^\\d{4}年\\d{1,2}月\\d{1,2}日$");

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
        addMainParagraphStructures(structures, document.getParagraphs());
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

    private void addMainParagraphStructures(List<TemplateStructureProfile> structures, List<XWPFParagraph> paragraphs) {
        boolean seenTitle = false;
        boolean seenBody = false;
        boolean seenRecipient = false;
        for (int index = 0; index < paragraphs.size(); index++) {
            XWPFParagraph paragraph = paragraphs.get(index);
            String text = normalizeText(paragraph.getText());
            if (text.isBlank()) {
                continue;
            }
            String inferredType = inferStructureType(paragraph, text, "PARAGRAPH");
            String type = refineMainParagraphType(
                    inferredType,
                    text,
                    paragraph,
                    paragraphs,
                    index,
                    seenTitle,
                    seenBody,
                    seenRecipient
            );
            addStructure(structures, paragraph, "PARAGRAPH", "paragraph-" + index, type);
            if ("TITLE".equals(type)) {
                seenTitle = true;
            }
            if ("BODY".equals(type)) {
                seenBody = true;
            }
            if ("RECIPIENT".equals(type)) {
                seenRecipient = true;
            }
        }
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
        addStructure(structures, paragraph, locationType, paragraphKey, type);
    }

    private void addStructure(
            List<TemplateStructureProfile> structures,
            XWPFParagraph paragraph,
            String locationType,
            String paragraphKey,
            String type
    ) {
        String text = normalizeText(paragraph.getText());
        if (text.isBlank()) {
            return;
        }
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
        TemplateLineSpacingProfile lineSpacing = lineSpacing(paragraph);
        return new TemplateStructureFormattingProfile(
                preferredFontFamily(run),
                fontSizeHalfPoints(run),
                run == null ? null : run.isBold(),
                paragraph.getAlignment() == null ? null : paragraph.getAlignment().name(),
                positiveOrNull(paragraph.getIndentationFirstLine()),
                legacySpacingBetween(lineSpacing),
                positiveOrNull(paragraph.getSpacingBefore()),
                positiveOrNull(paragraph.getSpacingAfter()),
                run == null ? null : run.getColor(),
                eastAsiaFontFamily(run),
                latinFontFamily(run),
                lineSpacing
        );
    }

    private String inferStructureType(XWPFParagraph paragraph, String text, String locationType) {
        if ("HEADER".equals(locationType)) {
            return "HEADER";
        }
        if ("FOOTER".equals(locationType)) {
            return "FOOTER";
        }
        if (isFormattingInstructionLine(text)) {
            return "UNKNOWN";
        }
        String raw = (text + " " + safeText(paragraph.getStyle()) + " " + safeText(styleName(paragraph))).toLowerCase();
        if (containsAny(raw, "标题", "title", "{{标题", "{{title")) {
            return "TITLE";
        }
        if (containsAny(raw, "主送", "recipient", "{{主送")) {
            return "RECIPIENT";
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
        if (containsAny(raw, "正文", "body", "{{正文")) {
            return "BODY";
        }
        if (containsAny(raw, "机关", "单位", "unit")) {
            return "UNIT";
        }
        if ("TABLE".equals(locationType)) {
            return "TABLE";
        }
        return "UNKNOWN";
    }

    private String refineMainParagraphType(
            String inferredType,
            String text,
            XWPFParagraph paragraph,
            List<XWPFParagraph> paragraphs,
            int index,
            boolean seenTitle,
            boolean seenBody,
            boolean seenRecipient
    ) {
        if (isDateLine(text)) {
            return "DATE";
        }
        if (isAttachmentLine(text)) {
            return "ATTACHMENT";
        }
        if (isLikelySignatureLine(text, paragraph, paragraphs, index, seenBody)) {
            return "SIGNATURE";
        }
        if (isLikelyRecipientLine(text, seenTitle, seenBody, seenRecipient)) {
            return "RECIPIENT";
        }
        if ("UNKNOWN".equals(inferredType) && !seenTitle && isLikelyDocumentTitle(text, paragraph)) {
            return "TITLE";
        }
        if ("UNKNOWN".equals(inferredType) && isLikelyBodyParagraph(text, seenTitle, seenBody, seenRecipient)) {
            return "BODY";
        }
        return inferredType;
    }

    private boolean isLikelyDocumentTitle(String text, XWPFParagraph paragraph) {
        String normalized = text.strip();
        return normalized.length() <= 80
                && !normalized.endsWith("：")
                && !normalized.endsWith(":")
                && !isDateLine(normalized)
                && !isAttachmentLine(normalized)
                && !isFormattingInstructionLine(normalized)
                && (isCentered(paragraph)
                || containsAny(normalized, "通知", "请示", "报告", "讲话", "发言", "会议", "推进会", "方案", "意见"));
    }

    private boolean isLikelyBodyParagraph(String text, boolean seenTitle, boolean seenBody, boolean seenRecipient) {
        String normalized = text.strip();
        if (!seenTitle || normalized.length() < 20 || isFormattingInstructionLine(normalized)) {
            return false;
        }
        return seenRecipient || seenBody || normalized.length() > 40;
    }

    private boolean isLikelyRecipientLine(String text, boolean seenTitle, boolean seenBody, boolean seenRecipient) {
        String normalized = text.strip();
        return seenTitle
                && !seenBody
                && !seenRecipient
                && normalized.length() <= 80
                && (normalized.endsWith("：") || normalized.endsWith(":"));
    }

    private boolean isAttachmentLine(String text) {
        String normalized = text.strip();
        return normalized.startsWith("附件：") || normalized.startsWith("附件:");
    }

    private boolean isDateLine(String text) {
        return CHINESE_DATE_LINE_PATTERN.matcher(text.strip()).matches();
    }

    private boolean isLikelySignatureLine(
            String text,
            XWPFParagraph paragraph,
            List<XWPFParagraph> paragraphs,
            int index,
            boolean seenBody
    ) {
        if (!seenBody || text.length() > 40 || isDateLine(text) || !isRightAligned(paragraph)) {
            return false;
        }
        String nextText = nextNonBlankText(paragraphs, index);
        return nextText != null && isDateLine(nextText);
    }

    private boolean isRightAligned(XWPFParagraph paragraph) {
        return paragraph.getAlignment() != null && "RIGHT".equals(paragraph.getAlignment().name());
    }

    private boolean isCentered(XWPFParagraph paragraph) {
        return paragraph.getAlignment() != null && "CENTER".equals(paragraph.getAlignment().name());
    }

    private String nextNonBlankText(List<XWPFParagraph> paragraphs, int index) {
        for (int cursor = index + 1; cursor < paragraphs.size(); cursor++) {
            String text = normalizeText(paragraphs.get(cursor).getText());
            if (!text.isBlank()) {
                return text;
            }
        }
        return null;
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

    private boolean isFormattingInstructionLine(String text) {
        String normalized = text.strip();
        if (PLACEHOLDER_PATTERN.matcher(normalized).find()) {
            return false;
        }
        boolean mentionsSlot = normalized.matches("^(\\d+[.．、]|[一二三四五六七八九十]+[、.．])?\\s*(标题|正文|附件|主送|落款|日期)[：:].*");
        boolean mentionsFormatting = containsAny(
                normalized,
                "方正",
                "小标宋",
                "仿宋",
                "黑体",
                "楷体",
                "字号",
                "二号",
                "三号",
                "四号",
                "首行缩进",
                "行距",
                "居中",
                "右对齐",
                "格式"
        );
        return mentionsSlot && mentionsFormatting;
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
                    preferredFontFamily(run),
                    eastAsiaFontFamily(run),
                    latinFontFamily(run),
                    fontSizeHalfPoints(run),
                    run == null ? null : run.isBold(),
                    paragraph == null || paragraph.getAlignment() == null ? null : paragraph.getAlignment().name(),
                    positiveOrNull(paragraph == null ? -1 : paragraph.getIndentationFirstLine()),
                    legacySpacingBetween(lineSpacing(paragraph)),
                    positiveOrNull(paragraph == null ? -1 : paragraph.getSpacingBefore()),
                    positiveOrNull(paragraph == null ? -1 : paragraph.getSpacingAfter()),
                    lineSpacing(paragraph)
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

    private String preferredFontFamily(XWPFRun run) {
        if (run == null) {
            return null;
        }
        return firstNonBlank(eastAsiaFontFamily(run), run.getFontFamily(), latinFontFamily(run));
    }

    private String eastAsiaFontFamily(XWPFRun run) {
        return run == null ? null : blankToNull(run.getFontFamily(XWPFRun.FontCharRange.eastAsia));
    }

    private String latinFontFamily(XWPFRun run) {
        if (run == null) {
            return null;
        }
        return firstNonBlank(
                run.getFontFamily(XWPFRun.FontCharRange.ascii),
                run.getFontFamily(XWPFRun.FontCharRange.hAnsi)
        );
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private TemplateLineSpacingProfile lineSpacing(XWPFParagraph paragraph) {
        if (paragraph == null || paragraph.getSpacingBetween() <= 0) {
            return null;
        }
        LineSpacingRule rule = paragraph.getSpacingLineRule();
        String mode = rule == null ? "AUTO" : rule.name();
        if ("EXACT".equals(mode) || "AT_LEAST".equals(mode)) {
            return new TemplateLineSpacingProfile(
                    mode,
                    (int) Math.round(paragraph.getSpacingBetween() * 20),
                    null
            );
        }
        return new TemplateLineSpacingProfile(
                "AUTO",
                null,
                (int) Math.round(paragraph.getSpacingBetween() * 100)
        );
    }

    private Integer legacySpacingBetween(TemplateLineSpacingProfile lineSpacing) {
        if (lineSpacing == null || !"AUTO".equals(lineSpacing.mode())) {
            return null;
        }
        return lineSpacing.multipleHundred();
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
