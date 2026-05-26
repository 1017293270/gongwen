package com.gongwen.assistant.template;

public record TemplateVersionSummary(
        long templateVersionId,
        long templateId,
        String templateName,
        int versionNo,
        String documentTypeCode,
        String originalFileName
) {
}
