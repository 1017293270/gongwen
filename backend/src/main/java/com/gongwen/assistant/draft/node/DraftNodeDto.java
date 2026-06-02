package com.gongwen.assistant.draft.node;

import java.time.Instant;

public record DraftNodeDto(
        long id,
        long draftId,
        Long structureMappingProfileId,
        String templateNodeKey,
        String parentTemplateNodeKey,
        String nodeType,
        String role,
        String slotKey,
        String title,
        String content,
        int sortOrder,
        String status,
        DraftNodeFormatOverride formatOverride,
        DraftNodeMetadata metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public static DraftNodeDto from(DraftNode node) {
        return new DraftNodeDto(
                node.id(),
                node.draftId(),
                node.structureMappingProfileId(),
                node.templateNodeKey(),
                node.parentTemplateNodeKey(),
                node.nodeType(),
                node.role(),
                node.slotKey(),
                node.title(),
                node.content(),
                node.sortOrder(),
                node.status(),
                node.formatOverride(),
                node.metadata(),
                node.createdAt(),
                node.updatedAt()
        );
    }
}
