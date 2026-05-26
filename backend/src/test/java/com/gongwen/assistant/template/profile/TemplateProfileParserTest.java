package com.gongwen.assistant.template.profile;

import com.gongwen.assistant.support.DocxTestFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateProfileParserTest {
    private final TemplateProfileParser parser = new TemplateProfileParser();

    @Test
    void parsesPlaceholdersAcrossRunsAndReportsRisk() {
        TemplateProfile profile = parser.parse(DocxTestFactory.docxWithSplitPlaceholder());

        assertThat(profile.placeholders())
                .extracting(TemplatePlaceholderProfile::key)
                .containsExactly("标题");
        assertThat(profile.placeholders().getFirst().splitAcrossRuns()).isTrue();
        assertThat(profile.validationItems())
                .extracting(TemplateValidationItem::code)
                .contains("PLACEHOLDER_SPLIT_ACROSS_RUNS");
    }

    @Test
    void parsesStylesSectionsAndHeaderFooterSummary() {
        TemplateProfile profile = parser.parse(DocxTestFactory.docxWithOfficialStyles());

        assertThat(profile.styles())
                .extracting(TemplateStyleProfile::styleId)
                .contains("official_title", "body_text");
        assertThat(profile.sections()).hasSize(1);
        assertThat(profile.sections().getFirst().hasHeader()).isTrue();
        assertThat(profile.sections().getFirst().hasFooter()).isFalse();
        assertThat(profile.placeholders())
                .extracting(TemplatePlaceholderProfile::key)
                .contains("标题", "正文");
    }

    @Test
    void parsesTablePlaceholders() {
        TemplateProfile profile = parser.parse(DocxTestFactory.docxWithTableCell("附件：{{附件}}"));

        assertThat(profile.tables()).hasSize(1);
        assertThat(profile.tables().getFirst().rowCount()).isEqualTo(1);
        assertThat(profile.tables().getFirst().columnCount()).isEqualTo(1);
        assertThat(profile.tables().getFirst().placeholderCount()).isEqualTo(1);
        assertThat(profile.placeholders())
                .extracting(TemplatePlaceholderProfile::key)
                .containsExactly("附件");
    }
}
