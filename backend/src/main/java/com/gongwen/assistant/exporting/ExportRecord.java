package com.gongwen.assistant.exporting;

public record ExportRecord(
        String templateName,
        int templateVersion,
        String fileName,
        String status,
        String errorCode,
        String errorMessage
) {
    public static ExportRecord success(String templateName, int templateVersion, String fileName) {
        return new ExportRecord(templateName, templateVersion, fileName, "SUCCESS", null, null);
    }

    public static ExportRecord failure(String templateName, int templateVersion, String fileName, String errorCode, String errorMessage) {
        return new ExportRecord(templateName, templateVersion, fileName, "FAILED", errorCode, errorMessage);
    }
}
