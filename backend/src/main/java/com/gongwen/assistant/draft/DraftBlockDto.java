package com.gongwen.assistant.draft;

public record DraftBlockDto(
        long id,
        String blockType,
        String content,
        int sortOrder
) {
}
