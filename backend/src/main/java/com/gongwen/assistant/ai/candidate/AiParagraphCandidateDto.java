package com.gongwen.assistant.ai.candidate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiParagraphCandidateDto(
        long id,
        long draftId,
        Long targetNodeId,
        String targetNodeRole,
        String targetNodeTitle,
        UUID outlineTraceId,
        UUID paragraphTraceId,
        int sectionIndex,
        String heading,
        List<String> points,
        String instructionSummary,
        String candidateText,
        String candidateTextDigest,
        String status,
        String errorCode,
        String errorMessage,
        Instant acceptedAt,
        Long acceptedBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static AiParagraphCandidateDto from(AiParagraphCandidate candidate) {
        return new AiParagraphCandidateDto(
                candidate.id(),
                candidate.draftId(),
                candidate.targetNodeId(),
                candidate.targetNodeRole(),
                candidate.targetNodeTitle(),
                candidate.outlineTraceId(),
                candidate.paragraphTraceId(),
                candidate.sectionIndex(),
                candidate.heading(),
                candidate.points(),
                candidate.instructionSummary(),
                candidate.candidateText(),
                candidate.candidateTextDigest(),
                candidate.status(),
                candidate.errorCode(),
                candidate.errorMessage(),
                candidate.acceptedAt(),
                candidate.acceptedBy(),
                candidate.createdAt(),
                candidate.updatedAt()
        );
    }
}
