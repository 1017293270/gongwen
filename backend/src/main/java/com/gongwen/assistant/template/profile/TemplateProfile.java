package com.gongwen.assistant.template.profile;

import java.util.List;

public record TemplateProfile(
        int schemaVersion,
        List<TemplateStructureProfile> structures,
        List<TemplateStyleProfile> styles,
        List<TemplatePlaceholderProfile> placeholders,
        List<TemplateSectionProfile> sections,
        List<TemplateTableProfile> tables,
        List<TemplateMediaProfile> media,
        List<TemplateValidationItem> validationItems,
        TemplateAnalysisProfile templateAnalysis
) {
    public TemplateProfile(
            int schemaVersion,
            List<TemplateStyleProfile> styles,
            List<TemplatePlaceholderProfile> placeholders,
            List<TemplateSectionProfile> sections,
            List<TemplateTableProfile> tables,
            List<TemplateMediaProfile> media,
            List<TemplateValidationItem> validationItems
    ) {
        this(schemaVersion, List.of(), styles, placeholders, sections, tables, media, validationItems, null);
    }

    public TemplateProfile(
            int schemaVersion,
            List<TemplateStructureProfile> structures,
            List<TemplateStyleProfile> styles,
            List<TemplatePlaceholderProfile> placeholders,
            List<TemplateSectionProfile> sections,
            List<TemplateTableProfile> tables,
            List<TemplateMediaProfile> media,
            List<TemplateValidationItem> validationItems
    ) {
        this(schemaVersion, structures, styles, placeholders, sections, tables, media, validationItems, null);
    }

    public TemplateProfile {
        structures = List.copyOf(structures == null ? List.of() : structures);
        styles = List.copyOf(styles);
        placeholders = List.copyOf(placeholders);
        sections = List.copyOf(sections);
        tables = List.copyOf(tables);
        media = List.copyOf(media);
        validationItems = List.copyOf(validationItems);
    }

    public TemplateProfile withTemplateAnalysis(TemplateAnalysisProfile analysis) {
        return new TemplateProfile(schemaVersion, structures, styles, placeholders, sections, tables, media, validationItems, analysis);
    }
}
