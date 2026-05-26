package com.gongwen.assistant.ai;

import java.util.List;

public record OutlinePrompt(
        String promptVersion,
        String documentTypeCode,
        String title,
        List<String> fieldSummaries,
        List<String> materialSummaries,
        String instruction,
        String inputSummary
) {
    public OutlinePrompt {
        fieldSummaries = List.copyOf(fieldSummaries);
        materialSummaries = List.copyOf(materialSummaries);
        instruction = instruction == null ? "" : instruction;
    }
}
