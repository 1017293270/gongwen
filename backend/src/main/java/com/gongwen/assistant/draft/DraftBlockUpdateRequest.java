package com.gongwen.assistant.draft;

public record DraftBlockUpdateRequest(
        String blockType,
        String content,
        int sortOrder
) {
}
