package com.gongwen.assistant.draft.node;

import java.util.List;

public record ApplyOutlineResponse(
        List<DraftNodeDto> nodes,
        List<AppliedOutlineSectionTarget> sectionTargets,
        List<String> formattingWarnings
) {
    public ApplyOutlineResponse {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        sectionTargets = sectionTargets == null ? List.of() : List.copyOf(sectionTargets);
        formattingWarnings = formattingWarnings == null
                ? List.of()
                : formattingWarnings.stream()
                .filter(warning -> warning != null && !warning.isBlank())
                .map(String::strip)
                .toList();
    }
}
