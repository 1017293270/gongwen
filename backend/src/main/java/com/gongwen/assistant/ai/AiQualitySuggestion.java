package com.gongwen.assistant.ai;

public record AiQualitySuggestion(
        String severity,
        String category,
        String code,
        String message,
        String suggestion
) {
}
