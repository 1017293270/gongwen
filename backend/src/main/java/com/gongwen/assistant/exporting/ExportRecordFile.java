package com.gongwen.assistant.exporting;

public record ExportRecordFile(
        String fileName,
        String contentType,
        byte[] content
) {
}
