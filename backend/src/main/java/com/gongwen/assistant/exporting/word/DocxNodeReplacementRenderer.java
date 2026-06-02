package com.gongwen.assistant.exporting.word;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
            replacements.entrySet().stream()
                    .filter(entry -> !clearKeys.contains(entry.getKey()))
                    .filter(entry -> !removeKeys.contains(entry.getKey()))
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(entry -> replaceNode(document, entry.getKey(), entry.getValue()));
            insertNodes(document, requestedInsertions, removeKeys);
            clearNodes(document, clearKeys);
            removeNodes(document, removeKeys);
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

    private void insertNodes(XWPFDocument document, List<NodeInsertion> insertions, Set<String> ignored) {
        Map<String, List<NodeInsertion>> byAnchor = insertions.stream()
                .filter(insertion -> insertion != null && !insertion.anchorNodeKey().isBlank())
                .filter(insertion -> !ignored.contains(insertion.anchorNodeKey()))
                .collect(Collectors.groupingBy(NodeInsertion::anchorNodeKey));
        byAnchor.forEach((anchorNodeKey, anchoredInsertions) -> {
            List<NodeInsertion> before = anchoredInsertions.stream()
                    .filter(insertion -> "BEFORE".equalsIgnoreCase(insertion.position()))
                    .toList();
            List<NodeInsertion> after = anchoredInsertions.stream()
                    .filter(insertion -> !"BEFORE".equalsIgnoreCase(insertion.position()))
                    .toList();
            if (!before.isEmpty()) {
                XWPFParagraph anchor = bodyAnchorParagraph(document, anchorNodeKey);
                List<NodeInsertion> reversed = new ArrayList<>(before);
                java.util.Collections.reverse(reversed);
                for (NodeInsertion insertion : reversed) {
                    insertParagraph(document, anchor, styleSourceParagraph(document, insertion, anchor), insertion.content(), false);
                }
            }
            if (!after.isEmpty()) {
                XWPFParagraph currentAnchor = bodyAnchorParagraph(document, anchorNodeKey);
                for (NodeInsertion insertion : after) {
                    currentAnchor = insertParagraph(
                            document,
                            currentAnchor,
                            styleSourceParagraph(document, insertion, currentAnchor),
                            insertion.content(),
                            true
                    );
                }
            }
        });
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
        return paragraph;
    }

    private void copyParagraphStyle(XWPFParagraph source, XWPFParagraph target) {
        if (source.getCTP().getPPr() != null) {
            target.getCTP().setPPr((CTPPr) source.getCTP().getPPr().copy());
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

    private void clearNodes(XWPFDocument document, Set<String> clearNodeKeys) {
        clearNodeKeys.stream()
                .sorted()
                .map(nodeKey -> locator.findParagraph(document, nodeKey)
                        .orElseThrow(() -> new MissingNodeLocatorException(nodeKey)))
                .distinct()
                .forEach(paragraph -> replaceParagraphText(paragraph, ""));
    }

    private void removeNodes(XWPFDocument document, Set<String> removeNodeKeys) {
        List<XWPFParagraph> ignoredParagraphs = removeNodeKeys.stream()
                .sorted()
                .map(nodeKey -> locator.findParagraph(document, nodeKey)
                        .orElseThrow(() -> new MissingNodeLocatorException(nodeKey)))
                .distinct()
                .toList();
        List<XWPFParagraph> bodyParagraphs = ignoredParagraphs.stream()
                .filter(paragraph -> document.getPosOfParagraph(paragraph) >= 0)
                .sorted(Comparator.comparingInt(document::getPosOfParagraph).reversed())
                .toList();
        List<XWPFParagraph> nonBodyParagraphs = ignoredParagraphs.stream()
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

    public record NodeInsertion(String anchorNodeKey, String position, String content, String styleSourceNodeKey) {
        public NodeInsertion(String anchorNodeKey, String position, String content) {
            this(anchorNodeKey, position, content, "");
        }

        public NodeInsertion {
            anchorNodeKey = anchorNodeKey == null ? "" : anchorNodeKey.strip();
            position = position == null || position.isBlank() ? "AFTER" : position.strip();
            content = content == null ? "" : content;
            styleSourceNodeKey = styleSourceNodeKey == null ? "" : styleSourceNodeKey.strip();
        }
    }
}
