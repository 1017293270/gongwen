package com.gongwen.assistant.ai.candidate;

import java.util.List;
import java.util.UUID;

public record CreateParagraphCandidateBatchRequest(
        UUID outlineTraceId,
        String instructionSummary,
        List<ParagraphCandidateSectionRequest> sections
) {
    public CreateParagraphCandidateBatchRequest {
        instructionSummary = instructionSummary == null ? "" : instructionSummary.strip();
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
