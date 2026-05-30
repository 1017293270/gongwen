package com.gongwen.assistant.ai;

import java.util.List;
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
        sections = List.copyOf(sections);
        missingInformation = List.copyOf(missingInformation);
        nodeSuggestions = nodeSuggestions == null ? List.of() : List.copyOf(nodeSuggestions);
    }
}
