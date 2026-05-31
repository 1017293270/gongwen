package com.gongwen.assistant.documentstructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongwen.assistant.support.DocxTestFactory;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentStructureExtractorTest {
    private final TemplateProfileParser profileParser = new TemplateProfileParser();
    private final DocumentStructureExtractor extractor = new DocumentStructureExtractor();

    @Test
    void extractsAllSupportedFactsFromSpeechReferenceDocument() {
        byte[] docx = DocxTestFactory.speechReferenceDocument();

        DocumentStructureProfile profile = extractor.extract(docx, "hash-speech");

        assertThat(profile.extractorVersion()).isEqualTo("document-structure-v2");
        assertThat(profile.nodes())
                .filteredOn(node -> "PARAGRAPH".equals(node.nodeType()))
                .extracting(DocumentNode::text)
                .contains(
                        "在全区重点工作推进会上的讲话",
                        "政务会议讲话稿测试样例",
                        "2026年5月30日",
                        "同志们：",
                        "一、提高政治站位，把思想和行动统一到重点任务落实上来",
                        "结束语",
                        "我就讲这些，谢谢大家。"
                );
        assertThat(profile.nodes())
                .filteredOn(node -> "TABLE_PARAGRAPH".equals(node.nodeType()))
                .extracting(DocumentNode::text)
                .contains("文档类型", "政务会议讲话稿（测试样例）", "使用说明");
        assertThat(profile.nodes())
                .filteredOn(node -> "HEADER_PARAGRAPH".equals(node.nodeType()))
                .extracting(DocumentNode::text)
                .contains("内部测试资料");
        assertThat(profile.nodes())
                .filteredOn(node -> "FOOTER_PARAGRAPH".equals(node.nodeType()))
                .extracting(DocumentNode::text)
                .contains("测试文档 | 讲话稿范文示例");
    }

    @Test
    void extractsDocumentNodesFromTemplateProfileStructures() {
        TemplateProfile templateProfile = profileParser.parse(DocxTestFactory.docxWithNoticeReferenceSkeleton());

        DocumentStructureProfile profile = extractor.extract(templateProfile, "hash-1");

        assertThat(profile.schemaVersion()).isEqualTo(1);
        assertThat(profile.sourceFileHash()).isEqualTo("hash-1");
        assertThat(profile.nodes()).isNotEmpty();
        assertThat(profile.nodes())
                .extracting(DocumentNode::roleSuggestion)
                .contains("TITLE", "RECIPIENT", "BODY", "SIGNATURE", "DATE");
        DocumentNode title = profile.nodes().stream()
                .filter(node -> "TITLE".equals(node.roleSuggestion()))
                .findFirst()
                .orElseThrow();
        assertThat(title.nodeKey()).startsWith("paragraph-");
        assertThat(title.nodeType()).isEqualTo("PARAGRAPH");
        assertThat(title.path()).contains("PARAGRAPH/");
        assertThat(title.formatting().fontFamily()).isEqualTo("SimSun");
    }

    @Test
    void documentStructureProfileSurvivesJsonRoundTrip() throws Exception {
        TemplateProfile templateProfile = profileParser.parse(DocxTestFactory.docxWithOfficialStyles());
        DocumentStructureProfile profile = extractor.extract(templateProfile, "hash-2");
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        DocumentStructureProfile restored = objectMapper.readValue(
                objectMapper.writeValueAsString(profile),
                DocumentStructureProfile.class
        );

        assertThat(restored.sourceFileHash()).isEqualTo("hash-2");
        assertThat(restored.nodes()).hasSameSizeAs(profile.nodes());
        assertThat(restored.nodes().getFirst().nodeKey()).isEqualTo(profile.nodes().getFirst().nodeKey());
    }
}
