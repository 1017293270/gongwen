package com.gongwen.assistant.ai.candidate;

import java.util.List;

public record ParagraphCandidateSectionRequest(
        Long targetNodeId,
        String targetNodeRole,
        String targetNodeTitle,
        Integer sectionIndex,
        String heading,
        List<String> points,
        String candidateText
) {
    public ParagraphCandidateSectionRequest {
        targetNodeRole = normalize(targetNodeRole);
        targetNodeTitle = normalize(targetNodeTitle);
        sectionIndex = sectionIndex == null || sectionIndex < 0 ? 0 : sectionIndex;
        heading = normalize(heading);
        points = points == null
                ? List.of()
                : points.stream()
                .map(ParagraphCandidateSectionRequest::normalize)
                .toList();
        candidateText = candidateText == null ? "" : candidateText.strip();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
