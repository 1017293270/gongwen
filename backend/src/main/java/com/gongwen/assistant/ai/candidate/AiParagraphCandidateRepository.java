package com.gongwen.assistant.ai.candidate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiParagraphCandidateRepository {
    AiParagraphCandidate insert(AiParagraphCandidate candidate);

    List<AiParagraphCandidate> findByDraftId(long draftId);

    Optional<AiParagraphCandidate> findById(long candidateId);

    Optional<AiParagraphCandidate> updateTextAndStatus(long candidateId, String text, String status, String digest);

    Optional<AiParagraphCandidate> updateStatusAndError(long candidateId, String status, String errorCode, String errorMessage);

    Optional<AiParagraphCandidate> markAccepted(long candidateId, UUID paragraphTraceId, long acceptedBy);

    void delete(long candidateId);
}
