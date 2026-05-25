package com.gongwen.assistant.draft;

public record CreateDraftRequest(
        String documentTypeCode,
        String title
) {
}
