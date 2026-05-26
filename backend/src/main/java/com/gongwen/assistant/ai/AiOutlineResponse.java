package com.gongwen.assistant.ai;

import java.util.List;
import java.util.UUID;

public record AiOutlineResponse(
        UUID traceId,
        String titleSuggestion,
        List<AiOutlineSection> sections,
        List<String> missingInformation
) {
    public AiOutlineResponse {
        sections = List.copyOf(sections);
        missingInformation = List.copyOf(missingInformation);
    }
}
