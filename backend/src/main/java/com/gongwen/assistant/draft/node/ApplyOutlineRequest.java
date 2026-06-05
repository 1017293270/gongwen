package com.gongwen.assistant.draft.node;

import com.gongwen.assistant.ai.AiOutlineSection;

import java.util.List;
import java.util.UUID;

public record ApplyOutlineRequest(
        UUID outlineTraceId,
        String titleSuggestion,
        List<AiOutlineSection> sections
) {
    public ApplyOutlineRequest {
        titleSuggestion = titleSuggestion == null ? "" : titleSuggestion.strip();
        sections = sections == null ? List.of() : sections.stream()
                .filter(section -> section != null)
                .toList();
    }
}
