package com.gongwen.assistant.draft.node;

public record InsertDraftNodeRequest(
        String role,
        Long anchorNodeId,
        String position
) {
}
