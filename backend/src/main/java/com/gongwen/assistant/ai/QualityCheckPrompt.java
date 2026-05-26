package com.gongwen.assistant.ai;

import java.util.List;

public record QualityCheckPrompt(
        String promptVersion,
        String documentTypeCode,
        String title,
        List<String> fieldSummaries,
        List<String> bodySummaries,
        List<String> materialSummaries,
        List<String> ruleSummaries,
        String inputSummary
) {
    public QualityCheckPrompt {
        fieldSummaries = List.copyOf(fieldSummaries);
        bodySummaries = List.copyOf(bodySummaries);
        materialSummaries = List.copyOf(materialSummaries);
        ruleSummaries = List.copyOf(ruleSummaries);
    }
}
