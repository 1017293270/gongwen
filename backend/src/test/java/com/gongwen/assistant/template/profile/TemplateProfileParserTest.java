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
    void parsesKeyParagraphAndRunFormattingForStyleCandidates() {
        TemplateProfile profile = parser.parse(DocxTestFactory.docxWithOfficialStyleFormatting());

        TemplateStructureProfile titleStructure = profile.structures().stream()
                .filter(structure -> "TITLE".equals(structure.structureType()))
                .findFirst()
                .orElseThrow();
        assertThat(titleStructure.label()).isEqualTo("公文标题");
        assertThat(titleStructure.textPreview()).contains("{{");
        assertThat(titleStructure.formatting().alignment()).isEqualTo("CENTER");
        assertThat(titleStructure.formatting().fontFamily()).isEqualTo("SimHei");
        assertThat(titleStructure.formatting().fontSizeHalfPoints()).isEqualTo(44);

        TemplateStructureProfile bodyStructure = profile.structures().stream()
                .filter(structure -> "BODY".equals(structure.structureType()))
                .findFirst()
                .orElseThrow();
        assertThat(bodyStructure.formatting().alignment()).isEqualTo("BOTH");
        assertThat(bodyStructure.formatting().indentationFirstLine()).isEqualTo(420);

        TemplateStyleProfile titleStyle = profile.styles().stream()
                .filter(style -> "official_title".equals(style.styleId()))
                .findFirst()
                .orElseThrow();
        assertThat(titleStyle.alignment()).isEqualTo("CENTER");
        assertThat(titleStyle.fontFamily()).isEqualTo("SimHei");
        assertThat(titleStyle.fontSizeHalfPoints()).isEqualTo(44);
        assertThat(titleStyle.bold()).isTrue();
        assertThat(titleStyle.spacingBefore()).isEqualTo(240);
        assertThat(titleStyle.spacingAfter()).isEqualTo(120);

        TemplateStyleProfile bodyStyle = profile.styles().stream()
                .filter(style -> "body_text".equals(style.styleId()))
                .findFirst()
                .orElseThrow();
        assertThat(bodyStyle.alignment()).isEqualTo("BOTH");
        assertThat(bodyStyle.fontFamily()).isEqualTo("FangSong");
        assertThat(bodyStyle.fontSizeHalfPoints()).isEqualTo(32);
        assertThat(bodyStyle.indentationFirstLine()).isEqualTo(420);
        assertThat(bodyStyle.spacingBetween()).isGreaterThan(0);
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
