package com.gongwen.assistant.template;

import java.time.Instant;

public record TemplateVersion(
        long id,
        long templateId,
        int versionNo,
        String originalFileName,
        String contentType,
        long fileSizeBytes,
        String filePath,
        String profileHash,
        String parseStatus,
        String parseErrorCode,
        String parseErrorMessage,
        Instant createdAt
) {
}
