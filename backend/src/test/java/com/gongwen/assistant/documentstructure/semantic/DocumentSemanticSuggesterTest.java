package com.gongwen.assistant.documentstructure.semantic;

import com.gongwen.assistant.documentstructure.DocumentNode;
import com.gongwen.assistant.documentstructure.DocumentStructureExtractor;
import com.gongwen.assistant.documentstructure.DocumentStructureProfile;
import com.gongwen.assistant.support.DocxTestFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentSemanticSuggesterTest {
    private final DocumentStructureExtractor extractor = new DocumentStructureExtractor();
    private final DocumentSemanticSuggester suggester = new DocumentSemanticSuggester();

    @Test
    void suggestsSpeechRolesWithoutTreatingBodyUnitWordsAsIssuingOrgan() {
        DocumentStructureProfile profile = extractor.extract(DocxTestFactory.speechReferenceDocument(), "hash");

        DocumentStructureProfile suggested = suggester.suggest(profile, "REFERENCE_DOCUMENT", "UNKNOWN");

        assertThat(roleByText(suggested, "在全区重点工作推进会上的讲话")).isEqualTo("TITLE");
        assertThat(roleByText(suggested, "2026年5月30日")).isEqualTo("DATE");
        assertThat(roleByText(suggested, "同志们：")).isEqualTo("RECIPIENT");
        assertThat(roleByText(suggested, "一、提高政治站位，把思想和行动统一到重点任务落实上来")).isEqualTo("BODY_HEADING_LEVEL_1");
        assertThat(roleByText(suggested, "二、聚焦关键环节，以务实举措推动工作提质增效")).isEqualTo("BODY_HEADING_LEVEL_1");
        assertThat(roleByText(suggested, "三、压紧压实责任，形成齐抓共管的工作合力")).isEqualTo("BODY_HEADING_LEVEL_1");
        assertThat(roleByText(suggested, "结束语")).isEqualTo("BODY_HEADING_LEVEL_1");
        assertThat(roleByPrefix(suggested, "今年以来，各部门各单位围绕中心")).isEqualTo("BODY");
        assertThat(roleByPrefix(suggested, "要健全闭环机制")).isEqualTo("BODY");
    }

    @Test
    void suggestsPlaceholderSlotsWithoutDependingOnTemplateProfileSemantics() {
        DocumentStructureProfile profile = extractor.extract(DocxTestFactory.docxWithOfficialStyles(), "hash");

        DocumentStructureProfile suggested = suggester.suggest(profile, "PLACEHOLDER_TEMPLATE", "NOTICE");

        assertThat(suggested.nodes())
                .extracting(DocumentNode::roleSuggestion)
                .contains("TITLE", "BODY", "STATIC_TEXT");
    }

    @Test
    void suggestsNoticeReferenceSlotsWithoutPromotingIssuingOrganAsTitle() {
        DocumentStructureProfile profile = extractor.extract(DocxTestFactory.docxWithNoticeReferenceSkeleton(), "hash");

        DocumentStructureProfile suggested = suggester.suggest(profile, "REFERENCE_DOCUMENT", "NOTICE");

        assertThat(roleByText(suggested, "示例单位文件")).isEqualTo("ISSUING_ORGAN");
        assertThat(roleByText(suggested, "示例办〔2026〕5号")).isEqualTo("DOC_NUMBER");
        assertThat(roleByText(suggested, "关于召开2026年第二季度行政办公例会的通知")).isEqualTo("TITLE");
        assertThat(roleByText(suggested, "各部门、各直属单位：")).isEqualTo("RECIPIENT");
        assertThat(roleByText(suggested, "为统筹推进近期重点工作，现将有关事项通知如下：")).isEqualTo("BODY");
        assertThat(roleByText(suggested, "一、会议时间")).isEqualTo("BODY_HEADING_LEVEL_1");
        assertThat(roleByText(suggested, "附件：会议议题征集表")).isEqualTo("ATTACHMENT_NOTE");
        assertThat(roleByText(suggested, "示例单位办公室")).isEqualTo("SIGNATURE");
        assertThat(roleByText(suggested, "2026年5月27日")).isEqualTo("DATE");
    }

    private String roleByText(DocumentStructureProfile profile, String text) {
        return profile.nodes().stream()
                .filter(node -> text.equals(node.text()))
                .findFirst()
                .orElseThrow()
                .roleSuggestion();
    }

    private String roleByPrefix(DocumentStructureProfile profile, String prefix) {
        return profile.nodes().stream()
                .filter(node -> node.text().startsWith(prefix))
                .findFirst()
                .orElseThrow()
                .roleSuggestion();
    }
}
