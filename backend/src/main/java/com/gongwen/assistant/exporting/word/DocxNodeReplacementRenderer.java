package com.gongwen.assistant.exporting.word;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
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
        Map<String, String> replacements = replacementsByNodeKey == null ? Map.of() : replacementsByNodeKey;
        Set<String> ignored = ignoredNodeKeys == null ? Set.of() : ignoredNodeKeys;
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(templateBytes));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            replacements.entrySet().stream()
                    .filter(entry -> !ignored.contains(entry.getKey()))
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(entry -> replaceNode(document, entry.getKey(), entry.getValue()));
            removeIgnoredNodes(document, ignored);
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

    private void removeIgnoredNodes(XWPFDocument document, Set<String> ignoredNodeKeys) {
        List<XWPFParagraph> ignoredParagraphs = ignoredNodeKeys.stream()
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
}
