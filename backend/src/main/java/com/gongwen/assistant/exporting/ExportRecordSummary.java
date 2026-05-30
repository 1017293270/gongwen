package com.gongwen.assistant.exporting;

import java.time.Instant;

public record ExportRecordSummary(
        long id,
        Long draftId,
        String draftTitle,
        String documentTypeCode,
        Long templateId,
        Long templateVersionId,
        String templateName,
        int templateVersion,
        String fileName,
        String status,
        String errorCode,
        String errorMessage,
        boolean canDownload,
        boolean canRetry,
        Instant createdAt
) {
}
