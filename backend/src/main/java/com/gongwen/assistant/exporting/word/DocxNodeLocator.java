package com.gongwen.assistant.exporting.word;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocxNodeLocator {
    private static final Pattern BODY_PARAGRAPH = Pattern.compile("^paragraph-(\\d+)$");
    private static final Pattern TABLE_PARAGRAPH =
            Pattern.compile("^table-(\\d+)-row-(\\d+)-cell-(\\d+)-paragraph-(\\d+)$");
    private static final Pattern HEADER_PARAGRAPH = Pattern.compile("^header-(\\d+)-paragraph-(\\d+)$");
    private static final Pattern FOOTER_PARAGRAPH = Pattern.compile("^footer-(\\d+)-paragraph-(\\d+)$");

    public Optional<XWPFParagraph> findParagraph(XWPFDocument document, String nodeKey) {
        if (document == null || nodeKey == null || nodeKey.isBlank()) {
            return Optional.empty();
        }
        Matcher bodyMatcher = BODY_PARAGRAPH.matcher(nodeKey);
        if (bodyMatcher.matches()) {
            return itemAt(document.getParagraphs(), intGroup(bodyMatcher, 1));
        }

        Matcher tableMatcher = TABLE_PARAGRAPH.matcher(nodeKey);
        if (tableMatcher.matches()) {
            return tableParagraph(document, tableMatcher);
        }

        Matcher headerMatcher = HEADER_PARAGRAPH.matcher(nodeKey);
        if (headerMatcher.matches()) {
            return itemAt(document.getHeaderList(), intGroup(headerMatcher, 1))
                    .flatMap(header -> itemAt(header.getParagraphs(), intGroup(headerMatcher, 2)));
        }

        Matcher footerMatcher = FOOTER_PARAGRAPH.matcher(nodeKey);
        if (footerMatcher.matches()) {
            return itemAt(document.getFooterList(), intGroup(footerMatcher, 1))
                    .flatMap(footer -> itemAt(footer.getParagraphs(), intGroup(footerMatcher, 2)));
        }

        return Optional.empty();
    }

    private Optional<XWPFParagraph> tableParagraph(XWPFDocument document, Matcher matcher) {
        return itemAt(document.getTables(), intGroup(matcher, 1))
                .flatMap(table -> itemAt(table.getRows(), intGroup(matcher, 2)))
                .flatMap(row -> itemAt(row.getTableCells(), intGroup(matcher, 3)))
                .map(XWPFTableCell::getParagraphs)
                .flatMap(paragraphs -> itemAt(paragraphs, intGroup(matcher, 4)));
    }

    private int intGroup(Matcher matcher, int group) {
        return Integer.parseInt(matcher.group(group));
    }

    private <T> Optional<T> itemAt(List<T> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(index));
    }
}
