package com.gongwen.assistant.ai;

import java.util.List;
import java.util.Objects;

public record AiOutlineSection(
        String heading,
        List<String> points,
        int level,
        List<String> sourceRefs
) {
    public AiOutlineSection(String heading, List<String> points) {
        this(heading, points, 0, List.of());
    }

    public AiOutlineSection {
        heading = heading == null ? "" : heading.strip();
        points = points == null
                ? List.of()
                : points.stream().filter(Objects::nonNull).map(String::strip).filter(value -> !value.isBlank()).toList();
        sourceRefs = sourceRefs == null
                ? List.of()
                : sourceRefs.stream().filter(Objects::nonNull).map(String::strip).filter(value -> !value.isBlank()).toList();
    }
}
