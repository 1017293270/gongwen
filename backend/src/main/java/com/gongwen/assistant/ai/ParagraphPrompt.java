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
        AiNodeContext nodeContext,
        String formattingSummary
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
        this(promptVersion, documentTypeCode, title, heading, points, fieldSummaries, materialSummaries, instruction, inputSummary, AiNodeContext.none(), "");
    }

    public ParagraphPrompt(
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
        this(promptVersion, documentTypeCode, title, heading, points, fieldSummaries, materialSummaries, instruction, inputSummary, nodeContext, "");
    }

    public ParagraphPrompt {
        points = List.copyOf(points);
        fieldSummaries = List.copyOf(fieldSummaries);
        materialSummaries = List.copyOf(materialSummaries);
        instruction = instruction == null ? "" : instruction;
        nodeContext = nodeContext == null ? AiNodeContext.none() : nodeContext;
        formattingSummary = formattingSummary == null ? "" : formattingSummary.strip();
    }
}
