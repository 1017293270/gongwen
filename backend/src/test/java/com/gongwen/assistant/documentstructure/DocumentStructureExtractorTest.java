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
