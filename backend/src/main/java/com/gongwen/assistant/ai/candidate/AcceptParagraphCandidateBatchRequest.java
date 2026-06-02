package com.gongwen.assistant.ai.candidate;

import java.util.List;

public record AcceptParagraphCandidateBatchRequest(List<Long> candidateIds) {
    public AcceptParagraphCandidateBatchRequest {
        candidateIds = candidateIds == null ? List.of() : List.copyOf(candidateIds);
    }
}
