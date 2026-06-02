package com.gongwen.assistant.ai.candidate;

import java.util.List;
import java.util.UUID;

public record ParagraphCandidateJobResponse(
        UUID jobId,
        List<Long> candidateIds,
        boolean cancelled
) {
    public ParagraphCandidateJobResponse {
        candidateIds = candidateIds == null ? List.of() : List.copyOf(candidateIds);
    }
}
