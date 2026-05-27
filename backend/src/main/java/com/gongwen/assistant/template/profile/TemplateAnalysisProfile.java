package com.gongwen.assistant.template.profile;

import java.util.List;

public record TemplateAnalysisProfile(
        String templateKind,
        double confidence,
        String documentTypeCode,
        List<String> inferredFields,
        List<TemplatePlaceholderSuggestionProfile> suggestedPlaceholders,
        String message,
        String source
) {
    public TemplateAnalysisProfile {
        inferredFields = inferredFields == null ? List.of() : List.copyOf(inferredFields);
        suggestedPlaceholders = suggestedPlaceholders == null ? List.of() : List.copyOf(suggestedPlaceholders);
        message = message == null ? "" : message;
        source = source == null ? "" : source;
    }
}
