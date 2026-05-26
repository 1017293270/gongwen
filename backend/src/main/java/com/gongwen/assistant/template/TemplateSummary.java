package com.gongwen.assistant.template;

public record TemplateSummary(
        long id,
        String templateName,
        String documentTypeCode,
        String status
) {
}
