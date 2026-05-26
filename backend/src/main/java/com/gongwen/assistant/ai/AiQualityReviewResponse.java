package com.gongwen.assistant.ai;

import java.util.List;

public record AiQualityReviewResponse(
        List<AiQualitySuggestion> suggestions
) {
    public AiQualityReviewResponse {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }
}
