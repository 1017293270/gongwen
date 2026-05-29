package com.gongwen.assistant.draft;

public record DraftSummaryDto(
        long id,
        String documentTypeCode,
        String title,
        String status,
        Long templateVersionId,
        String updatedAt
) {
}
