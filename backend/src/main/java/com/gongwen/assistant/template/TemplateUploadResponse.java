package com.gongwen.assistant.template;

import java.util.List;

public record TemplateUploadResponse(
        long templateVersionId,
        int versionNo,
        String parseStatus,
        int placeholderCount,
        int styleCount,
        int validationCount,
        List<String> validationCodes
) {
    public TemplateUploadResponse {
        validationCodes = List.copyOf(validationCodes);
    }
}
