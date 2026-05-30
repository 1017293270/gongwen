package com.gongwen.assistant.exporting;

public record ExportRecord(
        Long templateId,
        Long templateVersionId,
        String templateName,
        int templateVersion,
        Long draftId,
        Long exportedBy,
        Long departmentId,
        String fileName,
        String filePath,
        String status,
        String errorCode,
        String errorMessage
) {
    public static ExportRecord success(String templateName, int templateVersion, String fileName) {
        return new ExportRecord(null, null, templateName, templateVersion, null, null, null, fileName, null, "SUCCESS", null, null);
    }

    public static ExportRecord success(WordExportRequest request, String fileName, String filePath) {
        return new ExportRecord(
                request.templateId(),
                request.templateVersionId(),
                request.templateName(),
                request.templateVersion(),
                request.draftId(),
                request.exportedBy(),
                request.departmentId(),
                fileName,
                filePath,
                "SUCCESS",
                null,
                null
        );
    }

    public static ExportRecord failure(String templateName, int templateVersion, String fileName, String errorCode, String errorMessage) {
        return new ExportRecord(null, null, templateName, templateVersion, null, null, null, fileName, null, "FAILED", errorCode, errorMessage);
    }

    public static ExportRecord failure(WordExportRequest request, String fileName, String errorCode, String errorMessage) {
        return new ExportRecord(
                request.templateId(),
                request.templateVersionId(),
                request.templateName(),
                request.templateVersion(),
                request.draftId(),
                request.exportedBy(),
                request.departmentId(),
                fileName,
                null,
                "FAILED",
                errorCode,
                errorMessage
        );
    }
}
