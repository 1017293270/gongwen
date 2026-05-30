package com.gongwen.assistant.exporting;

import com.gongwen.assistant.exporting.word.ExportFormattingContext;
import com.gongwen.assistant.template.profile.TemplateProfile;

import java.util.Map;

public record WordExportRequest(
        String templateName,
        int templateVersion,
        Long templateId,
        Long templateVersionId,
        Long draftId,
        Long exportedBy,
        Long departmentId,
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
        this(templateName, templateVersion, null, null, null, null, null, values, formatting, null);
    }

    public WordExportRequest(String templateName, int templateVersion, Map<String, String> values) {
        this(templateName, templateVersion, null, null, null, null, null, values, null, null);
    }

    public static WordExportRequest draftExport(
            String templateName,
            int templateVersion,
            Long templateId,
            Long templateVersionId,
            Long draftId,
            Long exportedBy,
            Long departmentId,
            Map<String, String> values,
            ExportFormattingContext formatting,
            TemplateProfile templateProfile
    ) {
        return new WordExportRequest(
                templateName,
                templateVersion,
                templateId,
                templateVersionId,
                draftId,
                exportedBy,
                departmentId,
                values,
                formatting,
                templateProfile
        );
    }
}
