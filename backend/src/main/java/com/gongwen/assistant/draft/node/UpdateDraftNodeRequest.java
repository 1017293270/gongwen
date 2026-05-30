package com.gongwen.assistant.draft.node;

public record UpdateDraftNodeRequest(
        String content,
        String status
) {
}
