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
        List<String> riskCodes
) {
    public DocumentNode {
        riskCodes = riskCodes == null ? List.of() : List.copyOf(riskCodes);
    }
}
