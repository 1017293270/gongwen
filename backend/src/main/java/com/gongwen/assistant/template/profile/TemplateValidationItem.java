package com.gongwen.assistant.template.profile;

import java.util.Map;

public record TemplateValidationItem(
        String severity,
        String code,
        String message,
        String targetType,
        String targetKey,
        Map<String, Object> details
) {
    public TemplateValidationItem {
        details = Map.copyOf(details);
    }
}
