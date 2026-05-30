package com.gongwen.assistant.documentstructure;

import com.gongwen.assistant.template.profile.TemplateSectionProfile;
import com.gongwen.assistant.template.profile.TemplateStyleProfile;
import com.gongwen.assistant.template.profile.TemplateValidationItem;

import java.time.Instant;
import java.util.List;

public record DocumentStructureProfile(
        int schemaVersion,
        String sourceFileHash,
        String extractorVersion,
        List<DocumentNode> nodes,
        List<TemplateStyleProfile> styles,
        List<TemplateSectionProfile> sections,
        List<TemplateValidationItem> risks,
        Instant createdAt
) {
    public DocumentStructureProfile {
        sourceFileHash = sourceFileHash == null ? "" : sourceFileHash;
        extractorVersion = extractorVersion == null || extractorVersion.isBlank() ? "document-structure-v1" : extractorVersion;
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        styles = styles == null ? List.of() : List.copyOf(styles);
        sections = sections == null ? List.of() : List.copyOf(sections);
        risks = risks == null ? List.of() : List.copyOf(risks);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
