package com.gongwen.assistant.ai;

import java.util.List;

public record AiParagraphRequest(
        String heading,
        List<String> points,
        String instruction,
        Integer sortOrder
) {
    public AiParagraphRequest {
        points = points == null ? List.of() : List.copyOf(points);
    }
}
