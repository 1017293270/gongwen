package com.gongwen.assistant.exporting;

import com.gongwen.assistant.exporting.word.ExportFormattingContext;

import java.util.Map;

public record WordExportRequest(
        String templateName,
        int templateVersion,
        Map<String, String> values,
        ExportFormattingContext formatting
) {
    public WordExportRequest {
        formatting = formatting == null ? ExportFormattingContext.EMPTY : formatting;
    }

    public WordExportRequest(String templateName, int templateVersion, Map<String, String> values) {
        this(templateName, templateVersion, values, null);
    }
}
