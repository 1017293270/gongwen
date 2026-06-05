package com.gongwen.assistant.ai;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AiOutlineResponse(
        UUID traceId,
        String titleSuggestion,
        List<AiOutlineSection> sections,
        List<String> missingInformation,
        List<AiNodeSuggestion> nodeSuggestions
) {
    public AiOutlineResponse(UUID traceId, String titleSuggestion, List<AiOutlineSection> sections, List<String> missingInformation) {
        this(traceId, titleSuggestion, sections, missingInformation, List.of());
    }

    public AiOutlineResponse {
        sections = sections == null
                ? List.of()
                : sections.stream().filter(Objects::nonNull).toList();
        missingInformation = missingInformation == null
                ? List.of()
                : missingInformation.stream().filter(Objects::nonNull).map(String::strip).filter(value -> !value.isBlank()).toList();
        nodeSuggestions = nodeSuggestions == null
                ? List.of()
                : nodeSuggestions.stream().filter(Objects::nonNull).toList();
    }
}
