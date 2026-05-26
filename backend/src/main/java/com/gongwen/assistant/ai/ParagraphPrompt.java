package com.gongwen.assistant.ai;

import java.util.List;

public record ParagraphPrompt(
        String promptVersion,
        String documentTypeCode,
        String title,
        String heading,
        List<String> points,
        List<String> fieldSummaries,
        List<String> materialSummaries,
        String instruction,
        String inputSummary
) {
    public ParagraphPrompt {
        points = List.copyOf(points);
        fieldSummaries = List.copyOf(fieldSummaries);
        materialSummaries = List.copyOf(materialSummaries);
    }
}
