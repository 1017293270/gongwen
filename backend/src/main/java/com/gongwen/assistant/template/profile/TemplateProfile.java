package com.gongwen.assistant.template.profile;

import java.util.List;

public record TemplateProfile(
        int schemaVersion,
        List<TemplateStyleProfile> styles,
        List<TemplatePlaceholderProfile> placeholders,
        List<TemplateSectionProfile> sections,
        List<TemplateTableProfile> tables,
        List<TemplateMediaProfile> media,
        List<TemplateValidationItem> validationItems
) {
    public TemplateProfile {
        styles = List.copyOf(styles);
        placeholders = List.copyOf(placeholders);
        sections = List.copyOf(sections);
        tables = List.copyOf(tables);
        media = List.copyOf(media);
        validationItems = List.copyOf(validationItems);
    }
}
