package com.gongwen.assistant.exporting;

import java.time.Instant;

public record ExportRecordDetail(
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
        boolean fileAvailable,
        Instant createdAt
) {
    public ExportRecordDetail withFileAvailable(boolean nextFileAvailable) {
        return new ExportRecordDetail(
                id,
                draftId,
                draftTitle,
                documentTypeCode,
                templateId,
                templateVersionId,
                templateName,
                templateVersion,
                fileName,
                status,
                errorCode,
                errorMessage,
                canDownload && nextFileAvailable,
                canRetry,
                nextFileAvailable,
                createdAt
        );
    }
}
