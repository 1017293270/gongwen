package com.gongwen.assistant.exporting;

import com.gongwen.assistant.exporting.word.ExportFormattingContext;
import com.gongwen.assistant.template.profile.TemplateProfile;

import java.util.Map;

public record WordExportRequest(
        String templateName,
        int templateVersion,
        Map<String, String> values,
        ExportFormattingContext formatting,
        TemplateProfile templateProfile
) {
    public WordExportRequest {
        formatting = formatting == null ? ExportFormattingContext.EMPTY : formatting;
    }

    public WordExportRequest(
            String templateName,
            int templateVersion,
            Map<String, String> values,
            ExportFormattingContext formatting
    ) {
        this(templateName, templateVersion, values, formatting, null);
    }

    public WordExportRequest(String templateName, int templateVersion, Map<String, String> values) {
        this(templateName, templateVersion, values, null, null);
    }
}
