package com.gongwen.assistant.template.parser;

import com.gongwen.assistant.support.DocxTestFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocxPlaceholderParserTest {
    private final DocxPlaceholderParser parser = new DocxPlaceholderParser();

    @Test
    void parsesParagraphPlaceholdersInFirstSeenOrder() {
        byte[] docx = DocxTestFactory.docxWithParagraphs(
                "{{标题}}",
                "{{主送}}：",
                "{{正文}}",
                "{{落款}} {{日期}}"
        );

        ParsedTemplate parsed = parser.parse(docx);

        assertThat(parsed.placeholders()).containsExactly("标题", "主送", "正文", "落款", "日期");
    }

    @Test
    void parsesTableCellPlaceholders() {
        byte[] docx = DocxTestFactory.docxWithTableCell("字段：{{附件}}");

        ParsedTemplate parsed = parser.parse(docx);

        assertThat(parsed.placeholders()).containsExactly("附件");
    }

    @Test
    void removesDuplicatePlaceholders() {
        byte[] docx = DocxTestFactory.docxWithParagraphs("{{标题}}", "{{标题}}", "{{正文}}");

        ParsedTemplate parsed = parser.parse(docx);

        assertThat(parsed.placeholders()).containsExactly("标题", "正文");
    }
}
