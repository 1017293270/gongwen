package com.gongwen.assistant.ai;

import java.util.List;

public record AiOutlineSection(
        String heading,
        List<String> points
) {
    public AiOutlineSection {
        points = List.copyOf(points);
    }
}
