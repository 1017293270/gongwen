package com.gongwen.assistant.ai.candidate;

import com.gongwen.assistant.draft.DraftDetailDto;

import java.util.List;

public record AiParagraphCandidateBatchAcceptResponse(
        List<Long> acceptedIds,
        List<Long> skippedIds,
        DraftDetailDto updatedDraft
) {
    public AiParagraphCandidateBatchAcceptResponse {
        acceptedIds = acceptedIds == null ? List.of() : List.copyOf(acceptedIds);
        skippedIds = skippedIds == null ? List.of() : List.copyOf(skippedIds);
    }
}
