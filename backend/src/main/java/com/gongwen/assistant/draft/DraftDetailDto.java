package com.gongwen.assistant.draft;

import java.util.List;

public record DraftDetailDto(
        long id,
        String documentTypeCode,
        String title,
        String status,
        Long templateVersionId,
        List<DraftBlockDto> blocks
) {
    public DraftDetailDto(long id, String documentTypeCode, String title, String status, List<DraftBlockDto> blocks) {
        this(id, documentTypeCode, title, status, null, blocks);
    }

    public DraftDetailDto {
        blocks = List.copyOf(blocks);
    }
}
