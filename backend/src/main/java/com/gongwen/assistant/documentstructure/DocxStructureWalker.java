package com.gongwen.assistant.documentstructure;

import com.gongwen.assistant.template.profile.TemplateLineSpacingProfile;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.LineSpacingRule;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class DocxStructureWalker {
    public List<DocumentNode> walk(XWPFDocument document) {
        List<DocumentNode> nodes = new ArrayList<>();
        int orderIndex = 0;
        int paragraphIndex = 0;
        int tableIndex = 0;

        for (IBodyElement bodyElement : document.getBodyElements()) {
            if (bodyElement.getElementType() == BodyElementType.PARAGRAPH) {
                XWPFParagraph paragraph = (XWPFParagraph) bodyElement;
                DocumentNode node = paragraphNode(
                        paragraph,
                        "paragraph-" + paragraphIndex,
                        "PARAGRAPH",
                        orderIndex,
                        "body/paragraph[" + paragraphIndex + "]",
                        new DocumentNodeLocation("BODY", paragraphIndex, null, null, null, null)
                );
                if (node != null) {
                    nodes.add(node);
                    orderIndex++;
                }
                paragraphIndex++;
            } else if (bodyElement.getElementType() == BodyElementType.TABLE) {
                XWPFTable table = (XWPFTable) bodyElement;
                orderIndex = addTableNodes(nodes, table, tableIndex, orderIndex);
                tableIndex++;
            }
        }

        orderIndex = addHeaderNodes(nodes, document.getHeaderList(), orderIndex);
        addFooterNodes(nodes, document.getFooterList(), orderIndex);
        return nodes;
    }

    private int addTableNodes(List<DocumentNode> nodes, XWPFTable table, int tableIndex, int orderIndex) {
        for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++) {
            XWPFTableRow row = table.getRows().get(rowIndex);
            for (int cellIndex = 0; cellIndex < row.getTableCells().size(); cellIndex++) {
                XWPFTableCell cell = row.getTableCells().get(cellIndex);
                for (int paragraphIndex = 0; paragraphIndex < cell.getParagraphs().size(); paragraphIndex++) {
                    String nodeKey = "table-" + tableIndex
                            + "-row-" + rowIndex
                            + "-cell-" + cellIndex
                            + "-paragraph-" + paragraphIndex;
                    String path = "body/table[" + tableIndex + "]/row[" + rowIndex + "]/cell[" + cellIndex + "]/paragraph[" + paragraphIndex + "]";
                    DocumentNode node = paragraphNode(
                            cell.getParagraphs().get(paragraphIndex),
                            nodeKey,
                            "TABLE_PARAGRAPH",
                            orderIndex,
                            path,
                            new DocumentNodeLocation("TABLE", null, tableIndex, rowIndex, cellIndex, paragraphIndex)
                    );
                    if (node != null) {
                        nodes.add(node);
                        orderIndex++;
                    }
                }
            }
        }
        return orderIndex;
    }

    private int addHeaderNodes(List<DocumentNode> nodes, List<XWPFHeader> headers, int orderIndex) {
        for (int headerIndex = 0; headerIndex < headers.size(); headerIndex++) {
            XWPFHeader header = headers.get(headerIndex);
            for (int paragraphIndex = 0; paragraphIndex < header.getParagraphs().size(); paragraphIndex++) {
                String nodeKey = "header-" + headerIndex + "-paragraph-" + paragraphIndex;
                DocumentNode node = paragraphNode(
                        header.getParagraphs().get(paragraphIndex),
                        nodeKey,
                        "HEADER_PARAGRAPH",
                        orderIndex,
                        "header[" + headerIndex + "]/paragraph[" + paragraphIndex + "]",
                        new DocumentNodeLocation("HEADER", paragraphIndex, null, null, null, null)
                );
                if (node != null) {
                    nodes.add(node);
                    orderIndex++;
                }
            }
        }
        return orderIndex;
    }

    private int addFooterNodes(List<DocumentNode> nodes, List<XWPFFooter> footers, int orderIndex) {
        for (int footerIndex = 0; footerIndex < footers.size(); footerIndex++) {
            XWPFFooter footer = footers.get(footerIndex);
            for (int paragraphIndex = 0; paragraphIndex < footer.getParagraphs().size(); paragraphIndex++) {
                String nodeKey = "footer-" + footerIndex + "-paragraph-" + paragraphIndex;
                DocumentNode node = paragraphNode(
                        footer.getParagraphs().get(paragraphIndex),
                        nodeKey,
                        "FOOTER_PARAGRAPH",
                        orderIndex,
                        "footer[" + footerIndex + "]/paragraph[" + paragraphIndex + "]",
                        new DocumentNodeLocation("FOOTER", paragraphIndex, null, null, null, null)
                );
                if (node != null) {
                    nodes.add(node);
                    orderIndex++;
                }
            }
        }
        return orderIndex;
    }

    private DocumentNode paragraphNode(
            XWPFParagraph paragraph,
            String nodeKey,
            String nodeType,
            int orderIndex,
            String path,
            DocumentNodeLocation location
    ) {
        String text = normalizeText(paragraph.getText());
        if (text.isBlank()) {
            return null;
        }
        return new DocumentNode(
                nodeKey,
                null,
                nodeType,
                "UNKNOWN",
                text,
                previewText(text),
                orderIndex,
                path,
                formattingFromParagraph(paragraph),
                riskCodes(paragraph),
                location,
                runFacts(paragraph),
                numberingFact(paragraph)
        );
    }

    private List<DocumentRunFact> runFacts(XWPFParagraph paragraph) {
        List<DocumentRunFact> facts = new ArrayList<>();
        for (int index = 0; index < paragraph.getRuns().size(); index++) {
            XWPFRun run = paragraph.getRuns().get(index);
            String text = normalizeText(run.text());
            if (text.isBlank()) {
                continue;
            }
            facts.add(new DocumentRunFact(
                    index,
                    text,
                    eastAsiaFontFamily(run),
                    latinFontFamily(run),
                    fontSizeHalfPoints(run),
                    run.isBold(),
                    run.isItalic(),
                    blankToNull(run.getColor())
            ));
        }
        return facts;
    }

    private DocumentNumberingFact numberingFact(XWPFParagraph paragraph) {
        BigInteger numId = paragraph.getNumID();
        BigInteger ilvl = null;
        if (paragraph.getCTP().isSetPPr()
                && paragraph.getCTP().getPPr().isSetNumPr()
                && paragraph.getCTP().getPPr().getNumPr().isSetIlvl()) {
            ilvl = paragraph.getCTP().getPPr().getNumPr().getIlvl().getVal();
        }
        if (numId == null && ilvl == null) {
            return null;
        }
        return new DocumentNumberingFact(
                numId == null ? null : numId.toString(),
                ilvl == null ? null : ilvl.toString(),
                null
        );
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
                run == null ? null : blankToNull(run.getColor()),
                eastAsiaFontFamily(run),
                latinFontFamily(run),
                lineSpacing
        );
    }

    private XWPFRun firstRun(XWPFParagraph paragraph) {
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

    private List<String> riskCodes(XWPFParagraph paragraph) {
        List<String> risks = new ArrayList<>();
        if (paragraph.getCTP().getFldSimpleList() != null && !paragraph.getCTP().getFldSimpleList().isEmpty()) {
            risks.add("FIELD_SIMPLE");
        }
        if (paragraph.getCTP().getHyperlinkList() != null && !paragraph.getCTP().getHyperlinkList().isEmpty()) {
            risks.add("HYPERLINK");
        }
        return risks;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.strip();
    }

    private String previewText(String text) {
        if (text.length() <= 120) {
            return text;
        }
        return text.substring(0, 120) + "...";
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
}
