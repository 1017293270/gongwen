package com.gongwen.assistant.documentstructure;

import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;

import java.util.List;

public record DocumentNode(
        String nodeKey,
        String parentKey,
        String nodeType,
        String roleSuggestion,
        String text,
        String textPreview,
        int orderIndex,
        String path,
        TemplateStructureFormattingProfile formatting,
        List<String> riskCodes,
        DocumentNodeLocation location,
        List<DocumentRunFact> runs,
        DocumentNumberingFact numbering
) {
    public DocumentNode {
        nodeKey = nodeKey == null ? "" : nodeKey;
        nodeType = nodeType == null || nodeType.isBlank() ? "PARAGRAPH" : nodeType;
        roleSuggestion = roleSuggestion == null || roleSuggestion.isBlank() ? "UNKNOWN" : roleSuggestion;
        text = text == null ? "" : text;
        textPreview = textPreview == null || textPreview.isBlank() ? text : textPreview;
        path = path == null ? "" : path;
        riskCodes = riskCodes == null ? List.of() : List.copyOf(riskCodes);
        runs = runs == null ? List.of() : List.copyOf(runs);
    }

    public DocumentNode(
            String nodeKey,
            String parentKey,
            String nodeType,
            String roleSuggestion,
            String text,
            String textPreview,
            int orderIndex,
            String path,
            TemplateStructureFormattingProfile formatting,
            List<String> riskCodes
    ) {
        this(
                nodeKey,
                parentKey,
                nodeType,
                roleSuggestion,
                text,
                textPreview,
                orderIndex,
                path,
                formatting,
                riskCodes,
                null,
                List.of(),
                null
        );
    }
}
