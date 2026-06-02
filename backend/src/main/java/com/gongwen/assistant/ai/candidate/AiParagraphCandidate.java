package com.gongwen.assistant.ai.candidate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiParagraphCandidate(
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
    public AiParagraphCandidate {
        targetNodeRole = normalize(targetNodeRole);
        targetNodeTitle = normalize(targetNodeTitle);
        heading = normalize(heading);
        points = points == null
                ? List.of()
                : points.stream()
                .map(AiParagraphCandidate::normalize)
                .toList();
        instructionSummary = normalize(instructionSummary);
        candidateText = candidateText == null ? "" : candidateText;
        candidateTextDigest = normalize(candidateTextDigest);
        status = status == null || status.isBlank() ? "PENDING" : status.strip();
        errorCode = normalize(errorCode);
        errorMessage = normalize(errorMessage);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
