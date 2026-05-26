package com.gongwen.assistant.template;

public record CreateTemplateRequest(
        String templateName,
        String documentTypeCode
) {
}
