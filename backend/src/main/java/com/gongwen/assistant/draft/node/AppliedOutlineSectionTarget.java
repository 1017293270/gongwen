package com.gongwen.assistant.draft.node;

public record AppliedOutlineSectionTarget(
        int sectionIndex,
        String heading,
        int level,
        Long headingNodeId,
        Long bodyNodeId
) {
}
