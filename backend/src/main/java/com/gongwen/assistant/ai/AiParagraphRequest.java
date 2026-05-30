package com.gongwen.assistant.ai;

import java.util.List;

public record AiParagraphRequest(
        String heading,
        List<String> points,
        String instruction,
        Integer sortOrder,
        Long nodeId,
        String nodeRole,
        String nodeTitle,
        String nodeContext
) {
    public AiParagraphRequest(String heading, List<String> points, String instruction, Integer sortOrder) {
        this(heading, points, instruction, sortOrder, null, null, null, null);
    }

    public AiParagraphRequest {
        points = points == null ? List.of() : List.copyOf(points);
    }
}
