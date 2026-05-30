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
        String inputSummary,
        AiNodeContext nodeContext
) {
    public ParagraphPrompt(
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
        this(promptVersion, documentTypeCode, title, heading, points, fieldSummaries, materialSummaries, instruction, inputSummary, AiNodeContext.none());
    }

    public ParagraphPrompt {
        points = List.copyOf(points);
        fieldSummaries = List.copyOf(fieldSummaries);
        materialSummaries = List.copyOf(materialSummaries);
        nodeContext = nodeContext == null ? AiNodeContext.none() : nodeContext;
    }
}
