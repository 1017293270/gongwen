package com.gongwen.assistant.ai;

import java.util.List;

public record TemplateAnalysisResponse(
        String templateKind,
        double confidence,
        String documentTypeCode,
        List<String> inferredFields,
        List<TemplatePlaceholderSuggestion> suggestedPlaceholders,
        String message,
        String source
) {
    public TemplateAnalysisResponse {
        templateKind = templateKind == null || templateKind.isBlank() ? "UNKNOWN_DOCUMENT" : templateKind;
        documentTypeCode = documentTypeCode == null ? "" : documentTypeCode;
        inferredFields = inferredFields == null ? List.of() : List.copyOf(inferredFields);
        suggestedPlaceholders = suggestedPlaceholders == null ? List.of() : List.copyOf(suggestedPlaceholders);
        message = message == null ? "" : message;
        source = source == null || source.isBlank() ? "MODEL" : source;
    }
}
