package com.gongwen.assistant.exporting.word;

import com.gongwen.assistant.template.parser.DocxPlaceholderParser;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;
import com.gongwen.assistant.template.profile.TemplateStructureProfile;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DocxTemplateRenderer {
    private static final String TITLE_KEY = "\u6807\u9898";
    private static final String RECIPIENT_KEY = "\u4e3b\u9001";
    private static final String BODY_KEY = "\u6b63\u6587";
    private static final String ATTACHMENT_KEY = "\u9644\u4ef6";
    private static final String SIGNATURE_KEY = "\u843d\u6b3e";
    private static final String DATE_KEY = "\u65e5\u671f";
    private static final Set<String> REFERENCE_TOP_TYPES = Set.of("UNIT", "META");

    private static final TemplateStructureFormattingProfile DEFAULT_TITLE_FORMATTING =
            new TemplateStructureFormattingProfile("FangSong", 44, true, "CENTER", 0, null, 0, 0);
    private static final TemplateStructureFormattingProfile DEFAULT_RECIPIENT_FORMATTING =
            new TemplateStructureFormattingProfile("FangSong", 32, false, "LEFT", 0, null, 0, 0);
    private static final TemplateStructureFormattingProfile DEFAULT_BODY_FORMATTING =
            new TemplateStructureFormattingProfile("FangSong", 32, false, "LEFT", 0, null, 0, 0);
    private static final TemplateStructureFormattingProfile DEFAULT_SIGNATURE_FORMATTING =
            new TemplateStructureFormattingProfile("FangSong", 32, false, "RIGHT", 0, null, 0, 0);
    private static final TemplateStructureFormattingProfile DEFAULT_DATE_FORMATTING =
            new TemplateStructureFormattingProfile("FangSong", 32, false, "RIGHT", 0, null, 0, 0);

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
        return render(templateBytes, values, ExportFormattingContext.EMPTY);
    }

    public byte[] render(byte[] templateBytes, Map<String, String> values, ExportFormattingContext formatting) {
        validateValues(templateBytes, values);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(templateBytes));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getParagraphs().forEach(paragraph -> replaceInParagraph(paragraph, values, formatting));
            for (XWPFTable table : document.getTables()) {
                table.getRows().forEach(row -> row.getTableCells()
                        .forEach(cell -> cell.getParagraphs()
                                .forEach(paragraph -> replaceInParagraph(paragraph, values, formatting))));
            }
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("\u65e0\u6cd5\u6e32\u67d3 Word \u6a21\u677f", exception);
        }
    }

    public byte[] renderDraftSnapshot(Map<String, String> values) {
        return renderDraftSnapshot(values, ExportFormattingContext.EMPTY);
    }

    public byte[] renderDraftSnapshot(Map<String, String> values, ExportFormattingContext formatting) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addParagraph(document, value(values, TITLE_KEY, "TITLE"), effectiveTitleFormatting(formatting));
            addParagraph(document, recipientLine(values), effectiveRecipientFormatting(formatting));
            addBody(document, value(values, BODY_KEY, "BODY_PARAGRAPH"), effectiveBodyFormatting(formatting));
            addOptionalParagraph(document, value(values, ATTACHMENT_KEY, "ATTACHMENT"));
            addParagraph(document, value(values, SIGNATURE_KEY, "SIGNATURE"), effectiveSignatureFormatting(formatting));
            addParagraph(document, value(values, DATE_KEY, "DATE"), effectiveDateFormatting(formatting));
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("\u65e0\u6cd5\u751f\u6210\u5f53\u524d\u8349\u7a3f Word", exception);
        }
    }

    public byte[] renderReferenceDraft(
            byte[] templateBytes,
            Map<String, String> values,
            ExportFormattingContext formatting,
            TemplateProfile profile
    ) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(templateBytes));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            List<ReferenceTopParagraph> topParagraphs = referenceTopParagraphs(document, profile);
            clearBodyElements(document);
            addReferenceTopStructures(document, profile, topParagraphs);
            addParagraph(
                    document,
                    value(values, TITLE_KEY, "TITLE"),
                    effectiveTitleFormatting(formatting),
                    styleId(profile, "TITLE")
            );
            addParagraph(
                    document,
                    recipientLine(values),
                    effectiveRecipientFormatting(formatting),
                    styleId(profile, "RECIPIENT")
            );
            addBody(document, value(values, BODY_KEY, "BODY_PARAGRAPH"), effectiveBodyFormatting(formatting), styleId(profile, "BODY"));
            addOptionalParagraph(
                    document,
                    value(values, ATTACHMENT_KEY, "ATTACHMENT"),
                    effectiveAttachmentFormatting(profile, formatting),
                    styleId(profile, "ATTACHMENT")
            );
            addParagraph(
                    document,
                    value(values, SIGNATURE_KEY, "SIGNATURE"),
                    effectiveSignatureFormatting(formatting),
                    styleId(profile, "SIGNATURE")
            );
            addParagraph(
                    document,
                    value(values, DATE_KEY, "DATE"),
                    effectiveDateFormatting(formatting),
                    styleId(profile, "DATE")
            );
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("\u65e0\u6cd5\u57fa\u4e8e\u53c2\u8003\u6a21\u677f\u751f\u6210 Word", exception);
        }
    }

    private void validateValues(byte[] templateBytes, Map<String, String> values) {
        for (String placeholder : placeholderParser.parsePlaceholders(templateBytes)) {
            if (!values.containsKey(placeholder) || values.get(placeholder) == null) {
                throw new MissingTemplateValueException(placeholder);
            }
        }
    }

    private void replaceInParagraph(
            XWPFParagraph paragraph,
            Map<String, String> values,
            ExportFormattingContext formatting
    ) {
        String paragraphText = paragraphText(paragraph);
        Slot slot = detectSlot(paragraphText);
        if (paragraphText.contains("{{")) {
            replaceParagraphText(paragraph, replaceText(paragraphText, values));
        }
        if (slot != null) {
            applyFormatting(paragraph, slotFormatting(slot, formatting));
        }
    }

    private String replaceText(String text, Map<String, String> values) {
        String replaced = text;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            replaced = replaced.replace("{{" + entry.getKey() + "}}", entry.getValue());
            replaced = replaced.replace("{{ " + entry.getKey() + " }}", entry.getValue());
        }
        return replaced;
    }

    private void replaceParagraphText(XWPFParagraph paragraph, String replaced) {
        while (paragraph.getRuns().size() > 1) {
            paragraph.removeRun(paragraph.getRuns().size() - 1);
        }
        XWPFRun run = paragraph.getRuns().isEmpty() ? paragraph.createRun() : paragraph.getRuns().get(0);
        String[] lines = replaced.split("\\R", -1);
        run.setText(lines[0], 0);
        for (int index = 1; index < lines.length; index++) {
            run.addBreak();
            run.setText(lines[index]);
        }
    }

    private void addBody(XWPFDocument document, String body, TemplateStructureFormattingProfile formatting) {
        addBody(document, body, formatting, null);
    }

    private void addBody(
            XWPFDocument document,
            String body,
            TemplateStructureFormattingProfile formatting,
            String styleId
    ) {
        if (body == null || body.isBlank()) {
            return;
        }
        for (String line : body.split("\\R", -1)) {
            addParagraph(document, line, formatting, true, styleId);
        }
    }

    private void addOptionalParagraph(XWPFDocument document, String text) {
        addOptionalParagraph(document, text, DEFAULT_BODY_FORMATTING, null);
    }

    private void addOptionalParagraph(
            XWPFDocument document,
            String text,
            TemplateStructureFormattingProfile formatting,
            String styleId
    ) {
        if (text == null || text.isBlank() || "\u65e0".equals(text.trim())) {
            return;
        }
        addParagraph(document, text, formatting, false, styleId);
    }

    private void addParagraph(
            XWPFDocument document,
            String text,
            TemplateStructureFormattingProfile formatting
    ) {
        addParagraph(document, text, formatting, false, null);
    }

    private void addParagraph(
            XWPFDocument document,
            String text,
            TemplateStructureFormattingProfile formatting,
            String styleId
    ) {
        addParagraph(document, text, formatting, false, styleId);
    }

    private void addParagraph(
            XWPFDocument document,
            String text,
            TemplateStructureFormattingProfile formatting,
            boolean allowBlank
    ) {
        addParagraph(document, text, formatting, allowBlank, null);
    }

    private void addParagraph(
            XWPFDocument document,
            String text,
            TemplateStructureFormattingProfile formatting,
            boolean allowBlank,
            String styleId
    ) {
        if (text == null || (!allowBlank && text.isBlank())) {
            return;
        }
        XWPFParagraph paragraph = document.createParagraph();
        if (styleId != null && !styleId.isBlank()) {
            paragraph.setStyle(styleId);
        }
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        applyFormatting(paragraph, formatting);
    }

    private void applyFormatting(XWPFParagraph paragraph, TemplateStructureFormattingProfile formatting) {
        if (formatting == null) {
            return;
        }

        ParagraphAlignment alignment = toAlignment(formatting.alignment());
        if (alignment != null) {
            paragraph.setAlignment(alignment);
        }
        if (formatting.indentationFirstLine() != null) {
            paragraph.setFirstLineIndent(formatting.indentationFirstLine());
        }
        if (formatting.spacingBefore() != null) {
            paragraph.setSpacingBefore(formatting.spacingBefore());
        }
        if (formatting.spacingAfter() != null) {
            paragraph.setSpacingAfter(formatting.spacingAfter());
        }
        if (formatting.spacingBetween() != null) {
            paragraph.setSpacingBetween(formatting.spacingBetween() / 100.0d);
        }

        for (XWPFRun run : paragraph.getRuns()) {
            applyRunFormatting(run, formatting);
        }
    }

    private void applyRunFormatting(XWPFRun run, TemplateStructureFormattingProfile formatting) {
        if (formatting.fontFamily() != null && !formatting.fontFamily().isBlank()) {
            run.setFontFamily(formatting.fontFamily());
        }
        if (formatting.fontSizeHalfPoints() != null && formatting.fontSizeHalfPoints() > 0) {
            run.setFontSize(Math.max(1, (int) Math.round(formatting.fontSizeHalfPoints() / 2.0d)));
        }
        if (formatting.bold() != null) {
            run.setBold(formatting.bold());
        }
        if (formatting.colorHex() != null && !formatting.colorHex().isBlank()) {
            run.setColor(formatting.colorHex().replace("#", ""));
        }
    }

    private void clearBodyElements(XWPFDocument document) {
        for (int index = document.getBodyElements().size() - 1; index >= 0; index--) {
            document.removeBodyElement(index);
        }
    }

    private List<ReferenceTopParagraph> referenceTopParagraphs(XWPFDocument document, TemplateProfile profile) {
        if (profile == null) {
            return List.of();
        }
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        return sortedStructures(profile).stream()
                .filter(structure -> REFERENCE_TOP_TYPES.contains(structure.structureType()))
                .map(this::paragraphIndex)
                .filter(index -> index >= 0 && index < paragraphs.size())
                .map(index -> new ReferenceTopParagraph((CTP) paragraphs.get(index).getCTP().copy()))
                .toList();
    }

    private void addReferenceTopStructures(
            XWPFDocument document,
            TemplateProfile profile,
            List<ReferenceTopParagraph> topParagraphs
    ) {
        if (!topParagraphs.isEmpty()) {
            topParagraphs.forEach(copy -> document.createParagraph().getCTP().set(copy.paragraph()));
            return;
        }
        if (profile == null) {
            return;
        }
        sortedStructures(profile).stream()
                .filter(structure -> REFERENCE_TOP_TYPES.contains(structure.structureType()))
                .forEach(structure -> addParagraph(
                        document,
                        structure.textPreview(),
                        structure.formatting(),
                        structure.styleId()
                ));
    }

    private int paragraphIndex(TemplateStructureProfile structure) {
        if (!"PARAGRAPH".equals(structure.locationType()) || structure.structureKey() == null) {
            return -1;
        }
        String digits = structure.structureKey().replaceAll("\\D+", "");
        return digits.isBlank() ? -1 : Integer.parseInt(digits);
    }

    private TemplateStructureFormattingProfile effectiveAttachmentFormatting(
            TemplateProfile profile,
            ExportFormattingContext formatting
    ) {
        return firstStructure(profile, "ATTACHMENT")
                .map(TemplateStructureProfile::formatting)
                .orElse(effectiveBodyFormatting(formatting));
    }

    private String styleId(TemplateProfile profile, String structureType) {
        return firstStructure(profile, structureType)
                .map(TemplateStructureProfile::styleId)
                .orElse(null);
    }

    private Optional<TemplateStructureProfile> firstStructure(TemplateProfile profile, String structureType) {
        if (profile == null) {
            return Optional.empty();
        }
        return sortedStructures(profile).stream()
                .filter(structure -> structureType.equals(structure.structureType()))
                .findFirst();
    }

    private List<TemplateStructureProfile> sortedStructures(TemplateProfile profile) {
        return profile.structures().stream()
                .sorted(Comparator.comparingInt(structure -> structureSortOrder(structure.structureKey())))
                .toList();
    }

    private int structureSortOrder(String structureKey) {
        if (structureKey == null || structureKey.isBlank()) {
            return 0;
        }
        String digits = structureKey.replaceAll("\\D+", "");
        return digits.isBlank() ? 0 : Integer.parseInt(digits);
    }

    private String recipientLine(Map<String, String> values) {
        String recipient = value(values, RECIPIENT_KEY, "RECIPIENT").trim();
        if (recipient.isBlank() || recipient.endsWith("\uff1a") || recipient.endsWith(":")) {
            return recipient;
        }
        return recipient + "\uff1a";
    }

    private Slot detectSlot(String paragraphText) {
        if (containsPlaceholder(paragraphText, TITLE_KEY)) {
            return Slot.TITLE;
        }
        if (containsPlaceholder(paragraphText, RECIPIENT_KEY)) {
            return Slot.RECIPIENT;
        }
        if (containsPlaceholder(paragraphText, BODY_KEY)) {
            return Slot.BODY;
        }
        if (containsPlaceholder(paragraphText, SIGNATURE_KEY)) {
            return Slot.SIGNATURE;
        }
        if (containsPlaceholder(paragraphText, DATE_KEY)) {
            return Slot.DATE;
        }
        return null;
    }

    private boolean containsPlaceholder(String paragraphText, String key) {
        if (paragraphText == null || paragraphText.isBlank()) {
            return false;
        }
        return paragraphText.contains("{{" + key + "}}") || paragraphText.contains("{{ " + key + " }}");
    }

    private TemplateStructureFormattingProfile slotFormatting(Slot slot, ExportFormattingContext formatting) {
        return switch (slot) {
            case TITLE -> effectiveTitleFormatting(formatting);
            case RECIPIENT -> effectiveRecipientFormatting(formatting);
            case BODY -> effectiveBodyFormatting(formatting);
            case SIGNATURE -> effectiveSignatureFormatting(formatting);
            case DATE -> effectiveDateFormatting(formatting);
        };
    }

    private TemplateStructureFormattingProfile effectiveTitleFormatting(ExportFormattingContext formatting) {
        return formatting == null || formatting.title() == null ? DEFAULT_TITLE_FORMATTING : formatting.title();
    }

    private TemplateStructureFormattingProfile effectiveRecipientFormatting(ExportFormattingContext formatting) {
        return formatting == null || formatting.recipient() == null
                ? DEFAULT_RECIPIENT_FORMATTING
                : formatting.recipient();
    }

    private TemplateStructureFormattingProfile effectiveBodyFormatting(ExportFormattingContext formatting) {
        return formatting == null || formatting.body() == null ? DEFAULT_BODY_FORMATTING : formatting.body();
    }

    private TemplateStructureFormattingProfile effectiveSignatureFormatting(ExportFormattingContext formatting) {
        return formatting == null || formatting.signature() == null
                ? DEFAULT_SIGNATURE_FORMATTING
                : formatting.signature();
    }

    private TemplateStructureFormattingProfile effectiveDateFormatting(ExportFormattingContext formatting) {
        return formatting == null || formatting.date() == null ? DEFAULT_DATE_FORMATTING : formatting.date();
    }

    private ParagraphAlignment toAlignment(String alignment) {
        if (alignment == null || alignment.isBlank()) {
            return null;
        }
        return switch (alignment.trim().toUpperCase()) {
            case "CENTER" -> ParagraphAlignment.CENTER;
            case "RIGHT" -> ParagraphAlignment.RIGHT;
            case "BOTH", "JUSTIFIED" -> ParagraphAlignment.BOTH;
            case "LEFT" -> ParagraphAlignment.LEFT;
            default -> null;
        };
    }

    private String value(Map<String, String> values, String primaryKey, String fallbackKey) {
        String primary = values.get(primaryKey);
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        String fallback = values.get(fallbackKey);
        return fallback == null ? "" : fallback;
    }

    private String paragraphText(XWPFParagraph paragraph) {
        StringBuilder builder = new StringBuilder();
        List<XWPFRun> runs = paragraph.getRuns();
        for (XWPFRun run : runs) {
            String text = run.text();
            if (text != null) {
                builder.append(text);
            }
        }
        return builder.toString();
    }

    private enum Slot {
        TITLE,
        RECIPIENT,
        BODY,
        SIGNATURE,
        DATE
    }

    private record ReferenceTopParagraph(CTP paragraph) {
    }
}
