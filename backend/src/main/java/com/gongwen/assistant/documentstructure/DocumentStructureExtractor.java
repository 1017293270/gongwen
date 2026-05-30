package com.gongwen.assistant.documentstructure;

import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateStructureProfile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentStructureExtractor {
    private static final int SCHEMA_VERSION = 1;
    private static final String EXTRACTOR_VERSION = "document-structure-v1";

    public DocumentStructureProfile extract(TemplateProfile profile, String sourceFileHash) {
        if (profile == null) {
            return new DocumentStructureProfile(
                    SCHEMA_VERSION,
                    sourceFileHash,
                    EXTRACTOR_VERSION,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    Instant.now()
            );
        }
        List<DocumentNode> nodes = new ArrayList<>();
        for (int index = 0; index < profile.structures().size(); index++) {
            nodes.add(toNode(profile.structures().get(index), index));
        }
        return new DocumentStructureProfile(
                SCHEMA_VERSION,
                sourceFileHash,
                EXTRACTOR_VERSION,
                nodes,
                profile.styles(),
                profile.sections(),
                profile.validationItems(),
                Instant.now()
        );
    }

    private DocumentNode toNode(TemplateStructureProfile structure, int index) {
        String nodeType = switch (safeText(structure.locationType())) {
            case "TABLE" -> "TABLE_PARAGRAPH";
            case "HEADER" -> "HEADER_PARAGRAPH";
            case "FOOTER" -> "FOOTER_PARAGRAPH";
            default -> "PARAGRAPH";
        };
        return new DocumentNode(
                structure.structureKey(),
                null,
                nodeType,
                structure.structureType(),
                structure.textPreview(),
                structure.textPreview(),
                index,
                safeText(structure.locationType()) + "/" + safeText(structure.structureKey()),
                structure.formatting(),
                List.of()
        );
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
