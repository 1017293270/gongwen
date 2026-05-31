package com.gongwen.assistant.documentstructure;

import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateStructureProfile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentStructureExtractor {
    private static final int SCHEMA_VERSION = 1;
    private static final String EXTRACTOR_VERSION = "document-structure-v1";
    private static final String FACT_EXTRACTOR_VERSION = "document-structure-v2";

    private final DocxStructureWalker structureWalker;

    public DocumentStructureExtractor() {
        this(new DocxStructureWalker());
    }

    public DocumentStructureExtractor(DocxStructureWalker structureWalker) {
        this.structureWalker = structureWalker;
    }

    public DocumentStructureProfile extract(byte[] docxBytes, String sourceFileHash) {
        if (docxBytes == null || docxBytes.length == 0) {
            return new DocumentStructureProfile(
                    SCHEMA_VERSION,
                    sourceFileHash,
                    FACT_EXTRACTOR_VERSION,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    Instant.now()
            );
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            return new DocumentStructureProfile(
                    SCHEMA_VERSION,
                    sourceFileHash,
                    FACT_EXTRACTOR_VERSION,
                    structureWalker.walk(document),
                    List.of(),
                    List.of(),
                    List.of(),
                    Instant.now()
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read Word document structure", exception);
        }
    }

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
