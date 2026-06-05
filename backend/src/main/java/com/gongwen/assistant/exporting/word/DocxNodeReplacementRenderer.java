package com.gongwen.assistant.exporting.word;

import com.gongwen.assistant.template.profile.TemplateLineSpacingProfile;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;
import org.apache.poi.xwpf.usermodel.LineSpacingRule;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DocxNodeReplacementRenderer {
    private final DocxNodeLocator locator;

    public DocxNodeReplacementRenderer() {
        this(new DocxNodeLocator());
    }

    public DocxNodeReplacementRenderer(DocxNodeLocator locator) {
        this.locator = locator;
    }

    public byte[] render(byte[] templateBytes, Map<String, String> replacementsByNodeKey, Set<String> ignoredNodeKeys) {
        return render(templateBytes, replacementsByNodeKey, Set.of(), ignoredNodeKeys, List.of());
    }

    public byte[] render(
            byte[] templateBytes,
            Map<String, String> replacementsByNodeKey,
            Set<String> ignoredNodeKeys,
            List<NodeInsertion> insertions
    ) {
        return render(templateBytes, replacementsByNodeKey, Set.of(), ignoredNodeKeys, insertions);
    }

    public byte[] render(
            byte[] templateBytes,
            Map<String, String> replacementsByNodeKey,
            Set<String> clearNodeKeys,
            Set<String> removeNodeKeys,
            List<NodeInsertion> insertions
    ) {
        Map<String, String> replacements = replacementsByNodeKey == null ? Map.of() : replacementsByNodeKey;
        Set<String> clearKeys = clearNodeKeys == null ? Set.of() : clearNodeKeys;
        Set<String> removeKeys = removeNodeKeys == null ? Set.of() : removeNodeKeys;
        List<NodeInsertion> requestedInsertions = insertions == null ? List.of() : insertions;
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(templateBytes));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            List<XWPFParagraph> clearTargets = locateParagraphs(document, clearKeys);
            List<XWPFParagraph> removeTargets = locateParagraphs(document, removeKeys);
            replacements.entrySet().stream()
                    .filter(entry -> !clearKeys.contains(entry.getKey()))
                    .filter(entry -> !removeKeys.contains(entry.getKey()))
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(entry -> replaceNode(document, entry.getKey(), entry.getValue()));
            insertNodes(document, requestedInsertions);
            clearParagraphs(clearTargets);
            removeParagraphs(document, removeTargets);
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to render DOCX node replacements", exception);
        }
    }

    private void replaceNode(XWPFDocument document, String nodeKey, String replacement) {
        XWPFParagraph paragraph = locator.findParagraph(document, nodeKey)
                .orElseThrow(() -> new MissingNodeLocatorException(nodeKey));
        replaceParagraphText(paragraph, replacement == null ? "" : replacement);
    }

    private void insertNodes(XWPFDocument document, List<NodeInsertion> insertions) {
        Map<XWPFParagraph, List<ResolvedNodeInsertion>> byAnchor = new LinkedHashMap<>();
        insertions.stream()
                .filter(insertion -> insertion != null && !insertion.anchorNodeKey().isBlank())
                .map(insertion -> resolveInsertion(document, insertion))
                .forEach(insertion -> byAnchor
                        .computeIfAbsent(insertion.anchor(), ignored -> new ArrayList<>())
                        .add(insertion));
        byAnchor.forEach((anchor, anchoredInsertions) -> {
            List<ResolvedNodeInsertion> before = anchoredInsertions.stream()
                    .filter(insertion -> "BEFORE".equalsIgnoreCase(insertion.position()))
                    .toList();
            List<ResolvedNodeInsertion> after = anchoredInsertions.stream()
                    .filter(insertion -> !"BEFORE".equalsIgnoreCase(insertion.position()))
                    .toList();
            if (!before.isEmpty()) {
                List<ResolvedNodeInsertion> reversed = new ArrayList<>(before);
                java.util.Collections.reverse(reversed);
                for (ResolvedNodeInsertion insertion : reversed) {
                    insertParagraph(document, anchor, insertion.styleSource(), insertion.content(), insertion.formatting(), false);
                }
            }
            if (!after.isEmpty()) {
                XWPFParagraph currentAnchor = anchor;
                for (ResolvedNodeInsertion insertion : after) {
                    currentAnchor = insertParagraph(
                            document,
                            currentAnchor,
                            insertion.styleSource(),
                            insertion.content(),
                            insertion.formatting(),
                            true
                    );
                }
            }
        });
    }

    private ResolvedNodeInsertion resolveInsertion(XWPFDocument document, NodeInsertion insertion) {
        XWPFParagraph anchor = bodyAnchorParagraph(document, insertion.anchorNodeKey());
        return new ResolvedNodeInsertion(
                anchor,
                insertion.position(),
                insertion.content(),
                styleSourceParagraph(document, insertion, anchor),
                insertion.formatting()
        );
    }

    private XWPFParagraph styleSourceParagraph(XWPFDocument document, NodeInsertion insertion, XWPFParagraph fallback) {
        if (insertion.styleSourceNodeKey().isBlank()) {
            return fallback;
        }
        return locator.findParagraph(document, insertion.styleSourceNodeKey())
                .filter(paragraph -> document.getPosOfParagraph(paragraph) >= 0)
                .orElse(fallback);
    }

    private XWPFParagraph bodyAnchorParagraph(XWPFDocument document, String nodeKey) {
        XWPFParagraph paragraph = locator.findParagraph(document, nodeKey)
                .orElseThrow(() -> new MissingNodeLocatorException(nodeKey));
        if (document.getPosOfParagraph(paragraph) < 0) {
            throw new MissingNodeLocatorException(nodeKey);
        }
        return paragraph;
    }

    private XWPFParagraph insertParagraph(
            XWPFDocument document,
            XWPFParagraph anchor,
            XWPFParagraph styleSource,
            String content,
            TemplateStructureFormattingProfile formatting,
            boolean after
    ) {
        XmlCursor cursor = anchor.getCTP().newCursor();
        if (after) {
            cursor.toNextSibling();
        }
        XWPFParagraph paragraph = document.insertNewParagraph(cursor);
        cursor.dispose();
        copyParagraphStyle(styleSource == null ? anchor : styleSource, paragraph);
        replaceParagraphText(paragraph, content == null ? "" : content);
        applyParsedFormatting(paragraph, formatting);
        return paragraph;
    }

    private void copyParagraphStyle(XWPFParagraph source, XWPFParagraph target) {
        if (source.getCTP().getPPr() != null) {
            CTPPr paragraphProperties = (CTPPr) source.getCTP().getPPr().copy();
            sanitizeCopiedParagraphProperties(paragraphProperties);
            target.getCTP().setPPr(paragraphProperties);
        }
        target.setAlignment(source.getAlignment());
        if (!source.getRuns().isEmpty()) {
            XWPFRun sourceRun = source.getRuns().getFirst();
            XWPFRun targetRun = target.getRuns().isEmpty() ? target.createRun() : target.getRuns().getFirst();
            if (sourceRun.getCTR().getRPr() != null) {
                targetRun.getCTR().setRPr((CTRPr) sourceRun.getCTR().getRPr().copy());
            }
        }
    }

    private void sanitizeCopiedParagraphProperties(CTPPr paragraphProperties) {
        if (paragraphProperties.isSetSectPr()) {
            paragraphProperties.unsetSectPr();
        }
        if (paragraphProperties.isSetPageBreakBefore()) {
            paragraphProperties.unsetPageBreakBefore();
        }
        if (paragraphProperties.isSetKeepNext()) {
            paragraphProperties.unsetKeepNext();
        }
        if (paragraphProperties.isSetKeepLines()) {
            paragraphProperties.unsetKeepLines();
        }
        if (paragraphProperties.isSetNumPr()) {
            paragraphProperties.unsetNumPr();
        }
        if (paragraphProperties.isSetInd()) {
            paragraphProperties.unsetInd();
        }
    }

    private void applyParsedFormatting(XWPFParagraph paragraph, TemplateStructureFormattingProfile formatting) {
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
        if (formatting.lineSpacing() != null) {
            applyLineSpacing(paragraph, formatting.lineSpacing());
        } else if (formatting.spacingBetween() != null) {
            paragraph.setSpacingBetween(formatting.spacingBetween() / 100.0d);
        }
        for (XWPFRun run : paragraph.getRuns()) {
            applyRunFormatting(run, formatting);
        }
    }

    private ParagraphAlignment toAlignment(String alignment) {
        if (alignment == null || alignment.isBlank()) {
            return null;
        }
        return switch (alignment.strip().toUpperCase()) {
            case "CENTER" -> ParagraphAlignment.CENTER;
            case "RIGHT" -> ParagraphAlignment.RIGHT;
            case "BOTH", "JUSTIFY" -> ParagraphAlignment.BOTH;
            default -> ParagraphAlignment.LEFT;
        };
    }

    private void applyLineSpacing(XWPFParagraph paragraph, TemplateLineSpacingProfile lineSpacing) {
        LineSpacingRule rule = toLineSpacingRule(lineSpacing.mode());
        if (rule == LineSpacingRule.AUTO && lineSpacing.multipleHundred() != null) {
            paragraph.setSpacingBetween(lineSpacing.multipleHundred() / 100.0d, rule);
        } else if (lineSpacing.valueTwips() != null) {
            paragraph.setSpacingBetween(lineSpacing.valueTwips() / 20.0d, rule);
        }
    }

    private LineSpacingRule toLineSpacingRule(String mode) {
        if (mode == null || mode.isBlank()) {
            return LineSpacingRule.AUTO;
        }
        return switch (mode.strip().toUpperCase()) {
            case "EXACT" -> LineSpacingRule.EXACT;
            case "AT_LEAST" -> LineSpacingRule.AT_LEAST;
            default -> LineSpacingRule.AUTO;
        };
    }

    private void applyRunFormatting(XWPFRun run, TemplateStructureFormattingProfile formatting) {
        boolean appliedSpecificFonts = false;
        if (formatting.eastAsiaFontFamily() != null && !formatting.eastAsiaFontFamily().isBlank()) {
            run.setFontFamily(formatting.eastAsiaFontFamily(), XWPFRun.FontCharRange.eastAsia);
            appliedSpecificFonts = true;
        }
        if (formatting.latinFontFamily() != null && !formatting.latinFontFamily().isBlank()) {
            run.setFontFamily(formatting.latinFontFamily(), XWPFRun.FontCharRange.ascii);
            run.setFontFamily(formatting.latinFontFamily(), XWPFRun.FontCharRange.hAnsi);
            appliedSpecificFonts = true;
        }
        if (!appliedSpecificFonts && formatting.fontFamily() != null && !formatting.fontFamily().isBlank()) {
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

    private List<XWPFParagraph> locateParagraphs(XWPFDocument document, Set<String> nodeKeys) {
        return nodeKeys.stream()
                .sorted()
                .map(nodeKey -> locator.findParagraph(document, nodeKey)
                        .orElseThrow(() -> new MissingNodeLocatorException(nodeKey)))
                .distinct()
                .toList();
    }

    private void clearParagraphs(List<XWPFParagraph> paragraphs) {
        paragraphs.forEach(paragraph -> replaceParagraphText(paragraph, ""));
    }

    private void removeParagraphs(XWPFDocument document, List<XWPFParagraph> paragraphs) {
        List<XWPFParagraph> bodyParagraphs = paragraphs.stream()
                .filter(paragraph -> document.getPosOfParagraph(paragraph) >= 0)
                .sorted(Comparator.comparingInt(document::getPosOfParagraph).reversed())
                .toList();
        List<XWPFParagraph> nonBodyParagraphs = paragraphs.stream()
                .filter(paragraph -> document.getPosOfParagraph(paragraph) < 0)
                .toList();
        bodyParagraphs.forEach(paragraph -> document.removeBodyElement(document.getPosOfParagraph(paragraph)));
        nonBodyParagraphs.stream()
                .forEach(paragraph -> replaceParagraphText(paragraph, ""));
    }

    private void replaceParagraphText(XWPFParagraph paragraph, String replacement) {
        while (paragraph.getRuns().size() > 1) {
            paragraph.removeRun(paragraph.getRuns().size() - 1);
        }
        XWPFRun run = paragraph.getRuns().isEmpty() ? paragraph.createRun() : paragraph.getRuns().get(0);
        String[] lines = replacement.split("\\R", -1);
        run.setText(lines[0], 0);
        for (int index = 1; index < lines.length; index++) {
            run.addBreak();
            run.setText(lines[index]);
        }
    }

    public static class MissingNodeLocatorException extends RuntimeException {
        private final String nodeKey;

        public MissingNodeLocatorException(String nodeKey) {
            super("DOCX node locator missing: " + nodeKey);
            this.nodeKey = nodeKey;
        }

        public String nodeKey() {
            return nodeKey;
        }
    }

    public record NodeInsertion(
            String anchorNodeKey,
            String position,
            String content,
            String styleSourceNodeKey,
            TemplateStructureFormattingProfile formatting
    ) {
        public NodeInsertion(String anchorNodeKey, String position, String content) {
            this(anchorNodeKey, position, content, "", null);
        }

        public NodeInsertion(String anchorNodeKey, String position, String content, String styleSourceNodeKey) {
            this(anchorNodeKey, position, content, styleSourceNodeKey, null);
        }

        public NodeInsertion {
            anchorNodeKey = anchorNodeKey == null ? "" : anchorNodeKey.strip();
            position = position == null || position.isBlank() ? "AFTER" : position.strip();
            content = content == null ? "" : content;
            styleSourceNodeKey = styleSourceNodeKey == null ? "" : styleSourceNodeKey.strip();
        }
    }

    private record ResolvedNodeInsertion(
            XWPFParagraph anchor,
            String position,
            String content,
            XWPFParagraph styleSource,
            TemplateStructureFormattingProfile formatting
    ) {
    }
}
