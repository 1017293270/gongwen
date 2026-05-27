package com.gongwen.assistant.template.profile;

public record TemplateStructureProfile(
        String structureKey,
        String structureType,
        String label,
        String textPreview,
        String locationType,
        String styleId,
        String styleName,
        String source,
        TemplateStructureFormattingProfile formatting
) {
}
