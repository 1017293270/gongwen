package com.gongwen.assistant.ai;

import java.util.List;

public record TemplateAnalysisPrompt(
        String documentTypeCode,
        String originalFileName,
        String textSample,
        List<String> styleNames,
        int tableCount,
        boolean hasHeader,
        boolean hasFooter
) {
    public TemplateAnalysisPrompt {
        documentTypeCode = documentTypeCode == null ? "" : documentTypeCode;
        originalFileName = originalFileName == null ? "" : originalFileName;
        textSample = textSample == null ? "" : textSample;
        styleNames = styleNames == null ? List.of() : List.copyOf(styleNames);
    }
}
