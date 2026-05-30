package com.gongwen.assistant.ai;

import java.util.List;

public record OutlinePrompt(
        String promptVersion,
        String documentTypeCode,
        String title,
        List<String> fieldSummaries,
        List<String> materialSummaries,
        String instruction,
        String inputSummary,
        AiNodeContext nodeContext
) {
    public OutlinePrompt(
            String promptVersion,
            String documentTypeCode,
            String title,
            List<String> fieldSummaries,
            List<String> materialSummaries,
            String instruction,
            String inputSummary
    ) {
        this(promptVersion, documentTypeCode, title, fieldSummaries, materialSummaries, instruction, inputSummary, AiNodeContext.none());
    }

    public OutlinePrompt {
        fieldSummaries = List.copyOf(fieldSummaries);
        materialSummaries = List.copyOf(materialSummaries);
        instruction = instruction == null ? "" : instruction;
        nodeContext = nodeContext == null ? AiNodeContext.none() : nodeContext;
    }
}
