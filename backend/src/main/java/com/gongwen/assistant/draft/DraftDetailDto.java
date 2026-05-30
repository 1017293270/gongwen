package com.gongwen.assistant.draft;

import com.gongwen.assistant.draft.node.DraftNodeDto;

import java.util.List;

public record DraftDetailDto(
        long id,
        String documentTypeCode,
        String title,
        String status,
        Long templateVersionId,
        List<DraftBlockDto> blocks,
        List<DraftNodeDto> nodes
) {
    public DraftDetailDto(long id, String documentTypeCode, String title, String status, List<DraftBlockDto> blocks) {
        this(id, documentTypeCode, title, status, null, blocks, List.of());
    }

    public DraftDetailDto(long id, String documentTypeCode, String title, String status, Long templateVersionId, List<DraftBlockDto> blocks) {
        this(id, documentTypeCode, title, status, templateVersionId, blocks, List.of());
    }

    public DraftDetailDto {
        blocks = List.copyOf(blocks);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }
}
